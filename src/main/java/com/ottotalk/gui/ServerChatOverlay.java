package com.ottotalk.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ottotalk.OttoTalkClient;
import com.ottotalk.context.ChatHistoryManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Server Chat Settings overlay über dem OttoTalk overlay (oder über dem chat input).
 * Nur sichtbar wenn man auf Ottonien.com connected ist.
 * Enthält: Voice Range button, Help button, Offtopic button innen im frame.
 * Otto button und Settings button stehen rechts daneben, ausserhalb vom frame.
 */
public class ServerChatOverlay {

    // Textures
    private static final Identifier TEX_RUFEN = new Identifier("ottotalk", "textures/gui/rufen.png");
    private static final Identifier TEX_SPRECHEN = new Identifier("ottotalk", "textures/gui/sprechen.png");
    private static final Identifier TEX_FLUSTERN = new Identifier("ottotalk", "textures/gui/fluestern.png");
    private static final Identifier TEX_HELP_ACTIVE = new Identifier("ottotalk", "textures/gui/helpmode_active.png");
    private static final Identifier TEX_HELP_DISABLED = new Identifier("ottotalk", "textures/gui/helpmode_disabled.png");
    private static final Identifier TEX_OFFTOPIC_ACTIVE = new Identifier("ottotalk", "textures/gui/offtopic_active.png");
    private static final Identifier TEX_OFFTOPIC_DISABLED = new Identifier("ottotalk", "textures/gui/offtopic_disabled.png");
    private static final Identifier TEX_CURRENTMODE = new Identifier("ottotalk", "textures/gui/currentmode.png");
    private static final Identifier TEX_OTTOBUTTON_ACTIVE = new Identifier("ottotalk", "textures/gui/aihelper_enabled.png");
    private static final Identifier TEX_OTTOBUTTON_DISABLED = new Identifier("ottotalk", "textures/gui/aihelper_disabled.png");
    private static final Identifier TEX_SETTINGS = new Identifier("ottotalk", "textures/gui/context.png");
    private static final Identifier TEX_WORLDMAP = new Identifier("ottotalk", "textures/gui/worldmap.png");
    private static final Identifier TEX_ROLENAME_ACTIVE = new Identifier("ottotalk", "textures/gui/rolename_active.png");
    private static final Identifier TEX_ROLENAME_DISABLED = new Identifier("ottotalk", "textures/gui/rolename_disabled.png");
    private static final Identifier TEX_EXTENDER = new Identifier("ottotalk", "textures/gui/server-extender-3-tile.png");
    private static final Identifier TEX_OTTO_FRAME = new Identifier("ottotalk", "textures/gui/otto-new-frame.png");
    private static final Identifier TEX_HISTORY_DISABLED = new Identifier("ottotalk", "textures/gui/history.png");
    private static final Identifier TEX_HISTORY_ACTIVE = new Identifier("ottotalk", "textures/gui/history_active.png");
    private static final Identifier TEX_EMOTE_DISABLED = new Identifier("ottotalk", "textures/gui/emotemode_disabled.png");
    private static final Identifier TEX_EMOTE_ACTIVE = new Identifier("ottotalk", "textures/gui/emotemode_active.png");
    private static final Identifier TEX_EXIT = new Identifier("ottotalk", "textures/gui/exit.png");
    private static final Identifier TEX_TITLE_ADAPTER = new Identifier("ottotalk", "textures/gui/Titel-adapter.png");
    private static final Identifier TEX_REDE = new Identifier("ottotalk", "textures/gui/rede.png");
    private static final Identifier TEX_ANWEISUNG = new Identifier("ottotalk", "textures/gui/anweisung.png");
    private static final Identifier TEX_TOGGLE_REDE = new Identifier("ottotalk", "textures/gui/Toggle_rede.png");
    private static final Identifier TEX_TOGGLE_ANWEISUNG = new Identifier("ottotalk", "textures/gui/Toggle_anweisung.png");
    private static final Identifier TEX_TILE_FRAME = new Identifier("ottotalk", "textures/gui/3-tile-frame.png");
    private static final int TILE_FRAME_TEX_W = 35;
    private static final int TILE_FRAME_TEX_H = 18;
    private static final int TILE_FRAME_CAP = 5;
    private static final int TITLE_ADAPTER_W = 40;
    private static final int TITLE_ADAPTER_H = 7;

    // haupt-button dimensions (nativ 20x18)
    private static final int BTN_W = 20;
    private static final int BTN_H = 18;
    // extender button dimensions (native größen)
    private static final int OTTO_BTN_W = 12;
    private static final int OTTO_BTN_H = 12;
    private static final int SET_BTN_W = 12;
    private static final int SET_BTN_H = 12;
    private static final int FEATURE_BTN_W = 12;
    private static final int FEATURE_BTN_H = 12;

    // server-frame button padding (genauso wie beim otto frame)
    private static final int SERVER_FRAME_BTN_PAD = 6;

    // extender 3-tile textur dimensions (echte datei: 65x30)
    private static final int EXT_TEX_W = 65;
    private static final int EXT_TEX_H = 30;
    private static final int EXT_CAP = 11;

    // Layout
    private static final int MARGIN_LEFT = 2;
    private static final int MARGIN_BOTTOM = 4;
    private static final int BUTTON_SPACING = 1;
    private static final int EXT_BUTTON_SPACING = 1;
    private static final int TEXT_BTN_GAP = 2;
    private static final int BTN_LOWER_OFFSET = 2;
    private static final int TITLE_COLOR = 0xFFE8C49A;
    private static final int EXT_BTN_LEFT_OFFSET = 10;
    private static final int EXT_BTN_LOWER_OFFSET = 1;
    private static final int TITLE_LOWER_OFFSET = 1;
    private static final long ANIM_DURATION_MS = 200;
    private static final long BTN_ANIM_MS = 150;

