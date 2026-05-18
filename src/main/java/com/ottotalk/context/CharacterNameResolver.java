package com.ottotalk.context;

import com.ottotalk.OttoTalkClient;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import com.ottotalk.gui.ChatCheckboxRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Holt RP-Charakternamen aus den Chat-Hover-Tooltips von Ottonien.
 * Geht den Text-Tree der Messages durch, findet HoverEvents auf Gamertags,
 * baut daraus ne gamertag -> charakterinfo map.
 */
public class CharacterNameResolver {

    // gamertag -> tooltip text (character info aus dem hover)
    private static final Map<String, String> nameCache = Collections.synchronizedMap(new LinkedHashMap<>());

    // Skip-flag für refresh (sonst double-processing im ModifyVariable)
    private static volatile boolean skipReplacement = false;
    public static boolean shouldSkipReplacement() { return skipReplacement; }

    // Mapping replaced Text zu original Text (identity-basiert, fürs zurücksetzen der name replacement)
    private static final Map<Text, Text> replacedToOriginal = Collections.synchronizedMap(new IdentityHashMap<>());

    private static final int MAX_REPLACED_CACHE = 200;

    /** Original text vor dem replacement speichern damit man später wiederherstellen kann. */
    public static void storeOriginal(Text replaced, Text original) {
        if (replacedToOriginal.size() >= MAX_REPLACED_CACHE) {
            // älteste entries rauswerfen, sonst wächst das endlos
            synchronized (replacedToOriginal) {
                if (replacedToOriginal.size() >= MAX_REPLACED_CACHE) {
                    int toRemove = MAX_REPLACED_CACHE / 2;
                    var iter = replacedToOriginal.entrySet().iterator();
                    while (iter.hasNext() && toRemove > 0) {
                        iter.next();
                        iter.remove();
                        toRemove--;
                    }
                }
            }
        }
        replacedToOriginal.put(replaced, original);
    }

    /**
     * Process an incoming chat message Text to extract hover tooltip info.
     * Walks the Text tree and finds components with HoverEvent.SHOW_TEXT.
     * @param allowPersist if true, discovered names are saved permanently to player list
     */
    public static void extractFromMessage(Text message, boolean allowPersist) {
        if (message == null) return;
        walkText(message, allowPersist);
    }

    /** convenience overload: persistet immer (legacy-verhalten). */
    public static void extractFromMessage(Text message) {
        extractFromMessage(message, true);
    }

