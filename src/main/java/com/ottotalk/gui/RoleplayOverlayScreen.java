package com.ottotalk.gui;

import com.ottotalk.OttoTalkClient;
import com.ottotalk.context.ChatHistoryManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextHandler;

/**
 * Das roleplay overlay window, links ausgerichtet zwischen chat input und chat history.
 * Wenn offen, wird die chat history nach oben geschoben damit platz da ist.
 */
public class RoleplayOverlayScreen {
    private static final int OVERLAY_PADDING = 14;
    private static final int OVERLAY_MARGIN_LEFT = 2;
    private static final int OVERLAY_MARGIN_BOTTOM = 1;
    private static final int CLOSE_BUTTON_W = 20;
    private static final int CLOSE_BUTTON_H = 18;
    private static final int MODE_BUTTON_W = 20;
    private static final int MODE_BUTTON_H = 18;
    private static final int EDIT_BUTTON_W = 14;
    private static final int EDIT_BUTTON_H = 14;
    private static final int LINE_HEIGHT = 12;
    private static final int OPTION_MIN_HEIGHT = 20;
    private static final int OPTION_PADDING = 4;
    private static final int OPTION_SPACING = 2;
    private static final int OPTION_LINE_HEIGHT = 10;

    private static final Identifier TEX_CONTEXT = new Identifier("ottotalk", "textures/gui/context.png");
    private static final Identifier TEX_REDE = new Identifier("ottotalk", "textures/gui/rede.png");
    private static final Identifier TEX_ANWEISUNG = new Identifier("ottotalk", "textures/gui/anweisung.png");
    private static final Identifier TEX_EDIT = new Identifier("ottotalk", "textures/gui/edit.png");
    private static final Identifier TEX_EXIT = new Identifier("ottotalk", "textures/gui/exit.png");
    private static final Identifier TEX_HISTORY_DISABLED = new Identifier("ottotalk", "textures/gui/history.png");
    private static final Identifier TEX_HISTORY_ACTIVE = new Identifier("ottotalk", "textures/gui/history_active.png");
    private static final int HISTORY_BUTTON_W = 35;
    private static final int HISTORY_BUTTON_H = 18;
    private static final Identifier TEX_EMOTE_DISABLED = new Identifier("ottotalk", "textures/gui/emotemode_disabled.png");
    private static final Identifier TEX_EMOTE_ACTIVE = new Identifier("ottotalk", "textures/gui/emotemode_active.png");
    private static final int EMOTE_BUTTON_W = 20;
    private static final int EMOTE_BUTTON_H = 18;
    private static final Identifier TEX_TILE_FRAME = new Identifier("ottotalk", "textures/gui/3-tile-frame.png");
    private static final int TILE_FRAME_TEX_W = 35;
    private static final int TILE_FRAME_TEX_H = 18;
    private static final int TILE_FRAME_CAP = 5;
    private static final Identifier TEX_AI_ACTIVE = new Identifier("ottotalk", "textures/gui/ai_active.png");
    private static final int AI_ACTIVE_SRC_W = 33;
    private static final int AI_ACTIVE_SRC_H = 9;
    // Rendered pixel-exact (no scaling)
    private static final int AI_ACTIVE_RENDER_W = AI_ACTIVE_SRC_W;
    private static final int AI_ACTIVE_RENDER_H = AI_ACTIVE_SRC_H;

    public enum State { IDLE, LOADING, OPTIONS }

    private int closeButtonX, closeButtonY;
    private int modeButtonX, modeButtonY;
    private int historyButtonX, historyButtonY;
    private int emoteButtonX, emoteButtonY;
    private int titleFrameX, titleFrameY, titleFrameW;
    private int aiActiveX, aiActiveY;
    private int modeTextX, modeTextY;
    private int modeToggleX, modeToggleY;
    private int overlayX, overlayY, overlayWidth, overlayHeight;
    private boolean initialized = false;
    private static final long ANIM_DURATION_MS = 200;
    private static final long BTN_ANIM_MS = 150;
    private long visibleSince = 0;
    private boolean animStarted = false;
    private long buttonsVisibleSince = 0;
    private boolean buttonsAnimStarted = false;

