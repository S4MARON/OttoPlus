package com.ottotalk.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ottotalk.OttoTalkClient;
import com.ottotalk.config.OttoTalkConfig;
import okhttp3.*;

import com.ottotalk.gui.RoleplayStateManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AIApiClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    
    private final OkHttpClient client;
    private final OttoTalkConfig config;
    
    public AIApiClient(OttoTalkConfig config) {
        this.config = config;
        this.client = new OkHttpClient.Builder()
            .build();
    }
    
    public CompletableFuture<List<String>> generateMedievalOptions(String modernText, String context) {
        return generateMedievalOptions(modernText, context, RoleplayStateManager.getSpeechMode());
    }
    
    public CompletableFuture<List<String>> generateMedievalOptions(String modernText, String context, RoleplayStateManager.SpeechMode mode) {
        if (!config.isApiConfigured()) {
            CompletableFuture<List<String>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("API nicht konfiguriert. Bitte API-Key in der Konfiguration setzen."));
            return future;
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String provider = detectProvider();
                if ("gemini".equals(provider)) {
                    return callGeminiAPI(modernText, context, mode);
                } else {
                    return callOpenAIAPI(modernText, context, mode);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private String detectProvider() {
        // URL gewinnt immer über stored provider, verhindert mismatched config 401s
        if (config.apiUrl != null) {
            if (config.apiUrl.contains("generativelanguage.googleapis.com")) return "gemini";
            if (config.apiUrl.contains("api.openai.com") || config.apiUrl.contains("/v1/chat/completions")) return "openai";
        }
        if (config.apiProvider != null && !config.apiProvider.isEmpty()) return config.apiProvider;
        return "openai";
    }
    
    private List<String> callGeminiAPI(String modernText, String context, RoleplayStateManager.SpeechMode mode) throws IOException {
        String prompt = buildGeminiPrompt(modernText, context, mode);
        
        JsonObject requestBody = new JsonObject();

        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        requestBody.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", config.temperature);
        generationConfig.addProperty("maxOutputTokens", config.maxTokens);
        requestBody.add("generationConfig", generationConfig);
        
        RequestBody body = RequestBody.create(GSON.toJson(requestBody), JSON);
        
        // Gemini will den API key als query parameter
        String urlWithKey = config.apiUrl + "?key=" + config.apiKey;
        
        Request request = new Request.Builder()
            .url(urlWithKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build();
        
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                OttoTalkClient.LOGGER.error("Gemini API error body: " + errBody);
                if (response.code() == 429) {
                    throw new IOException("API-Limit erreicht. Bitte warte einen Moment und versuche es erneut.");
                }
                throw new IOException("Gemini API request failed: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            return parseGeminiResponse(responseBody);
        }
    }
    
    private List<String> callOpenAIAPI(String modernText, String context, RoleplayStateManager.SpeechMode mode) throws IOException {
        String systemPrompt = buildSystemPrompt(mode);
        String userPrompt = buildUserPrompt(modernText, context, mode);
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.model);
        requestBody.addProperty("temperature", config.temperature);
        requestBody.addProperty("max_tokens", config.maxTokens);
        
        JsonArray messages = new JsonArray();
        
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);
        
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);
        
        requestBody.add("messages", messages);
        
        RequestBody body = RequestBody.create(GSON.toJson(requestBody), JSON);
        
        Request.Builder requestBuilder = new Request.Builder()
            .url(config.apiUrl)
            .addHeader("Content-Type", "application/json")
            .post(body);
        
        // auth header dranhängen wenn der key gesetzt ist (für lokale LLMs nicht nötig)
        if (config.apiKey != null && !config.apiKey.isEmpty() 
                && !"not-needed-for-local".equals(config.apiKey)) {
            requestBuilder.addHeader("Authorization", "Bearer " + config.apiKey);
        }
        
        Request request = requestBuilder.build();
        
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                OttoTalkClient.LOGGER.error("OpenAI API error body: " + errBody);
                if (response.code() == 429) {
                    throw new IOException("API-Limit erreicht. Bitte warte einen Moment und versuche es erneut.");
                }
                throw new IOException("OpenAI API request failed: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            return parseOpenAIResponse(responseBody);
        }
    }
    
    private String buildSystemPrompt(RoleplayStateManager.SpeechMode mode) {
        StringBuilder sb = new StringBuilder();
        boolean emoteMode = RoleplayStateManager.isEmoteModeEnabled();
        
        sb.append("Du bist ein sprachlicher Assistent f\u00fcr ein mittelalterliches Rollenspiel.\n\n");
        
        if (emoteMode) {
            sb.append("[EMOTE-MODUS] \u2013 Nur Aktionen/Status\n");
            sb.append("Deine Aufgabe: Formuliere NUR eine kurze Handlung/Aktion/Geste in *Sternchen*.\n");
            sb.append("Der Spieler gibt dir eine Beschreibung oder Anweisung, und du gibst NUR die Aktion zur\u00fcck.\n\n");
            sb.append("Regeln:\n");
            sb.append("- Gib NUR Emotes/Aktionen in *Sternchen* aus, z.B. *atmet tief ein*, *z\u00f6gert*, *nickt langsam*.\n");
            sb.append("- KEIN gesprochener Text, KEINE Anf\u00fchrungszeichen, KEINE W\u00f6rter au\u00dferhalb der Sternchen.\n");
            sb.append("- Halte die Aktionen kurz und nat\u00fcrlich (max. 5-8 W\u00f6rter).\n");
            sb.append("- Die Aktionen sollen zum mittelalterlichen Setting passen.\n");
            sb.append("- Sei kreativ und abwechslungsreich.\n");
        } else if (mode == RoleplayStateManager.SpeechMode.REDE) {
            sb.append("[REDE] \u2013 \u00dcbersetzungsmodus\n");
            sb.append("\u00dcbertrage den Text des Spielers in leicht altertümliches, aber natürlich klingendes Deutsch.\n\n");
            sb.append("Regeln:\n");
            sb.append("- Bewahre Bedeutung und Tonfall der Eingabe.\n");
            sb.append("- Korrigiere Rechtschreibung und Grammatik.\n");
            sb.append("- Der Stil soll natürlich und alltagstauglich klingen, nur leicht altertümlich gefärbt.\n");
            sb.append("- VERMEIDE gestelzte, übertrieben poetische oder theatralische Formulierungen.\n");
            sb.append("- VERMEIDE Wörter wie: wohlan, fürwahr, wahrlich, so sei es, gestattet, gewähren, vermögen, kundtun, darob.\n");
            sb.append("- Verwende keine modernen Slang-Begriffe, aber bleibe nah an normalem Deutsch.\n");
            sb.append("- Füge keine neuen Inhalte hinzu.\n");
        } else {
            sb.append("[ANWEISUNG] \u2013 Erstellungsmodus\n");
            sb.append("Der Spieler gibt dir eine kurze Anweisung/Aufgabe (z.B. 'feilschen', 'lange Geschichte erzählen', 'drohen', 'schmeicheln').\n");
            sb.append("Deine Aufgabe: FÜHRE die Anweisung AUS. Erstelle den Text, den der Charakter im Rollenspiel sagen/tun würde.\n\n");
            sb.append("Regeln:\n");
            sb.append("- Die Anweisung ist ein BEFEHL an dich, KEINE Aussage des Charakters.\n");
            sb.append("- 'feilschen' = schreibe einen Text in dem der Charakter feilscht/handelt.\n");
            sb.append("- 'lange Geschichte' = schreibe eine längere Erzählung/Geschichte die der Charakter erzählt.\n");
            sb.append("- 'drohen' = schreibe eine Drohung die der Charakter ausspricht.\n");
            sb.append("- Nutze den Gesprächsverlauf als Kontext für passende Inhalte.\n");
            sb.append("- Der Stil soll natürlich und alltagstauglich klingen, nur leicht altertümlich gefärbt.\n");
            sb.append("- Bei 'lange Geschichte' oder ähnlichen Anweisungen darf der Text LÄNGER sein (3-5 Sätze).\n");
        }
        
        sb.append("- Schreibe so, wie ein normaler Mensch in einem Mittelalter-Setting reden w\u00fcrde \u2014 nicht wie Shakespeare.\n");
        if (mode == RoleplayStateManager.SpeechMode.REDE) {
            sb.append("- Kurze, nat\u00fcrliche S\u00e4tze (max. 1-2 S\u00e4tze pro Variante).\n");
        }
        if (!emoteMode) {
            sb.append("- F\u00fcge passend kurze Emotes/Aktionen in *Sternchen* ein, z.B. *r\u00e4uspert sich*, *z\u00f6gert*, *l\u00e4chelt*, *nickt*, *senkt die Stimme*. Maximal 1 pro Variante, nur wenn es passt.\n");
        }
        
        sb.append("\nWortregeln (WICHTIG):\n");
        sb.append("- In dieser Spielwelt gibt es das Wort 'rumpfen' (OHNE Umlaut). Es bedeutet eine bestimmte Geste.\n");
        sb.append("- Korrekte Formen: rumpfen, rumpft, Rumpf, Rumpfritter. Schreibe es IMMER mit 'u', NIEMALS mit 'ue' oder Umlaut.\n");
        sb.append("- Wenn der Spieler 'rumpfen' oder 'rumpft' schreibt, belasse es EXAKT so. NICHT korrigieren.\n");
        
        sb.append("\nKontext-Format:\n");
        sb.append("- Im Gespr\u00e4chsverlauf steht 'ICH:' f\u00fcr das, was der Spieler selbst gesagt hat.\n");
        sb.append("- Andere Sprecher sind als 'Person 1', 'Person 2' etc. anonymisiert.\n");
        sb.append("- In Klammern hinter den Sprechern steht ggf. deren Rang, z.B. 'Person 1 (Graf)'.\n");
        sb.append("- WICHTIG: Alle Namen im Kontext sind technische Minecraft-Gamertags, KEINE Rollenspiel-Charakternamen.\n");
        sb.append("- Verwende NIEMALS Spielernamen, Gamertags oder die Bezeichnungen 'Person 1' etc. in deinen Antworten.\n");
        sb.append("- Dein Output enth\u00e4lt NUR den gesprochenen Text des Charakters \u2014 ohne Namen, ohne Anreden mit Spielernamen.\n");
        sb.append("- Beziehe dich inhaltlich auf den Kontext, aber erw\u00e4hne keine Bezeichnungen oder Gamertags.\n");
        
        sb.append("\nAnrede-Regeln (WICHTIG):\n");
        sb.append("- Im Gespr\u00e4chsverlauf steht vor den Spielernamen oft ein Rang (z.B. Graf, Freiherr, Ritter, B\u00fcrger).\n");
        sb.append("- Personen gleichen oder h\u00f6heren Standes: mit 'Ihr/Euch' ansprechen.\n");
        sb.append("- Personen niedrigeren Standes: mit 'du/dich/dir' ansprechen.\n");
        sb.append("- Wenn kein Rang erkennbar ist, gehe von gemeinem Volk aus.\n");
        sb.append("- Passe die Anrede an den Rang des Gegen\u00fcbers an, NICHT pauschal 'Ihr' verwenden.\n");
        
        String charContext = buildCharacterContext();
        if (!charContext.isEmpty()) {
            sb.append(charContext);
        }
        
        if (emoteMode) {
            sb.append("\nErstelle GENAU 3 verschiedene Emote-Varianten. ");
            sb.append("Jede Variante ist NUR eine Aktion in *Sternchen*, KEIN gesprochener Text. ");
            sb.append("Gib NUR die 3 Varianten als nummerierte Liste aus. ");
            sb.append("KEINE Erkl\u00e4rungen, KEINE Kommentare, KEINE Anf\u00fchrungszeichen.");
        } else {
            sb.append("\nErstelle GENAU 3 Varianten mit verschiedenen Tonlagen: ");
            sb.append("1) h\u00f6fisch/f\u00f6rmlich 2) b\u00fcrgerlich/neutral 3) volkst\u00fcmlich/derb. ");
            sb.append("Gib NUR die 3 Varianten als nummerierte Liste aus. ");
            sb.append("KEINE Erkl\u00e4rungen, KEINE Kommentare, KEINE Tags wie [h\u00f6fisch] oder [derb], KEINE Anf\u00fchrungszeichen um die S\u00e4tze.");
        }
        return sb.toString();
    }
    
    private String buildUserPrompt(String modernText, String context, RoleplayStateManager.SpeechMode mode) {
        StringBuilder prompt = new StringBuilder();
        boolean emoteMode = RoleplayStateManager.isEmoteModeEnabled();
        
        if (context != null && !context.trim().isEmpty()) {
            prompt.append("Gespr\u00e4chsverlauf (ICH = der Spieler selbst, Personen sind anonymisiert):\n").append(context).append("\n\n");
        }
        
        if (emoteMode) {
            prompt.append("[EMOTE] \"").append(modernText).append("\"\n");
            prompt.append("\n3 Emote-Varianten (NUR *Aktionen* in Sternchen, KEIN gesprochener Text):\n");
        } else if (mode == RoleplayStateManager.SpeechMode.REDE) {
            prompt.append("[REDE] \"").append(modernText).append("\"\n");
            prompt.append("\n3 Varianten (NUR den gesprochenen Text, optional mit *Emote*, KEINE Tags, KEINE Anf\u00fchrungszeichen):\n");
        } else {
            prompt.append("[ANWEISUNG] \"").append(modernText).append("\"\n");
            prompt.append("\nF\u00fchre die Anweisung aus! 3 Varianten (der Charakter MACHT was die Anweisung sagt, optional mit *Emote*, KEINE Tags, KEINE Anf\u00fchrungszeichen):\n");
        }
        prompt.append("1.\n2.\n3.");
        
        return prompt.toString();
    }
    
    private List<String> parseOpenAIResponse(String responseBody) {
        List<String> options = new ArrayList<>();
        
        try {
            JsonObject response = GSON.fromJson(responseBody, JsonObject.class);
            JsonArray choices = response.getAsJsonArray("choices");
            
            if (choices != null && choices.size() > 0) {
                JsonObject firstChoice = choices.get(0).getAsJsonObject();
                JsonObject message = firstChoice.getAsJsonObject("message");
                String text = message.get("content").getAsString();

                String[] lines = text.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.matches("^\\d+\\.\\s+.*")) {
                        String option = line.replaceFirst("^\\d+\\.\\s+", "").trim();
                        option = cleanOption(option);
                        if (!option.isEmpty()) {
                            options.add(option);
                        }
                    }
                }
            }
        } catch (Exception e) {
            OttoTalkClient.LOGGER.error("Failed to parse OpenAI API response: " + responseBody, e);
        }
        
        // Fallback if parsing failed
        if (options.isEmpty()) {
            options.add("Vergebt mir, die Worte wollen mir nicht gelingen.");
            options.add("Möge Euch meine Botschaft dennoch erreichen.");
            options.add("Die Zunge versagt mir in diesem Moment.");
        }
        
        return options;
    }
    
    /**
     * LLM-artefakte aus ner option rauswerfen: [tags], anführungszeichen drumrum, asterisks, etc.
     */
    private String cleanOption(String option) {
        if (option == null || option.isEmpty()) return option;
        // tags wie [höfisch/förmlich], [bürgerlich/neutral], [volkstümlich/derb] usw. raus
        option = option.replaceAll("\\[.*?\\]\\s*", "").trim();
        // Remove surrounding quotes (", ', «, »)
        option = option.replaceAll("^[\"'\u00AB\u00BB\u201E\u201C]+|[\"'\u00AB\u00BB\u201D]+$", "").trim();
        // markdown bold (** oder ***) weg, einzelne * für emotes wie *räuspert sich* behalten
        option = option.replaceAll("\\*{2,}", "").trim();
        return option;
    }
    
    private String buildCharacterContext() {
        StringBuilder sb = new StringBuilder();
        boolean hasInfo = false;
        
        if (config.characterName != null && !config.characterName.isEmpty()) {
            sb.append("\nDer Spieler spielt den Charakter \"" + config.characterName + "\". ");
            hasInfo = true;
        }
        if (config.characterRole != null && !config.characterRole.isEmpty()) {
            sb.append(hasInfo ? "" : "\n");
            sb.append("Der Charakter ist ein/eine " + config.characterRole + ". ");
            hasInfo = true;
        }
        if (config.characterBackground != null && !config.characterBackground.isEmpty()) {
            sb.append(hasInfo ? "" : "\n");
            sb.append("Hintergrund: " + config.characterBackground + ". ");
            hasInfo = true;
        }
        if (config.additionalInstructions != null && !config.additionalInstructions.isEmpty()) {
            sb.append(hasInfo ? "" : "\n");
            sb.append("Spezielle Anweisungen des Spielers: " + config.additionalInstructions + " ");
            hasInfo = true;
        }
        if (hasInfo) {
            sb.append("Passe die Antworten an den Stand und die Pers\u00f6nlichkeit dieses Charakters an. ");
            sb.append("Der Rang des Charakters bestimmt, wen er mit 'Ihr' und wen mit 'du' anspricht. ");
        }
        return sb.toString();
    }
    
    private String buildGeminiPrompt(String modernText, String context, RoleplayStateManager.SpeechMode mode) {
        StringBuilder prompt = new StringBuilder();
        boolean emoteMode = RoleplayStateManager.isEmoteModeEnabled();
        
        prompt.append("Du bist ein sprachlicher Assistent f\u00fcr ein mittelalterliches Rollenspiel.\n\n");
        
        if (emoteMode) {
            prompt.append("[EMOTE-MODUS] \u2013 Nur Aktionen/Status\n");
            prompt.append("Deine Aufgabe: Formuliere NUR eine kurze Handlung/Aktion/Geste in *Sternchen*.\n");
            prompt.append("Der Spieler gibt dir eine Beschreibung oder Anweisung, und du gibst NUR die Aktion zur\u00fcck.\n\n");
            prompt.append("Regeln:\n");
            prompt.append("- Gib NUR Emotes/Aktionen in *Sternchen* aus, z.B. *atmet tief ein*, *z\u00f6gert*, *nickt langsam*.\n");
            prompt.append("- KEIN gesprochener Text, KEINE Anf\u00fchrungszeichen, KEINE W\u00f6rter au\u00dferhalb der Sternchen.\n");
            prompt.append("- Halte die Aktionen kurz und nat\u00fcrlich (max. 5-8 W\u00f6rter).\n");
            prompt.append("- Die Aktionen sollen zum mittelalterlichen Setting passen.\n");
            prompt.append("- Sei kreativ und abwechslungsreich.\n");
        } else if (mode == RoleplayStateManager.SpeechMode.REDE) {
            prompt.append("[REDE] \u2013 \u00dcbersetzungsmodus\n");
            prompt.append("\u00dcbertrage den Text des Spielers in leicht altert\u00fcmliches, aber nat\u00fcrlich klingendes Deutsch.\n\n");
            prompt.append("Regeln:\n");
            prompt.append("- Bewahre Bedeutung und Tonfall der Eingabe.\n");
            prompt.append("- Korrigiere Rechtschreibung und Grammatik.\n");
            prompt.append("- Der Stil soll nat\u00fcrlich und alltagstauglich klingen, nur leicht altert\u00fcmlich gef\u00e4rbt.\n");
            prompt.append("- VERMEIDE gestelzte, \u00fcbertrieben poetische oder theatralische Formulierungen.\n");
            prompt.append("- VERMEIDE W\u00f6rter wie: wohlan, f\u00fcrwahr, wahrlich, so sei es, gestattet, gew\u00e4hren, verm\u00f6gen, kundtun, darob.\n");
            prompt.append("- Verwende keine modernen Slang-Begriffe, aber bleibe nah an normalem Deutsch.\n");
            prompt.append("- F\u00fcge keine neuen Inhalte hinzu.\n");
        } else {
            prompt.append("[ANWEISUNG] \u2013 Erstellungsmodus\n");
            prompt.append("Der Spieler gibt dir eine kurze Anweisung/Aufgabe (z.B. 'feilschen', 'lange Geschichte erz\u00e4hlen', 'drohen', 'schmeicheln').\n");
            prompt.append("Deine Aufgabe: F\u00dcHRE die Anweisung AUS. Erstelle den Text, den der Charakter im Rollenspiel sagen/tun w\u00fcrde.\n\n");
            prompt.append("Regeln:\n");
            prompt.append("- Die Anweisung ist ein BEFEHL an dich, KEINE Aussage des Charakters.\n");
            prompt.append("- 'feilschen' = schreibe einen Text in dem der Charakter feilscht/handelt.\n");
            prompt.append("- 'lange Geschichte' = schreibe eine l\u00e4ngere Erz\u00e4hlung/Geschichte die der Charakter erz\u00e4hlt.\n");
            prompt.append("- 'drohen' = schreibe eine Drohung die der Charakter ausspricht.\n");
            prompt.append("- Nutze den Gespr\u00e4chsverlauf als Kontext f\u00fcr passende Inhalte.\n");
            prompt.append("- Der Stil soll nat\u00fcrlich und alltagstauglich klingen, nur leicht altert\u00fcmlich gef\u00e4rbt.\n");
            prompt.append("- Bei 'lange Geschichte' oder \u00e4hnlichen Anweisungen darf der Text L\u00c4NGER sein (3-5 S\u00e4tze).\n");
            prompt.append("- VERMEIDE gestelzte, \u00fcbertrieben poetische oder theatralische Formulierungen.\n");
            prompt.append("- VERMEIDE W\u00f6rter wie: wohlan, f\u00fcrwahr, wahrlich, so sei es, gestattet, gew\u00e4hren, verm\u00f6gen, kundtun, darob.\n");
            prompt.append("- Verwende keine Meta-Erkl\u00e4rungen.\n");
        }
        
        prompt.append("- Schreibe so, wie ein normaler Mensch in einem Mittelalter-Setting reden w\u00fcrde \u2014 nicht wie Shakespeare.\n");
        if (mode == RoleplayStateManager.SpeechMode.REDE) {
            prompt.append("- Kurze, nat\u00fcrliche S\u00e4tze (max. 1-2 S\u00e4tze pro Variante).\n");
        }
        if (!emoteMode) {
            prompt.append("- F\u00fcge passend kurze Emotes/Aktionen in *Sternchen* ein, z.B. *r\u00e4uspert sich*, *z\u00f6gert*, *l\u00e4chelt*, *nickt*, *senkt die Stimme*. Maximal 1 pro Variante, nur wenn es passt.\n");
        }
        
        prompt.append("\nWortregeln (WICHTIG):\n");
        prompt.append("- In dieser Spielwelt gibt es das Wort 'rumpfen' (OHNE Umlaut). Es bedeutet eine bestimmte Geste.\n");
        prompt.append("- Korrekte Formen: rumpfen, rumpft, Rumpf, Rumpfritter. Schreibe es IMMER mit 'u', NIEMALS mit 'ue' oder Umlaut.\n");
        prompt.append("- Wenn der Spieler 'rumpfen' oder 'rumpft' schreibt, belasse es EXAKT so. NICHT korrigieren.\n");
        
        prompt.append("\nKontext-Format:\n");
        prompt.append("- Im Gespr\u00e4chsverlauf steht 'ICH:' f\u00fcr das, was der Spieler selbst gesagt hat.\n");
        prompt.append("- Andere Sprecher sind als 'Person 1', 'Person 2' etc. anonymisiert.\n");
        prompt.append("- In Klammern hinter den Sprechern steht ggf. deren Rang, z.B. 'Person 1 (Graf)'.\n");
        prompt.append("- WICHTIG: Alle Namen im Kontext sind technische Minecraft-Gamertags, KEINE Rollenspiel-Charakternamen.\n");
        prompt.append("- Verwende NIEMALS Spielernamen, Gamertags oder die Bezeichnungen 'Person 1' etc. in deinen Antworten.\n");
        prompt.append("- Dein Output enth\u00e4lt NUR den gesprochenen Text des Charakters \u2014 ohne Namen, ohne Anreden mit Spielernamen.\n");
        prompt.append("- Beziehe dich inhaltlich auf den Kontext, aber erw\u00e4hne keine Bezeichnungen oder Gamertags.\n");
        
        prompt.append("\nAnrede-Regeln (WICHTIG):\n");
        prompt.append("- Im Gespr\u00e4chsverlauf steht vor den Spielernamen oft ein Rang (z.B. Graf, Freiherr, Ritter, B\u00fcrger).\n");
        prompt.append("- Personen gleichen oder h\u00f6heren Standes: mit 'Ihr/Euch' ansprechen.\n");
        prompt.append("- Personen niedrigeren Standes: mit 'du/dich/dir' ansprechen.\n");
        prompt.append("- Wenn kein Rang erkennbar ist, gehe von gemeinem Volk aus.\n");
        prompt.append("- Passe die Anrede an den Rang des Gegen\u00fcbers an, NICHT pauschal 'Ihr' verwenden.\n");
        
        String charContext = buildCharacterContext();
        if (!charContext.isEmpty()) {
            prompt.append(charContext).append("\n");
        }
        prompt.append("\n");
        
        if (context != null && !context.trim().isEmpty()) {
            prompt.append("Gespr\u00e4chsverlauf (ICH = der Spieler selbst, Personen sind anonymisiert):\n");
            prompt.append(context).append("\n\n");
        }
        
        if (emoteMode) {
            prompt.append("[EMOTE] \"").append(modernText).append("\"\n");
            prompt.append("\n3 Emote-Varianten (NUR *Aktionen* in Sternchen, KEIN gesprochener Text):\n");
        } else if (mode == RoleplayStateManager.SpeechMode.REDE) {
            prompt.append("[REDE] \"").append(modernText).append("\"\n");
            prompt.append("\n3 Varianten (NUR den gesprochenen Text, optional mit *Emote*, KEINE Tags, KEINE Anf\u00fchrungszeichen):\n");
        } else {
            prompt.append("[ANWEISUNG] \"").append(modernText).append("\"\n");
            prompt.append("\nF\u00fchre die Anweisung aus! 3 Varianten (der Charakter MACHT was die Anweisung sagt, optional mit *Emote*, KEINE Tags, KEINE Anf\u00fchrungszeichen):\n");
        }
        prompt.append("1.\n2.\n3.");
        
        if (emoteMode) {
            prompt.append("\nErstelle GENAU 3 verschiedene Emote-Varianten. ");
            prompt.append("Jede Variante ist NUR eine Aktion in *Sternchen*, KEIN gesprochener Text. ");
            prompt.append("KEINE Erkl\u00e4rungen, KEINE Kommentare, KEINE Anf\u00fchrungszeichen.");
        }
        
        return prompt.toString();
    }
    
    private List<String> parseGeminiResponse(String responseBody) {
        List<String> options = new ArrayList<>();
        
        try {
            JsonObject response = GSON.fromJson(responseBody, JsonObject.class);
            JsonArray candidates = response.getAsJsonArray("candidates");
            
            if (candidates != null && candidates.size() > 0) {
                JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                JsonObject content = firstCandidate.getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");
                
                if (parts != null && parts.size() > 0) {
                    JsonObject firstPart = parts.get(0).getAsJsonObject();
                    String text = firstPart.get("text").getAsString();

                    String[] lines = text.split("\n");
                    for (String line : lines) {
                        line = line.trim();
                        if (line.matches("^\\d+\\.\\s+.*")) {
                            String option = line.replaceFirst("^\\d+\\.\\s+", "").trim();
                            option = cleanOption(option);
                            if (!option.isEmpty()) {
                                options.add(option);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            OttoTalkClient.LOGGER.error("Failed to parse Gemini API response", e);
        }
        
        // Fallback if parsing failed
        if (options.isEmpty()) {
            options.add("Vergebt mir, die Worte wollen mir nicht gelingen.");
            options.add("Möge Euch meine Botschaft dennoch erreichen.");
            options.add("Die Zunge versagt mir in diesem Moment.");
        }
        
        return options;
    }
}
