package com.ottotalk.mixins;

import com.ottotalk.OttoTalkClient;
import com.ottotalk.context.CharacterNameResolver;
import com.ottotalk.context.ChatHistoryManager;
import com.ottotalk.context.PlayerNameList;
import com.ottotalk.gui.ChatCheckboxRenderer;
import com.ottotalk.gui.RoleplayOverlayScreen;
import com.ottotalk.gui.RoleplayStateManager;
import com.ottotalk.gui.ServerChatState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Shadow private List<ChatHudLine.Visible> visibleMessages;
    @Shadow private List<ChatHudLine> messages;
    @Shadow private int scrolledLines;

    @Unique private int ottotalk_yOffset = 0;
    @Unique private int ottotalk_currentTick = 0;
    @Unique private static int ottotalk_xShift = 0;
    @Unique private boolean ottotalk_headsEnabled = false;
    @Unique private int ottotalk_headScreenX = 0;
    @Unique private static final int OTTOTALK_HEAD_SIZE   = 8;
    @Unique private static final int OTTOTALK_HEAD_PAD    = 2;  // 2px halt
    @Unique private static final int OTTOTALK_HEAD_MARGIN = 10; // HEAD_SIZE + 2 Gap nach dem Head
    // pro Zeile Head-Daten jedes Frame in pushChatUp neu berechnet
    @Unique private boolean[] ottotalk_lineHasHead       = new boolean[0];
    @Unique private boolean[] ottotalk_lineIsContinuation = new boolean[0]; // gewrappter Rest einer Head-Zeile
    @Unique private int[]     ottotalk_lineNameChar = new int[0]; // Codepoint Index wo der Name anfängt
    @Unique private int[]     ottotalk_lineHeadX    = new int[0]; // berechnetes Head Screen X
    @Unique private int       ottotalk_drawLineIdx  = 0;
    @Unique private double    ottotalk_chatScaleVal = 1.0;

    /**
     * Replicate Minecraft's chat message opacity calculation.
     * When chat is focused, returns 1.0. Otherwise fades based on message age.
     */
    @Unique
    private double ottotalk_getLineOpacity(ChatHudLine.Visible line, boolean chatFocused) {
        if (chatFocused) return 1.0;
        double age = (double)(ottotalk_currentTick - line.addedTime());
        double opacity = 1.0 - age / 200.0;
        opacity *= 10.0;
        opacity = MathHelper.clamp(opacity, 0.0, 1.0);
        opacity *= opacity;
        return opacity;
    }

    /**
     * Render per-line black transparent background extension matching chat fade-out.
     * Covers the full left strip (heads + roleplay checkboxes + oU icons).
     */
    @Unique
    private void renderChatBackgroundExtension(DrawContext context, boolean roleplayActive, boolean debugMode) {
        int bgWidth = ottotalk_xShift; // already includes heads + checkboxes + icons
        if (bgWidth == 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        boolean chatFocused = client.currentScreen instanceof ChatScreen;
        int scaledHeight = client.getWindow().getScaledHeight();
        double chatScale = ((ChatHud)(Object)this).getChatScale();
        int visibleLineCount = ((ChatHud)(Object)this).getVisibleLineCount();
        int baseY = MathHelper.floor((float)(scaledHeight - 40) / (float)chatScale);

        // Hintergrund pro Zeile faded passend zur Opacity damit alte Zeilen mit verblassen
        for (int n = 0; n + scrolledLines < visibleMessages.size() && n < visibleLineCount; n++) {
            ChatHudLine.Visible line = visibleMessages.get(n + scrolledLines);
            double opacity = ottotalk_getLineOpacity(line, chatFocused);
            if (opacity <= 0.01) continue;

            int alpha = (int)(opacity * 128); // 0x80 = 128 max Alpha
            int color = (alpha << 24); // schwarz mit variabler Alpha

            int lineTop = (int)((baseY - n * 9 - 9) * chatScale) - ottotalk_yOffset;
            int lineBottom = (int)((baseY - n * 9) * chatScale) - ottotalk_yOffset;
            context.fill(0, lineTop, bgWidth, lineBottom, color);
        }
    }

    /**
     * Replace gamertags with RP character names in chat messages when setting is enabled.
     */
    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true)
    private Text ottotalk_replaceCharacterNames(Text message) {
        boolean onOttonien = ServerChatState.isOnOttonien();
        if (!onOttonien) return message;
        com.ottotalk.config.OttoTalkConfig cfg = OttoTalkClient.getConfig();
        boolean nameMode = cfg.showCharacterNames;
        if (nameMode && OttoTalkClient.shouldShowNamesForMessage(message.getString())) {
            // volle Verarbeitung: Namen ersetzen und Farben remappen
            Text result = CharacterNameResolver.replaceNamesInText(message);
            String before = message.getString();
            String after = result.getString();
            if (!before.equals(after)) {
                CharacterNameResolver.storeOriginal(result, message);
                ChatHistoryManager.updateMessage(before, after);
            }
            return result;
        }
        // Namen aus oder per-Channel aus: Palette-Farben trotzdem remappen
        return CharacterNameResolver.remapTextColors(message, cfg);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void pushChatUp(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
        ChatCheckboxRenderer.chatHudMessages = this.messages;
        ottotalk_currentTick = currentTick;
        ottotalk_yOffset = 0;
        // Otto overlay pushes the chat up while LOADING/OPTIONS, but stays out of the way during IDLE.
        if (RoleplayStateManager.shouldRenderOverlay()) {
            RoleplayOverlayScreen overlay = RoleplayStateManager.getCurrentOverlay();
            if (overlay != null && overlay.getState() != RoleplayOverlayScreen.State.IDLE) {
                ottotalk_yOffset += overlay.getOverlayHeight() + 4;
            }
        }
        // Server Overlay stapelt sich aufs Otto Overlay wenn beide sichtbar sind
        int serverOverlayH = ServerChatState.getLastOverlayHeight();
        if (serverOverlayH > 0) {
            ottotalk_yOffset += serverOverlayH + 4;
        }
        if (ottotalk_yOffset > 0) {
            context.getMatrices().push();
            context.getMatrices().translate(0, -ottotalk_yOffset, 0);
        }
        // Chat-Text nach rechts schieben damit Platz für oU Icons und Checkboxen bleibt
        ottotalk_xShift = 0;
        if (RoleplayStateManager.isDebugMode()) ottotalk_xShift += ChatCheckboxRenderer.OU_ICON_AREA_WIDTH;
        if (RoleplayStateManager.isRoleplayModeActive()) ottotalk_xShift += ChatCheckboxRenderer.CHECKBOX_AREA_WIDTH;
        // Chat heads: per-line shift via @ModifyArg, only lines with a sender are indented
        ottotalk_headsEnabled = OttoTalkClient.getConfig().showChatHeads && ServerChatState.isOnOttonien();
        ottotalk_chatScaleVal = ((ChatHud)(Object)this).getChatScale();
        ottotalk_drawLineIdx  = 0;
        ottotalk_headScreenX  = ottotalk_xShift + 1; // Head sitzt direkt nach dem oU/Checkbox Strip
        if (ottotalk_headsEnabled) {
            MinecraftClient mc2 = MinecraftClient.getInstance();
            if (mc2.getNetworkHandler() != null) {
                int visCount = Math.min(((ChatHud)(Object)this).getVisibleLineCount(),
                        visibleMessages.size() - scrolledLines);
                ottotalk_lineHasHead       = new boolean[visCount];
                ottotalk_lineIsContinuation = new boolean[visCount];
                ottotalk_lineNameChar = new int[visCount];
                ottotalk_lineHeadX    = new int[visCount];
                int[] ni = {0};
                for (int n = 0; n < visCount; n++) {
                    ni[0] = 0;
                    StringBuilder sb2 = new StringBuilder();
                    visibleMessages.get(n + scrolledLines).content()
                            .accept((i2, s2, cp2) -> { sb2.appendCodePoint(cp2); return true; });
                    if (ottotalk_findSenderEntry(mc2, sb2.toString(), ni) != null) {
                        ottotalk_lineHasHead[n] = true;
                        ottotalk_lineNameChar[n] = ni[0];
                    }
                }
                // Mark continuation lines: n is a wrap of n-1 when same addedTime and n-1 is head/cont
                for (int n = 1; n < visCount; n++) {
                    if (!ottotalk_lineHasHead[n]
                            && (ottotalk_lineHasHead[n-1] || ottotalk_lineIsContinuation[n-1])
                            && visibleMessages.get(n + scrolledLines).addedTime()
                               == visibleMessages.get(n-1 + scrolledLines).addedTime()) {
                        ottotalk_lineIsContinuation[n] = true;
                    }
                }
            } else {
                ottotalk_lineHasHead       = new boolean[0];
                ottotalk_lineIsContinuation = new boolean[0];
            }
        } else {
            ottotalk_lineHasHead       = new boolean[0];
            ottotalk_lineIsContinuation = new boolean[0];
            ottotalk_lineNameChar = new int[0];
            ottotalk_lineHeadX    = new int[0];
        }
        // Offsets speichern für den Hover-Fix im ChatScreenMixin
        ChatCheckboxRenderer.currentXShift = ottotalk_xShift;
        ChatCheckboxRenderer.currentYOffset = ottotalk_yOffset;
        if (ottotalk_xShift > 0) {
            context.getMatrices().push();
            context.getMatrices().translate(ottotalk_xShift, 0, 0);
        }
    }

    /**
     * Intercept each chat line draw: for lines with a head, split the OrderedText at the
     * name position, draw the prefix at x, draw the name+rest at x+prefixW+headGap.
     * This creates space exactly before the name where the head icon will be drawn.
     */
    @Redirect(
        method = "render",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I")
    )
    private int ottotalk_redirectChatLine(DrawContext ctx, net.minecraft.client.font.TextRenderer tr,
                                           OrderedText orderedText, int x, int y, int color) {
        int idx = ottotalk_drawLineIdx++;
        // Continuation Zeile: gleicher Shift wie Head-Zeile kein Split
        if (ottotalk_headsEnabled && idx < ottotalk_lineIsContinuation.length && ottotalk_lineIsContinuation[idx]) {
            int headShift = (int)Math.ceil((OTTOTALK_HEAD_MARGIN + OTTOTALK_HEAD_PAD) / ottotalk_chatScaleVal);
            return ctx.drawTextWithShadow(tr, orderedText, x + headShift, y, color);
        }
        if (!ottotalk_headsEnabled || idx >= ottotalk_lineHasHead.length || !ottotalk_lineHasHead[idx]) {
            return ctx.drawTextWithShadow(tr, orderedText, x, y, color);
        }
        int nameChar = ottotalk_lineNameChar[idx];
        int headShift = (int)Math.ceil((OTTOTALK_HEAD_MARGIN + OTTOTALK_HEAD_PAD) / ottotalk_chatScaleVal);
        int prefixW = 0;
        if (nameChar > 0) {
            OrderedText prefix = ottotalk_orderedPrefix(orderedText, nameChar);
            ctx.drawTextWithShadow(tr, prefix, x, y, color);
            prefixW = tr.getWidth(prefix);
        }
        // Name + Rest um headShift nach rechts schieben damit ne Lücke fürs Head-Icon entsteht
        OrderedText suffix = ottotalk_orderedSuffix(orderedText, nameChar);
        ctx.drawTextWithShadow(tr, suffix, x + prefixW + headShift, y, color);
        // Head Screen X speichern: PAD Pixel nach dem Prefix-Ende für ne kleine Lücke davor
        if (idx < ottotalk_lineHeadX.length) {
            ottotalk_lineHeadX[idx] = (int)(ottotalk_xShift + (x + prefixW) * ottotalk_chatScaleVal) + OTTOTALK_HEAD_PAD;
        }
        return 0;
    }

    /** OrderedText containing only the first {@code end} code points of {@code source}. */
    @Unique
    private OrderedText ottotalk_orderedPrefix(OrderedText source, int end) {
        return visitor -> {
            int[] count = {0};
            source.accept((charIndex, style, codePoint) -> {
                if (count[0] >= end) return false;
                count[0]++;
                return visitor.accept(charIndex, style, codePoint);
            });
            return true;
        };
    }

    /** OrderedText skipping the first {@code start} code points of {@code source}. */
    @Unique
    private OrderedText ottotalk_orderedSuffix(OrderedText source, int start) {
        return visitor -> {
            int[] count = {0};
            return source.accept((charIndex, style, codePoint) -> {
                if (count[0]++ < start) return true;
                return visitor.accept(charIndex, style, codePoint);
            });
        };
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void restoreChatPosition(DrawContext context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
        boolean roleplayActive = RoleplayStateManager.isRoleplayModeActive();
        boolean debugMode = RoleplayStateManager.isDebugMode();

        // Chat X-Shift poppen egal welche Kombi aus Debug/Roleplay/Heads is immer genau eine Matrix
        if (ottotalk_xShift > 0) {
            context.getMatrices().pop();
        }
        // Overlay Offset poppen passt zum kombinierten Push aus pushChatUp
        if (ottotalk_yOffset > 0) {
            context.getMatrices().pop();
        }

        // Checkboxen erst nach allen Matrix Pops zeichnen damit die Hit-Areas in echten Screen-Koordinaten sind
        if (roleplayActive) {
            ChatCheckboxRenderer.renderFromChatHud(
                context, (ChatHud)(Object)this,
                visibleMessages, scrolledLines,
                mouseX, mouseY, ottotalk_yOffset
            );
        }

        ChatCheckboxRenderer.renderOttoUserIcons(
                context, (ChatHud)(Object)this,
                visibleMessages, scrolledLines,
                ottotalk_yOffset, roleplayActive, currentTick
        );

        renderChatBackgroundExtension(context, roleplayActive, debugMode);

        if (ottotalk_headsEnabled) {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean chatFocused = mc.currentScreen instanceof ChatScreen;
            renderChatHeads(context, currentTick, chatFocused);
        }
    }

    @Unique
    private void renderChatHeads(DrawContext context, int currentTick, boolean chatFocused) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return;
        double chatScale = ((ChatHud)(Object)this).getChatScale();
        int visibleLineCount = ((ChatHud)(Object)this).getVisibleLineCount();
        int scaledHeight = client.getWindow().getScaledHeight();
        int baseY = MathHelper.floor((float)(scaledHeight - 40) / (float)chatScale);
        int headPx = Math.max(6, (int)(OTTOTALK_HEAD_SIZE * chatScale));

        for (int n = 0; n + scrolledLines < visibleMessages.size() && n < visibleLineCount; n++) {
            // Zeilen überspringen die nicht als Head-Zeile vorberechnet wurden
            if (n >= ottotalk_lineHasHead.length || !ottotalk_lineHasHead[n]) continue;

            ChatHudLine.Visible line = visibleMessages.get(n + scrolledLines);
            double opacity = ottotalk_getLineOpacity(line, chatFocused);
            if (opacity <= 0.01) continue;

            // Sender komplett suchen um den Skin zu kriegen
            StringBuilder sb = new StringBuilder();
            line.content().accept((index, style, codePoint) -> { sb.appendCodePoint(codePoint); return true; });
            int[] nameIdx = {0};
            PlayerListEntry skinEntry = ottotalk_findSenderEntry(client, sb.toString(), nameIdx);
            if (skinEntry == null) continue;

            // Head sitzt in der Lücke vor dem Namen im @Redirect berechnet
            int headX = (n < ottotalk_lineHeadX.length && ottotalk_lineHeadX[n] > 0)
                    ? ottotalk_lineHeadX[n] + 1 : ottotalk_headScreenX;
            int lineTop = (int)((baseY - n * 9 - 9) * chatScale) - ottotalk_yOffset;
            int lineH = (int)(9 * chatScale);
            int headY = lineTop + (lineH - headPx) / 2 + 1;

            Identifier skin = skinEntry.getSkinTextures().texture();
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, (float)opacity);
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            context.drawTexture(skin, headX, headY, headPx, headPx, 8, 8, 8, 8, 64, 64);
            context.drawTexture(skin, headX, headY, headPx, headPx, 40, 8, 8, 8, 64, 64);
            context.getMatrices().pop();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    /**
     * Finds the PlayerListEntry for a chat line and outputs the character index where the name starts.
     * outNameIdx[0] is set to the index of the first character of the matched name in lineText.
     */
    @Unique
    private PlayerListEntry ottotalk_findSenderEntry(MinecraftClient client, String lineText, int[] outNameIdx) {
        if (lineText == null || lineText.isEmpty()) return null;
        if (client.getNetworkHandler() == null) return null;
        String scan = lineText.length() > 60 ? lineText.substring(0, 60) : lineText;

        List<PlayerNameList.PlayerEntry> players = PlayerNameList.getAllEntries();
        for (PlayerNameList.PlayerEntry p : players) {
            String acc = p.accountName;
            String chr = (p.characterName != null && !p.characterName.isEmpty()
                          && !"Unbekannt".equals(p.characterName)) ? p.characterName : null;
            PlayerListEntry entry = ottotalk_tryMatch(client, scan, acc, chr, outNameIdx);
            if (entry != null) return entry;
        }
        // fallback: halt alle Online Spieler durchgehen
        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().getName();
            int idx = ottotalk_nameIndex(scan, name);
            if (idx >= 0) { outNameIdx[0] = idx; return entry; }
        }
        return null;
    }

    /** Tries to match account or character name in scan text; sets outNameIdx and returns entry on match. */
    @Unique
    private PlayerListEntry ottotalk_tryMatch(MinecraftClient client, String scan,
                                               String acc, String chr, int[] outNameIdx) {
        int idx = ottotalk_nameIndex(scan, acc);
        if (idx >= 0) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(acc);
            if (entry != null) { outNameIdx[0] = idx; return entry; }
        }
        if (chr != null) {
            idx = ottotalk_nameIndex(scan, chr);
            if (idx >= 0) {
                PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(acc);
                if (entry != null) { outNameIdx[0] = idx; return entry; }
            }
        }
        return null;
    }

    /** Returns the start index of `name` in `scan` using common chat patterns, or -1 if not found. */
    @Unique
    private int ottotalk_nameIndex(String scan, String name) {
        int i;
        if ((i = scan.indexOf(name + ":"))  >= 0) return i;
        if ((i = scan.indexOf("[" + name + "]")) >= 0) return i + 1; // skip the '['
        if ((i = scan.indexOf(name + " "))  >= 0) return i;
        return -1;
    }

}