    private State state = State.IDLE;
    private final List<String> responseOptions = new ArrayList<>();
    private int[] optionYPositions = new int[0];
    private int[] optionHeights = new int[0];
    private List<List<String>> wrappedOptionLines = new ArrayList<>();
    private long loadingStartTime = 0;

    // mindestbreite damit sich die buttons nicht überlappen
    private static final int MIN_OVERLAY_WIDTH = 200;

    public void init(int chatHudWidth, int chatInputY) {
        this.overlayWidth = Math.max(chatHudWidth, MIN_OVERLAY_WIDTH);
        this.overlayX = OVERLAY_MARGIN_LEFT;

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int optContentWidth = overlayWidth - 2 * OVERLAY_PADDING - 2 * OPTION_PADDING;

        // höhe dynamisch je nach state berechnen
        int titleHeight = LINE_HEIGHT;
        int contentHeight;
        switch (state) {
            case LOADING:
                contentHeight = LINE_HEIGHT;
                break;
            case OPTIONS:
                // jede option word-wrap'n und pro-option die höhe berechnen
                wrappedOptionLines.clear();
                contentHeight = 0;
                optionHeights = new int[responseOptions.size()];
                for (int i = 0; i < responseOptions.size(); i++) {
                    String label = (i + 1) + ". " + responseOptions.get(i);
                    List<String> lines = wrapText(textRenderer, label, optContentWidth);
                    wrappedOptionLines.add(lines);
                    int h = Math.max(OPTION_MIN_HEIGHT, OPTION_PADDING * 2 + lines.size() * OPTION_LINE_HEIGHT);
                    optionHeights[i] = h;
                    contentHeight += h + OPTION_SPACING;
                }
                break;
            default: // IDLE
                contentHeight = 0;
                break;
        }
        this.overlayHeight = OVERLAY_PADDING + contentHeight + OVERLAY_PADDING;

        this.overlayY = chatInputY - overlayHeight - OVERLAY_MARGIN_BOTTOM;

        this.closeButtonX = overlayX + overlayWidth - CLOSE_BUTTON_W - OVERLAY_PADDING;
        this.closeButtonY = overlayY + OVERLAY_PADDING;

        // mode toggle sitzt links vom close button (button-strip von rechts nach links)
        this.modeButtonX = this.closeButtonX - MODE_BUTTON_W - 2;
        this.modeButtonY = overlayY + OVERLAY_PADDING;

        // emote mode button position (links neben mode button)
        this.emoteButtonX = this.modeButtonX - EMOTE_BUTTON_W - 2;
        this.emoteButtonY = overlayY + OVERLAY_PADDING;

        // history button position (links vom emote button)
        this.historyButtonX = this.emoteButtonX - HISTORY_BUTTON_W - 2;
        this.historyButtonY = overlayY + OVERLAY_PADDING;

        // Y positionen der options vorberechnen fürs click detection
        if (state == State.OPTIONS && !responseOptions.isEmpty()) {
            optionYPositions = new int[responseOptions.size()];
            int baseY = overlayY + OVERLAY_PADDING;
            for (int i = 0; i < responseOptions.size(); i++) {
                optionYPositions[i] = baseY;
                baseY += optionHeights[i] + OPTION_SPACING;
            }
        }

        this.initialized = true;
    }

    private List<String> wrapText(TextRenderer textRenderer, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        // in wörter splitten und zeilen greedy auffüllen
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (currentLine.length() == 0) {
                currentLine.append(word);
            } else {
                String test = currentLine + " " + word;
                if (textRenderer.getWidth(test) <= maxWidth) {
                    currentLine.append(" ").append(word);
                } else {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                }
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    // --- State management ---

    public void setLoading() {
        this.state = State.LOADING;
        this.responseOptions.clear();
        this.loadingStartTime = System.currentTimeMillis();
    }

    public void setOptions(List<String> options) {
        this.state = State.OPTIONS;
        this.responseOptions.clear();
        if (options != null) {
            this.responseOptions.addAll(options);
        }
    }

    public void resetToIdle() {
        this.state = State.IDLE;
        this.responseOptions.clear();
    }

    public void resetAnimation() {
        this.animStarted = false;
        this.buttonsAnimStarted = false;
    }

    private void renderScaled(DrawContext context, float scale, int x, int y, int w, int h, Runnable drawCall) {
        if (scale >= 1.0f) {
            drawCall.run();
            return;
        }
        float cx = x + w / 2.0f;
        float cy = y + h / 2.0f;
        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-cx, -cy, 0);
        drawCall.run();
        context.getMatrices().pop();
    }

    public State getState() {
        return state;
    }

    // --- Rendering ---

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!initialized) return;

