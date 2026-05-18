package com.ottotalk.gui;

import com.ottotalk.context.PlayerNameList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Screen zum auswählen des empfängers für nen brief.
 * Zeigt die spielerliste (online + bekannt), suchbar nach account oder charaktername.
 * beim bestätigen: schickt /letter <pageN> für jede seite, danach /post <account>.
 */
public class PostRecipientScreen extends Screen {

    private static final Identifier TEX_LETTER_SEND = new Identifier("ottotalk", "textures/gui/letter_send.png");
    private static final Identifier TEX_EXIT        = new Identifier("ottotalk", "textures/gui/exit.png");
    private static final Identifier TEX_HELLER_3    = new Identifier("ottotalk", "textures/gui/heller_3.png");
    private static final int TEX_W = 187;
    private static final int TEX_H = 206;

    private static final int FRAME_W      = 187;
    private static final int FRAME_H      = 206;
    private static final int INNER_PAD    = 12;
    private static final int ROW_H        = 14;
    private static final int SEND_BTN_H   = 18;
    private static final int ICON_BTN_W   = 20;
    private static final int ICON_BTN_H   = 18;

    private int frameX, frameY;
    private int listX, listY, listW, listH;
    private int scrollOffset  = 0;
    private int selectedIndex = -1;

    private List<PlayerEntry> displayList = new ArrayList<>();
    private String searchQuery = "";

    private TextFieldWidget searchField;

    private int sendBtnX, sendBtnY, sendBtnW;
    private int exitBtnX, exitBtnY;

    private static class PlayerEntry {
        final String accountName;
        final String characterName;
        final boolean online;
        PlayerEntry(String account, String character, boolean online) {
            this.accountName   = account;
            this.characterName = character;
            this.online        = online;
        }
    }

    public PostRecipientScreen() {
        super(Text.literal("Brief senden an..."));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    protected void init() {
        frameX = (width  - FRAME_W) / 2;
        frameY = (height - FRAME_H) / 2;

        int cx = frameX + INNER_PAD;
        int cy = frameY + INNER_PAD;
        int cw = FRAME_W - 2 * INNER_PAD;

        searchField = new TextFieldWidget(textRenderer, cx, cy + 16, cw, 12, Text.literal("Suchen"));
        searchField.setMaxLength(64);
        searchField.setDrawsBackground(false);
        searchField.setEditableColor(0x99FFFFFF);
        searchField.setChangedListener(q -> {
            searchQuery = q.toLowerCase();
            scrollOffset  = 0;
            selectedIndex = -1;
            rebuildDisplayList();
        });
        addDrawableChild(searchField);
        setFocused(searchField);

        listX = cx;
        listY = cy + 34;
        listW = cw;

        int bottomY = frameY + FRAME_H - INNER_PAD - SEND_BTN_H;
        listH = bottomY - listY - 6;

        int gap   = 6;
        exitBtnX  = frameX + FRAME_W - INNER_PAD - ICON_BTN_W;
        exitBtnY  = bottomY;
        sendBtnW  = FRAME_W - 2 * INNER_PAD - ICON_BTN_W - gap;
        sendBtnX  = frameX + INNER_PAD;
        sendBtnY  = bottomY;

        rebuildDisplayList();
    }

    private void rebuildDisplayList() {
        Set<String> onlineKeys = new HashSet<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String n = e.getProfile().getName();
                if (n != null && !n.isEmpty()) onlineKeys.add(n.toLowerCase());
            }
        }