    // Otto expanded panel (3-tile frame)
    private static final int OTTO_FRAME_TEX_W = 70; // breite der source textur
    private static final int OTTO_FRAME_TEX_H = 27; // höhe der source textur
    private static final int OTTO_FRAME_CAP = 10; // linke/rechte cap fürs 3-tile
    private static final int OTTO_FRAME_BTN_PAD = 6; // padding vor dem ersten / nach dem letzten button
    private static final int HISTORY_BTN_W = 35;
    private static final int HISTORY_BTN_H = 18;
    private static final int EMOTE_BTN_W = 20;
    private static final int EMOTE_BTN_H = 18;
    private static final int CLOSE_BTN_W = 20;
    private static final int CLOSE_BTN_H = 18;
    private static final int MODE_BTN_W = 20;
    private static final int MODE_BTN_H = 18;
    private static final int OTTO_PANEL_BTN_SPACING = 2;
    private static final long OTTO_EXPAND_ANIM_MS = 200;

    // berechnete positionen
    private int overlayX, overlayY, overlayWidth, overlayHeight;
    private int frameWidth;
    private int extenderX, extenderWidth, extenderHeight;
    private int voiceBtnX, voiceBtnY;
    private int helpBtnX, helpBtnY;
    private int offtopicBtnX, offtopicBtnY;
    private int ottoBtnX, ottoBtnY;
    private int settingsBtnX, settingsBtnY;
    private int worldmapBtnX, worldmapBtnY;
    private int rolenameBtnX, rolenameBtnY;
    private boolean extWorldmapVisible;
    private boolean extRolenameVisible;
    private boolean extOttoVisible;
    private int titleY;
    private int extenderY;
    private int serverFrameRenderW;
    private int serverAdapterX, serverAdapterY;
    // positionen für das otto expanded panel
    private int ottoFrameX, ottoFrameY;
    private int historyBtnX, historyBtnY;
    private int emoteBtnX, emoteBtnY;
    private int closeBtnX, closeBtnY;
    private int modeBtnX, modeBtnY;
    private int ottoPanelTitleY;
    private int ottoAdapterX, ottoAdapterY;
    private int modeLabelX, modeLabelY;
    private int expandedExtenderWidth;
    private int ottoFrameRenderW;
    private boolean initialized = false;
    private long visibleSince = 0;
    private boolean wasVisible = false;
    private long buttonsVisibleSince = 0;
    private boolean buttonsAnimStarted = false;
    private boolean ottoExpanded = false;
    private long ottoExpandSince = 0;
    private long ottoBtnsVisibleSince = 0;
    private boolean ottoBtnsAnimStarted = false;