        // visibility für die animation tracken
        if (!animStarted) {
            visibleSince = System.currentTimeMillis();
            animStarted = true;
        }

        // animation progress berechnen (ease-out), wenn animationen aus dann skip
        float progress;
        float eased;
        if (!OttoTalkClient.getConfig().enableAnimations) {
            progress = 1.0f;
            eased = 1.0f;
        } else {
            long elapsed = System.currentTimeMillis() - visibleSince;
            progress = Math.min(1.0f, (float) elapsed / ANIM_DURATION_MS);
            eased = 1.0f - (1.0f - progress) * (1.0f - progress);
        }
        int animW = (int) (overlayWidth * eased);
        if (animW < 20) animW = 20;

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        NineSliceRenderer.draw(context, overlayX, overlayY, animW, overlayHeight);
        int borderColor = 0xFFFFD700;

        // content und buttons bleiben versteckt bis der frame seine open-animation fertig hat
        if (progress >= 1.0f) {
            if (!buttonsAnimStarted) {
                buttonsVisibleSince = System.currentTimeMillis();
                buttonsAnimStarted = true;
            }
            float btnScale;
            if (!OttoTalkClient.getConfig().enableAnimations) {
                btnScale = 1.0f;
            } else {
                long btnElapsed = System.currentTimeMillis() - buttonsVisibleSince;
                float btnProgress = Math.min(1.0f, (float) btnElapsed / BTN_ANIM_MS);
                btnScale = 1.0f - (1.0f - btnProgress) * (1.0f - btnProgress);
            }

            int contentY = overlayY + OVERLAY_PADDING;
            int contentX = overlayX + OVERLAY_PADDING + 2;

            switch (state) {
                case LOADING:
                    renderLoading(context, textRenderer, contentX, contentY);
                    break;
                case OPTIONS:
                    renderOptions(context, textRenderer, mouseX, mouseY);
                    break;
                default:
                    break;
            }
        }
    }

    private void renderLoading(DrawContext context, TextRenderer textRenderer, int x, int y) {
        long elapsed = System.currentTimeMillis() - loadingStartTime;
        int dots = (int) ((elapsed / 500) % 4);
        String dotStr = ".".repeat(dots);
        context.drawText(textRenderer, Text.literal("Generiere mittelalterliche Antworten" + dotStr), x, y, 0xFFFFCC00, false);
    }

    private void renderOptions(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        int optX = overlayX + OVERLAY_PADDING;
        int optWidth = overlayWidth - 2 * OVERLAY_PADDING;

        for (int i = 0; i < responseOptions.size(); i++) {
            int optY = optionYPositions[i];
            int optH = optionHeights[i];

            int editX = optX + optWidth - EDIT_BUTTON_W - 2;
            int editY = optY + (optH - EDIT_BUTTON_H) / 2;
            boolean editHovered = mouseX >= editX && mouseX <= editX + EDIT_BUTTON_W
                    && mouseY >= editY && mouseY <= editY + EDIT_BUTTON_H;

            // die haupt-hit-area der option lässt den edit button aus, sonst würde ein klick aufs icon
            // auch die option auswählen.
            boolean optionHovered = mouseX >= optX && mouseX < editX - 2
                    && mouseY >= optY && mouseY <= optY + optH;

            boolean anyHovered = optionHovered || editHovered;

            NineSliceRenderer.drawOption(context, optX, optY, optWidth, optH);

            if (anyHovered) {
                context.fill(optX + 2, optY + 2, optX + optWidth - 2, optY + optH - 2, 0x22FFFFFF);
            }

            int textColor = anyHovered ? 0xFFFFFFFF : 0xFFDDDDDD;
            List<String> lines = wrappedOptionLines.get(i);
            int totalTextH = lines.size() * OPTION_LINE_HEIGHT;
            int textStartY = optY + (optH - totalTextH) / 2;
            for (int l = 0; l < lines.size(); l++) {
                context.drawText(textRenderer, Text.literal(lines.get(l)),
                        optX + OPTION_PADDING + 4,
                        textStartY + l * OPTION_LINE_HEIGHT,
                        textColor, false);
            }

            // edit button textur
            context.drawTexture(TEX_EDIT, editX, editY, EDIT_BUTTON_W, EDIT_BUTTON_H,
                    0, 0, 14, 14, 14, 14);
            if (editHovered) {
                context.fill(editX + 1, editY + 1,
                        editX + EDIT_BUTTON_W - 1, editY + EDIT_BUTTON_H - 1, 0x33FFFFFF);
            }
        }
    }

    private void renderTileFrame(DrawContext context, int x, int y, int width) {
        int cap = TILE_FRAME_CAP;
        int h = TILE_FRAME_TEX_H;
        int texW = TILE_FRAME_TEX_W;
        int midSrcW = texW - cap * 2;
        // linke cap
        context.drawTexture(TEX_TILE_FRAME, x, y, cap, h,
                0, 0, cap, h, texW, h);
        // mitte (getilet)
        int midStart = x + cap;
        int midEnd = x + width - cap;
        int drawX = midStart;
        while (drawX < midEnd) {
            int drawW = Math.min(midSrcW, midEnd - drawX);
            context.drawTexture(TEX_TILE_FRAME, drawX, y, drawW, h,
                    cap, 0, drawW, h, texW, h);
            drawX += drawW;
        }
        // rechte cap
        context.drawTexture(TEX_TILE_FRAME, x + width - cap, y, cap, h,
                texW - cap, 0, cap, h, texW, h);
    }

    private void renderHistoryButton(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, int borderColor) {
        boolean enabled = RoleplayStateManager.isHistoryEnabled();
        Identifier tex = enabled ? TEX_HISTORY_ACTIVE : TEX_HISTORY_DISABLED;
        context.drawTexture(tex, historyButtonX, historyButtonY, HISTORY_BUTTON_W, HISTORY_BUTTON_H,
                0, 0, 35, 18, 35, 18);
        boolean hovered = mouseX >= historyButtonX && mouseX <= historyButtonX + HISTORY_BUTTON_W
                && mouseY >= historyButtonY && mouseY <= historyButtonY + HISTORY_BUTTON_H;
        if (hovered) {
            context.fill(historyButtonX + 1, historyButtonY + 1,
                    historyButtonX + HISTORY_BUTTON_W - 1, historyButtonY + HISTORY_BUTTON_H - 1, 0x33FFFFFF);
        }
        int checked = ChatHistoryManager.getCheckedCount();
        int total = ChatHistoryManager.size();
        String countText = checked + "/" + total;
        float scale = 0.5f;
        int scaledTextW = (int)(textRenderer.getWidth(countText) * scale);
        int regionCenter = historyButtonX + HISTORY_BUTTON_W / 4;
        int textX = regionCenter - scaledTextW / 2;
        int textY = historyButtonY + (HISTORY_BUTTON_H - (int)(8 * scale)) / 2;
        context.getMatrices().push();
        context.getMatrices().translate(textX, textY, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawText(textRenderer, Text.literal(countText), 0, 0, 0xFFFFFFFF, false);
        context.getMatrices().pop();
    }

    private void renderEmoteButton(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, int borderColor) {
        boolean enabled = RoleplayStateManager.isEmoteModeEnabled();
        Identifier tex = enabled ? TEX_EMOTE_ACTIVE : TEX_EMOTE_DISABLED;
        context.drawTexture(tex, emoteButtonX, emoteButtonY, EMOTE_BUTTON_W, EMOTE_BUTTON_H,
                0, 0, 20, 18, 20, 18);
        boolean hovered = mouseX >= emoteButtonX && mouseX <= emoteButtonX + EMOTE_BUTTON_W
                && mouseY >= emoteButtonY && mouseY <= emoteButtonY + EMOTE_BUTTON_H;
        if (hovered) {
            context.fill(emoteButtonX + 1, emoteButtonY + 1,
                    emoteButtonX + EMOTE_BUTTON_W - 1, emoteButtonY + EMOTE_BUTTON_H - 1, 0x33FFFFFF);
        }
    }

    private void renderModeButton(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, int borderColor) {
        boolean isRede = RoleplayStateManager.getSpeechMode() == RoleplayStateManager.SpeechMode.REDE;
        Identifier tex = isRede ? TEX_REDE : TEX_ANWEISUNG;
        context.drawTexture(tex, modeButtonX, modeButtonY, MODE_BUTTON_W, MODE_BUTTON_H,
                0, 0, 20, 18, 20, 18);
        boolean hovered = mouseX >= modeButtonX && mouseX <= modeButtonX + MODE_BUTTON_W
                && mouseY >= modeButtonY && mouseY <= modeButtonY + MODE_BUTTON_H;
        if (hovered) {
            context.fill(modeButtonX + 1, modeButtonY + 1,
                    modeButtonX + MODE_BUTTON_W - 1, modeButtonY + MODE_BUTTON_H - 1, 0x33FFFFFF);
        }
    }

    private void renderCloseButton(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, int borderColor) {
        context.drawTexture(TEX_EXIT, closeButtonX, closeButtonY, CLOSE_BUTTON_W, CLOSE_BUTTON_H,
                0, 0, 20, 18, 20, 18);
        boolean hovered = mouseX >= closeButtonX && mouseX <= closeButtonX + CLOSE_BUTTON_W
                && mouseY >= closeButtonY && mouseY <= closeButtonY + CLOSE_BUTTON_H;
        if (hovered) {
            context.fill(closeButtonX + 1, closeButtonY + 1,
                    closeButtonX + CLOSE_BUTTON_W - 1, closeButtonY + CLOSE_BUTTON_H - 1, 0x33FFFFFF);
        }
    }

    // --- Click handling ---

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // klicks auf options
        if (state == State.OPTIONS && !responseOptions.isEmpty()) {
            int optX = overlayX + OVERLAY_PADDING;
            int optWidth = overlayWidth - 2 * OVERLAY_PADDING;

            for (int i = 0; i < responseOptions.size(); i++) {
                int optY = optionYPositions[i];
                int optH = optionHeights[i];

                // edit button bereich
                int editX = optX + optWidth - EDIT_BUTTON_W - 2;
                int editY = optY + (optH - EDIT_BUTTON_H) / 2;
                if (mouseX >= editX && mouseX <= editX + EDIT_BUTTON_W
                        && mouseY >= editY && mouseY <= editY + EDIT_BUTTON_H) {
                    // text ins chat field zum editieren reinpacken und den AI helper deaktivieren
                    RoleplayStateManager.setPendingEditText(responseOptions.get(i));
                    RoleplayStateManager.disableRoleplayMode();
                    return true;
                }

                if (mouseX >= optX && mouseX <= optX + optWidth
                        && mouseY >= optY && mouseY <= optY + optH) {
                    sendOptionAsChat(responseOptions.get(i));
                    return true;
                }
            }
        }

        return false;
    }

    private void sendOptionAsChat(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) { resetToIdle(); return; }
        java.util.List<String> parts = splitAtWordBoundary(message, 249);
        for (int i = 0; i < parts.size(); i++) {
            final String part = parts.get(i);
            if (i == 0) {
                RoleplayStateManager.setBypassInterception(true);
                client.player.networkHandler.sendChatMessage(part);
                RoleplayStateManager.setBypassInterception(false);
            } else {
                ChatScreenHandler.scheduleBypassMessage(part, i * 600L);
            }
        }
        resetToIdle();
    }

    private static java.util.List<String> splitAtWordBoundary(String message, int maxChars) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int offset = 0;
        while (offset < message.length()) {
            if (message.length() - offset <= maxChars) {
                parts.add(message.substring(offset));
                break;
            }
            String chunk = message.substring(offset, offset + maxChars);
            int splitAt = chunk.lastIndexOf(' ');
            if (splitAt > maxChars / 2) {
                parts.add(message.substring(offset, offset + splitAt) + ">");
                offset += splitAt + 1;
            } else {
                parts.add(chunk + ">");
                offset += maxChars;
            }
        }
        return parts;
    }

    public int getOverlayY() {
        return overlayY;
    }

    public int getOverlayHeight() {
        return overlayHeight;
    }
}