        Map<String, PlayerEntry> map = new LinkedHashMap<>();
        for (PlayerNameList.PlayerEntry p : PlayerNameList.getAllEntries()) {
            boolean online = onlineKeys.contains(p.accountName.toLowerCase());
            map.put(p.accountName.toLowerCase(),
                    new PlayerEntry(p.accountName, p.characterName, online));
        }
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String n = e.getProfile().getName();
                if (n == null || n.isEmpty()) continue;
                if (!map.containsKey(n.toLowerCase()))
                    map.put(n.toLowerCase(), new PlayerEntry(n, "Unbekannt", true));
            }
        }

        List<PlayerEntry> result = new ArrayList<>();
        for (PlayerEntry pe : map.values()) {
            if (searchQuery.isEmpty()
                    || pe.accountName.toLowerCase().contains(searchQuery)
                    || (pe.characterName != null
                            && pe.characterName.toLowerCase().contains(searchQuery))) {
                result.add(pe);
            }
        }
        result.sort((a, b) -> {
            if (a.online != b.online) return a.online ? -1 : 1;
            return a.accountName.compareToIgnoreCase(b.accountName);
        });
        displayList    = result;
        selectedIndex  = -1;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawTexture(TEX_LETTER_SEND, frameX, frameY, FRAME_W, FRAME_H,
                0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        int cx = frameX + INNER_PAD;
        int cy = frameY + INNER_PAD;
        int cw = FRAME_W - 2 * INNER_PAD;

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Brief senden an..."), frameX + FRAME_W / 2, cy, 0xFFFFD700);
        ctx.fill(cx, cy + 11, cx + cw, cy + 12, 0x66FFFFFF);

        ctx.fill(cx, cy + 14, cx + cw, cy + 28, 0x33FFFFFF);

        super.render(ctx, mouseX, mouseY, delta);

        if (searchQuery.isEmpty()) {
            ctx.drawText(textRenderer, "Account- oder Rollenname...", cx + 4, cy + 18, 0x44FFFFFF, false);
        }

        ctx.fill(cx, listY - 2, cx + cw, listY - 1, 0x55FFFFFF);

        int maxVisible = listH / ROW_H;
        int maxScroll  = Math.max(0, displayList.size() - maxVisible);
        scrollOffset   = Math.max(0, Math.min(scrollOffset, maxScroll));

        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < maxVisible && (i + scrollOffset) < displayList.size(); i++) {
            int idx  = i + scrollOffset;
            PlayerEntry pe  = displayList.get(idx);
            int rowY = listY + i * ROW_H;
            boolean selected = idx == selectedIndex;
            boolean hovered  = mouseX >= listX && mouseX <= listX + listW
                    && mouseY >= rowY && mouseY <= rowY + ROW_H;

            if (selected)     ctx.fill(listX, rowY, listX + listW, rowY + ROW_H, 0x88FFD700);
            else if (hovered) ctx.fill(listX, rowY, listX + listW, rowY + ROW_H, 0x44FFFFFF);

            int nameColor = pe.online ? 0xFF88FF88 : 0xFFCCCCCC;
            String display = pe.accountName;
            if (pe.characterName != null && !pe.characterName.isEmpty()
                    && !"Unbekannt".equals(pe.characterName)) {
                display += "  [" + pe.characterName + "]";
            } else if (pe.online) {
                display += "  (online)";
            }
            ctx.drawText(textRenderer, display, listX + 4, rowY + (ROW_H - 8) / 2, nameColor, false);
        }
        ctx.disableScissor();

        if (displayList.size() > maxVisible) {
            int sbX    = listX + listW - 4;
            int trackH = listH;
            ctx.fill(sbX, listY, sbX + 3, listY + trackH, 0x33FFFFFF);
            int thumbH = Math.max(8, trackH * maxVisible / displayList.size());
            int thumbY = listY + (trackH - thumbH)
                    * scrollOffset / Math.max(1, maxScroll);
            ctx.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xAAFFFFFF);
        }

        int bottomY = frameY + FRAME_H - INNER_PAD - SEND_BTN_H;
        ctx.fill(cx, bottomY - 4, cx + cw, bottomY - 3, 0x55FFFFFF);

        boolean canConfirm = selectedIndex >= 0 && selectedIndex < displayList.size();
        drawSendBtn(ctx, sendBtnX, sendBtnY, sendBtnW, SEND_BTN_H, mouseX, mouseY, canConfirm);
        drawIconBtn(ctx, exitBtnX, exitBtnY, mouseX, mouseY);
    }

    private void drawSendBtn(DrawContext ctx, int x, int y, int w, int h, int mx, int my, boolean enabled) {
        NineSliceRenderer.drawThreeTile(ctx, x, y, w, h);
        if (!enabled) {
            ctx.fill(x, y, x + w, y + h, 0x88888888);
        } else if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
            ctx.fill(x, y, x + w, y + h, 0x22FFFFFF);
        }
        int iconX = x + 4;
        int iconY = y + (h - 16) / 2;
        ctx.drawTexture(TEX_HELLER_3, iconX, iconY, 16, 16, 0, 0, 16, 16, 16, 16);
        String label = "Senden";
        int fg = !enabled ? 0xFF888877 : (mx >= x && mx <= x + w && my >= y && my <= y + h ? 0xFFFFE0AA : 0xFFEECCAA);
        int lw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, label, x + (w + 16 + 4 - lw) / 2 + 2, y + (h - 7) / 2, fg, false);
    }

    private void drawIconBtn(DrawContext ctx, int x, int y, int mx, int my) {
        ctx.drawTexture(TEX_EXIT, x, y, ICON_BTN_W, ICON_BTN_H, 0, 0, ICON_BTN_W, ICON_BTN_H, ICON_BTN_W, ICON_BTN_H);
        if (mx >= x && mx <= x + ICON_BTN_W && my >= y && my <= y + ICON_BTN_H) {
            ctx.fill(x, y, x + ICON_BTN_W, y + ICON_BTN_H, 0x33FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            int i = (my - listY) / ROW_H;
            int idx = i + scrollOffset;
            if (idx >= 0 && idx < displayList.size()) selectedIndex = idx;
            return true;
        }

        boolean canConfirm = selectedIndex >= 0 && selectedIndex < displayList.size();
        if (canConfirm && LetterScreen.hit(mx, my, sendBtnX, sendBtnY, sendBtnW, SEND_BTN_H)) {
            sendLetter(displayList.get(selectedIndex).accountName);
            return true;
        }

        if (LetterScreen.hit(mx, my, exitBtnX, exitBtnY, ICON_BTN_W, ICON_BTN_H)) {
            close(); return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        if (mouseX >= listX && mouseX <= listX + listW
                && mouseY >= listY && mouseY <= listY + listH) {
            int maxVisible = listH / ROW_H;
            int maxScroll  = Math.max(0, displayList.size() - maxVisible);
            scrollOffset   = Math.max(0, Math.min(scrollOffset - (int) vertAmount, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizAmount, vertAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendLetter(String accountName) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.networkHandler.sendChatCommand("post " + accountName);
        }
        close();
    }

    @Override
    public boolean shouldPause() { return false; }
}