    /**
     * Initialisiert die overlay-position. wird jedes frame aufgerufen.
     * @param chatHudWidth breite vom chat HUD
     * @param bottomY die Y position unter der das overlay sitzt (oberkante vom otto overlay oder chat input)
     */
    public void init(int chatHudWidth, int bottomY) {
        // ottoExpanded mit dem roleplay state syncen, damits beim chat-wiederöffnen passt mit dem AI mode.
        // ottoExpandSince nur bei echten state-wechseln updaten, nicht jedes frame
        boolean shouldBeExpanded = RoleplayStateManager.isRoleplayModeActive();
        if (shouldBeExpanded && !ottoExpanded) {
            ottoExpanded = true;
            ottoExpandSince = System.currentTimeMillis();
        } else if (!shouldBeExpanded && ottoExpanded) {
            ottoExpanded = false;
            ottoExpandSince = System.currentTimeMillis();
        }
        this.overlayX = MARGIN_LEFT;

        // hauptframe: 3-tile mit otto-new-frame, breite richtet sich nach buttons + padding
        int totalBtnW = (BTN_W * 3) + (BUTTON_SPACING * 2);
        this.serverFrameRenderW = totalBtnW + SERVER_FRAME_BTN_PAD * 2;
        this.overlayHeight = OTTO_FRAME_TEX_H;
        this.overlayY = bottomY - overlayHeight - MARGIN_BOTTOM;
        this.frameWidth = serverFrameRenderW;

        // buttons: horizontal und vertikal im frame zentriert
        int btnStartX = overlayX + SERVER_FRAME_BTN_PAD;
        int btnY = overlayY + (OTTO_FRAME_TEX_H - BTN_H) / 2 + BTN_LOWER_OFFSET;

        this.voiceBtnX = btnStartX;
        this.voiceBtnY = btnY;

        this.helpBtnX = voiceBtnX + BTN_W + BUTTON_SPACING;
        this.helpBtnY = btnY;

        this.offtopicBtnX = helpBtnX + BTN_W + BUTTON_SPACING;
        this.offtopicBtnY = btnY;

        // titel: über den buttons (gleiches layout wie bei AI HELPER)
        int scaledTextH = 4; // 8px font at 0.55 scale
        this.titleY = btnY - scaledTextH - TEXT_BTN_GAP;

        // server Titel-adapter unter dem titel zentrieren
        this.serverAdapterX = overlayX + (serverFrameRenderW - TITLE_ADAPTER_W) / 2;
        this.serverAdapterY = this.titleY + scaledTextH + 1 - 5;

        // extender frame: direkt daneben, vertikal am server frame zentriert
        this.extenderX = overlayX + frameWidth;
        this.extWorldmapVisible = OttoTalkClient.getConfig().mapOverlayEnabled;
        this.extRolenameVisible = OttoTalkClient.getConfig().rolenameFeatureEnabled;
        this.extOttoVisible = OttoTalkClient.getConfig().aiHelperEnabled;
        this.extenderHeight = EXT_TEX_H;
        this.extenderY = overlayY + (OTTO_FRAME_TEX_H - EXT_TEX_H) / 2;

        // extender buttons werden dynamisch positioniert; layout hängt davon ab welche buttons an sind
        int extBtnY = this.extenderY + (EXT_TEX_H - SET_BTN_H) / 2 + EXT_BTN_LOWER_OFFSET;
        int curExtX = extenderX + 4;
        this.settingsBtnX = curExtX; this.settingsBtnY = extBtnY;
        curExtX += SET_BTN_W + EXT_BUTTON_SPACING;
        if (extWorldmapVisible) {
            this.worldmapBtnX = curExtX; this.worldmapBtnY = extBtnY;
            curExtX += FEATURE_BTN_W + EXT_BUTTON_SPACING;
        }
        if (extRolenameVisible) {
            this.rolenameBtnX = curExtX; this.rolenameBtnY = extBtnY;
            curExtX += FEATURE_BTN_W + EXT_BUTTON_SPACING;
        }
        if (extOttoVisible) {
            this.ottoBtnX = curExtX; this.ottoBtnY = extBtnY;
            curExtX += OTTO_BTN_W + EXT_BUTTON_SPACING;
        }
        // gesamtbreite vom extender, vom linken rand bis zum rechten rand vom letzten button
        this.extenderWidth = (curExtX - EXT_BUTTON_SPACING) - extenderX + 6;

        // otto frame: 3-tile, breite = buttons + padding auf beiden seiten
        int panelBtnsW = HISTORY_BTN_W + OTTO_PANEL_BTN_SPACING + EMOTE_BTN_W + OTTO_PANEL_BTN_SPACING + CLOSE_BTN_W + OTTO_PANEL_BTN_SPACING + MODE_BTN_W;
        int ottoFrameRenderW = panelBtnsW + OTTO_FRAME_BTN_PAD * 2;

        // expanded extender: otto frame render breite + gap + originaler buttons-bereich
        this.expandedExtenderWidth = extenderWidth + ottoFrameRenderW + 4;

        // otto frame position: startet direkt nach dem linken rand-bereich vom original extender
        this.ottoFrameX = extenderX + 2;
        this.ottoFrameY = overlayY + (OTTO_FRAME_TEX_H - OTTO_FRAME_TEX_H) / 2;
        this.ottoFrameRenderW = ottoFrameRenderW;

        // buttons innen im otto-new-frame: gleiches layout wie server frame (zentriert + lower offset)
        int panelBtnStartX = ottoFrameX + OTTO_FRAME_BTN_PAD;
        int panelBtnY = ottoFrameY + (OTTO_FRAME_TEX_H - HISTORY_BTN_H) / 2 + BTN_LOWER_OFFSET;

        // titel "AI HELPER" über den buttons (gleiches positioning wie KANÄLE im server frame)
        int ottoPanelScaledTextH = 4; // 8px font at 0.55 scale
        this.ottoPanelTitleY = panelBtnY - ottoPanelScaledTextH - TEXT_BTN_GAP;

        // Titel-adapter unter dem titel zentriert
        this.ottoAdapterX = ottoFrameX + (ottoFrameRenderW - TITLE_ADAPTER_W) / 2;
        this.ottoAdapterY = this.ottoPanelTitleY + ottoPanelScaledTextH + 1 - 5;
        this.historyBtnX = panelBtnStartX;
        this.historyBtnY = panelBtnY;
        this.emoteBtnX = historyBtnX + HISTORY_BTN_W + OTTO_PANEL_BTN_SPACING;
        this.emoteBtnY = panelBtnY;
        this.closeBtnX = emoteBtnX + EMOTE_BTN_W + OTTO_PANEL_BTN_SPACING;
        this.closeBtnY = panelBtnY;
        this.modeBtnX = closeBtnX + CLOSE_BTN_W + OTTO_PANEL_BTN_SPACING;
        this.modeBtnY = panelBtnY;

        // gesamtbreite umfasst beide frames (wenn offen, expanded nehmen)
        this.overlayWidth = (extenderX + (ottoExpanded ? expandedExtenderWidth : extenderWidth)) - overlayX;

        this.initialized = true;
    }

    public int getOverlayHeight() {
        return overlayHeight;
    }

    public int getOverlayY() {
        return overlayY;
    }

