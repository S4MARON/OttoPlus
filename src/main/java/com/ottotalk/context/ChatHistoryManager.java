package com.ottotalk.context;

import com.ottotalk.OttoTalkClient;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verwaltet die sichtbare chat history für OTTOTALK context.
 * Speichert die letzten 20 chat messages mit nem checkbox-state (drin/draußen).
 * Wenn das OTTO overlay offen ist können spieler die checkboxen toggeln, um zu steuern
 * welche messages als context ans LLM gehen.
 */
public class ChatHistoryManager {
    private static final int MAX_HISTORY = 50;

    private static final List<ChatHistoryEntry> history = Collections.synchronizedList(new ArrayList<>());
    private static String lastAddedText = "";
    private static long lastAddedTime = 0;

    // track which messages came from OttoTalk users (contain teh hidden marker)
    private static final int MAX_OTTOTALK_MESSAGES = 50;
    private static final Set<String> ottoTalkUserMessages = Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    /**
     * Neue chat message in die history packen. neue messages sind per default checked (drin).
     */
    public static void addMessage(String sender, String content) {
        String fullMessage = sender.isEmpty() ? content : sender + ": " + content;
        addRawMessage(fullMessage);
    }

    /**
     * Raw text message dazupacken (system messages, action bar, etc.).
     * macht dedupe wenn die gleiche message innerhalb von 2 sekunden zweimal kommt.
     */
    public static void addRawMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;
        // OTTOTALK system messages überspringen
        if (text.contains("[OTTOTALK]")) return;
        // Only track voice channel messages ([Sprechen]/[Fl\u00FCstern]/[Rufen]) for AI history context
        String clean = text.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "");
        if (!clean.startsWith("[Sprechen]") && !clean.startsWith("[Fl\u00FCstern]") && !clean.startsWith("[Rufen]")) return;

        // dedupe: skip wenn der gleiche text grade erst hinzugefügt wurde
        long now = System.currentTimeMillis();
        synchronized (history) {
            if (text.equals(lastAddedText) && (now - lastAddedTime) < 2000) {
                return;
            }
            lastAddedText = text;
            lastAddedTime = now;
            history.add(new ChatHistoryEntry(text, true));
            while (history.size() > MAX_HISTORY) {
                history.remove(0);
            }
        }
    }

    /**
     * checkbox-state ner message am gegebenen index toggeln.
     */
    public static void toggleChecked(int index) {
        if (index >= 0 && index < history.size()) {
            ChatHistoryEntry entry = history.get(index);
            entry.setChecked(!entry.isChecked());
        }
    }

    /**
     * Normalisieren fürs matching: Minecraft format codes raus,
     * trim und alle whitespaces auf single spaces zusammenstauchen.
     */
    private static String normalizeForMatching(String text) {
        // Minecraft format codes raus (§ gefolgt von nem zeichen)
        String stripped = text.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "");
        // trim und alle whitespaces zusammenfassen
        return stripped.trim().replaceAll("\\s+", " ");
    }

    /**
     * ALLES an whitespace rausschmeißen für aggressives matching als fallback.
     */
    private static String stripAllWhitespace(String text) {
        return text.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "").replaceAll("\\s+", "");
    }

    /**
     * checken ob ne message anhand ihres text contents checked ist.
     * macht multi-level matching: erst normalized, dann stripped whitespace.
     */
    public static boolean isCheckedByText(String text) {
        if (text == null || text.isEmpty()) return true;
        String normalized = normalizeForMatching(text);
        String stripped = stripAllWhitespace(text);
        synchronized (history) {
            // level 1: normalized match
            for (ChatHistoryEntry entry : history) {
                if (normalizeForMatching(entry.getMessage()).equals(normalized)) {
                    return entry.isChecked();
                }
            }
            // level 2: stripped-whitespace match
            for (ChatHistoryEntry entry : history) {
                if (stripAllWhitespace(entry.getMessage()).equals(stripped)) {
                    return entry.isChecked();
                }
            }
        }
        return true;
    }

    /**
     * Wenn der name-replacement mixin den angezeigten text ändert, den stored entry mit updaten,
     * damit isCheckedByText / toggleByText gegen den sichtbaren (ersetzten) text matchen.
     */
    public static void updateMessage(String originalText, String replacedText) {
        if (originalText == null || replacedText == null) return;
        String normOrig = normalizeForMatching(originalText);
        String stripOrig = stripAllWhitespace(originalText);
        synchronized (history) {
            for (ChatHistoryEntry entry : history) {
                String stored = normalizeForMatching(entry.getMessage());
                if (stored.equals(normOrig) || stripAllWhitespace(entry.getMessage()).equals(stripOrig)) {
                    entry.setMessage(replacedText);
                    return;
                }
            }
        }
    }

    /**
     * checked state über den text content der message toggeln.
     * macht multi-level matching: normalized -> stripped whitespace.
     * wenns dann immer noch nicht gefunden wird, eintrag hinzufügen, damit die checkbox wenigstens irgendwie geht.
     */
    public static void toggleByText(String text) {
        if (text == null || text.isEmpty()) return;
        String normalized = normalizeForMatching(text);
        String stripped = stripAllWhitespace(text);
        synchronized (history) {
            // Level 1: normalized match
            for (ChatHistoryEntry entry : history) {
                if (normalizeForMatching(entry.getMessage()).equals(normalized)) {
                    entry.setChecked(!entry.isChecked());
                    return;
                }
            }
            // Level 2: stripped-whitespace match
            for (ChatHistoryEntry entry : history) {
                if (stripAllWhitespace(entry.getMessage()).equals(stripped)) {
                    entry.setChecked(!entry.isChecked());
                    return;
                }
            }
        }
    }

    /**
     * alle history entries holen (neueste am ende).
     */
    public static List<ChatHistoryEntry> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * Get the number of entries.
     */
    public static int size() {
        return history.size();
    }

    /**
     * Baut den context-string nur aus checked messages.
     * Eigener spieler wird mit dem rolennamen aus der config gelabelt (Titel + Name).
     * Andere spieler kriegen ihre erste tooltip-zeile (Titel + Rollenname) wenn vorhanden,
     * sonst halt den gamertag. keine anonymisierung.
     * oben gibts noch nen participants-block mit den vollen tooltip infos.
     */
    public static String getCheckedContextString() {
        StringBuilder sb = new StringBuilder();
        java.util.LinkedHashMap<String, String> nameMap = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, String> gamertagToAlias = new java.util.LinkedHashMap<>();
        String ownName = null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSession() != null) {
            ownName = client.getSession().getUsername();
        }
        // eigenes label kombiniert die konfigurierte rolle und den charakternamen (z.b. "Graf Hans von Burg")
        String ownLabel = buildOwnLabel(ownName);
        synchronized (history) {
            int maxCtx = OttoTalkClient.getConfig().maxContextMessages;
            // checked entries einsammeln, neueste zuerst, capped auf maxContextMessages
            java.util.List<ChatHistoryEntry> checked = new java.util.ArrayList<>();
            for (int i = history.size() - 1; i >= 0 && checked.size() < maxCtx; i--) {
                if (history.get(i).isChecked()) checked.add(history.get(i));
            }
            java.util.Collections.reverse(checked); // chronologische reihenfolge wiederherstellen
            for (ChatHistoryEntry entry : checked) {
                {
                    String msg = entry.getMessage();
                    boolean isSelf = false;
                    
                    // prüfen ob der eigene name in der message vorkommt
                    if (ownName != null && !ownName.isEmpty() && msg.contains(ownName)) {
                        isSelf = true;
                    }
                    
                    // versuchen spielernamen in verschiedenen formaten zu erkennen und zu anonymisieren
                    // format 1: <PlayerName> message (vanilla)
                    if (msg.startsWith("<") && msg.contains("> ")) {
                        int end = msg.indexOf("> ");
                        String playerName = msg.substring(1, end);
                        if (isSelf || playerName.equals(ownName)) {
                            msg = ownLabel + ": " + msg.substring(end + 2);
                        } else {
                            String alias = resolveWithTitle(nameMap, playerName, "");
                            if (!nameMap.containsKey(playerName)) {
                                nameMap.put(playerName, alias);
                                gamertagToAlias.put(playerName, alias);
                            }
                            msg = nameMap.get(playerName) + ": " + msg.substring(end + 2);
                        }
                    }
                    // format 2: [Rang] Name » message oder Name » message
                    else if (msg.contains(" \u00BB ")) {
                        int sep = msg.indexOf(" \u00BB ");
                        String prefix = msg.substring(0, sep).trim();
                        String content = msg.substring(sep + 3).trim();
                        if (isSelf) {
                            msg = ownLabel + ": " + content;
                        } else {
                            String nameOnly = extractNameFromPrefix(prefix);
                            String rank = extractRankFromPrefix(prefix);
                            if (!nameMap.containsKey(prefix)) {
                                String alias = resolveWithTitle(nameMap, nameOnly, rank);
                                nameMap.put(prefix, alias);
                                gamertagToAlias.put(nameOnly, alias);
                            }
                            msg = nameMap.get(prefix) + ": " + content;
                        }
                    }
                    // format 3: Name: message (einfach)
                    else if (msg.contains(": ") && msg.indexOf(": ") < 40) {
                        int sep = msg.indexOf(": ");
                        String prefix = msg.substring(0, sep).trim();
                        String content = msg.substring(sep + 2).trim();
                        if (isSelf) {
                            msg = ownLabel + ": " + content;
                        } else {
                            String nameOnly = extractNameFromPrefix(prefix);
                            String rank = extractRankFromPrefix(prefix);
                            if (!nameMap.containsKey(prefix)) {
                                String alias = resolveWithTitle(nameMap, nameOnly, rank);
                                nameMap.put(prefix, alias);
                                gamertagToAlias.put(nameOnly, alias);
                            }
                            msg = nameMap.get(prefix) + ": " + content;
                        }
                    }
                    // kein bekanntes format, trotzdem auf self prüfen
                    else if (isSelf) {
                        msg = ownLabel + ": " + msg;
                    }
                    
                    sb.append(msg).append("\n");
                }
            }
        }
        
        StringBuilder participants = new StringBuilder();
        if (ownName != null) {
            String ownTooltip = CharacterNameResolver.getCharacterInfo(ownName);
            if (ownTooltip != null && !ownTooltip.isEmpty()) {
                participants.append(ownLabel).append(": ").append(ownTooltip.replace("\n", " / ")).append("\n");
            }
        }
        for (java.util.Map.Entry<String, String> e : gamertagToAlias.entrySet()) {
            String tooltip = CharacterNameResolver.getCharacterInfo(e.getKey());
            if (tooltip != null && !tooltip.isEmpty()) {
                participants.append(e.getValue()).append(": ").append(tooltip.replace("\n", " / ")).append("\n");
            }
        }
        
        if (participants.length() > 0) {
            return "Beteiligte Personen:\n" + participants.toString().trim()
                    + "\n\nGesprächsverlauf:\n" + sb.toString().trim();
        }
        return sb.toString().trim();
    }
    
    /**
     * Extract rank/title that appears before the player name in a prefix string.
     * E.g. "Graf Spieler1" with ownName "Spieler1" returns "Graf"
     */
    private static String extractRankBeforeName(String prefix, String name) {
        if (name == null || !prefix.contains(name)) return "";
        int idx = prefix.indexOf(name);
        if (idx > 0) {
            return prefix.substring(0, idx).trim();
        }
        return "";
    }
    
    /**
     * Versucht nen rang/titel aus nem prefix rauszuziehen (z.b. "[Graf] Name" oder "Graf Name").
     * Bekannte ränge werden erkannt und zurückgegeben.
     */
    private static String extractRankFromPrefix(String prefix) {
        // klammern wegmachen falls da
        String cleaned = prefix.replaceAll("[\\[\\]]", "").trim();
        String[] knownRanks = {"Kaiser", "K\u00f6nig", "Herzog", "F\u00fcrst", "Markgraf", "Landgraf", 
                               "Graf", "Vizegraf", "Baron", "Freiherr", "Ritter", "Edler",
                               "B\u00fcrger", "Bauer", "Knecht", "Magd", "Priester", "Bischof",
                               "Abt", "M\u00f6nch", "Kaufmann", "Handwerker", "Wirt"};
        for (String rank : knownRanks) {
            if (cleaned.startsWith(rank + " ") || cleaned.startsWith(rank)) {
                return rank;
            }
        }
        // bracket format wie [Graf] auch prüfen
        if (prefix.contains("[") && prefix.contains("]")) {
            int start = prefix.indexOf("[") + 1;
            int end = prefix.indexOf("]");
            if (end > start) {
                return prefix.substring(start, end).trim();
            }
        }
        return "";
    }
    
    /**
     * Baut das "ICH" label für den eigenen spieler aus der config (Titel + Rollenname).
     * fallback: erste tooltip-zeile, dann gamertag.
     */
    private static String buildOwnLabel(String ownGamertag) {
        com.ottotalk.config.OttoTalkConfig cfg = com.ottotalk.OttoTalkClient.getConfig();
        String role = (cfg.characterRole != null) ? cfg.characterRole.trim() : "";
        String name = (cfg.characterName != null) ? cfg.characterName.trim() : "";
        if (!role.isEmpty() && !name.isEmpty()) return role + " " + name;
        if (!name.isEmpty()) return name;
        if (!role.isEmpty()) return role;
        // fallback: erste tooltip zeile oder halt der gamertag
        if (ownGamertag != null) {
            String info = CharacterNameResolver.getCharacterInfo(ownGamertag);
            if (info != null && !info.isEmpty()) return info.split("\n")[0].trim();
            return ownGamertag;
        }
        return "ICH";
    }

    /**
     * Resolved nen spielernamen zu seinem vollen Rollennamen inkl. Titel.
     * nimmt die erste tooltip-zeile (z.b. "Graf Hans von Burg") wenn vorhanden.
     * fallback: gamertag, ohne anonymisierung.
     */
    private static String resolveWithTitle(java.util.LinkedHashMap<String, String> nameMap, String playerName, String rank) {
        String charInfo = CharacterNameResolver.getCharacterInfo(playerName);
        if (charInfo != null && !charInfo.isEmpty()) {
            // die komplette erste zeile nehmen: "Titel Vorname Nachname"
            return charInfo.split("\n")[0].trim();
        }
        // keine tooltip info, gamertag direkt nehmen und rang anhängen wenn vorhanden
        return playerName + (rank.isEmpty() ? "" : " (" + rank + ")");
    }

    /**
     * holt den spielernamen aus nem prefix wie "[Graf] SpielerName" oder "Graf SpielerName".
     */
    private static String extractNameFromPrefix(String prefix) {
        String rank = extractRankFromPrefix(prefix);
        if (!rank.isEmpty()) {
            String cleaned = prefix.replaceAll("[\\[\\]]", "").trim();
            if (cleaned.startsWith(rank)) {
                return cleaned.substring(rank.length()).trim();
            }
        }
        // kein rang gefunden, dann ist vielleicht der ganze prefix der name
        return prefix.replaceAll("[\\[\\]]", "").trim();
    }

    /**
     * Get the number of checked (selected) entries.
     */
    public static int getCheckedCount() {
        int count = 0;
        synchronized (history) {
            for (ChatHistoryEntry entry : history) {
                if (entry.isChecked()) count++;
            }
        }
        return count;
    }

    /**
     * checkt ob ein message text von nem OttoTalk user gesendet wurde.
     */
    public static boolean isOttoTalkUserMessage(String text) {
        if (text == null || text.isEmpty()) return false;
        if (ottoTalkUserMessages.isEmpty()) return false;
        String normalized = normalizeForMatching(text);
        if (ottoTalkUserMessages.contains(normalized)) return true;
        String stripped = stripAllWhitespace(text);
        for (String stored : ottoTalkUserMessages) {
            if (stripAllWhitespace(stored).equals(stripped)) return true;
        }
        return false;
    }

    /**
     * Get the number of tracked OttoTalk user messages.
     */
    public static int getOttoTalkUserMessageCount() {
        return ottoTalkUserMessages.size();
    }

    /**
     * komplette history leeren.
     */
    public static void clear() {
        history.clear();
        ottoTalkUserMessages.clear();
    }

    /**
     * Ein einzelner chat history eintrag mit checkbox-state.
     */
    public static class ChatHistoryEntry {
        private String message;
        private boolean checked;

        public ChatHistoryEntry(String message, boolean checked) {
            this.message = message;
            this.checked = checked;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }
    }
}
