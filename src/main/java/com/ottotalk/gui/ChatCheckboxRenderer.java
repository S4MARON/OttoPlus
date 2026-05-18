package com.ottotalk.gui;

import com.ottotalk.context.ChatHistoryManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import com.ottotalk.OttoTalkClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Zeichnet checkboxen neben chat messages wenn OTTO roleplay mode an ist.
 * Nutzt ChatHuds exakte rendering math um die checkboxen richtig zu positionieren.
 * Ordnet checkboxen den messages über den TEXT CONTENT zu (nicht über index), damit alles
 * ausgerichtet bleibt auch wenn neue messages reinkommen oder system messages dazwischenliegen.
 */
public class ChatCheckboxRenderer {

    private static final Identifier TEX_CHECKBOX_CHECKED = new Identifier("ottotalk", "textures/gui/checkbox_checked.png");
    private static final Identifier TEX_CHECKBOX_UNCHECKED = new Identifier("ottotalk", "textures/gui/checkbox_unchecked.png");
    private static final Identifier TEX_CHECKBOX_LOCKED = new Identifier("ottotalk", "textures/gui/checkbox_locked.png");
    public static final int CHECKBOX_SIZE = 10;
    public static final int CHECKBOX_MARGIN_RIGHT = 2;
    public static final int CHECKBOX_AREA_WIDTH = CHECKBOX_SIZE + CHECKBOX_MARGIN_RIGHT;
    public static final int OU_ICON_AREA_WIDTH = 10; // oU icon (8px) + 2px margin

    // aktuelle chat offsets (vom ChatHudMixin gesetzt, ChatScreenMixin nutzt sie für den hover fix)
    public static int currentXShift = 0;
    public static int currentYOffset = 0;

    // messages liste vom ChatHud (vom ChatHudMixin gesetzt, vom CharacterNameResolver genutzt)
    public static List<ChatHudLine> chatHudMessages = null;

    // hit-areas im screen-space, fürs klick detection gespeichert
    private static final ArrayList<CheckboxHitArea> hitAreas = new ArrayList<>();