    private static void walkText(Text text, boolean allowPersist) {
        // den style dieses components nach nem hover event abklappern
        Style style = text.getStyle();
        if (style != null) {
            HoverEvent hover = style.getHoverEvent();
            if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
                Text hoverText = hover.getValue(HoverEvent.Action.SHOW_TEXT);
                if (hoverText != null) {
                    // nur direct content nehmen (NICHT getString(), das zieht die siblings mit!)
                    String displayedName = getDirectContent(text).trim();
                    String tooltipContent = hoverText.getString().trim();
                    if (!displayedName.isEmpty() && !tooltipContent.isEmpty()
                            && !tooltipContent.equals(displayedName)) {
                        String cleanName = stripFormatting(displayedName);
                        if (!cleanName.isEmpty() && cleanName.length() <= 30) {
                            nameCache.put(cleanName, tooltipContent);
                            // persistent player name liste nur updaten wenn der channel das erlaubt
                            if (allowPersist) {
                                String charName = extractNameFromTooltipString(tooltipContent);
                                if (charName != null && !charName.equals(cleanName)) {
                                    PlayerNameList.updateCharacterName(cleanName, charName);
                                }
                                // titel/rang und farbe auch persistieren (erstes gefärbtes segment)
                                String[] rankAndName = extractRankAndNameFromTooltip(hoverText);
                                if (rankAndName != null && rankAndName[0] != null && !rankAndName[0].isEmpty()) {
                                    PlayerNameList.setCharacterTitle(cleanName, rankAndName[0]);
                                    Integer rankColor = extractRankColorFromTooltip(hoverText);
                                    if (rankColor != null) {
                                        PlayerNameList.setCharacterTitleColor(cleanName, rankColor);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // siblings durchgehen
        for (Text sibling : text.getSiblings()) {
            walkText(sibling, allowPersist);
        }
    }

    /**
     * Holt die cached character info für nen gamertag.
     * gibt null zurück wenn kein tooltip info gecached ist.
     */
    public static String getCharacterInfo(String gamertag) {
        if (gamertag == null) return null;
        String clean = stripFormatting(gamertag.trim());
        return nameCache.get(clean);
    }

    /**
     * Get the character name for a gamertag from cached tooltip.
     * On Ottonien, the tooltip structure is:
     *   Line 1: Title/Rank (e.g. "Ritter", "Graf", "Mädchen für alles") - colored differently
     *   Line 2: Actual character name (e.g. "Hans von Burg")
     *   Line 3+: Additional info
     * Returns the gamertag itself if no mapping exists.
     */
    public static String getCharacterName(String gamertag) {
        String info = getCharacterInfo(gamertag);
        if (info == null) return gamertag;
        return extractNameFromTooltipString(info);
    }

    /**
     * Extract the actual character name from a tooltip string.
     * Ottonien tooltip format: "Rank CharFirstName CharLastName\n\nGeschlecht: ..."
     * The rank is the first word on line 1, the rest is the character name.
     */
    private static String extractNameFromTooltipString(String tooltip) {
        // erste zeile holen (vor irgendwelchen newlines)
        String firstLine = tooltip.split("\n")[0].trim();
        // erstes wort = rang, alles danach ist der charaktername
        int spaceIdx = firstLine.indexOf(' ');
        if (spaceIdx > 0 && spaceIdx < firstLine.length() - 1) {
            return firstLine.substring(spaceIdx + 1).trim();
        }
        // kein leerzeichen gefunden, dann halt as-is (einzelwort-name oder kein rang)
        return firstLine;
    }

    /**
     * checken ob wir character info für nen gamertag haben.
     */
    public static boolean hasCharacterInfo(String gamertag) {
        return getCharacterInfo(gamertag) != null;
    }

    /**
     * Get the full name cache (for history context building).
     */
    public static Map<String, String> getNameCache() {
        return Collections.unmodifiableMap(nameCache);
    }

    /**
     * gecachte namen leeren (z.b. beim disconnect).
     */
    public static void clear() {
        nameCache.clear();
        replacedToOriginal.clear();
    }

    /**
     * Replace gamertags with character names in a Text tree.
     * Walks the tree and for any node that has a HoverEvent.SHOW_TEXT,
     * replaces the displayed text content with the first line of the tooltip.
     */
    public static Text replaceNamesInText(Text original) {
        Text replaced = rebuildNode(original);
        com.ottotalk.config.OttoTalkConfig cfg = OttoTalkClient.getConfig();
        return remapTextColors(replaced, cfg);
    }

    /** läuft den Text tree ab und remappt palette RGB werte auf die vom user konfigurierten farben. */
    public static MutableText remapTextColors(Text text, com.ottotalk.config.OttoTalkConfig cfg) {
        Style style = text.getStyle();
        net.minecraft.text.TextColor tc = style != null ? style.getColor() : null;
        MutableText copy = text.copy();
        if (tc != null) {
            int remapped = cfg.mapTitleColor(tc.getRgb());
            if (remapped != (tc.getRgb() & 0x00FFFFFF)) {
                copy.setStyle(style.withColor(net.minecraft.text.TextColor.fromRgb(remapped)));
            }
        }
        java.util.List<Text> sibs = new java.util.ArrayList<>(text.getSiblings());
        copy.getSiblings().clear();
        for (Text sib : sibs) copy.append(remapTextColors(sib, cfg));
        return copy;
    }

    private static MutableText rebuildNode(Text text) {
        Style style = text.getStyle();
        HoverEvent hover = style != null ? style.getHoverEvent() : null;

        // checken ob DIESER node ersetzt werden soll (hat hover mit character info)
        if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
            String directContent = getDirectContent(text);
            Text hoverText = hover.getValue(HoverEvent.Action.SHOW_TEXT);
            if (!directContent.isEmpty() && directContent.length() <= 30 && hoverText != null) {
                String cleanContent = stripFormatting(directContent);
                String[] rankAndName = extractRankAndNameFromTooltip(hoverText);
                if (rankAndName != null) {
                    String charName = rankAndName[1];
                    MutableText node = Text.literal(charName);
                    node.setStyle(style);
                    for (Text sibling : text.getSiblings()) {
                        node.append(rebuildNode(sibling));
                    }
                    return node;
                }
            }
        }

        // kein replacement nötig - originalen content-type per copy() bewahren
        // copy() macht nen deep copy inkl. siblings, also clear und die verarbeiteten siblings wieder dranhängen
        MutableText copy = text.copy();
        List<Text> originalSiblings = new ArrayList<>(text.getSiblings());
        copy.getSiblings().clear();

        for (int i = 0; i < originalSiblings.size(); i++) {
            Text sibling = originalSiblings.get(i);

            // lookahead: wenn das NÄCHSTE sibling ne character name replacement triggern wird,
            // den rank prefix aus DIESEM sibling rauswerfen
            if (i + 1 < originalSiblings.size()) {
                String rank = getRankIfNextReplaced(originalSiblings.get(i + 1));
                if (rank != null) {
                    String directContent = getDirectContent(sibling);
                    // rank (mit trailing space) am ende vom content abschneiden
                    if (directContent.endsWith(rank + " ")) {
                        String stripped = directContent.substring(0, directContent.length() - rank.length() - 1);
                        MutableText mod = Text.literal(stripped);
                        mod.setStyle(sibling.getStyle());
                        for (Text childSib : sibling.getSiblings()) {
                            mod.append(rebuildNode(childSib));
                        }
                        copy.append(mod);
                        continue;
                    } else if (directContent.endsWith(rank)) {
                        String stripped = directContent.substring(0, directContent.length() - rank.length());
                        MutableText mod = Text.literal(stripped);
                        mod.setStyle(sibling.getStyle());
                        for (Text childSib : sibling.getSiblings()) {
                            mod.append(rebuildNode(childSib));
                        }
                        copy.append(mod);
                        continue;
                    }
                }
            }

            copy.append(rebuildNode(sibling));
        }
        return copy;
    }

    /**
     * checkt ob ein text node ne character name replacement triggern wird.
     * Wenn ja, gibt den rang zurück der aus dem davorstehenden sibling raus muss.
     * nutzt farb-basiertes erkennen für multi-word ränge.
     */
    private static String getRankIfNextReplaced(Text text) {
        Style style = text.getStyle();
        if (style == null) return null;
        HoverEvent hover = style.getHoverEvent();
        if (hover == null || hover.getAction() != HoverEvent.Action.SHOW_TEXT) return null;

        String directContent = getDirectContent(text);
        if (directContent.isEmpty() || directContent.length() > 30) return null;

        Text hoverText = hover.getValue(HoverEvent.Action.SHOW_TEXT);
        if (hoverText == null) return null;

        String[] rankAndName = extractRankAndNameFromTooltip(hoverText);
        if (rankAndName != null) {
            return rankAndName[0]; // der rang
        }
        return null;
    }

    /**
     * Extract the color of the rank/title segment from a tooltip (first colored segment).
     * Returns null if no explicit color is found.
     */
    private static Integer extractRankColorFromTooltip(Text hoverText) {
        List<String> contents = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        collectColoredSegments(hoverText, contents, colors, null);
        if (!colors.isEmpty() && colors.get(0) != null) return colors.get(0);
        return null;
    }

    /**
     * Walk the tooltip Text tree and extract rank + character name based on color changes.
     * The rank has one color, the character name has a different color.
     * Only processes the first line (before any newline).
     * Returns String[]{rank, charName} or null if cannot determine.
     */
    private static String[] extractRankAndNameFromTooltip(Text hoverText) {
        List<String> contents = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        collectColoredSegments(hoverText, contents, colors, null);

        if (contents.isEmpty()) return null;

        // farbe vom ersten segment = rank color
        Integer rankColor = colors.get(0);
        StringBuilder rank = new StringBuilder();
        StringBuilder name = new StringBuilder();
        boolean inName = false;

        for (int i = 0; i < contents.size(); i++) {
            String content = contents.get(i);
            Integer color = colors.get(i);

            // bei newline stoppen, nur die erste zeile bearbeiten
            if (content.contains("\n")) {
                String before = content.substring(0, content.indexOf('\n'));
                if (!inName && !Objects.equals(color, rankColor)) {
                    inName = true;
                }
                if (inName) name.append(before);
                else rank.append(before);
                break;
            }

            // farbwechsel = anfang vom charaktername
            if (!inName && !Objects.equals(color, rankColor)) {
                inName = true;
            }

            if (inName) name.append(content);
            else rank.append(content);
        }

        String r = rank.toString().trim();
        String n = name.toString().trim();
        if (n.isEmpty()) {
            // fallback: erstes wort als rang splitten (für single-color tooltips)
            String full = (r + " " + n).trim();
            if (full.isEmpty()) full = hoverText.getString().split("\n")[0].trim();
            int sp = full.indexOf(' ');
            if (sp > 0 && sp < full.length() - 1) {
                r = full.substring(0, sp).trim();
                n = full.substring(sp + 1).trim();
            }
        }

        if (n.isEmpty() || "null".equals(n)) return null;
        return new String[]{r, n};
    }

    /**
     * sammelt text segmente und ihre effektiven farben rekursiv aus nem Text tree.
     * kümmert sich auch um die color inheritance von parent nodes.
     */
    private static void collectColoredSegments(Text text, List<String> contents, List<Integer> colors, Integer parentColor) {
        String direct = getDirectContent(text);
        TextColor tc = text.getStyle().getColor();
        Integer effectiveColor = tc != null ? Integer.valueOf(tc.getRgb()) : parentColor;

        if (!direct.isEmpty()) {
            contents.add(direct);
            colors.add(effectiveColor);
        }
        for (Text sibling : text.getSiblings()) {
            collectColoredSegments(sibling, contents, colors, effectiveColor);
        }
    }

    /**
     * direct text content eines Text nodes holen (ohne siblings).
     */
    private static String getDirectContent(Text text) {
        StringBuilder sb = new StringBuilder();
        text.getContent().visit(s -> {
            sb.append(s);
            return Optional.empty();
        });
        return sb.toString();
    }

    /**
     * den vollen tooltip-text (titel + name) für die anzeige holen.
     * wird genutzt wenn die volle info gebraucht wird, nicht nur der charaktername.
     */
    public static String getFullTooltipText(String gamertag) {
        String info = getCharacterInfo(gamertag);
        return info != null ? info : gamertag;
    }

    /**
     * Minecraft format codes rausnehmen (§ gefolgt von nem zeichen).
     */
    private static String stripFormatting(String text) {
        return text.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "").trim();
    }

    /**
     * Refresh all existing chat messages: apply or revert character name replacements.
     * @param enable true = replace gamertags with RP names, false = restore original gamertags
     */
    public static void refreshChatMessages(boolean enable) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null) return;
        ChatHud chatHud = client.inGameHud.getChatHud();
        if (chatHud == null) return;

        List<ChatHudLine> messages = ChatCheckboxRenderer.chatHudMessages;
        if (messages == null || messages.isEmpty()) return;

        int changed = 0;
        for (int i = 0; i < messages.size(); i++) {
            ChatHudLine line = messages.get(i);
            // per-channel check: even when globally enabled respect per-channel display settings
            boolean channelAllowed = enable
                    && OttoTalkClient.shouldShowNamesForMessage(line.content().getString());
            if (channelAllowed) {
                // gamertags durch charakternamen ersetzen
                Text replaced = replaceNamesInText(line.content());
                if (!replaced.getString().equals(line.content().getString())) {
                    replacedToOriginal.put(replaced, line.content());
                    messages.set(i, new ChatHudLine(line.creationTick(), replaced, line.signature(), line.indicator()));
                    changed++;
                }
            } else {
                // originale gamertags wiederherstellen (global aus ODER channel aus)
                Text original = replacedToOriginal.get(line.content());
                if (original != null) {
                    messages.set(i, new ChatHudLine(line.creationTick(), original, line.signature(), line.indicator()));
                    changed++;
                }
            }
        }

        if (!enable) {
            replacedToOriginal.clear();
        }

        if (changed > 0) {
            try {
                chatHud.reset();
            } catch (Exception e) {
                OttoTalkClient.LOGGER.warn("[CharName] Could not reset chat hud: {}", e.getMessage());
            }
        }
    }
}