    // --- Rendering ---

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!initialized) return;

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        // visibility für die animation tracken
        if (!wasVisible) {
            visibleSince = System.currentTimeMillis();
            wasVisible = true;
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
        renderOttoFrame(context, overlayX, overlayY, serverFrameRenderW);

        // der extender hat seine eigene animation, bewusst erst nachdem das hauptframe fertig ist
        float extProgress;
        float extEased;
        if (!OttoTalkClient.getConfig().enableAnimations) {
            extProgress = 1.0f;
            extEased = 1.0f;
        } else {
            long extElapsed = System.currentTimeMillis() - visibleSince - ANIM_DURATION_MS;
            extProgress = Math.min(1.0f, Math.max(0.0f, (float) extElapsed / ANIM_DURATION_MS));
            extEased = 1.0f - (1.0f - extProgress) * (1.0f - extProgress);
        }

        // animation progress fürs otto-expand (startet erst wenn der extender komplett sichtbar ist)
        float ottoExpandProgress;
        float ottoExpandEased;
        if (!ottoExpanded) {
            ottoExpandProgress = 0.0f;
            ottoExpandEased = 0.0f;
        } else if (!OttoTalkClient.getConfig().enableAnimations) {
            ottoExpandProgress = 1.0f;
            ottoExpandEased = 1.0f;
        } else if (extProgress < 1.0f) {
            // extender ist noch nicht fertig, timer immer wieder resetten damit die animation frisch startet
            ottoExpandProgress = 0.0f;
            ottoExpandEased = 0.0f;
            ottoExpandSince = System.currentTimeMillis();
        } else {
            long ottoElapsed = System.currentTimeMillis() - ottoExpandSince;
            ottoExpandProgress = Math.min(1.0f, Math.max(0.0f, (float) ottoElapsed / OTTO_EXPAND_ANIM_MS));
            ottoExpandEased = 1.0f - (1.0f - ottoExpandProgress) * (1.0f - ottoExpandProgress);
        }

        // aktuelle extender breite (animiert zwischen collapsed und expanded)
        int currentExtW = extenderWidth + (int)((expandedExtenderWidth - extenderWidth) * ottoExpandEased);
        if (extProgress > 0.0f) {
            int animExtW = (int)(currentExtW * extEased);
            if (animExtW >= EXT_CAP * 2) {
                renderExtenderFrame(context, extenderX, extenderY, animExtW, EXT_TEX_H);
            }
        }

        // otto-new-frame erscheint erst nachdem der extender fertig ausgefahren ist; sonst würden
        // sich die beiden frames mitten in der animation überlappen.
        if (ottoExpanded && extProgress >= 1.0f && ottoExpandProgress > 0.2f) {
            renderOttoFrame(context, ottoFrameX, ottoFrameY, ottoFrameRenderW);

            // Titel-adapter zuerst (im z-order unter dem titel)
            context.drawTexture(TEX_TITLE_ADAPTER, ottoAdapterX, ottoAdapterY, TITLE_ADAPTER_W, TITLE_ADAPTER_H,
                    0, 0, TITLE_ADAPTER_W, TITLE_ADAPTER_H, TITLE_ADAPTER_W, TITLE_ADAPTER_H);

            // titel "AI HELPER" über dem adapter
            net.minecraft.text.MutableText ottoTitleText = Text.literal("AI HELPER")
                    .setStyle(net.minecraft.text.Style.EMPTY.withBold(true));
            float ottoTitleScale = 0.55f;
            int ottoFullTitleW = textRenderer.getWidth(ottoTitleText) + 1;
            int ottoScaledTitleW = Math.round(ottoFullTitleW * ottoTitleScale);
            int ottoTitleX = ottoFrameX + (ottoFrameRenderW - ottoScaledTitleW) / 2;
            context.getMatrices().push();
            context.getMatrices().translate(ottoTitleX, ottoPanelTitleY + TITLE_LOWER_OFFSET, 0);
            context.getMatrices().scale(ottoTitleScale, ottoTitleScale, 1.0f);
            context.drawText(textRenderer, ottoTitleText, 0, 0, TITLE_COLOR, false);
            context.getMatrices().pop();
        }

        if (progress >= 0.5f) {
            // adapter wird vor dem titel gezeichnet damit der titel-text im z-order obenauf bleibt
            context.drawTexture(TEX_TITLE_ADAPTER, serverAdapterX, serverAdapterY, TITLE_ADAPTER_W, TITLE_ADAPTER_H,
                    0, 0, TITLE_ADAPTER_W, TITLE_ADAPTER_H, TITLE_ADAPTER_W, TITLE_ADAPTER_H);

            // titel "KANÄLE" über dem adapter
            net.minecraft.text.MutableText titleText = Text.literal("KANÄLE")
                    .setStyle(net.minecraft.text.Style.EMPTY.withBold(true));
            float scale = 0.55f;
            int fullTitleW = textRenderer.getWidth(titleText) + 1;
            int scaledTitleW = Math.round(fullTitleW * scale);
            int titleX = overlayX + (serverFrameRenderW - scaledTitleW) / 2 + 2;
            context.getMatrices().push();
            context.getMatrices().translate(titleX, titleY + TITLE_LOWER_OFFSET, 0);
            context.getMatrices().scale(scale, scale, 1.0f);
            context.drawText(textRenderer, titleText, 0, 0, TITLE_COLOR, false);
            context.getMatrices().pop();
        }

        // buttons erst rendern wenn die animation fertig ist
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
            // 3 buttons innen im hauptframe
            renderScaledButton(context, mouseX, mouseY, btnScale, voiceBtnX, voiceBtnY, () -> renderVoiceButton(context, mouseX, mouseY));
            renderScaledButton(context, mouseX, mouseY, btnScale, helpBtnX, helpBtnY, () -> renderHelpButton(context, mouseX, mouseY));
            renderScaledButton(context, mouseX, mouseY, btnScale, offtopicBtnX, offtopicBtnY, () -> renderOfftopicButton(context, mouseX, mouseY));

            // Settings + Otto buttons: nach rechts shiften wenn expanded
            int extBtnShift = (int)((expandedExtenderWidth - extenderWidth) * ottoExpandEased);
            int curSettingsX = settingsBtnX + extBtnShift;
            int curOttoX = ottoBtnX + extBtnShift;

            renderScaledButton(context, mouseX, mouseY, btnScale, curSettingsX, settingsBtnY,
                    () -> renderSettingsButtonAt(context, mouseX, mouseY, curSettingsX, settingsBtnY));
            if (extWorldmapVisible) {
                int curWorldmapX = worldmapBtnX + extBtnShift;
                renderScaledButton(context, mouseX, mouseY, btnScale, curWorldmapX, worldmapBtnY,
                        () -> renderWorldmapButtonAt(context, mouseX, mouseY, curWorldmapX, worldmapBtnY));
            }
            if (extRolenameVisible) {
                int curRolenameX = rolenameBtnX + extBtnShift;
                renderScaledButton(context, mouseX, mouseY, btnScale, curRolenameX, rolenameBtnY,
                        () -> renderRolenameButtonAt(context, mouseX, mouseY, curRolenameX, rolenameBtnY));
            }
            if (extOttoVisible) {
                renderScaledButton(context, mouseX, mouseY, btnScale, curOttoX, ottoBtnY,
                        () -> renderOttoButtonAt(context, mouseX, mouseY, curOttoX, ottoBtnY));
            }

            // otto panel buttons (history, emote, close, mode toggle) wenn expanded - mit scale-animation
            if (ottoExpanded && ottoExpandProgress >= 1.0f) {
                if (!ottoBtnsAnimStarted) {
                    ottoBtnsVisibleSince = System.currentTimeMillis();
                    ottoBtnsAnimStarted = true;
                }
                float ottoBtnScale;
                if (!OttoTalkClient.getConfig().enableAnimations) {
                    ottoBtnScale = 1.0f;
                } else {
                    long ottoBtnElapsed = System.currentTimeMillis() - ottoBtnsVisibleSince;
                    float ottoBtnProgress = Math.min(1.0f, (float) ottoBtnElapsed / BTN_ANIM_MS);
                    ottoBtnScale = 1.0f - (1.0f - ottoBtnProgress) * (1.0f - ottoBtnProgress);
                }
                renderScaledButton(context, mouseX, mouseY, ottoBtnScale, historyBtnX, historyBtnY,
                        () -> renderHistoryButton(context, textRenderer, mouseX, mouseY));
                renderScaledButton(context, mouseX, mouseY, ottoBtnScale, emoteBtnX, emoteBtnY,
                        () -> renderEmoteButton(context, mouseX, mouseY));
                renderScaledButton(context, mouseX, mouseY, ottoBtnScale, closeBtnX, closeBtnY,
                        () -> renderCloseButton(context, mouseX, mouseY));
                renderScaledButton(context, mouseX, mouseY, ottoBtnScale, modeBtnX, modeBtnY,
                        () -> renderModeToggleButton(context, mouseX, mouseY));
            } else {
                ottoBtnsAnimStarted = false;
            }
        }
    }

    public void resetAnimation() {
        wasVisible = false;
        buttonsAnimStarted = false;
        ottoBtnsAnimStarted = false;
    }

    private void renderScaledButton(DrawContext context, int mouseX, int mouseY, float scale, int btnX, int btnY, Runnable drawCall) {
        if (scale >= 1.0f) {
            drawCall.run();
            return;
        }
        float cx = btnX + BTN_W / 2.0f;
        float cy = btnY + BTN_H / 2.0f;
        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-cx, -cy, 0);
        drawCall.run();
        context.getMatrices().pop();
    }

    private void renderExtenderFrame(DrawContext context, int x, int y, int width, int height) {
        int cap = EXT_CAP;
        int texW = EXT_TEX_W;
        int texH = EXT_TEX_H;
        int tileW = texW - 2 * cap;
        int middleW = width - 2 * cap;

        // linke cap auf nativer textur-höhe (kein vertikaler stretch)
        context.drawTexture(TEX_EXTENDER, x, y, cap, texH,
                0, 0, cap, texH, texW, texH);
        // mitte horizontal getilet, native höhe
        if (middleW > 0 && tileW > 0) {
            int drawnX = 0;
            while (drawnX < middleW) {
                int drawW = Math.min(tileW, middleW - drawnX);
                context.drawTexture(TEX_EXTENDER, x + cap + drawnX, y, drawW, texH,
                        cap, 0, drawW, texH, texW, texH);
                drawnX += tileW;
            }
        }
        // rechte cap auf nativer textur-höhe
        context.drawTexture(TEX_EXTENDER, x + width - cap, y, cap, texH,
                texW - cap, 0, cap, texH, texW, texH);
    }

    private void renderOttoFrame(DrawContext context, int x, int y, int width) {
        int cap = OTTO_FRAME_CAP;
        int texW = OTTO_FRAME_TEX_W;
        int texH = OTTO_FRAME_TEX_H;
        int tileW = texW - 2 * cap;
        int middleW = width - 2 * cap;

        // Left cap
        context.drawTexture(TEX_OTTO_FRAME, x, y, cap, texH,
                0, 0, cap, texH, texW, texH);
        // Middle tiled horizontally
        if (middleW > 0 && tileW > 0) {
            int drawnX = 0;
            while (drawnX < middleW) {
                int drawW = Math.min(tileW, middleW - drawnX);
                context.drawTexture(TEX_OTTO_FRAME, x + cap + drawnX, y, drawW, texH,
                        cap, 0, drawW, texH, texW, texH);
                drawnX += tileW;
            }
        }
        // Right cap
        context.drawTexture(TEX_OTTO_FRAME, x + width - cap, y, cap, texH,
                texW - cap, 0, cap, texH, texW, texH);
    }

    private void renderVoiceButton(DrawContext context, int mouseX, int mouseY) {
        ServerChatState.VoiceRange range = ServerChatState.getVoiceRange();
        Identifier tex;
        switch (range) {
            case RUFEN: tex = TEX_RUFEN; break;
            case FLUSTERN: tex = TEX_FLUSTERN; break;
            default: tex = TEX_SPRECHEN; break;
        }

        context.drawTexture(tex, voiceBtnX, voiceBtnY, BTN_W, BTN_H, 0, 0, 20, 18, 20, 18);

        // currentmode overlay wenn currently_writing (mit alpha blending)
        if (ServerChatState.isVoiceWriting()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.drawTexture(TEX_CURRENTMODE, voiceBtnX, voiceBtnY, BTN_W, BTN_H, 0, 0, 20, 18, 20, 18);
            RenderSystem.disableBlend();
        }

        // hover effekt
        if (isHovered(mouseX, mouseY, voiceBtnX, voiceBtnY, BTN_W, BTN_H)) {
            context.fill(voiceBtnX + 1, voiceBtnY + 1,
                    voiceBtnX + BTN_W - 1, voiceBtnY + BTN_H - 1, 0x33FFFFFF);
        }
    }

    private void renderHelpButton(DrawContext context, int mouseX, int mouseY) {
        ServerChatState.ChannelState state = ServerChatState.getHelpState();
        boolean active = state != ServerChatState.ChannelState.DISABLED;
        Identifier tex = active ? TEX_HELP_ACTIVE : TEX_HELP_DISABLED;

        context.drawTexture(tex, helpBtnX, helpBtnY, BTN_W, BTN_H, 0, 0, 20, 18, 20, 18);

        // Currentmode overlay if currently_writing (with alpha blending)
        if (state == ServerChatState.ChannelState.CURRENTLY_WRITING) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.drawTexture(TEX_CURRENTMODE, helpBtnX, helpBtnY, BTN_W, BTN_H, 0, 0, 20, 18, 20, 18);
            RenderSystem.disableBlend();
        }

        // Hover effect
        if (isHovered(mouseX, mouseY, helpBtnX, helpBtnY, BTN_W, BTN_H)) {
            context.fill(helpBtnX + 1, helpBtnY + 1,
                    helpBtnX + BTN_W - 1, helpBtnY + BTN_H - 1, 0x33FFFFFF);
        }
    }

    private void renderOfftopicButton(DrawContext context, int mouseX, int mouseY) {
        ServerChatState.ChannelState state = ServerChatState.getOfftopicState();
        boolean active = state != ServerChatState.ChannelState.DISABLED;
        Identifier tex = active ? TEX_OFFTOPIC_ACTIVE : TEX_OFFTOPIC_DISABLED;

        context.drawTexture(tex, offtopicBtnX, offtopicBtnY, BTN_W, BTN_H, 0, 0, 20, 18, 20, 18);

        // Currentmode overlay if currently_writing (with alpha blending)
        if (state == ServerChatState.ChannelState.CURRENTLY_WRITING) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.drawTexture(TEX_CURRENTMODE, offtopicBtnX, offtopicBtnY, BTN_W, BTN_H, 0, 0, 20, 18, 20, 18);
            RenderSystem.disableBlend();
        }

        // Hover effect
        if (isHovered(mouseX, mouseY, offtopicBtnX, offtopicBtnY, BTN_W, BTN_H)) {
            context.fill(offtopicBtnX + 1, offtopicBtnY + 1,
                    offtopicBtnX + BTN_W - 1, offtopicBtnY + BTN_H - 1, 0x33FFFFFF);
        }
    }

    private void renderOttoButtonAt(DrawContext context, int mouseX, int mouseY, int x, int y) {
        boolean ottoActive = ottoExpanded;
        Identifier tex = ottoActive ? TEX_OTTOBUTTON_ACTIVE : TEX_OTTOBUTTON_DISABLED;
        context.drawTexture(tex, x, y, OTTO_BTN_W, OTTO_BTN_H, 0, 0, 12, 12, 12, 12);
        if (isHovered(mouseX, mouseY, x, y, OTTO_BTN_W, OTTO_BTN_H)) {
            context.fill(x + 1, y + 1, x + OTTO_BTN_W - 1, y + OTTO_BTN_H - 1, 0x33FFFFFF);
        }
    }

    private void renderSettingsButtonAt(DrawContext context, int mouseX, int mouseY, int x, int y) {
        context.drawTexture(TEX_SETTINGS, x, y, SET_BTN_W, SET_BTN_H, 0, 0, 12, 12, 12, 12);
        if (isHovered(mouseX, mouseY, x, y, SET_BTN_W, SET_BTN_H)) {
            context.fill(x + 1, y + 1, x + SET_BTN_W - 1, y + SET_BTN_H - 1, 0x33FFFFFF);
        }
    }

    private void renderWorldmapButtonAt(DrawContext context, int mouseX, int mouseY, int x, int y) {
        context.drawTexture(TEX_WORLDMAP, x, y, FEATURE_BTN_W, FEATURE_BTN_H, 0, 0, FEATURE_BTN_W, FEATURE_BTN_H, FEATURE_BTN_W, FEATURE_BTN_H);
        if (isHovered(mouseX, mouseY, x, y, FEATURE_BTN_W, FEATURE_BTN_H)) {
            context.fill(x + 1, y + 1, x + FEATURE_BTN_W - 1, y + FEATURE_BTN_H - 1, 0x33FFFFFF);
        }
    }

    private void renderRolenameButtonAt(DrawContext context, int mouseX, int mouseY, int x, int y) {
        boolean active = OttoTalkClient.getConfig().showCharacterNames;
        Identifier tex = active ? TEX_ROLENAME_ACTIVE : TEX_ROLENAME_DISABLED;
        context.drawTexture(tex, x, y, FEATURE_BTN_W, FEATURE_BTN_H, 0, 0, FEATURE_BTN_W, FEATURE_BTN_H, FEATURE_BTN_W, FEATURE_BTN_H);
        if (isHovered(mouseX, mouseY, x, y, FEATURE_BTN_W, FEATURE_BTN_H)) {
            context.fill(x + 1, y + 1, x + FEATURE_BTN_W - 1, y + FEATURE_BTN_H - 1, 0x33FFFFFF);
        }
    }

    private void renderHistoryButton(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        boolean enabled = RoleplayStateManager.isHistoryEnabled();
        Identifier tex = enabled ? TEX_HISTORY_ACTIVE : TEX_HISTORY_DISABLED;
        context.drawTexture(tex, historyBtnX, historyBtnY, HISTORY_BTN_W, HISTORY_BTN_H, 0, 0, 35, 18, 35, 18);
        if (isHovered(mouseX, mouseY, historyBtnX, historyBtnY, HISTORY_BTN_W, HISTORY_BTN_H)) {
            context.fill(historyBtnX + 1, historyBtnY + 1,
                    historyBtnX + HISTORY_BTN_W - 1, historyBtnY + HISTORY_BTN_H - 1, 0x33FFFFFF);
        }
        int checked = ChatHistoryManager.getCheckedCount();
        int total = ChatHistoryManager.size();
        String countText = checked + "/" + total;
        float scale = 0.5f;
        int scaledTextW = (int)(textRenderer.getWidth(countText) * scale);
        int regionCenter = historyBtnX + HISTORY_BTN_W / 4;
        int textX = regionCenter - scaledTextW / 2;
        int textY = historyBtnY + (HISTORY_BTN_H - (int)(8 * scale)) / 2;
        context.getMatrices().push();
        context.getMatrices().translate(textX, textY, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawText(textRenderer, Text.literal(countText), 0, 0, 0xFFFFFFFF, false);
        context.getMatrices().pop();
    }

    private void renderEmoteButton(DrawContext context, int mouseX, int mouseY) {
        boolean enabled = RoleplayStateManager.isEmoteModeEnabled();
        Identifier tex = enabled ? TEX_EMOTE_ACTIVE : TEX_EMOTE_DISABLED;
        context.drawTexture(tex, emoteBtnX, emoteBtnY, EMOTE_BTN_W, EMOTE_BTN_H, 0, 0, 20, 18, 20, 18);
        if (isHovered(mouseX, mouseY, emoteBtnX, emoteBtnY, EMOTE_BTN_W, EMOTE_BTN_H)) {
            context.fill(emoteBtnX + 1, emoteBtnY + 1,
                    emoteBtnX + EMOTE_BTN_W - 1, emoteBtnY + EMOTE_BTN_H - 1, 0x33FFFFFF);
        }
    }

    private void renderCloseButton(DrawContext context, int mouseX, int mouseY) {
        context.drawTexture(TEX_EXIT, closeBtnX, closeBtnY, CLOSE_BTN_W, CLOSE_BTN_H, 0, 0, 20, 18, 20, 18);
        if (isHovered(mouseX, mouseY, closeBtnX, closeBtnY, CLOSE_BTN_W, CLOSE_BTN_H)) {
            context.fill(closeBtnX + 1, closeBtnY + 1,
                    closeBtnX + CLOSE_BTN_W - 1, closeBtnY + CLOSE_BTN_H - 1, 0x33FFFFFF);
        }
    }

    private void renderModeToggleButton(DrawContext context, int mouseX, int mouseY) {
        boolean isRede = RoleplayStateManager.getSpeechMode() == RoleplayStateManager.SpeechMode.REDE;
        Identifier tex = isRede ? TEX_TOGGLE_REDE : TEX_TOGGLE_ANWEISUNG;
        context.drawTexture(tex, modeBtnX, modeBtnY, MODE_BTN_W, MODE_BTN_H, 0, 0, 20, 18, 20, 18);
        if (isHovered(mouseX, mouseY, modeBtnX, modeBtnY, MODE_BTN_W, MODE_BTN_H)) {
            context.fill(modeBtnX + 1, modeBtnY + 1,
                    modeBtnX + MODE_BTN_W - 1, modeBtnY + MODE_BTN_H - 1, 0x33FFFFFF);
        }
    }

    private void renderTileFrame(DrawContext context, int x, int y, int width) {
        int cap = TILE_FRAME_CAP;
        int h = TILE_FRAME_TEX_H;
        int texW = TILE_FRAME_TEX_W;
        int midSrcW = texW - cap * 2;
        // Left cap
        context.drawTexture(TEX_TILE_FRAME, x, y, cap, h, 0, 0, cap, h, texW, h);
        // Middle (tiled)
        int midStart = x + cap;
        int midEnd = x + width - cap;
        int drawX = midStart;
        while (drawX < midEnd) {
            int drawW = Math.min(midSrcW, midEnd - drawX);
            context.drawTexture(TEX_TILE_FRAME, drawX, y, drawW, h, cap, 0, drawW, h, texW, h);
            drawX += drawW;
        }
        // Right cap
        context.drawTexture(TEX_TILE_FRAME, x + width - cap, y, cap, h, texW - cap, 0, cap, h, texW, h);
    }

    private boolean isHovered(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static void openXaeroWorldMap() {
        MinecraftClient mc = MinecraftClient.getInstance();
        // matchesKey() durchgehen um den keycode zu finden den Xaero an seine world-map action gebunden hat
        int resolved = org.lwjgl.glfw.GLFW.GLFW_KEY_M; // default fallback
        for (net.minecraft.client.option.KeyBinding kb : mc.options.allKeys) {
            if ("key.xaero_world_map".equals(kb.getTranslationKey())) {
                for (int k = 32; k <= 348; k++) {
                    if (kb.matchesKey(k, 0)) { resolved = k; break; }
                }
                break;
            }
        }
        final int keyCode = resolved;
        // schritt 1: erstmal alle offenen screens zu - ChatScreen verschluckt key bindings solang er offen ist
        mc.execute(() -> {
            if (mc.currentScreen != null) mc.setScreen(null);
            // schritt 2: jetzt wo der screen weg ist, den key direkt über den GLFW callback feuern
            mc.execute(() -> {
                try {
                    long win = mc.getWindow().getHandle();
                    org.lwjgl.glfw.GLFWKeyCallback prev =
                            org.lwjgl.glfw.GLFW.glfwSetKeyCallback(win, null);
                    if (prev != null) {
                        prev.invoke(win, keyCode, 0, org.lwjgl.glfw.GLFW.GLFW_PRESS, 0);
                        org.lwjgl.glfw.GLFW.glfwSetKeyCallback(win, prev);
                    }
                } catch (Exception ignored) {}
            });
        });
    }

    // --- Click handling ---

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!initialized) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Voice Range button
        if (isHovered(mx, my, voiceBtnX, voiceBtnY, BTN_W, BTN_H)) { // klick
            ServerChatState.clickVoiceRange();
            return true;
        }

        // hilfe button
        if (isHovered(mx, my, helpBtnX, helpBtnY, BTN_W, BTN_H)) {
            ServerChatState.clickHelp();
            return true;
        }

        // offtopic button
        if (isHovered(mx, my, offtopicBtnX, offtopicBtnY, BTN_W, BTN_H)) {
            ServerChatState.clickOfftopic();
            return true;
        }

        // aktuell geshiftete positionen für settings/otto berechnen
        float ottoExpandEased;
        if (!ottoExpanded) {
            ottoExpandEased = 0.0f;
        } else if (!OttoTalkClient.getConfig().enableAnimations) {
            ottoExpandEased = 1.0f;
        } else {
            long ottoElapsed = System.currentTimeMillis() - ottoExpandSince;
            float p = Math.min(1.0f, Math.max(0.0f, (float) ottoElapsed / OTTO_EXPAND_ANIM_MS));
            ottoExpandEased = 1.0f - (1.0f - p) * (1.0f - p);
        }
        int extBtnShift = (int)((expandedExtenderWidth - extenderWidth) * ottoExpandEased);
        int curOttoX = ottoBtnX + extBtnShift;
        int curSettingsX = settingsBtnX + extBtnShift;

        // Otto button: toggelt das expanded panel UND das roleplay overlay zusammen
        if (extOttoVisible && isHovered(mx, my, curOttoX, ottoBtnY, OTTO_BTN_W, OTTO_BTN_H)) {
            ottoExpanded = !ottoExpanded;
            ottoExpandSince = System.currentTimeMillis();
            if (ottoExpanded) {
                RoleplayStateManager.enableRoleplayMode();
            } else {
                RoleplayStateManager.disableRoleplayMode();
            }
            return true;
        }

        // settings button: macht den CharacterInfoScreen auf
        if (isHovered(mx, my, curSettingsX, settingsBtnY, SET_BTN_W, SET_BTN_H)) {
            MinecraftClient.getInstance().setScreen(new CharacterInfoScreen());
            return true;
        }

        // worldmap button: öffnet Xaeros World Map
        if (extWorldmapVisible) {
            int curWorldmapX = worldmapBtnX + extBtnShift;
            if (isHovered(mx, my, curWorldmapX, worldmapBtnY, FEATURE_BTN_W, FEATURE_BTN_H)) {
                openXaeroWorldMap();
                return true;
            }
        }

        // rolename toggle button
        if (extRolenameVisible) {
            int curRolenameX = rolenameBtnX + extBtnShift;
            if (isHovered(mx, my, curRolenameX, rolenameBtnY, FEATURE_BTN_W, FEATURE_BTN_H)) {
                com.ottotalk.config.OttoTalkConfig cfg = OttoTalkClient.getConfig();
                cfg.showCharacterNames = !cfg.showCharacterNames;
                cfg.save();
                com.ottotalk.context.CharacterNameResolver.refreshChatMessages(cfg.showCharacterNames);
                return true;
            }
        }

        // otto panel buttons (nur wenn voll expanded)
        if (ottoExpanded) {
            // history toggle
            if (isHovered(mx, my, historyBtnX, historyBtnY, HISTORY_BTN_W, HISTORY_BTN_H)) {
                RoleplayStateManager.toggleHistory();
                return true;
            }
            // emote modus umschalten
            if (isHovered(mx, my, emoteBtnX, emoteBtnY, EMOTE_BTN_W, EMOTE_BTN_H)) {
                RoleplayStateManager.toggleEmoteMode();
                return true;
            }
            // mode toggle button (rede/anweisung)
            if (isHovered(mx, my, modeBtnX, modeBtnY, MODE_BTN_W, MODE_BTN_H)) {
                RoleplayStateManager.toggleSpeechMode();
                return true;
            }
            if (isHovered(mx, my, closeBtnX, closeBtnY, CLOSE_BTN_W, CLOSE_BTN_H)) {
                ottoExpanded = false;
                ottoExpandSince = System.currentTimeMillis();
                RoleplayStateManager.disableRoleplayMode();
                return true;
            }
        }

        return false;
    }
}