    /**
     * plain text aus nem OrderedText rausziehen (Minecrafts rendered text type).
     */
    private static String extractText(OrderedText orderedText) {
        StringBuilder sb = new StringBuilder();
        orderedText.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    /**
     * Render checkboxes using ChatHud's actual visible messages and position math.
     * Called from ChatHudMixin AFTER all matrix pops, so we render in screen coordinates.
     *
     * endOfEntry() marks the LAST/BOTTOM line of a message in visibleMessages.
     * When iterating bottom-to-top (n=0 = newest/bottom):
     *   - endOfEntry=true starts a new message group (it's the bottom line)
     *   - endOfEntry=false lines above it are continuation lines of the same message
     *   - The next endOfEntry=true means the previous group is complete
     */
    public static void renderFromChatHud(
            DrawContext context, ChatHud chatHud,
            List<ChatHudLine.Visible> visibleMessages, int scrolledLines,
            int mouseX, int mouseY, int yOffset) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof ChatScreen)) return;
        if (visibleMessages == null || visibleMessages.isEmpty()) return;

        int scaledHeight = client.getWindow().getScaledHeight();
        double chatScale = chatHud.getChatScale();
        int visibleLineCount = chatHud.getVisibleLineCount();

        // ChatHuds exakte formel für die base Y koordinate (im scaled space)
        int baseY = MathHelper.floor((float)(scaledHeight - 40) / (float)chatScale);

        hitAreas.clear();

        // zeilen die zur aktuellen message-group gehören (bottom-to-top reihenfolge)
        ArrayList<String> lineTexts = new ArrayList<>();
        int bottomLineN = -1; // n-wert der untersten zeile (da wo endOfEntry=true)

        // sichtbare chat zeilen sind bottom-up indiziert (n=0 ist die neueste zeile ganz unten)
        for (int n = 0; n + scrolledLines < visibleMessages.size() && n < visibleLineCount; n++) {
            ChatHudLine.Visible visible = visibleMessages.get(n + scrolledLines);
            String lineText = extractText(visible.content());

            if (visible.endOfEntry()) {
                // das ist die UNTERSTE zeile einer neuen message.
                // wenn grad ne group offen war, ist sie jetzt fertig, also verarbeiten.
                if (!lineTexts.isEmpty() && bottomLineN != -1) {
                    int topLineN = n - 1; // die zeile direkt davor ist die oberste
                    renderCheckboxForMessage(context, lineTexts, bottomLineN, topLineN,
                            baseY, chatScale, yOffset, mouseX, mouseY);
                    lineTexts.clear();
                }
                bottomLineN = n;
                lineTexts.add(lineText);
            } else {
                // continuation zeile (über der bottom-zeile), zur aktuellen group dazu
                lineTexts.add(lineText);
            }
        }

        // letzte group noch verarbeiten (kann incomplete sein wenn die top zeile off-screen ist)
        if (!lineTexts.isEmpty() && bottomLineN != -1) {
            int topLineN = bottomLineN + lineTexts.size() - 1;
            renderCheckboxForMessage(context, lineTexts, bottomLineN, topLineN,
                    baseY, chatScale, yOffset, mouseX, mouseY);
        }
    }

    /**
     * Render a single checkbox for a message and store its hit area.
     * @param lineTexts lines in bottom-to-top order
     * @param bottomLineN n-value of the bottom line
     * @param topLineN n-value of the top line (where name/checkbox goes)
     */
    private static void renderCheckboxForMessage(
            DrawContext context, ArrayList<String> lineTexts,
            int bottomLineN, int topLineN,
            int baseY, double chatScale, int yOffset,
            int mouseX, int mouseY) {

        // umdrehen für top-to-bottom (lesereihenfolge), dann joinen
        ArrayList<String> ordered = new ArrayList<>(lineTexts);
        Collections.reverse(ordered);
        String fullMessageText = String.join(" ", ordered);

        // Only render checkboxes for voice channel messages
        if (!fullMessageText.startsWith("[Sprechen]") && !fullMessageText.startsWith("[Fl\u00FCstern]") && !fullMessageText.startsWith("[Rufen]")) return;

        // checked-state über den text content nachschlagen
        boolean checked = ChatHistoryManager.isCheckedByText(fullMessageText);

        // alle koordinaten im GUI pixel space (nach allen matrix pops)
        int topLineTop = (int)((baseY - topLineN * 9 - 9) * chatScale) - yOffset;
        int topLineBottom = (int)((baseY - topLineN * 9) * chatScale) - yOffset;
        int topLineHeight = topLineBottom - topLineTop;

        // checkbox so groß wie eine chat-zeile
        int cbSize = Math.max(6, (int)(9 * chatScale));
        int cbX = RoleplayStateManager.isDebugMode() ? OU_ICON_AREA_WIDTH : 0;
        int cbY = topLineTop + (topLineHeight - cbSize) / 2;

        // hit area geht über alle zeilen der message
        int hitTop = topLineTop;
        int hitBottom = (int)((baseY - bottomLineN * 9) * chatScale) - yOffset;
        int hitH = hitBottom - hitTop;

        boolean hovered = mouseX >= cbX && mouseX < cbX + CHECKBOX_AREA_WIDTH
                && mouseY >= hitTop && mouseY < hitTop + hitH;

        boolean historyEnabled = RoleplayStateManager.isHistoryEnabled();
        Identifier tex;
        if (!historyEnabled) {
            tex = TEX_CHECKBOX_LOCKED;
        } else if (checked) {
            tex = TEX_CHECKBOX_CHECKED;
        } else {
            tex = TEX_CHECKBOX_UNCHECKED;
        }
        context.drawTexture(tex, cbX, cbY, cbSize, cbSize,
                0, 0, CHECKBOX_SIZE, CHECKBOX_SIZE, CHECKBOX_SIZE, CHECKBOX_SIZE);
        if (hovered && historyEnabled) {
            context.fill(cbX, cbY,
                    cbX + cbSize, cbY + cbSize, 0x33FFFFFF);
        }

        // hit area für click detection speichern
        hitAreas.add(new CheckboxHitArea(cbX, hitTop, CHECKBOX_AREA_WIDTH, hitH, fullMessageText));
    }

    /**
     * mouse-klick auf checkboxen behandeln. gibt true zurück wenn ne checkbox angeklickt wurde.
     */
    public static boolean handleClick(double mouseX, double mouseY) {
        for (CheckboxHitArea area : hitAreas) {
            if (mouseX >= area.x && mouseX <= area.x + area.width
                    && mouseY >= area.y && mouseY <= area.y + area.height) {
                ChatHistoryManager.toggleByText(area.messageText);
                return true;
            }
        }
        return false;
    }

    private static final Identifier TEX_OU_ICON = new Identifier("ottotalk", "textures/gui/oU.png");
    private static final int OU_ICON_SIZE = 10;

    /**
     * Zeichnet das oU.png icon vor usernames bei messages mit dem versteckten OttoTalk marker.
     * wird nur gerufen wenn debug mode an ist.
     */
    public static void renderOttoUserIcons(
            DrawContext context, ChatHud chatHud,
            List<ChatHudLine.Visible> visibleMessages, int scrolledLines,
            int yOffset, boolean roleplayActive, int currentTick) {

        if (!RoleplayStateManager.isDebugMode()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (visibleMessages == null || visibleMessages.isEmpty()) return;
        boolean chatFocused = client.currentScreen instanceof ChatScreen;

        int scaledHeight = client.getWindow().getScaledHeight();
        double chatScale = chatHud.getChatScale();
        int visibleLineCount = chatHud.getVisibleLineCount();
        int baseY = MathHelper.floor((float)(scaledHeight - 40) / (float)chatScale);

        // icon X position: immer am linken rand (chat text wird ja geshiftet damit platz ist)
        int iconX = 0;

        ArrayList<String> lineTexts = new ArrayList<>();
        int bottomLineN = -1;
        // den visible entry der bottom-zeile für die opacity tracken
        ChatHudLine.Visible bottomLineVisible = null;

        for (int n = 0; n + scrolledLines < visibleMessages.size() && n < visibleLineCount; n++) {
            ChatHudLine.Visible visible = visibleMessages.get(n + scrolledLines);
            String lineText = extractText(visible.content());

            if (visible.endOfEntry()) {
                if (!lineTexts.isEmpty() && bottomLineN != -1 && bottomLineVisible != null) {
                    int topLineN = n - 1;
                    double opacity = getLineOpacity(bottomLineVisible, chatFocused, currentTick);
                    renderOuIconIfMarked(context, lineTexts, topLineN, baseY, chatScale, yOffset, iconX, opacity);
                    lineTexts.clear();
                }
                bottomLineN = n;
                bottomLineVisible = visible;
                lineTexts.add(lineText);
            } else {
                lineTexts.add(lineText);
            }
        }
        if (!lineTexts.isEmpty() && bottomLineN != -1 && bottomLineVisible != null) {
            int topLineN = bottomLineN + lineTexts.size() - 1;
            double opacity = getLineOpacity(bottomLineVisible, chatFocused, currentTick);
            renderOuIconIfMarked(context, lineTexts, topLineN, baseY, chatScale, yOffset, iconX, opacity);
        }
    }

    /**
     * Minecrafts chat line opacity nachbauen (matched das ChatHud fade-out).
     */
    private static double getLineOpacity(ChatHudLine.Visible line, boolean chatFocused, int currentTick) {
        if (chatFocused) return 1.0;
        double age = (double)(currentTick - line.addedTime());
        double opacity = 1.0 - age / 200.0;
        opacity *= 10.0;
        opacity = MathHelper.clamp(opacity, 0.0, 1.0);
        opacity *= opacity;
        return opacity;
    }

    private static void renderOuIconIfMarked(
            DrawContext context, ArrayList<String> lineTexts,
            int topLineN, int baseY, double chatScale, int yOffset, int iconX, double opacity) {

        if (opacity <= 0.01) return;

        ArrayList<String> ordered = new ArrayList<>(lineTexts);
        Collections.reverse(ordered);
        String fullText = String.join(" ", ordered);

        if (!ChatHistoryManager.isOttoTalkUserMessage(fullText)) return;

        int topLineTop = (int)((baseY - topLineN * 9 - 9) * chatScale) - yOffset;
        int topLineBottom = (int)((baseY - topLineN * 9) * chatScale) - yOffset;
        int topLineHeight = topLineBottom - topLineTop;

        int ouSize = Math.max(6, (int)(9 * chatScale));
        int iconY = topLineTop + (topLineHeight - ouSize) / 2;
        // setShaderColor ist der einzige weg in dieser MC version, per-draw alpha auf drawTexture anzuwenden
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, (float)opacity);
        context.drawTexture(TEX_OU_ICON, iconX, iconY, ouSize, ouSize,
                0, 0, 8, 8, 8, 8);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static class CheckboxHitArea {
        final int x, y, width, height;
        final String messageText;

        CheckboxHitArea(int x, int y, int width, int height, String messageText) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.messageText = messageText;
        }
    }
}
