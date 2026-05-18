package com.ottotalk.gui;

import com.ottotalk.OttoTalkClient;
import com.ottotalk.config.OttoTalkConfig;
import com.ottotalk.context.CharacterNameResolver;
import com.ottotalk.context.PlayerNameList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Settings-Screen mit Sidebar-Navigation und Hauptbereich rechts daneben.
 * Drauf liegt das otto_frame 9-slice.
 */
public class CharacterInfoScreen extends Screen {

    private enum Tab { FEATURES, WELTKARTE, ROLLENNAMEN, CHAT, FARBEN, OBERFLAECHE, KONTEXT, LLM, SPIELER, INFO }
    private Tab activeTab = Tab.FEATURES;

    // texturen
    private static final Identifier TEX_EXIT = new Identifier("ottotalk", "textures/gui/exit.png");
    private static final Identifier TEX_SAVE = new Identifier("ottotalk", "textures/gui/save.png");
    private static final Identifier TEX_EYE = new Identifier("ottotalk", "textures/gui/eye.png");
    private static final Identifier TEX_EMPTY = new Identifier("ottotalk", "textures/gui/empty.png");
    private static final Identifier TEX_CURRENTMODE = new Identifier("ottotalk", "textures/gui/currentmode.png");
    private static final Identifier TEX_ON = new Identifier("ottotalk", "textures/gui/on.png");
    private static final Identifier TEX_OFF = new Identifier("ottotalk", "textures/gui/off.png");
    private static final Identifier TEX_UNBEKANNT = new Identifier("ottotalk", "textures/gui/Unbekannt.png");
    private static final Identifier TEX_HELPMODE_ACTIVE = new Identifier("ottotalk", "textures/gui/helpmode_active.png");
    private static final Identifier TEX_HELPMODE_DISABLED = new Identifier("ottotalk", "textures/gui/helpmode_disabled.png");
    private static final Identifier TEX_OFFTOPIC_ACTIVE = new Identifier("ottotalk", "textures/gui/offtopic_active.png");
    private static final Identifier TEX_OFFTOPIC_DISABLED = new Identifier("ottotalk", "textures/gui/offtopic_disabled.png");
    private static final Identifier TEX_RUFEN = new Identifier("ottotalk", "textures/gui/rufen.png");
    private static final Identifier TEX_SKIN             = new Identifier("ottotalk", "textures/gui/skin.png");
    private static final Identifier TEX_PREVIEW_ACCOUNT  = new Identifier("ottotalk", "textures/gui/account_preview.png");
    private static final Identifier TEX_PREVIEW_ROLE     = new Identifier("ottotalk", "textures/gui/rollenname_preview.png");
    private static final Identifier TEX_PREVIEW_TITLE    = new Identifier("ottotalk", "textures/gui/titel_preview.png");
    private static final int SKIN_TEX_W = 269;
    private static final int SKIN_TEX_H = 175;

    // texturen für die feature toggle cards
    private static final Identifier TEX_EINSTELLUNGEN = new Identifier("ottotalk", "textures/gui/Einstellungen.png");
    private static final Identifier TEX_OTTO_HEADER = new Identifier("ottotalk", "textures/gui/Ottoplus.png");
    private static final Identifier TEX_TOGGLE_WORLDMAP = new Identifier("ottotalk", "textures/gui/worldmapToggle.png");
    private static final Identifier TEX_TOGGLE_ROLENAME = new Identifier("ottotalk", "textures/gui/rolenameToggle.png");
    private static final Identifier TEX_TOGGLE_AIHELPER = new Identifier("ottotalk", "textures/gui/aihelperToggle.png");
    private static final Identifier TEX_TOGGLE_EMPTY = new Identifier("ottotalk", "textures/gui/empty.png");
    private static final int TOGGLE_CARD_W = 46;
    private static final int TOGGLE_CARD_H = 52;
    private static final int TOGGLE_ICON_SIZE = 30;
    private static final int SECTION_HEADER_H = 13;
    private static final int ICON_W = 20;
    private static final int ICON_H = 18;

    // setting row dimensions (ein server-3-tile frame pro setting)
    private static final int SETTING_ROW_H = 32;
    private static final int SETTING_ROW_GAP = 6;

    // layout maße
    private static final int SIDEBAR_W = 80;
    private static final int SIDEBAR_GAP = 4;
    private static final int FRAME_W_IDEAL = 300;
    private static final int FRAME_H_IDEAL = 300;
    private static final int INNER_PAD = 16;
    private static final int FIELD_HEIGHT = 20;
    // dynamische frame-größe (im init aufm screen geclamped)
    private int FRAME_W = FRAME_W_IDEAL;
    private int FRAME_H = FRAME_H_IDEAL;

    // farben
    private static final int COLOR_TITLE = 0xFFFFD700;
    private static final int COLOR_LABEL = 0xFFEEDDAA;
    private static final int COLOR_DESC = 0xFFBBBBBB;
    private static final int COLOR_TAB_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_TAB_INACTIVE = 0xFF9B7653;
    private static final int COLOR_VERSION = 0xFF999999;

    private static final String VERSION = "1.6.2";

    // sidebar position
    private int sidebarX, sidebarY;
    private int sidebarInnerX, sidebarInnerW;
    private int sidebarScrollPx = 0;
    private static final int TAB_COUNT = 10;
    private int[] sideTabY = new int[TAB_COUNT];
    private static final int SIDE_TAB_H = 16;
    private static final int SIDE_TAB_GAP = 4;
    private static final int SIDE_HEADER_H = 9;  // höhe vom sidebar section label
    private static final int SIDE_HEADER_GAP = 2; // abstand nach dem section label
    private static final String[] TAB_NAMES = {"Features", "Weltkarte", "Rollennamen", "Chat", "Farben", "Oberfl\u00E4che", "Kontext", "LLM", "Spieler", "Info"};

    // haupt-frame position
    private int frameX, frameY;
    private int contentX, contentY, contentW;

    // button positionen
    private int closeBtnX, closeBtnY;

    // titel-frame (eigener frame über dem hauptcontent)
    private static final int TITLE_FRAME_H = 38;
    private static final int TITLE_GAP = 4;
    private int titleFrameX, titleFrameY, titleFrameW;

    // felder vom Kontext tab
    private TextFieldWidget nameField;
    private TextFieldWidget roleField;
    private TextFieldWidget titleField;
    private TextFieldWidget backgroundField;
    private TextFieldWidget instructionsField;

    // felder vom LLM tab
    private TextFieldWidget apiKeyField;
    private TextFieldWidget apiUrlField;
    private TextFieldWidget modelField;
    private String selectedProvider;
    private int providerBtnX, providerBtnY, providerBtnW, providerBtnH;
    private boolean apiKeyVisible = false;
    private int eyeBtnX, eyeBtnY;

    // toggles vom Allgemein tab
    private boolean animationsEnabled = true;
    private boolean showCharacterNames = false;
    // setting row positionen (Y von jedem server-3-tile)
    private int animRowY;
    private int charNamesRowY;
    // toggle button positionen (rechts in der reihe)
    private int animToggleX, animToggleY;
    private int charNamesToggleX, charNamesToggleY;

    // hotkey buttons (multi-slot: 0=charName, 1=hilfe, 2=sprach, 3=offtopic)
    private int[] hotkeyBtnX = new int[4];
    private int[] hotkeyBtnY = new int[4];
    private int listeningSlot = -1; // -1 = hört nicht
    private int characterNameHotkey = -1;
    private int hilfeHotkey = -1;
    private int sprachHotkey = -1;
    private int offtopicHotkey = -1;

    // setting rows nur mit hotkey (Hilfe, Sprach, Offtopic)
    private int hilfeRowY, sprachRowY, offtopicRowY;

    // Chat tab: color picker fürs anpassen
    private static final int COLOR_ROW_H = 22;
    private TextFieldWidget[] colorFields = new TextFieldWidget[6];
    private int colorResetBtnX;
    private int[] colorResetBtnY = new int[6];

    // dynamische color klassen (Farben tab)
    private static final int MAX_DYNAMIC_CLASSES = 8;
    private static final int DYN_COLOR_BASE = 10; // picker idx = DYN_COLOR_BASE + n*2 (+0=display,+1=server)
    private int[] dynDisplaySwatchY = new int[MAX_DYNAMIC_CLASSES];
    private int[] dynServerSwatchY  = new int[MAX_DYNAMIC_CLASSES];
    private int[] dynDeleteBtnY     = new int[MAX_DYNAMIC_CLASSES];
    private int dynAddBtnX, dynAddBtnY, dynAddBtnW;
    private TextFieldWidget dynLabelField;
    private int dynEditLabelIdx = -1;

    // Farben tab: eigene color presets
    private static final int MAX_CUSTOM_PRESETS = 8;
    private TextFieldWidget[] presetNameFields = new TextFieldWidget[MAX_CUSTOM_PRESETS];
    private int[] presetDeleteBtnY = new int[MAX_CUSTOM_PRESETS];
    private int[] presetApplyBtnY  = new int[MAX_CUSTOM_PRESETS];
    private int presetDeleteBtnX, presetApplyBtnX;
    private int presetAddBtnX = -1, presetAddBtnY = -1;
    private int presetSaveCurBtnX = -1, presetSaveCurBtnY = -1, presetSaveCurBtnW = 0;

    // color picker popup (HSV)
    private boolean colorPickerOpen = false;
    private int colorPickerTargetIdx = -1; // 0-4 = haupt color field index
    private float cpHue = 0f, cpSat = 1f, cpVal = 1f;
    private int cpOriginalColor = 0;
    private int cpX, cpY;
    private static final int CP_SV  = 96; // größe vom SV quadrat
    private static final int CP_HH  = 10; // höhe vom hue strip
    private static final int CP_PAD =  8; // inneres padding
    private static final int CP_W   = CP_SV + CP_PAD * 2;
    private static final int CP_H   = CP_SV + 6 + CP_HH + 6 + 14 + 4 + 18 + CP_PAD * 2; // +22 für die button-reihe
    private int cpSvX, cpSvY, cpHueX, cpHueY;
    private int cpConfirmX, cpConfirmY, cpCancelX, cpCancelY, cpPreviewX, cpPreviewY;
    private boolean cpDraggingSV = false, cpDraggingHue = false;
    private TextFieldWidget cpHexField;

    // Spieler-edit popup
    private boolean spielerPopupVisible = false;
    private int spielerPopupEditIdx = -1;
    private static final int SPOP_W = 186, SPOP_H = 108;
    private int spielerPopupX, spielerPopupY;
    private int spielerPopupSaveBtnX, spielerPopupSaveBtnY;
    private int spielerPopupCancelBtnX, spielerPopupCancelBtnY;
    private int spielerPopupSwatchX, spielerPopupSwatchY;

    // name learning kanal-toggles (eine reihe mit 3 inline icon buttons)
    private boolean nameLearningHelp = false;
    private boolean nameLearningOfftopic = false;
    private int nlRowY;
    private int nlRufenBtnX, nlRufenBtnY;
    private int nlHelpBtnX, nlHelpBtnY;
    private int nlOfftopicBtnX, nlOfftopicBtnY;
    // nametag zeilen-visibility toggles
    private boolean showTitleInNametag = true;
    private boolean showRolenameInNametag = true;
    private boolean showAccountnameInNametag = true;
    private int nametagTitleTogX, nametagTitleTogY;
    private int nametagRoleTogX, nametagRoleTogY;
    private int nametagAccTogX, nametagAccTogY;

    // rolename anzeige-toggles pro kanal
    private boolean showNamesVoice = true;
    private boolean showNamesHelp = true;
    private boolean showNamesOfftopic = false;
    private boolean showNamesInTablist = true;
    private int tablistTogX, tablistTogY;
    private boolean showChatHeads = false;
    private int chatHeadsTogX, chatHeadsTogY;
    private int showNamesRowY;
    private int showNamesVoiceTogX, showNamesVoiceTogY;
    private int showNamesHelpTogX, showNamesHelpTogY;
    private int showNamesOfftopicTogX, showNamesOfftopicTogY;
    // Allgemein tab: position vom map overlay toggle
    private int allgemeinMapTogX, allgemeinMapTogY;
    // Weltkarte tab: chunks beim raus-zoomen verstecken
    private boolean hideChunksOnZoomOut;
    private int hideChunksTogX, hideChunksTogY;

    // pro-tab scroll (pixel offset, index passt zu Tab.ordinal())
    private int[] tabScrollPx = new int[TAB_COUNT];
    // verfügbare content höhe (im init gesetzt, fürs scroll clamping)
    private int contentH;

    // deferred tooltip (wird nach disableScissor gezeichnet, sonst wird er geclippt)
    private java.util.List<Text> pendingTooltipLines = null;
    private int pendingTooltipX, pendingTooltipY;
    private void setPendingTooltip(java.util.List<Text> lines, int x, int y) {
        this.pendingTooltipLines = lines;
        this.pendingTooltipX = x;
        this.pendingTooltipY = y;
    }

    // Info tab kram
    private int versionClickCount = 0;
    private long lastVersionClickTime = 0;
    private int versionY;

    // Spieler tab kram
    private int spielerScrollOffset = 0;
    private static final int PLAYER_ROW_H = 30;
    private static final int ACCOUNT_COL_W = 110;
    private static final int LOCK_COL_W = 52;
    private static final int SPIELER_HEADER_H = 38; // header row (18) + search row (20)
    private java.util.List<PlayerNameList.PlayerEntry> cachedPlayerList;
    private java.util.List<PlayerNameList.PlayerEntry> filteredPlayerList;
    private String spielerSearchQuery = "";
    private TextFieldWidget spielerSearchField;
    private int editingPlayerIndex = -1; // index in der cachedPlayerList
    private TextFieldWidget playerEditField;
    private TextFieldWidget playerTitleEditField;
    private int spielerListTop, spielerListBottom;
    private int titleColorSwatchBtnX = -1, titleColorSwatchBtnY = -1;

    // state vom Features tab
    private boolean featuresWorldmap;
    private boolean featuresRolename;
    private boolean featuresAiHelper;
    private int hoveredToggleIdx = -1;
    private int toggleCardsStartX, toggleCardsY, toggleCardGap;
    private static final java.util.Set<String> toggleTexAvail = new java.util.HashSet<>();
    private static final java.util.Set<String> toggleTexChecked = new java.util.HashSet<>();

    // partikel-system
    private final java.util.List<FeatureParticle> particles = new java.util.ArrayList<>();

    private static final long ANIM_DURATION_MS = 250;
    private long openedTime = 0;

    public CharacterInfoScreen() {
        super(Text.literal("EINSTELLUNGEN"));
        this.openedTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();

        OttoTalkConfig config = OttoTalkClient.getConfig();
        this.selectedProvider = config.apiProvider != null ? config.apiProvider : "gemini";
        this.characterNameHotkey = config.characterNameHotkey;
        this.hilfeHotkey = config.hilfeHotkey;
        this.sprachHotkey = config.sprachHotkey;
        this.offtopicHotkey = config.offtopicHotkey;

        // frame-größe auf den verfügbaren screen space clampen (geht mit allen HUD scales)
        int margin = 4;
        int maxFrameW = this.width - SIDEBAR_W - SIDEBAR_GAP - margin * 2;
        int maxFrameH = this.height - TITLE_FRAME_H - TITLE_GAP - margin * 2;
        FRAME_W = Math.min(FRAME_W_IDEAL, Math.max(180, maxFrameW));
        FRAME_H = Math.min(FRAME_H_IDEAL, Math.max(200, maxFrameH));

        int totalW = SIDEBAR_W + SIDEBAR_GAP + FRAME_W;
        int startX = Math.max(margin, (this.width - totalW) / 2);
        int totalH = TITLE_FRAME_H + TITLE_GAP + FRAME_H;
        int startY = Math.max(margin, (this.height - totalH) / 2);

        // titel-frame (über dem haupt-frame, breite passt zum titel-text)
        int titleTextW = this.textRenderer.getWidth(this.title) + 52; // padding
        this.titleFrameW = titleTextW;
        this.titleFrameX = startX + SIDEBAR_W + SIDEBAR_GAP + (FRAME_W - titleTextW) / 2;
        this.titleFrameY = startY;

        // sidebar position (an den haupt-frame ausgerichtet)
        this.sidebarX = startX;
        this.sidebarY = startY + TITLE_FRAME_H + TITLE_GAP;
        this.sidebarInnerX = sidebarX + 12;
        this.sidebarInnerW = SIDEBAR_W - 24;
        // sideTabY[] must mirror the layout produced by renderSidebar so click hit-tests stay in sync.
        int sh = SIDE_HEADER_H + SIDE_HEADER_GAP; // header block height
        int st = SIDE_TAB_H + SIDE_TAB_GAP;        // tab block height
        int sy = sidebarY + 12;
        sy += sh; // "Allgemein" header
        sideTabY[Tab.FEATURES.ordinal()]    = sy;  sy += st;
        sideTabY[Tab.OBERFLAECHE.ordinal()] = sy;  sy += st;
        sy += sh; // "Rollennamen" header
        sideTabY[Tab.ROLLENNAMEN.ordinal()] = sy;  sy += st;
        sideTabY[Tab.SPIELER.ordinal()]     = sy;  sy += st;
        sy += sh; // "Weltkarte" header
        sideTabY[Tab.WELTKARTE.ordinal()]   = sy;  sy += st;
        sy += sh; // "Chat" header
        sideTabY[Tab.CHAT.ordinal()]        = sy;  sy += st;
        sy += sh; // "AI Helper" header
        sideTabY[Tab.KONTEXT.ordinal()]     = sy;  sy += st;
        sideTabY[Tab.LLM.ordinal()]         = sy;
        sideTabY[Tab.INFO.ordinal()]        = sidebarY + FRAME_H - 12 - SIDE_TAB_H;

        // haupt-frame position (rechts von der sidebar)
        this.frameX = startX + SIDEBAR_W + SIDEBAR_GAP;
        this.frameY = sidebarY;
        this.contentX = frameX + INNER_PAD;
        this.contentY = frameY + INNER_PAD;
        this.contentW = FRAME_W - 2 * INNER_PAD;

        this.closeBtnX = frameX + FRAME_W / 2 - ICON_W / 2;
        this.closeBtnY = frameY + FRAME_H - INNER_PAD - ICON_H;

        this.contentH = closeBtnY - contentY - 4;

        tabScrollPx = new int[TAB_COUNT];
        sidebarScrollPx = 0;

        buildFeaturesTab(config);
        buildKontextTab(config);
        buildLlmTab(config);
        buildAllgemeinTab(config);
        buildSpielerTab();
        buildChatColorFields(config);
        updateTabVisibility();
    }

    private void buildFeaturesTab(OttoTalkConfig config) {
        this.featuresWorldmap = config.mapOverlayEnabled;
        this.featuresRolename = config.rolenameFeatureEnabled;
        this.featuresAiHelper = config.aiHelperEnabled;
        this.hideChunksOnZoomOut = config.hideChunksOnZoomOut;
    }

    private void buildAllgemeinTab(OttoTalkConfig config) {
        int y0 = contentY;
        this.animationsEnabled = config.enableAnimations;
        this.showCharacterNames = config.showCharacterNames;
        this.nameLearningHelp = config.nameLearningHelp;
        this.nameLearningOfftopic = config.nameLearningOfftopic;
        this.showNamesVoice = config.showNamesInVoice;
        this.showNamesHelp = config.showNamesInHelp;
        this.showNamesOfftopic = config.showNamesInOfftopic;
        this.showNamesInTablist = config.showNamesInTablist;
        this.showChatHeads = config.showChatHeads;
        this.showTitleInNametag = config.showTitleInNametag;
        this.showRolenameInNametag = config.showRolenameInNametag;
        this.showAccountnameInNametag = config.showAccountnameInNametag;
        int rightEdge = contentX + contentW - 4;
        int iconCenterY;

        // zeile 1: Animationen (nur toggle, kein hotkey)
        this.animRowY = y0 + 4;
        this.animToggleX = rightEdge - ICON_W;
        this.animToggleY = animRowY + (SETTING_ROW_H - ICON_H) / 2;

        // zeile 2: Charakternamen (toggle + hotkey)
        this.charNamesRowY = animRowY + SETTING_ROW_H + SETTING_ROW_GAP;
        iconCenterY = charNamesRowY + (SETTING_ROW_H - ICON_H) / 2;
        this.hotkeyBtnX[0] = rightEdge - ICON_W;
        this.hotkeyBtnY[0] = iconCenterY;
        this.charNamesToggleX = hotkeyBtnX[0] - ICON_W - 2;
        this.charNamesToggleY = iconCenterY;

        // zeile 3: Hilfe Chat (nur hotkey)
        this.hilfeRowY = charNamesRowY + SETTING_ROW_H + SETTING_ROW_GAP;
        iconCenterY = hilfeRowY + (SETTING_ROW_H - ICON_H) / 2;
        this.hotkeyBtnX[1] = rightEdge - ICON_W;
        this.hotkeyBtnY[1] = iconCenterY;

        // zeile 4: Sprach Chat (nur hotkey)
        this.sprachRowY = hilfeRowY + SETTING_ROW_H + SETTING_ROW_GAP;
        iconCenterY = sprachRowY + (SETTING_ROW_H - ICON_H) / 2;
        this.hotkeyBtnX[2] = rightEdge - ICON_W;
        this.hotkeyBtnY[2] = iconCenterY;

        // zeile 5: Offtopic Chat (nur hotkey)
        this.offtopicRowY = sprachRowY + SETTING_ROW_H + SETTING_ROW_GAP;
        iconCenterY = offtopicRowY + (SETTING_ROW_H - ICON_H) / 2;
        this.hotkeyBtnX[3] = rightEdge - ICON_W;
        this.hotkeyBtnY[3] = iconCenterY;

        // zeile 6: Namenlernen
        this.nlRowY = offtopicRowY + SETTING_ROW_H + SETTING_ROW_GAP;
        iconCenterY = nlRowY + (SETTING_ROW_H - ICON_H) / 2;
        this.nlOfftopicBtnX = rightEdge - ICON_W;
        this.nlOfftopicBtnY = iconCenterY;
        this.nlHelpBtnX = nlOfftopicBtnX - ICON_W - 2;
        this.nlHelpBtnY = iconCenterY;
        this.nlRufenBtnX = nlHelpBtnX - ICON_W - 2;
        this.nlRufenBtnY = iconCenterY;

        // row 7: Anzeige in Kanälen (display channel toggles)
        this.showNamesRowY = nlRowY + SETTING_ROW_H + SETTING_ROW_GAP;
        iconCenterY = showNamesRowY + (SETTING_ROW_H - ICON_H) / 2;
        this.showNamesOfftopicTogX = rightEdge - ICON_W;
        this.showNamesOfftopicTogY = iconCenterY;
        this.showNamesHelpTogX = showNamesOfftopicTogX - ICON_W - 2;
        this.showNamesHelpTogY = iconCenterY;
        this.showNamesVoiceTogX = showNamesHelpTogX - ICON_W - 2;
        this.showNamesVoiceTogY = iconCenterY;
    }

    private void buildChatColorFields(OttoTalkConfig config) {
        int[] initColors = {
            config.colorName, config.colorTitleDefault,
            config.colorTitleVorkoster, config.colorTitleAdel,
            config.colorTitleKlerus, config.colorTitleGamemaster
        };
        for (int i = 0; i < 6; i++) {
            final int fi = i;
            TextFieldWidget f = new TextFieldWidget(this.textRenderer, 0, 0, 52, 14, Text.empty());
            f.setMaxLength(6);
            f.setText(String.format("%06X", initColors[i]));
            f.setChangedListener(hex -> applyColorFromField(fi, hex));
            f.visible = false;
            colorFields[i] = f;
            addDrawableChild(f);
        }
        // preset-name felder (eins pro preset slot)
        for (int i = 0; i < MAX_CUSTOM_PRESETS; i++) {
            final int fi = i;
            OttoTalkConfig.CustomColorPreset preset =
                    i < config.customColorPresets.size() ? config.customColorPresets.get(i) : null;
            TextFieldWidget nf = new TextFieldWidget(this.textRenderer, 0, 0, 100, 14, Text.empty());
            nf.setMaxLength(32);
            nf.setText(preset != null ? preset.name : "");
            nf.setChangedListener(name -> applyPresetName(fi, name));
            nf.visible = false;
            presetNameFields[i] = nf;
            addDrawableChild(nf);
        }
        // umbenenn-feld für dynamische class labels (ein wiederverwendbares widget)
        dynLabelField = new TextFieldWidget(this.textRenderer, 0, 0, 80, 14, Text.empty());
        dynLabelField.setMaxLength(24);
        dynLabelField.setChangedListener(name -> {
            if (dynEditLabelIdx >= 0) {
                java.util.List<OttoTalkConfig.DynamicColorClass> dc = OttoTalkClient.getConfig().dynamicColorClasses;
                if (dynEditLabelIdx < dc.size()) { dc.get(dynEditLabelIdx).label = name; autoSave(); }
            }
        });
        dynLabelField.visible = false;
        addDrawableChild(dynLabelField);
        // hex input vom color picker
        cpHexField = new TextFieldWidget(this.textRenderer, 0, 0, 52, 12, Text.empty());
        cpHexField.setMaxLength(6);
        cpHexField.setChangedListener(hex -> {
            if (hex.length() == 6) {
                try {
                    int rgb = Integer.parseInt(hex, 16);
                    float[] hsv = rgbToHsv(rgb);
                    cpHue = hsv[0]; cpSat = hsv[1]; cpVal = hsv[2];
                } catch (NumberFormatException ignored) {}
            }
        });
        cpHexField.visible = false;
        addDrawableChild(cpHexField);
    }

    private void applyColorFromField(int i, String hex) {
        if (hex.length() != 6) return;
        try {
            int color = Integer.parseInt(hex, 16);
            OttoTalkConfig cfg = OttoTalkClient.getConfig();
            if (i >= DYN_COLOR_BASE) {
                int n = i - DYN_COLOR_BASE;
                if (n < cfg.dynamicColorClasses.size())
                    cfg.dynamicColorClasses.get(n).displayColor = color;
            } else {
                switch (i) {
                    case 0: cfg.colorName = color; break;
                    case 1: cfg.colorTitleDefault = color; break;
                    case 2: cfg.colorTitleVorkoster = color; break;
                    case 3: cfg.colorTitleAdel = color; break;
                    case 4: cfg.colorTitleKlerus = color; break;
                    case 5: cfg.colorTitleGamemaster = color; break;
                }
            }
            autoSave();
            CharacterNameResolver.refreshChatMessages(cfg.showCharacterNames);
        } catch (NumberFormatException ignored) {}
    }

    private void applyPresetName(int i, String name) {
        OttoTalkConfig cfg = OttoTalkClient.getConfig();
        if (i < cfg.customColorPresets.size()) {
            cfg.customColorPresets.get(i).name = name;
            autoSave();
        }
    }

    private void deleteCustomPreset(int index) {
        OttoTalkConfig cfg = OttoTalkClient.getConfig();
        if (index < 0 || index >= cfg.customColorPresets.size()) return;
        cfg.customColorPresets.remove(index);
        cfg.save();
        for (int i = 0; i < MAX_CUSTOM_PRESETS; i++) {
            if (i < cfg.customColorPresets.size()) {
                if (presetNameFields[i] != null) presetNameFields[i].setText(cfg.customColorPresets.get(i).name);
            } else {
                if (presetNameFields[i] != null) { presetNameFields[i].setText(""); presetNameFields[i].visible = false; }
            }
        }
    }

    private static int getDefaultColor(int i) {
        switch (i) {
            case 0: return OttoTalkConfig.DEFAULT_COLOR_NAME;
            case 1: return OttoTalkConfig.DEFAULT_COLOR_TITLE_DEFAULT;
            case 2: return OttoTalkConfig.DEFAULT_COLOR_TITLE_VORKOSTER;
            case 3: return OttoTalkConfig.DEFAULT_COLOR_TITLE_ADEL;
            case 4: return OttoTalkConfig.DEFAULT_COLOR_TITLE_KLERUS;
            case 5: return OttoTalkConfig.DEFAULT_COLOR_TITLE_GAMEMASTER;
            default: return 0xFFFFFF;
        }
    }

    private void buildSpielerTab() {
        this.spielerListTop = contentY + SPIELER_HEADER_H;
        this.spielerListBottom = closeBtnY - 6;
        this.cachedPlayerList = PlayerNameList.getAllEntries();
        this.filteredPlayerList = new java.util.ArrayList<>(this.cachedPlayerList);
        this.spielerSearchQuery = "";
        this.spielerScrollOffset = 0;
        if (spielerSearchField == null) {
            spielerSearchField = new TextFieldWidget(this.textRenderer, contentX + 2, contentY + 20, contentW - 4, 12, Text.literal("Suchen"));
            spielerSearchField.setMaxLength(64);
            spielerSearchField.setDrawsBackground(false);
            spielerSearchField.setEditableColor(0x99FFFFFF);
            spielerSearchField.setChangedListener(q -> {
                spielerSearchQuery = q.toLowerCase();
                spielerScrollOffset = 0;
                rebuildFilteredPlayerList();
            });
            addDrawableChild(spielerSearchField);
        } else {
            spielerSearchField.setText("");
        }
        spielerSearchField.visible = (activeTab == Tab.SPIELER);
        this.editingPlayerIndex = -1;
        this.spielerPopupVisible = false;
        this.spielerPopupEditIdx = -1;

        // popup name/title felder (vorher inline, jetzt im floating popup)
        int fW = SPOP_W - 32 - 40; // popup width minus label and padding
        this.playerEditField = new TextFieldWidget(this.textRenderer, 0, 0, fW, 12, Text.literal("CharName"));
        this.playerEditField.setMaxLength(64);
        this.playerEditField.visible = false;
        this.addDrawableChild(this.playerEditField);

        this.playerTitleEditField = new TextFieldWidget(this.textRenderer, 0, 0, fW - 16, 12, Text.literal("Titel"));
        this.playerTitleEditField.setMaxLength(64);
        this.playerTitleEditField.visible = false;
        this.addDrawableChild(this.playerTitleEditField);
    }

    private static final int FIELD_ROW_H = 31;  // server-3-tile native height
    private static final int FIELD_LABEL_H = 11; // label text + gap above input row
    private static final int FIELD_STEP = FIELD_LABEL_H + FIELD_ROW_H + 4; // total per block incl. gap

    private void buildKontextTab(OttoTalkConfig config) {
        int y0 = contentY;
        int fX = contentX + 6;          // inside server-3-tile with padding
        int fW = contentW - 12;         // full width minus padding
        int inputYOff = FIELD_LABEL_H + 11; // label above + centered in 31px row

        this.nameField = new TextFieldWidget(this.textRenderer, fX, y0 + inputYOff, fW, 12, Text.literal("Name"));
        this.nameField.setMaxLength(64);
        this.nameField.setText(config.characterName != null ? config.characterName : "");
        this.nameField.setDrawsBackground(false);
        this.addDrawableChild(this.nameField);

        this.roleField = new TextFieldWidget(this.textRenderer, fX, y0 + FIELD_STEP + inputYOff, fW, 12, Text.literal("Rolle"));
        this.roleField.setMaxLength(64);
        this.roleField.setText(config.characterRole != null ? config.characterRole : "");
        this.roleField.setDrawsBackground(false);
        this.roleField.setChangedListener(t -> OttoTalkClient.getConfig().characterRole = t.trim());
        this.addDrawableChild(this.roleField);

        this.titleField = new TextFieldWidget(this.textRenderer, fX, y0 + FIELD_STEP * 2 + inputYOff, fW, 12, Text.literal("Titel"));
        this.titleField.setMaxLength(64);
        this.titleField.setText(config.characterTitle != null ? config.characterTitle : "");
        this.titleField.setDrawsBackground(false);
        this.titleField.setChangedListener(t -> OttoTalkClient.getConfig().characterTitle = t.trim());
        this.addDrawableChild(this.titleField);

        this.backgroundField = new TextFieldWidget(this.textRenderer, fX, y0 + FIELD_STEP * 3 + inputYOff, fW, 12, Text.literal("Hintergrund"));
        this.backgroundField.setMaxLength(256);
        this.backgroundField.setText(config.characterBackground != null ? config.characterBackground : "");
        this.backgroundField.setDrawsBackground(false);
        this.addDrawableChild(this.backgroundField);

        this.instructionsField = new TextFieldWidget(this.textRenderer, fX, y0 + FIELD_STEP * 4 + inputYOff, fW, 12, Text.literal("Anweisungen"));
        this.instructionsField.setMaxLength(512);
        this.instructionsField.setText(config.additionalInstructions != null ? config.additionalInstructions : "");
        this.instructionsField.setDrawsBackground(false);
        this.addDrawableChild(this.instructionsField);
    }

    private void buildLlmTab(OttoTalkConfig config) {
        int y0 = contentY;
        int fX = contentX + 6;
        int fW = contentW - 12;
        int inputYOff = FIELD_LABEL_H + 11;

        // zeile 1: LLM Anbieter (eigener klickbarer bereich innen im server-3-tile)
        int rowY1 = y0 + FIELD_LABEL_H;
        this.providerBtnX = contentX + 3;
        this.providerBtnY = rowY1 + 7;
        this.providerBtnW = contentW - 6;
        this.providerBtnH = 18;

        // zeile 2: API Key (mit auge-icon)
        this.apiKeyField = new TextFieldWidget(this.textRenderer, fX, y0 + FIELD_STEP + inputYOff, fW - ICON_W - 4, 12, Text.literal("API Key"));
        this.apiKeyField.setMaxLength(256);
        this.apiKeyField.setText(config.apiKey != null ? config.apiKey : "");
        this.apiKeyField.setDrawsBackground(false);
        this.apiKeyField.setRenderTextProvider((text, firstCharIdx) -> {
            if (!apiKeyVisible && text != null && !text.isEmpty()) {
                return Text.literal("\u2022".repeat(Math.min(text.length(), 30))).asOrderedText();
            }
            return Text.literal(text).asOrderedText();
        });
        this.addDrawableChild(this.apiKeyField);

        this.eyeBtnX = contentX + contentW - ICON_W - 4;
        this.eyeBtnY = y0 + FIELD_STEP + FIELD_LABEL_H + 7;

        // zeile 3: API URL
        this.apiUrlField = new TextFieldWidget(this.textRenderer, fX, y0 + FIELD_STEP * 2 + inputYOff, fW, 12, Text.literal("API URL"));
        this.apiUrlField.setMaxLength(512);
        this.apiUrlField.setText(config.apiUrl != null ? config.apiUrl : "");
        this.apiUrlField.setDrawsBackground(false);
        this.addDrawableChild(this.apiUrlField);

        // zeile 4: Modell
        this.modelField = new TextFieldWidget(this.textRenderer, fX, y0 + FIELD_STEP * 3 + inputYOff, fW, 12, Text.literal("Model"));
        this.modelField.setMaxLength(128);
        this.modelField.setText(config.model != null ? config.model : "");
        this.modelField.setDrawsBackground(false);
        this.addDrawableChild(this.modelField);
    }

    private String getProviderLabel() {
        if ("openai".equals(selectedProvider)) return "Anbieter: OpenAI-kompatibel";
        return "Anbieter: Google Gemini";
    }

    private static final String GEMINI_DEFAULT_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final String OPENAI_DEFAULT_URL  = "https://api.openai.com/v1/chat/completions";

    private void cycleProvider() {
        if ("gemini".equals(selectedProvider)) {
            selectedProvider = "openai";
            // URL automatisch umstellen wenn sie noch auf Gemini zeigt
            String cur = apiUrlField.getText().trim();
            if (cur.isEmpty() || cur.contains("generativelanguage.googleapis.com")) {
                apiUrlField.setText(OPENAI_DEFAULT_URL);
            }
            // zurück auf OpenAI: ein übrig gebliebenes gemini-* model wegmachen damits formular noch stimmt
            String mod = modelField.getText().trim();
            if (mod.isEmpty() || mod.startsWith("gemini")) {
                modelField.setText("gpt-4o-mini");
            }
        } else {
            selectedProvider = "gemini";
            // den user zum gemini endpoint rüberholen wenn er die URL nie selbst angepasst hat
            String cur = apiUrlField.getText().trim();
            if (cur.isEmpty() || cur.equals(OPENAI_DEFAULT_URL)) {
                apiUrlField.setText(GEMINI_DEFAULT_URL);
            }
            String mod = modelField.getText().trim();
            if (mod.isEmpty() || mod.startsWith("gpt")) {
                modelField.setText("gemini-2.0-flash");
            }
        }
    }

    private void updateTabVisibility() {
        boolean kontext = (activeTab == Tab.KONTEXT);
        nameField.visible = kontext;
        roleField.visible = kontext;
        titleField.visible = kontext;
        backgroundField.visible = kontext;
        instructionsField.visible = kontext;

        boolean llm = (activeTab == Tab.LLM);
        apiKeyField.visible = llm;
        apiUrlField.visible = llm;
        modelField.visible = llm;

        // Farben widgets sind by default unsichtbar; renderFarbenTab zeigt sie wieder wenn der tab aktiv ist
        for (TextFieldWidget cf : colorFields)      if (cf != null) cf.visible = false;
        for (TextFieldWidget pf : presetNameFields) if (pf != null) pf.visible = false;

        if (colorPickerOpen) {
            closeColorPicker(false); // cancel = revert
        }

        if (activeTab != Tab.SPIELER) {
            if (spielerPopupVisible) cancelSpielerEditPopup();
            if (playerEditField != null) playerEditField.visible = false;
            if (playerTitleEditField != null) playerTitleEditField.visible = false;
            editingPlayerIndex = -1;
        }
        if (spielerSearchField != null) spielerSearchField.visible = (activeTab == Tab.SPIELER);
        if (dynLabelField != null) { dynLabelField.visible = false; dynEditLabelIdx = -1; }
    }

    private void rebuildFilteredPlayerList() {
        if (cachedPlayerList == null) cachedPlayerList = PlayerNameList.getAllEntries();
        if (spielerSearchQuery == null || spielerSearchQuery.isEmpty()) {
            filteredPlayerList = new java.util.ArrayList<>(cachedPlayerList);
        } else {
            filteredPlayerList = new java.util.ArrayList<>();
            for (PlayerNameList.PlayerEntry e : cachedPlayerList) {
                if ((e.accountName != null && e.accountName.toLowerCase().contains(spielerSearchQuery))
                        || (e.characterName != null && e.characterName.toLowerCase().contains(spielerSearchQuery))) {
                    filteredPlayerList.add(e);
                }
            }
        }
    }

    /** Commit the current player edit field value to the player list. */
    private void commitPlayerEdit() {
        if (editingPlayerIndex >= 0 && editingPlayerIndex < cachedPlayerList.size() && playerEditField != null) {
            PlayerNameList.PlayerEntry entry = cachedPlayerList.get(editingPlayerIndex);
            String newName = playerEditField.getText().trim();
            if (!newName.isEmpty()) {
                PlayerNameList.setCharacterName(entry.accountName, newName);
                entry.characterName = newName;
            }
            if (playerTitleEditField != null) {
                String newTitle = playerTitleEditField.getText().trim();
                PlayerNameList.setCharacterTitle(entry.accountName, newTitle);
                entry.characterTitle = newTitle;
            }
        }
        editingPlayerIndex = -1;
    }

    // --- Spieler edit popup ---

    private void openSpielerEditPopup(int idx) {
        if (idx < 0 || idx >= cachedPlayerList.size()) return;
        PlayerNameList.PlayerEntry entry = cachedPlayerList.get(idx);
        if (entry.locked) return;
        spielerPopupEditIdx = idx;
        spielerPopupVisible = true;
        // popup über dem settings frame zentrieren
        spielerPopupX = frameX + (FRAME_W - SPOP_W) / 2;
        spielerPopupY = frameY + (FRAME_H - SPOP_H) / 2;
        int innerX = spielerPopupX + 10;
        int innerW = SPOP_W - 20;
        int labelW = 36;
        int fieldX = innerX + labelW;
        int fieldW = innerW - labelW - 2;
        playerEditField.setText(entry.characterName != null ? entry.characterName : "");
        playerEditField.setX(fieldX);
        playerEditField.setY(spielerPopupY + 30);
        playerEditField.setWidth(fieldW);
        playerEditField.visible = true;
        playerEditField.setFocused(true);
        this.setFocused(playerEditField);
        int swatchW2 = 14;
        playerTitleEditField.setText(entry.characterTitle != null ? entry.characterTitle : "");
        playerTitleEditField.setX(fieldX);
        playerTitleEditField.setY(spielerPopupY + 50);
        playerTitleEditField.setWidth(fieldW - swatchW2 - 2);
        playerTitleEditField.visible = true;
        spielerPopupSwatchX = fieldX + (fieldW - swatchW2 - 2) + 2;
        spielerPopupSwatchY = spielerPopupY + 50;
        int btnRowY = spielerPopupY + SPOP_H - 10 - ICON_H;
        spielerPopupSaveBtnX   = spielerPopupX + SPOP_W / 2 - ICON_W - 6;
        spielerPopupSaveBtnY   = btnRowY;
        spielerPopupCancelBtnX = spielerPopupX + SPOP_W / 2 + 6;
        spielerPopupCancelBtnY = btnRowY;
    }

    private void saveSpielerEditPopup() {
        if (spielerPopupEditIdx >= 0 && spielerPopupEditIdx < cachedPlayerList.size()) {
            PlayerNameList.PlayerEntry entry = cachedPlayerList.get(spielerPopupEditIdx);
            String newName = playerEditField.getText().trim();
            if (!newName.isEmpty()) {
                PlayerNameList.setCharacterName(entry.accountName, newName);
                entry.characterName = newName;
            }
            String newTitle = playerTitleEditField.getText().trim();
            PlayerNameList.setCharacterTitle(entry.accountName, newTitle);
            entry.characterTitle = newTitle;
        }
        cancelSpielerEditPopup();
    }

    private void cancelSpielerEditPopup() {
        spielerPopupVisible = false;
        spielerPopupEditIdx = -1;
        if (playerEditField != null)      playerEditField.visible = false;
        if (playerTitleEditField != null) playerTitleEditField.visible = false;
    }

    /** Step 1: backdrop + panel + field-slot backgrounds. Called BEFORE super.render(). */
    private void renderSpielerEditPopupBg(DrawContext ctx) {
        if (!spielerPopupVisible || spielerPopupEditIdx < 0 || spielerPopupEditIdx >= cachedPlayerList.size()) return;
        // dunkler hintergrund über den ganzen frame
        ctx.fill(frameX, frameY, frameX + FRAME_W, frameY + FRAME_H, 0xAA000000);
        // popup panel
        NineSliceRenderer.drawServerNineSlice(ctx, spielerPopupX, spielerPopupY, SPOP_W, SPOP_H);
        // hintergründe für die field slots (an die TextFieldWidget positionen aus openSpielerEditPopup angepasst)
        int fldX = spielerPopupX + 46;   // matches fieldX in openSpielerEditPopup
        int fldW = SPOP_W - 58;          // matches fieldW (= 186-58 = 128)
        int swatchW = 14;
        NineSliceRenderer.drawServerNineSlice(ctx, fldX, spielerPopupY + 26, fldW, 17);             // Name
        NineSliceRenderer.drawServerNineSlice(ctx, fldX, spielerPopupY + 46, fldW - swatchW - 2, 17); // Titel
    }

    /** Step 2: labels + swatch + buttons. Called AFTER super.render(). */
    private void renderSpielerEditPopupFg(DrawContext ctx, int mouseX, int mouseY) {
        if (!spielerPopupVisible || spielerPopupEditIdx < 0 || spielerPopupEditIdx >= cachedPlayerList.size()) return;
        PlayerNameList.PlayerEntry entry = cachedPlayerList.get(spielerPopupEditIdx);

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Bearbeite: " + entry.accountName),
                spielerPopupX + SPOP_W / 2, spielerPopupY + 10, COLOR_LABEL);

        int innerX = spielerPopupX + 10;
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Name:"),  innerX, spielerPopupY + 32, COLOR_DESC);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Titel:"), innerX, spielerPopupY + 52, COLOR_DESC);

        // Titel color swatch (klick wechselt die gruppe durch)
        int swatchColor = (entry.characterTitleColor > 0) ? (0xFF000000 | entry.characterTitleColor) : 0xFFA27F5F;
        ctx.fill(spielerPopupSwatchX, spielerPopupSwatchY,
                spielerPopupSwatchX + 14, spielerPopupSwatchY + 12, swatchColor);
        ctx.fill(spielerPopupSwatchX, spielerPopupSwatchY, spielerPopupSwatchX + 14, spielerPopupSwatchY + 1, 0x55FFFFFF);
        ctx.fill(spielerPopupSwatchX, spielerPopupSwatchY, spielerPopupSwatchX + 1, spielerPopupSwatchY + 12, 0x55FFFFFF);
        if (mouseX >= spielerPopupSwatchX && mouseX <= spielerPopupSwatchX + 14
                && mouseY >= spielerPopupSwatchY && mouseY <= spielerPopupSwatchY + 12)
            ctx.fill(spielerPopupSwatchX, spielerPopupSwatchY, spielerPopupSwatchX + 14, spielerPopupSwatchY + 12, 0x33FFFFFF);
        // color group label in eigener zeile unter Titel, innerhalb vom popup
        String colorGroupLabel = getTitleColorGroupLabel(entry);
        if (!colorGroupLabel.isEmpty()) {
            int labelColor = entry.characterTitleColor > 0 ? (0xFF000000 | entry.characterTitleColor) : 0xFFA27F5F;
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(colorGroupLabel),
                    spielerPopupX + 46, spielerPopupY + 66, labelColor);
        }

        ctx.drawTexture(TEX_SAVE, spielerPopupSaveBtnX, spielerPopupSaveBtnY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (mouseX >= spielerPopupSaveBtnX && mouseX <= spielerPopupSaveBtnX + ICON_W
                && mouseY >= spielerPopupSaveBtnY && mouseY <= spielerPopupSaveBtnY + ICON_H)
            ctx.fill(spielerPopupSaveBtnX, spielerPopupSaveBtnY,
                    spielerPopupSaveBtnX + ICON_W, spielerPopupSaveBtnY + ICON_H, 0x33FFFFFF);
        ctx.drawTexture(TEX_EXIT, spielerPopupCancelBtnX, spielerPopupCancelBtnY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (mouseX >= spielerPopupCancelBtnX && mouseX <= spielerPopupCancelBtnX + ICON_W
                && mouseY >= spielerPopupCancelBtnY && mouseY <= spielerPopupCancelBtnY + ICON_H)
            ctx.fill(spielerPopupCancelBtnX, spielerPopupCancelBtnY,
                    spielerPopupCancelBtnX + ICON_W, spielerPopupCancelBtnY + ICON_H, 0x33FFFFFF);
    }

    // color picker (HSV)

    private static int hsvToRgb(float h, float s, float v) {
        h = ((h % 1.0f) + 1.0f) % 1.0f;
        int i = (int)(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0: r=v; g=t; b=p; break; case 1: r=q; g=v; b=p; break;
            case 2: r=p; g=v; b=t; break; case 3: r=p; g=q; b=v; break;
            case 4: r=t; g=p; b=v; break; default: r=v; g=p; b=q; break;
        }
        return 0xFF000000 | ((int)(r*255)<<16) | ((int)(g*255)<<8) | (int)(b*255);
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb>>16)&0xFF)/255f, g = ((rgb>>8)&0xFF)/255f, b = (rgb&0xFF)/255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0, s = max > 0 ? d / max : 0, v = max;
        if (d > 0.0001f) {
            if (max == r) h = (g - b) / d / 6f;
            else if (max == g) h = ((b - r) / d + 2) / 6f;
            else h = ((r - g) / d + 4) / 6f;
            if (h < 0) h += 1;
        }
        return new float[]{h, s, v};
    }

    private void openColorPicker(int targetIdx, int nearX, int nearY) {
        colorPickerTargetIdx = targetIdx;
        cpOriginalColor = getCurrentColorForIdx(targetIdx);
        float[] hsv = rgbToHsv(cpOriginalColor);
        cpHue = hsv[0]; cpSat = hsv[1]; cpVal = hsv[2];
        // picker positionieren: immer rechts vom haupt-frame, fallback links
        int px = frameX + FRAME_W + 6;
        if (px + CP_W > this.width - 4) px = frameX - CP_W - 6;
        int py = nearY - CP_PAD;
        if (py + CP_H > this.height - 4) py = this.height - 4 - CP_H;
        if (py < 2) py = 2;
        cpX = px; cpY = py;
        cpSvX = cpX + CP_PAD; cpSvY = cpY + CP_PAD;
        cpHueX = cpSvX; cpHueY = cpSvY + CP_SV + 6;
        cpPreviewX = cpSvX; cpPreviewY = cpHueY + CP_HH + 6;
        cpHexField.setX(cpPreviewX + 22);
        cpHexField.setY(cpPreviewY + 1);
        cpHexField.visible = true;
        cpHexField.setText(String.format("%06X", cpOriginalColor & 0xFFFFFF));
        // buttons in einer neuen reihe unter preview/hex, im CP_W zentriert
        int btnRowY = cpPreviewY + 16;
        cpConfirmX = cpX + CP_W / 2 - ICON_W - 2;
        cpConfirmY = btnRowY;
        cpCancelX  = cpX + CP_W / 2 + 2;
        cpCancelY  = btnRowY;
        colorPickerOpen = true;
    }

    private int getCurrentColorForIdx(int idx) {
        OttoTalkConfig cfg = OttoTalkClient.getConfig();
        if (idx >= DYN_COLOR_BASE) {
            int n = idx - DYN_COLOR_BASE;
            if (n < cfg.dynamicColorClasses.size()) return cfg.dynamicColorClasses.get(n).displayColor;
            return 0x888888;
        }
        switch (idx) {
            case 0: return cfg.colorName;
            case 1: return cfg.colorTitleDefault;
            case 2: return cfg.colorTitleVorkoster;
            case 3: return cfg.colorTitleAdel;
            case 4: return cfg.colorTitleKlerus;
            case 5: return cfg.colorTitleGamemaster;
            default: return 0xFFFFFF;
        }
    }

    private void closeColorPicker(boolean confirm) {
        if (!confirm && colorPickerTargetIdx >= 0) {
            applyColorFromField(colorPickerTargetIdx, String.format("%06X", cpOriginalColor & 0xFFFFFF));
        }
        colorPickerOpen = false;
        colorPickerTargetIdx = -1;
        cpDraggingSV = cpDraggingHue = false;
        if (cpHexField != null) cpHexField.visible = false;
    }

    private void updatePickerFromHsv() {
        int rgb = hsvToRgb(cpHue, cpSat, cpVal) & 0xFFFFFF;
        if (colorPickerTargetIdx >= 0) applyColorFromField(colorPickerTargetIdx, String.format("%06X", rgb));
        if (cpHexField != null && !cpHexField.isFocused())
            cpHexField.setText(String.format("%06X", rgb));
    }

    private void renderColorPicker(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(cpX - 1, cpY - 1, cpX + CP_W + 1, cpY + CP_H + 1, 0xFF222222);
        NineSliceRenderer.drawServerNineSlice(ctx, cpX, cpY, CP_W, CP_H);

        // SV quadrat: spalte für spalte vertikaler gradient (S links->rechts, V oben->unten)
        for (int x = 0; x < CP_SV; x++) {
            float s = (float) x / CP_SV;
            int top = hsvToRgb(cpHue, s, 1.0f);
            ctx.fillGradient(cpSvX + x, cpSvY, cpSvX + x + 1, cpSvY + CP_SV, top, 0xFF000000);
        }
        // SV fadenkreuz
        int curX = cpSvX + (int)(cpSat * (CP_SV - 1));
        int curY = cpSvY + (int)((1 - cpVal) * (CP_SV - 1));
        ctx.fill(curX - 3, curY, curX + 4, curY + 1, 0xFFFFFFFF);
        ctx.fill(curX, curY - 3, curX + 1, curY + 4, 0xFFFFFFFF);
        ctx.fill(curX - 2, curY, curX + 3, curY + 1, 0xFF000000);
        ctx.fill(curX, curY - 2, curX + 1, curY + 3, 0xFF000000);

        for (int x = 0; x < CP_SV; x++) {
            float h = (float) x / CP_SV;
            ctx.fill(cpHueX + x, cpHueY, cpHueX + x + 1, cpHueY + CP_HH, hsvToRgb(h, 1f, 1f));
        }
        int hueMarkerX = cpHueX + (int)(cpHue * (CP_SV - 1));
        ctx.fill(hueMarkerX - 1, cpHueY - 1, hueMarkerX + 2, cpHueY + CP_HH + 1, 0xFFFFFFFF);
        ctx.fill(hueMarkerX,     cpHueY,     hueMarkerX + 1, cpHueY + CP_HH,     0xFF000000);

        int previewColor = hsvToRgb(cpHue, cpSat, cpVal);
        ctx.fill(cpPreviewX, cpPreviewY, cpPreviewX + 20, cpPreviewY + 12, previewColor);
        ctx.fill(cpPreviewX, cpPreviewY, cpPreviewX + 20, cpPreviewY + 1, 0x55FFFFFF);
        ctx.fill(cpPreviewX, cpPreviewY, cpPreviewX + 1, cpPreviewY + 12, 0x55FFFFFF);

        ctx.drawTexture(TEX_SAVE, cpConfirmX, cpConfirmY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (mouseX >= cpConfirmX && mouseX <= cpConfirmX + ICON_W
                && mouseY >= cpConfirmY && mouseY <= cpConfirmY + ICON_H)
            ctx.fill(cpConfirmX, cpConfirmY, cpConfirmX + ICON_W, cpConfirmY + ICON_H, 0x33FFFFFF);
        ctx.drawTexture(TEX_EXIT, cpCancelX, cpCancelY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (mouseX >= cpCancelX && mouseX <= cpCancelX + ICON_W
                && mouseY >= cpCancelY && mouseY <= cpCancelY + ICON_H)
            ctx.fill(cpCancelX, cpCancelY, cpCancelX + ICON_W, cpCancelY + ICON_H, 0x33FFFFFF);
    }

    // === Preset helpers ===

    private void applyPreset(int presetIdx) {
        OttoTalkConfig cfg = OttoTalkClient.getConfig();
        if (presetIdx < 0 || presetIdx >= cfg.customColorPresets.size()) return;
        OttoTalkConfig.CustomColorPreset p = cfg.customColorPresets.get(presetIdx);
        cfg.colorName           = p.colorName;
        cfg.colorTitleDefault   = p.colorTitleDefault;
        cfg.colorTitleVorkoster = p.colorTitleVorkoster;
        cfg.colorTitleAdel      = p.colorTitleAdel;
        cfg.colorTitleKlerus    = p.colorTitleKlerus;
        cfg.colorTitleGamemaster = p.colorTitleGamemaster;
        autoSave();
        CharacterNameResolver.refreshChatMessages(cfg.showCharacterNames);
        // hex felder syncen
        int[] vals = {cfg.colorName, cfg.colorTitleDefault, cfg.colorTitleVorkoster, cfg.colorTitleAdel, cfg.colorTitleKlerus, cfg.colorTitleGamemaster};
        for (int i = 0; i < 6; i++)
            if (colorFields[i] != null) colorFields[i].setText(String.format("%06X", vals[i]));
    }

    private void saveCurrentAsPreset() {
        OttoTalkConfig cfg = OttoTalkClient.getConfig();
        if (cfg.customColorPresets.size() >= MAX_CUSTOM_PRESETS) return;
        int idx = cfg.customColorPresets.size();
        cfg.customColorPresets.add(new OttoTalkConfig.CustomColorPreset("Profil " + (idx + 1), cfg));
        cfg.save();
        if (presetNameFields[idx] != null) presetNameFields[idx].setText("Profil " + (idx + 1));
    }

    /** Cycle the popup player's title color through the 5 Ottonien color groups. */
    private void cycleTitleColorGroup() {
        int idx = spielerPopupEditIdx;
        if (idx < 0 || idx >= cachedPlayerList.size()) return;
        PlayerNameList.PlayerEntry entry = cachedPlayerList.get(idx);
        OttoTalkConfig cfg = OttoTalkClient.getConfig();
        // cycle reihenfolge: fünf feste farb-slots, danach die user-definierten dynamic classes
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        colors.add(cfg.colorName);
        colors.add(cfg.colorTitleDefault);
        colors.add(cfg.colorTitleVorkoster);
        colors.add(cfg.colorTitleAdel);
        colors.add(cfg.colorTitleKlerus);
        colors.add(cfg.colorTitleGamemaster);
        for (OttoTalkConfig.DynamicColorClass dcc : cfg.dynamicColorClasses)
            colors.add(dcc.displayColor);
        int currentGroup = -1;
        for (int i = 0; i < colors.size(); i++) {
            if (entry.characterTitleColor == colors.get(i)) { currentGroup = i; break; }
        }
        int nextGroup = (currentGroup + 1) % colors.size();
        int nextColor = colors.get(nextGroup);
        entry.characterTitleColor = nextColor;
        PlayerNameList.setCharacterTitleColor(entry.accountName, nextColor);
    }

    /** Returns the label for the current title color group of a player entry. */
    private String getTitleColorGroupLabel(PlayerNameList.PlayerEntry entry) {
        if (entry == null) return "";
        OttoTalkConfig cfg = OttoTalkClient.getConfig();
        int c = entry.characterTitleColor;
        if (c == cfg.colorName)           return "Name";
        if (c == cfg.colorTitleDefault)   return "Gew\u00F6hnlich";
        if (c == cfg.colorTitleVorkoster) return "Vorkoster";
        if (c == cfg.colorTitleAdel)      return "Adel";
        if (c == cfg.colorTitleKlerus)    return "Klerus";
        if (c == cfg.colorTitleGamemaster) return "Gamemaster";
        for (OttoTalkConfig.DynamicColorClass dcc : cfg.dynamicColorClasses)
            if (c == dcc.displayColor) return dcc.label;
        return "";
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // no-op: NICHT Minecrafts dunkles overlay oder den dirt background zeichnen
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float progress, eased;
        if (!OttoTalkClient.getConfig().enableAnimations) {
            progress = 1.0f;
            eased = 1.0f;
        } else {
            long elapsed = System.currentTimeMillis() - openedTime;
            progress = Math.min(1.0f, (float) elapsed / ANIM_DURATION_MS);
            eased = 1.0f - (1.0f - progress) * (1.0f - progress);
        }

        // animierte titel-textur (Einstellungen.png, nativ 1024x105)
        // skaliert auf titel-frame breite + padding, im titel-frame zentriert
        int einTexH = 105;
        int einDispW = titleFrameW + 60;
        int einDispH = einDispW * einTexH / 1024;
        int maxDispH = TITLE_FRAME_H - 6;
        if (einDispH > maxDispH) { einDispH = maxDispH; einDispW = einDispH * 1024 / einTexH; }
        int aTW = Math.max(20, (int)(einDispW * eased));
        int aTH = Math.max(4, (int)(einDispH * eased));
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, eased);
        ctx.drawTexture(TEX_EINSTELLUNGEN,
                titleFrameX + (titleFrameW - aTW) / 2,
                titleFrameY + (TITLE_FRAME_H - aTH) / 2 + 3,
                aTW, aTH, 0, 0, 1024, 105, 1024, 105);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // animierte sidebar
        int aSW = Math.max(20, (int)(SIDEBAR_W * eased));
        int aSH = Math.max(20, (int)(FRAME_H * eased));
        NineSliceRenderer.draw(ctx, sidebarX + (SIDEBAR_W - aSW) / 2,
                sidebarY + (FRAME_H - aSH) / 2, aSW, aSH);

        // animierter haupt-frame
        int aFW = Math.max(20, (int)(FRAME_W * eased));
        int aFH = Math.max(20, (int)(FRAME_H * eased));
        NineSliceRenderer.draw(ctx, frameX + (FRAME_W - aFW) / 2,
                frameY + (FRAME_H - aFH) / 2, aFW, aFH);

        if (progress < 1.0f) return;

        // sidebar tabs
        renderSidebar(ctx, mouseX, mouseY);

        // tab content (per scissor geclippt, scrollbar)
        int tabIdx = activeTab.ordinal();
        int scroll = tabScrollPx[tabIdx];
        int y0 = contentY - scroll;
        int clipX = contentX - 2;
        int clipW = contentW + 4;

        // Spieler: fixen header zuerst rendern (außerhalb scissor), dann die liste drunter clippen
        // Spieler nutzt nen eigenen row-basierten scroll (spielerScrollOffset), kein pixel scroll
        if (activeTab == Tab.SPIELER) {
            renderSpielerHeader(ctx, contentY);
            ctx.enableScissor(clipX, contentY + SPIELER_HEADER_H, clipX + clipW, contentY + contentH);
            renderSpielerList(ctx, mouseX, mouseY, contentY + SPIELER_HEADER_H, false);
        } else {
            ctx.enableScissor(clipX, contentY, clipX + clipW, contentY + contentH);
            if (activeTab != Tab.FARBEN) {
                for (TextFieldWidget cf : colorFields)      if (cf != null) cf.visible = false;
                for (TextFieldWidget pf : presetNameFields) if (pf != null) pf.visible = false;
                if (cpHexField != null) cpHexField.visible = false;
            }
            if (activeTab == Tab.FEATURES) renderFeaturesTab(ctx, mouseX, mouseY + scroll, y0);
            else if (activeTab == Tab.WELTKARTE) renderWeltkartTab(ctx, mouseX, mouseY + scroll, y0);
            else if (activeTab == Tab.ROLLENNAMEN) renderRollennamenTab(ctx, mouseX, mouseY + scroll, y0);
            else if (activeTab == Tab.CHAT) renderChatTab(ctx, mouseX, mouseY + scroll, y0);
            else if (activeTab == Tab.FARBEN) renderFarbenTab(ctx, mouseX, mouseY + scroll, y0);
            else if (activeTab == Tab.OBERFLAECHE) renderOberflaecheTab(ctx, mouseX, mouseY + scroll, y0);
            else if (activeTab == Tab.KONTEXT) renderKontextTab(ctx, mouseX, mouseY + scroll, y0);
            else if (activeTab == Tab.LLM) renderLlmTab(ctx, mouseX, mouseY + scroll, y0);
            else if (activeTab == Tab.INFO) renderInfoTab(ctx, mouseX, mouseY + scroll, y0);
        }
        ctx.disableScissor();
        // Spieler scrollbar + stats außerhalb vom scissor zeichnen
        if (activeTab == Tab.SPIELER && cachedPlayerList != null) {
            renderSpielerScrollbarAndStats(ctx);
        }
        renderGifPreview(ctx, mouseX, mouseY);

        // scrollbar (SPIELER hat seine eigene via renderSpielerScrollbarAndStats)
        int totalH = getTabContentHeight(activeTab);
        if (activeTab != Tab.SPIELER && totalH > contentH) {
            int scrollX = frameX + FRAME_W - 8;
            int scrollW = 4;
            int trackH = contentH;
            ctx.fill(scrollX, contentY, scrollX + scrollW, contentY + trackH, 0x33FFFFFF);
            int thumbH = Math.max(12, trackH * contentH / totalH);
            int maxScroll = totalH - contentH;
            int thumbY = contentY + (trackH - thumbH) * scroll / maxScroll;
            ctx.fill(scrollX, thumbY, scrollX + scrollW, thumbY + thumbH, 0xAAFFFFFF);
        }

        boolean closeHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + ICON_W
                && mouseY >= closeBtnY && mouseY <= closeBtnY + ICON_H;
        ctx.drawTexture(TEX_EXIT, closeBtnX, closeBtnY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (closeHovered) ctx.fill(closeBtnX, closeBtnY, closeBtnX + ICON_W, closeBtnY + ICON_H, 0x33FFFFFF);

        // Popup background rendered BEFORE super.render() so TextFieldWidgets draw on top
        if (spielerPopupVisible) renderSpielerEditPopupBg(ctx);

        super.render(ctx, mouseX, mouseY, delta);

        // Color picker overlay (above text fields, below tooltip)
        if (colorPickerOpen) renderColorPicker(ctx, mouseX, mouseY);

        // Popup foreground (labels + buttons) rendered AFTER super.render() so they're on top of fields
        if (spielerPopupVisible) renderSpielerEditPopupFg(ctx, mouseX, mouseY);

        // Deferred tooltip: drawn LAST so it renders above everything (sidebar headers, scrollbar, etc.)
        if (pendingTooltipLines != null) {
            ctx.drawTooltip(this.textRenderer, pendingTooltipLines, pendingTooltipX, pendingTooltipY);
            pendingTooltipLines = null;
        }
    }

    private void renderSidebar(DrawContext ctx, int mouseX, int mouseY) {
        int x = sidebarInnerX;
        int w = sidebarInnerW;
        int xi = x + 2;   // indented x for grouped tabs
        int wi = w - 2;   // indented width

        // Info tab is fixed at the bottom of the sidebar
        int infoY = sidebarY + FRAME_H - 12 - SIDE_TAB_H;
        // Scrollable area: from top padding to just above Info tab
        int scrollTop = sidebarY + 12;
        int scrollBottom = infoY - 4;

        // Clamp sidebar scroll
        int sidebarTotalH = getSidebarContentHeight();
        int visibleH = scrollBottom - scrollTop;
        int maxScroll = Math.max(0, sidebarTotalH - visibleH);
        sidebarScrollPx = Math.max(0, Math.min(sidebarScrollPx, maxScroll));

        ctx.enableScissor(sidebarX, scrollTop, sidebarX + SIDEBAR_W, scrollBottom);
        int sy = scrollTop - sidebarScrollPx;

        renderSideHeader(ctx, x, sy, w, "Allgemein");
        sy += SIDE_HEADER_H + SIDE_HEADER_GAP;
        sideTabY[Tab.FEATURES.ordinal()] = sy;
        renderSideTab(ctx, mouseX, mouseY, xi, sy, wi, Tab.FEATURES, "Allgemein");
        sy += SIDE_TAB_H + SIDE_TAB_GAP;
        sideTabY[Tab.OBERFLAECHE.ordinal()] = sy;
        renderSideTab(ctx, mouseX, mouseY, xi, sy, wi, Tab.OBERFLAECHE, "Oberfl\u00E4che");
        sy += SIDE_TAB_H + SIDE_TAB_GAP;

        renderSideHeader(ctx, x, sy, w, "Rollennamen");
        sy += SIDE_HEADER_H + SIDE_HEADER_GAP;
        sideTabY[Tab.ROLLENNAMEN.ordinal()] = sy;
        renderSideTab(ctx, mouseX, mouseY, xi, sy, wi, Tab.ROLLENNAMEN, "RP-Name");
        sy += SIDE_TAB_H + SIDE_TAB_GAP;
        sideTabY[Tab.SPIELER.ordinal()] = sy;
        renderSideTab(ctx, mouseX, mouseY, xi, sy, wi, Tab.SPIELER, "Spieler");
        sy += SIDE_TAB_H + SIDE_TAB_GAP;

        renderSideHeader(ctx, x, sy, w, "Weltkarte");
        sy += SIDE_HEADER_H + SIDE_HEADER_GAP;
        sideTabY[Tab.WELTKARTE.ordinal()] = sy;
        renderSideTab(ctx, mouseX, mouseY, xi, sy, wi, Tab.WELTKARTE, "Weltkarte");
        sy += SIDE_TAB_H + SIDE_TAB_GAP;

        renderSideHeader(ctx, x, sy, w, "Chat");
        sy += SIDE_HEADER_H + SIDE_HEADER_GAP;
        sideTabY[Tab.CHAT.ordinal()] = sy;
        renderSideTab(ctx, mouseX, mouseY, xi, sy, wi, Tab.CHAT, "Chat");
        sy += SIDE_TAB_H + SIDE_TAB_GAP;
        sideTabY[Tab.FARBEN.ordinal()] = sy;
        renderSideTab(ctx, mouseX, mouseY, xi, sy, wi, Tab.FARBEN, "Farben");
        sy += SIDE_TAB_H + SIDE_TAB_GAP;

        renderSideHeader(ctx, x, sy, w, "AI Helper");
        sy += SIDE_HEADER_H + SIDE_HEADER_GAP;
        sideTabY[Tab.KONTEXT.ordinal()] = sy;
        renderSideTab(ctx, mouseX, mouseY, xi, sy, wi, Tab.KONTEXT, "Kontext");
        sy += SIDE_TAB_H + SIDE_TAB_GAP;
        sideTabY[Tab.LLM.ordinal()] = sy;
        renderSideTab(ctx, mouseX, mouseY, xi, sy, wi, Tab.LLM, "LLM");

        ctx.disableScissor();

        // Info at bottom (fixed, outside scroll)
        sideTabY[Tab.INFO.ordinal()] = infoY;
        renderSideTab(ctx, mouseX, mouseY, x, infoY, w, Tab.INFO, "Info");
    }

    /** Total pixel height of all sidebar tabs+headers (excluding Info). */
    private int getSidebarContentHeight() {
        int sh = SIDE_HEADER_H + SIDE_HEADER_GAP;
        int st = SIDE_TAB_H + SIDE_TAB_GAP;
        // 5 headers + 7 tabs with gap + 1 tab without gap (LLM)
        return 5 * sh + 8 * st + SIDE_TAB_H;
    }

    private boolean isTabEnabled(Tab tab) {
        switch (tab) {
            case ROLLENNAMEN: case SPIELER: return featuresRolename;
            case WELTKARTE: return featuresWorldmap;
            case KONTEXT: case LLM: return featuresAiHelper;
            default: return true;
        }
    }

    private void renderSideTab(DrawContext ctx, int mouseX, int mouseY,
                                int x, int y, int w, Tab tab, String label) {
        boolean tabEnabled = isTabEnabled(tab);
        NineSliceRenderer.drawThreeTile(ctx, x, y, w, SIDE_TAB_H);
        if (!tabEnabled) ctx.fill(x, y, x + w, y + SIDE_TAB_H, 0x88888888);
        if (activeTab == tab)
            ctx.fill(x + 2, y + 2, x + w - 2, y + SIDE_TAB_H - 4, 0x22FFFFFF);
        int maxTW = w - 6;
        int rawTW = this.textRenderer.getWidth(label);
        boolean active = activeTab == tab;
        int activeColor  = tabEnabled ? COLOR_TAB_ACTIVE   : 0xFF777777;
        int inactiveColor = tabEnabled ? COLOR_TAB_INACTIVE : 0xFF555555;
        if (rawTW > maxTW) {
            float ts = (float) maxTW / rawTW;
            int scaledW = (int)(rawTW * ts);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + (w - scaledW) / 2.0, y + 4, 0);
            ctx.getMatrices().scale(ts, ts, 1);
            if (active) ctx.drawTextWithShadow(this.textRenderer, Text.literal(label), 0, 0, activeColor);
            else        ctx.drawText(this.textRenderer, Text.literal(label), 0, 0, inactiveColor, false);
            ctx.getMatrices().pop();
        } else {
            int tX = x + (w - rawTW) / 2;
            if (active) ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label), x + w / 2, y + 4, activeColor);
            else        ctx.drawText(this.textRenderer, Text.literal(label), tX, y + 4, inactiveColor, false);
        }
    }

    private void renderSideHeader(DrawContext ctx, int x, int y, int w, String label) {
        ctx.fill(x, y, x + w, y + SIDE_HEADER_H, 0x88AA8833);
        ctx.fill(x, y + SIDE_HEADER_H - 1, x + w, y + SIDE_HEADER_H, 0xCCDDCC44);
        float scale = 0.85f;
        int textW = (int)(this.textRenderer.getWidth(label) * scale);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + (w - textW) / 2.0, y + 1, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(label), 0, 0, 0xFFFFDD88);
        ctx.getMatrices().pop();
    }

    private void renderWeltkartTab(DrawContext ctx, int mouseX, int mouseY, int y0) {
        int rightEdge = contentX + contentW - 4;
        int curY = y0 + 8;
        this.allgemeinMapTogX = rightEdge - ICON_W;
        this.allgemeinMapTogY = curY + (SETTING_ROW_H - ICON_H) / 2;
        renderToggleRow(ctx, mouseX, mouseY, contentX, curY, contentW, SETTING_ROW_H,
                "Karten-Overlay", "Ottonien-Karte auf der Weltkarte",
                featuresWorldmap, allgemeinMapTogX, allgemeinMapTogY, -1);
        curY += SETTING_ROW_H + SETTING_ROW_GAP;
        this.hideChunksTogX = rightEdge - ICON_W;
        this.hideChunksTogY = curY + (SETTING_ROW_H - ICON_H) / 2;
        renderToggleRow(ctx, mouseX, mouseY, contentX, curY, contentW, SETTING_ROW_H,
                "Chunks ausblenden", "Geladene Chunks beim Rauszoomen verbergen",
                hideChunksOnZoomOut, hideChunksTogX, hideChunksTogY, -1);
    }

    private void renderRollennamenTab(DrawContext ctx, int mouseX, int mouseY, int y0) {
        int rightEdge = contentX + contentW - 4;
        int curY = y0 + 8;
        int iconCenterY;

        // ---- Nametag preview section (TOP) ----
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("Im Nametag anzeigen"), contentX + 2, curY, 0xFFCCBB88);
        curY += 12;

        final int NT_ROW_H   = 22;
        final int NT_ROW_GAP = 3;
        final int NT_PAD     = 8;
        final int SKIN_DISP_H = 64;
        final int SKIN_DISP_W = SKIN_DISP_H * SKIN_TEX_W / SKIN_TEX_H;
        int rowsAreaH  = NT_ROW_H * 3 + NT_ROW_GAP * 2;
        int sectionH   = rowsAreaH + NT_PAD * 2;
        int sectionX   = contentX;
        int sectionW   = contentW;
        int sectionY   = curY;
        NineSliceRenderer.drawServerNineSlice(ctx, sectionX, sectionY, sectionW, sectionH);

        int skinX = sectionX + sectionW - NT_PAD - SKIN_DISP_W;
        int skinY = sectionY + (sectionH - SKIN_DISP_H) / 2;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        ctx.drawTexture(TEX_SKIN, skinX, skinY, SKIN_DISP_W, SKIN_DISP_H,
                0, 0, SKIN_TEX_W, SKIN_TEX_H, SKIN_TEX_W, SKIN_TEX_H);
        if (showAccountnameInNametag)
            ctx.drawTexture(TEX_PREVIEW_ACCOUNT, skinX, skinY, SKIN_DISP_W, SKIN_DISP_H,
                    0, 0, SKIN_TEX_W, SKIN_TEX_H, SKIN_TEX_W, SKIN_TEX_H);
        if (showRolenameInNametag)
            ctx.drawTexture(TEX_PREVIEW_ROLE, skinX, skinY, SKIN_DISP_W, SKIN_DISP_H,
                    0, 0, SKIN_TEX_W, SKIN_TEX_H, SKIN_TEX_W, SKIN_TEX_H);
        if (showTitleInNametag)
            ctx.drawTexture(TEX_PREVIEW_TITLE, skinX, skinY, SKIN_DISP_W, SKIN_DISP_H,
                    0, 0, SKIN_TEX_W, SKIN_TEX_H, SKIN_TEX_W, SKIN_TEX_H);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        int rowW  = sectionW - NT_PAD * 2 - SKIN_DISP_W - 6;
        int row1Y = sectionY + NT_PAD;
        int row2Y = row1Y + NT_ROW_H + NT_ROW_GAP;
        int row3Y = row2Y + NT_ROW_H + NT_ROW_GAP;
        int rowX  = sectionX + NT_PAD;

        this.nametagTitleTogX = rowX + 4;
        this.nametagTitleTogY = row1Y + (NT_ROW_H - ICON_H) / 2;
        renderNametagRow(ctx, mouseX, mouseY, rowX, row1Y, rowW, NT_ROW_H,
                "Titel", showTitleInNametag, nametagTitleTogX, nametagTitleTogY);

        this.nametagRoleTogX = rowX + 4;
        this.nametagRoleTogY = row2Y + (NT_ROW_H - ICON_H) / 2;
        renderNametagRow(ctx, mouseX, mouseY, rowX, row2Y, rowW, NT_ROW_H,
                "Rollenname", showRolenameInNametag, nametagRoleTogX, nametagRoleTogY);

        this.nametagAccTogX = rowX + 4;
        this.nametagAccTogY = row3Y + (NT_ROW_H - ICON_H) / 2;
        renderNametagRow(ctx, mouseX, mouseY, rowX, row3Y, rowW, NT_ROW_H,
                "Accountname", showAccountnameInNametag, nametagAccTogX, nametagAccTogY);

        curY = sectionY + sectionH + SETTING_ROW_GAP;

        // Divider before channel settings
        ctx.fill(contentX, curY, contentX + contentW, curY + 1, 0x44FFFFFF);
        curY += 5;

        // "Charakternamen anzeigen" row
        this.charNamesRowY = curY;
        iconCenterY = curY + (SETTING_ROW_H - ICON_H) / 2;
        this.hotkeyBtnX[0] = rightEdge - ICON_W;
        this.hotkeyBtnY[0] = iconCenterY;
        this.charNamesToggleX = hotkeyBtnX[0] - ICON_W - 2;
        this.charNamesToggleY = iconCenterY;
        renderToggleRow(ctx, mouseX, mouseY, contentX, curY, contentW, SETTING_ROW_H,
                "Charakternamen anzeigen", "Zeigt RP-Namen statt Accountnamen",
                showCharacterNames, charNamesToggleX, charNamesToggleY, 0);
        curY += SETTING_ROW_H + SETTING_ROW_GAP;

        // Tablist row
        iconCenterY = curY + (SETTING_ROW_H - ICON_H) / 2;
        this.tablistTogX = rightEdge - ICON_W;
        this.tablistTogY = iconCenterY;
        renderToggleRow(ctx, mouseX, mouseY, contentX, curY, contentW, SETTING_ROW_H,
                "Tabliste", "RP-Namen in der Tab-Spielerliste",
                showNamesInTablist, tablistTogX, tablistTogY, -1);
        curY += SETTING_ROW_H + SETTING_ROW_GAP;

        // Namenlernen row
        this.nlRowY = curY;
        iconCenterY = curY + (SETTING_ROW_H - ICON_H) / 2;
        this.nlOfftopicBtnX = rightEdge - ICON_W;
        this.nlOfftopicBtnY = iconCenterY;
        this.nlHelpBtnX = nlOfftopicBtnX - ICON_W - 2;
        this.nlHelpBtnY = iconCenterY;
        this.nlRufenBtnX = nlHelpBtnX - ICON_W - 2;
        this.nlRufenBtnY = iconCenterY;
        NineSliceRenderer.drawServerThreeTile(ctx, contentX, curY, contentW, SETTING_ROW_H);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Namenlernen"), contentX + 6, curY + 5, COLOR_LABEL);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Kan\u00E4le f\u00FCr RP-Namenlernen"), contentX + 6, curY + 17, COLOR_DESC);
        ctx.drawTexture(TEX_RUFEN, nlRufenBtnX, nlRufenBtnY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (mouseX >= nlRufenBtnX && mouseX <= nlRufenBtnX + ICON_W
                && mouseY >= nlRufenBtnY && mouseY <= nlRufenBtnY + ICON_H)
            setPendingTooltip(java.util.List.of(
                    Text.literal("Sprach-Kan\u00E4le sind immer aktiv"),
                    Text.literal("[Sprechen] [Fl\u00FCstern] [Rufen]")
            ), mouseX, mouseY);
        Identifier helpTex = nameLearningHelp ? TEX_HELPMODE_ACTIVE : TEX_HELPMODE_DISABLED;
        ctx.drawTexture(helpTex, nlHelpBtnX, nlHelpBtnY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (mouseX >= nlHelpBtnX && mouseX <= nlHelpBtnX + ICON_W
                && mouseY >= nlHelpBtnY && mouseY <= nlHelpBtnY + ICON_H)
            ctx.fill(nlHelpBtnX, nlHelpBtnY, nlHelpBtnX + ICON_W, nlHelpBtnY + ICON_H, 0x33FFFFFF);
        Identifier offtopicTex = nameLearningOfftopic ? TEX_OFFTOPIC_ACTIVE : TEX_OFFTOPIC_DISABLED;
        ctx.drawTexture(offtopicTex, nlOfftopicBtnX, nlOfftopicBtnY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (mouseX >= nlOfftopicBtnX && mouseX <= nlOfftopicBtnX + ICON_W
                && mouseY >= nlOfftopicBtnY && mouseY <= nlOfftopicBtnY + ICON_H)
            ctx.fill(nlOfftopicBtnX, nlOfftopicBtnY, nlOfftopicBtnX + ICON_W, nlOfftopicBtnY + ICON_H, 0x33FFFFFF);
        curY += SETTING_ROW_H + SETTING_ROW_GAP;

        // Anzeige in Kanälen row
        this.showNamesRowY = curY;
        iconCenterY = curY + (SETTING_ROW_H - ICON_H) / 2;
        this.showNamesOfftopicTogX = rightEdge - ICON_W;
        this.showNamesOfftopicTogY = iconCenterY;
        this.showNamesHelpTogX = showNamesOfftopicTogX - ICON_W - 2;
        this.showNamesHelpTogY = iconCenterY;
        this.showNamesVoiceTogX = showNamesHelpTogX - ICON_W - 2;
        this.showNamesVoiceTogY = iconCenterY;
        NineSliceRenderer.drawServerThreeTile(ctx, contentX, curY, contentW, SETTING_ROW_H);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Anzeige in Kan\u00E4len"), contentX + 6, curY + 5, COLOR_LABEL);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Kan\u00E4le mit RP-Namenansicht"), contentX + 6, curY + 17, COLOR_DESC);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(showNamesVoice ? 1f : 0.35f, showNamesVoice ? 1f : 0.35f, showNamesVoice ? 1f : 0.35f, 1f);
        ctx.drawTexture(TEX_RUFEN, showNamesVoiceTogX, showNamesVoiceTogY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (mouseX >= showNamesVoiceTogX && mouseX <= showNamesVoiceTogX + ICON_W
                && mouseY >= showNamesVoiceTogY && mouseY <= showNamesVoiceTogY + ICON_H) {
            ctx.fill(showNamesVoiceTogX, showNamesVoiceTogY, showNamesVoiceTogX + ICON_W, showNamesVoiceTogY + ICON_H, 0x33FFFFFF);
            setPendingTooltip(java.util.List.of(
                    Text.literal("Sprach-Kan\u00E4le"), Text.literal("[Sprechen] [Fl\u00FCstern] [Rufen]")), mouseX, mouseY);
        }
        Identifier showHelpTex = showNamesHelp ? TEX_HELPMODE_ACTIVE : TEX_HELPMODE_DISABLED;
        ctx.drawTexture(showHelpTex, showNamesHelpTogX, showNamesHelpTogY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (mouseX >= showNamesHelpTogX && mouseX <= showNamesHelpTogX + ICON_W
                && mouseY >= showNamesHelpTogY && mouseY <= showNamesHelpTogY + ICON_H)
            ctx.fill(showNamesHelpTogX, showNamesHelpTogY, showNamesHelpTogX + ICON_W, showNamesHelpTogY + ICON_H, 0x33FFFFFF);
        Identifier showOfftopicTex = showNamesOfftopic ? TEX_OFFTOPIC_ACTIVE : TEX_OFFTOPIC_DISABLED;
        ctx.drawTexture(showOfftopicTex, showNamesOfftopicTogX, showNamesOfftopicTogY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (mouseX >= showNamesOfftopicTogX && mouseX <= showNamesOfftopicTogX + ICON_W
                && mouseY >= showNamesOfftopicTogY && mouseY <= showNamesOfftopicTogY + ICON_H)
            ctx.fill(showNamesOfftopicTogX, showNamesOfftopicTogY, showNamesOfftopicTogX + ICON_W, showNamesOfftopicTogY + ICON_H, 0x33FFFFFF);

        curY += SETTING_ROW_H + SETTING_ROW_GAP;


    }

    /** Draws a single dark-transparent nametag preview row with toggle on the left. */
    private void renderNametagRow(DrawContext ctx, int mouseX, int mouseY,
                                   int x, int y, int w, int h,
                                   String label, boolean enabled,
                                   int togX, int togY) {
        ctx.fill(x, y, x + w, y + h, enabled ? 0xBB0A0A14 : 0x88080808);
        ctx.drawBorder(x, y, w, h, enabled ? 0x55AAAADD : 0x33555566);

        Identifier toggleTex = enabled ? TEX_ON : TEX_OFF;
        ctx.drawTexture(toggleTex, togX, togY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (mouseX >= togX && mouseX <= togX + ICON_W && mouseY >= togY && mouseY <= togY + ICON_H)
            ctx.fill(togX, togY, togX + ICON_W, togY + ICON_H, 0x33FFFFFF);

        int textX = togX + ICON_W + 5;
        int textY = y + (h - 7) / 2;
        int textColor = enabled ? COLOR_LABEL : 0xFF777766;
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(label), textX, textY, textColor);
    }

    private void renderChatTab(DrawContext ctx, int mouseX, int mouseY, int y0) {
        int rightEdge = contentX + contentW - 4;
        int curY = y0 + 8;
        int iconCenterY;

        this.hilfeRowY = curY;
        iconCenterY = curY + (SETTING_ROW_H - ICON_H) / 2;
        this.hotkeyBtnX[1] = rightEdge - ICON_W;
        this.hotkeyBtnY[1] = iconCenterY;
        renderHotkeyOnlyRow(ctx, mouseX, mouseY, contentX, curY, contentW, SETTING_ROW_H,
                "Hilfe Chat", "Hotkey f\u00FCr Hilfe-Kanal", 1);
        curY += SETTING_ROW_H + SETTING_ROW_GAP;

        this.sprachRowY = curY;
        iconCenterY = curY + (SETTING_ROW_H - ICON_H) / 2;
        this.hotkeyBtnX[2] = rightEdge - ICON_W;
        this.hotkeyBtnY[2] = iconCenterY;
        renderHotkeyOnlyRow(ctx, mouseX, mouseY, contentX, curY, contentW, SETTING_ROW_H,
                "Sprach Chat", "Hotkey f\u00FCr Sprach-Kanal", 2);
        curY += SETTING_ROW_H + SETTING_ROW_GAP;

        this.offtopicRowY = curY;
        iconCenterY = curY + (SETTING_ROW_H - ICON_H) / 2;
        this.hotkeyBtnX[3] = rightEdge - ICON_W;
        this.hotkeyBtnY[3] = iconCenterY;
        renderHotkeyOnlyRow(ctx, mouseX, mouseY, contentX, curY, contentW, SETTING_ROW_H,
                "Offtopic Chat", "Hotkey f\u00FCr Offtopic-Kanal", 3);
        curY += SETTING_ROW_H + SETTING_ROW_GAP;

        // Chat Köpfe toggle
        iconCenterY = curY + (SETTING_ROW_H - ICON_H) / 2;
        this.chatHeadsTogX = rightEdge - ICON_W;
        this.chatHeadsTogY = iconCenterY;
        renderToggleRow(ctx, mouseX, mouseY, contentX, curY, contentW, SETTING_ROW_H,
                "Chat K\u00f6pfe", "Spieler-Kopf vor Chat-Nachrichten",
                showChatHeads, chatHeadsTogX, chatHeadsTogY, -1);
        curY += SETTING_ROW_H + SETTING_ROW_GAP;

    }

    private int getActivePresetIndex() {
        OttoTalkConfig cfg = OttoTalkClient.getConfig();
        for (int i = 0; i < cfg.customColorPresets.size(); i++) {
            OttoTalkConfig.CustomColorPreset p = cfg.customColorPresets.get(i);
            if (p.colorName           == cfg.colorName
             && p.colorTitleDefault   == cfg.colorTitleDefault
             && p.colorTitleVorkoster == cfg.colorTitleVorkoster
             && p.colorTitleAdel      == cfg.colorTitleAdel
             && p.colorTitleKlerus      == cfg.colorTitleKlerus
             && p.colorTitleGamemaster  == cfg.colorTitleGamemaster) return i;
        }
        return -1;
    }

    private void renderFarbenTab(DrawContext ctx, int mouseX, int mouseY, int y0) {
        int curY = y0 + 8;
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("Anzeigefarben"), contentX + 2, curY, 0xFFCCBB88);
        curY += 14;

        OttoTalkConfig colorCfg = OttoTalkClient.getConfig();
        int[] colorValues = {
            colorCfg.colorName, colorCfg.colorTitleDefault,
            colorCfg.colorTitleVorkoster, colorCfg.colorTitleAdel,
            colorCfg.colorTitleKlerus, colorCfg.colorTitleGamemaster
        };
        String[] colorLabels = {"Name", "Gew\u00F6hnlich", "Vorkoster", "Adel", "Klerus", "Gamemaster"};
        colorResetBtnX = contentX + contentW - ICON_W - 4;
        int fieldX = colorResetBtnX - 58;

        for (int i = 0; i < 6; i++) {
            int col = colorValues[i];
            int def = getDefaultColor(i);
            colorResetBtnY[i] = curY + (COLOR_ROW_H - ICON_H) / 2;
            colorFields[i].setX(fieldX);
            colorFields[i].setY(curY + (COLOR_ROW_H - 14) / 2);
            colorFields[i].visible = true;
            if (!colorFields[i].isFocused()) {
                String expectedHex = String.format("%06X", col);
                if (!colorFields[i].getText().equalsIgnoreCase(expectedHex))
                    colorFields[i].setText(expectedHex);
            }
            NineSliceRenderer.drawServerNineSlice(ctx, contentX, curY, contentW, COLOR_ROW_H);
            // Clickable color swatch (opens color picker)
            int swatchY = curY + (COLOR_ROW_H - 12) / 2;
            boolean swatchHovered = mouseX >= contentX + 6 && mouseX < contentX + 18
                    && mouseY >= swatchY && mouseY < swatchY + 12;
            ctx.fill(contentX + 6, swatchY, contentX + 18, swatchY + 12, 0xFF000000 | col);
            ctx.fill(contentX + 6, swatchY, contentX + 18, swatchY + 1, 0x55FFFFFF);
            ctx.fill(contentX + 6, swatchY, contentX + 7, swatchY + 12, 0x55FFFFFF);
            if (swatchHovered) ctx.fill(contentX + 6, swatchY, contentX + 18, swatchY + 12, 0x33FFFFFF);
            // Active picker indicator
            if (colorPickerOpen && colorPickerTargetIdx == i)
                ctx.fill(contentX + 4, swatchY - 1, contentX + 20, swatchY + 13, 0x66FFFFFF);
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(colorLabels[i]),
                    contentX + 22, curY + (COLOR_ROW_H - 9) / 2, COLOR_LABEL);
            boolean isDefault = (col == def);
            Identifier resetTex = isDefault ? TEX_OFF : TEX_ON;
            ctx.drawTexture(resetTex, colorResetBtnX, colorResetBtnY[i], ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
            if (!isDefault
                    && mouseX >= colorResetBtnX && mouseX <= colorResetBtnX + ICON_W
                    && mouseY >= colorResetBtnY[i] && mouseY <= colorResetBtnY[i] + ICON_H)
                ctx.fill(colorResetBtnX, colorResetBtnY[i],
                        colorResetBtnX + ICON_W, colorResetBtnY[i] + ICON_H, 0x33FFFFFF);
            curY += COLOR_ROW_H + 3;
        }

        curY += 6;
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("Farbprofile"), contentX + 2, curY + 1, 0xFFCCBB88);

        OttoTalkConfig pCfg = OttoTalkClient.getConfig();
        boolean canSave = pCfg.customColorPresets.size() < MAX_CUSTOM_PRESETS;
        int saveH = 13;
        presetSaveCurBtnW = textRenderer.getWidth("+ Neues Set") + 10;
        presetSaveCurBtnX = contentX + contentW - 4 - presetSaveCurBtnW;
        presetSaveCurBtnY = curY;
        if (canSave) {
            NineSliceRenderer.drawServerNineSlice(ctx, presetSaveCurBtnX, presetSaveCurBtnY, presetSaveCurBtnW, saveH);
            boolean hovSave = mouseX >= presetSaveCurBtnX && mouseX <= presetSaveCurBtnX + presetSaveCurBtnW
                    && mouseY >= presetSaveCurBtnY && mouseY <= presetSaveCurBtnY + saveH;
            if (hovSave) ctx.fill(presetSaveCurBtnX, presetSaveCurBtnY,
                    presetSaveCurBtnX + presetSaveCurBtnW, presetSaveCurBtnY + saveH, 0x33FFFFFF);
            ctx.drawText(textRenderer, "+ Neues Set",
                    presetSaveCurBtnX + (presetSaveCurBtnW - textRenderer.getWidth("+ Neues Set")) / 2,
                    presetSaveCurBtnY + (saveH - 7) / 2, hovSave ? 0xFFFFFF88 : 0xFFEECC66, false);
        } else {
            // Dimmed when full
            NineSliceRenderer.drawServerNineSlice(ctx, presetSaveCurBtnX, presetSaveCurBtnY, presetSaveCurBtnW, saveH);
            ctx.fill(presetSaveCurBtnX, presetSaveCurBtnY,
                    presetSaveCurBtnX + presetSaveCurBtnW, presetSaveCurBtnY + saveH, 0x88000000);
            ctx.drawText(textRenderer, "+ Neues Set",
                    presetSaveCurBtnX + (presetSaveCurBtnW - textRenderer.getWidth("+ Neues Set")) / 2,
                    presetSaveCurBtnY + (saveH - 7) / 2, 0xFF666655, false);
        }
        curY += 16;

        int n = pCfg.customColorPresets.size();
        presetDeleteBtnX = contentX + contentW - ICON_W - 4;
        presetApplyBtnX  = presetDeleteBtnX - ICON_W - 4;
        // Preset name field: from contentX+6, width = presetApplyBtnX - contentX - 6 - 4 - 5*9
        int swatchBlockW = 6 * 9; // 6 swatches x (7px + 2px gap)
        int presetNameX  = contentX + 6;
        int presetNameW  = presetApplyBtnX - presetNameX - 4 - swatchBlockW - 4;

        int activePresetIdx = getActivePresetIndex();
        for (int i = 0; i < n && i < MAX_CUSTOM_PRESETS; i++) {
            OttoTalkConfig.CustomColorPreset preset = pCfg.customColorPresets.get(i);
            NineSliceRenderer.drawServerNineSlice(ctx, contentX, curY, contentW, COLOR_ROW_H);
            // Active preset: strong golden highlight
            if (i == activePresetIdx) {
                ctx.fill(contentX + 1, curY + 1, contentX + contentW - 1, curY + COLOR_ROW_H - 1, 0x22FFCC44);
                ctx.drawBorder(contentX, curY, contentW, COLOR_ROW_H, 0xCCFFCC22);
            }

            // Name field (editable)
            presetNameFields[i].setX(presetNameX);
            presetNameFields[i].setY(curY + (COLOR_ROW_H - 14) / 2);
            presetNameFields[i].setWidth(presetNameW);
            presetNameFields[i].visible = true;
            if (!presetNameFields[i].isFocused() && !presetNameFields[i].getText().equals(preset.name))
                presetNameFields[i].setText(preset.name);

            // 6 mini color swatches
            int[] pColors = {preset.colorName, preset.colorTitleDefault, preset.colorTitleVorkoster,
                              preset.colorTitleAdel, preset.colorTitleKlerus, preset.colorTitleGamemaster};
            int sx = presetNameX + presetNameW + 4;
            int sy = curY + (COLOR_ROW_H - 7) / 2;
            for (int s = 0; s < 6; s++) {
                ctx.fill(sx, sy, sx + 7, sy + 7, 0xFF000000 | pColors[s]);
                ctx.fill(sx, sy, sx + 7, sy + 1, 0x44FFFFFF);
                sx += 9;
            }

            presetApplyBtnY[i] = curY + (COLOR_ROW_H - ICON_H) / 2;
            ctx.drawTexture(TEX_SAVE, presetApplyBtnX, presetApplyBtnY[i], ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
            if (mouseX >= presetApplyBtnX && mouseX <= presetApplyBtnX + ICON_W
                    && mouseY >= presetApplyBtnY[i] && mouseY <= presetApplyBtnY[i] + ICON_H)
                ctx.fill(presetApplyBtnX, presetApplyBtnY[i], presetApplyBtnX + ICON_W, presetApplyBtnY[i] + ICON_H, 0x33FFFFFF);

            presetDeleteBtnY[i] = curY + (COLOR_ROW_H - ICON_H) / 2;
            ctx.drawTexture(TEX_EXIT, presetDeleteBtnX, presetDeleteBtnY[i], ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
            if (mouseX >= presetDeleteBtnX && mouseX <= presetDeleteBtnX + ICON_W
                    && mouseY >= presetDeleteBtnY[i] && mouseY <= presetDeleteBtnY[i] + ICON_H)
                ctx.fill(presetDeleteBtnX, presetDeleteBtnY[i],
                        presetDeleteBtnX + ICON_W, presetDeleteBtnY[i] + ICON_H, 0x44FF4444);

            curY += COLOR_ROW_H + 3;
        }
        for (int i = n; i < MAX_CUSTOM_PRESETS; i++)
            if (presetNameFields[i] != null) presetNameFields[i].visible = false;
        if (n == 0)
            ctx.drawText(textRenderer, "(keine Farbprofile)", contentX + 4, curY + 4, 0xFF888877, false);

        curY += (n == 0 ? 18 : 6);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("Zus\u00E4tzliche Klassen"), contentX + 2, curY + 1, 0xFFCCBB88);
        boolean canAddDyn = OttoTalkClient.getConfig().dynamicColorClasses.size() < MAX_DYNAMIC_CLASSES;
        dynAddBtnW = textRenderer.getWidth("+ Neue Klasse") + 10;
        dynAddBtnX = contentX + contentW - 4 - dynAddBtnW;
        dynAddBtnY = curY;
        NineSliceRenderer.drawServerNineSlice(ctx, dynAddBtnX, dynAddBtnY, dynAddBtnW, 13);
        if (!canAddDyn) ctx.fill(dynAddBtnX, dynAddBtnY, dynAddBtnX + dynAddBtnW, dynAddBtnY + 13, 0x88000000);
        boolean hovDyn = canAddDyn && mouseX >= dynAddBtnX && mouseX <= dynAddBtnX + dynAddBtnW
                && mouseY >= dynAddBtnY && mouseY <= dynAddBtnY + 13;
        if (hovDyn) ctx.fill(dynAddBtnX, dynAddBtnY, dynAddBtnX + dynAddBtnW, dynAddBtnY + 13, 0x33FFFFFF);
        ctx.drawText(textRenderer, "+ Neue Klasse",
                dynAddBtnX + (dynAddBtnW - textRenderer.getWidth("+ Neue Klasse")) / 2,
                dynAddBtnY + 3, canAddDyn ? (hovDyn ? 0xFFFFFF88 : 0xFFEECC66) : 0xFF666655, false);
        curY += 16;

        java.util.List<OttoTalkConfig.DynamicColorClass> dynList = OttoTalkClient.getConfig().dynamicColorClasses;
        int delBtnX = contentX + contentW - ICON_W - 4;
        for (int d = 0; d < dynList.size() && d < MAX_DYNAMIC_CLASSES; d++) {
            OttoTalkConfig.DynamicColorClass dc = dynList.get(d);
            NineSliceRenderer.drawServerNineSlice(ctx, contentX, curY, contentW, COLOR_ROW_H);
            int swy = curY + (COLOR_ROW_H - 12) / 2;
            // Color swatch
            dynDisplaySwatchY[d] = swy;
            ctx.fill(contentX + 6, swy, contentX + 18, swy + 12, 0xFF000000 | (dc.displayColor & 0xFFFFFF));
            ctx.fill(contentX + 6, swy, contentX + 18, swy + 1, 0x55FFFFFF);
            ctx.fill(contentX + 6, swy, contentX + 7,  swy + 12, 0x55FFFFFF);
            if (colorPickerOpen && colorPickerTargetIdx == DYN_COLOR_BASE + d)
                ctx.fill(contentX + 4, swy - 1, contentX + 20, swy + 13, 0x66FFFFFF);
            if (mouseX >= contentX + 6 && mouseX < contentX + 18 && mouseY >= swy && mouseY < swy + 12)
                ctx.fill(contentX + 6, swy, contentX + 18, swy + 12, 0x33FFFFFF);
            // Label (or inline rename field)
            if (dynEditLabelIdx == d && dynLabelField != null) {
                dynLabelField.setX(contentX + 22);
                dynLabelField.setY(curY + (COLOR_ROW_H - 14) / 2);
                dynLabelField.setWidth(delBtnX - contentX - 22 - 4);
                dynLabelField.visible = true;
                if (!dynLabelField.isFocused()) dynLabelField.setText(dc.label);
            } else {
                boolean hovLabel = mouseX >= contentX + 22 && mouseX < delBtnX - 4
                        && mouseY >= curY && mouseY < curY + COLOR_ROW_H;
                if (hovLabel) ctx.fill(contentX + 20, curY + 2, delBtnX - 2, curY + COLOR_ROW_H - 2, 0x22FFFFFF);
                ctx.drawTextWithShadow(textRenderer, Text.literal(dc.label),
                        contentX + 22, curY + (COLOR_ROW_H - 9) / 2, 0xFF000000 | (dc.displayColor & 0xFFFFFF));
            }
            // Delete button
            dynDeleteBtnY[d] = curY + (COLOR_ROW_H - ICON_H) / 2;
            ctx.drawTexture(TEX_EXIT, delBtnX, dynDeleteBtnY[d], ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
            if (mouseX >= delBtnX && mouseX <= delBtnX + ICON_W
                    && mouseY >= dynDeleteBtnY[d] && mouseY <= dynDeleteBtnY[d] + ICON_H)
                ctx.fill(delBtnX, dynDeleteBtnY[d], delBtnX + ICON_W, dynDeleteBtnY[d] + ICON_H, 0x44FF4444);
            curY += COLOR_ROW_H + 3;
        }
        if (dynEditLabelIdx < 0 || dynEditLabelIdx >= dynList.size())
            if (dynLabelField != null) dynLabelField.visible = false;
        if (dynList.isEmpty())
            ctx.drawText(textRenderer, "(keine zus\u00E4tzlichen Klassen)", contentX + 4, curY + 4, 0xFF888877, false);
    }

    private void renderOberflaecheTab(DrawContext ctx, int mouseX, int mouseY, int y0) {
        int rightEdge = contentX + contentW - 4;
        int curY = y0 + 8;
        this.animRowY = curY;
        this.animToggleX = rightEdge - ICON_W;
        this.animToggleY = curY + (SETTING_ROW_H - ICON_H) / 2;
        renderToggleRow(ctx, mouseX, mouseY, contentX, curY, contentW, SETTING_ROW_H,
                "Animationen", "Aktiviert Fenster-Animationen",
                animationsEnabled, animToggleX, animToggleY, -1);
    }

    private void renderFeaturesTab(DrawContext ctx, int mouseX, int mouseY, int y0) {
        int centerX = frameX + FRAME_W / 2;

        // Vertical centering: compute total content height and offset y0
        int ottoW = 58, ottoH = 14; // Ottoplus at ~1.5x: 39*1.49~58, 9*1.56~14
        int totalContentH = 30 + TOGGLE_CARD_H + 8 + 36;
        int vOffset = Math.max(0, (contentH - totalContentH) / 2);
        int startY = y0 + vOffset;

        // Header row: "Willkommen bei" (1.1x darker) + Ottoplus texture inline
        String welcomeText = "Willkommen bei";
        float wbScale = 1.1f;
        int scaledWbW = (int)(textRenderer.getWidth(welcomeText) * wbScale);
        int headerGap = 5;
        int pairW = scaledWbW + headerGap + ottoW;
        int pairStartX = centerX - pairW / 2;
        int headerY = startY + 4;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(pairStartX, headerY + (ottoH - (int)(8 * wbScale)) / 2.0, 0);
        ctx.getMatrices().scale(wbScale, wbScale, 1);
        ctx.drawText(textRenderer, Text.literal(welcomeText), 0, 0, 0xFFEEDDBB, false);
        ctx.getMatrices().pop();
        ctx.drawTexture(TEX_OTTO_HEADER, pairStartX + scaledWbW + headerGap, headerY,
                ottoW, ottoH, 0, 0, 39, 9, 39, 9);

        int numCards = 4;
        int CARD_GAP = 5;
        int totalCardsW = numCards * TOGGLE_CARD_W + (numCards - 1) * CARD_GAP;
        int cardsStartX = contentX + (contentW - totalCardsW) / 2;
        int cardsY = startY + 30;
        this.toggleCardsStartX = cardsStartX;
        this.toggleCardsY = cardsY;
        this.toggleCardGap = CARD_GAP;

        String[] cardLabels = {"Weltkarte", "Rollennamen", "KI-Helfer", "???"};
        boolean[] cardStates = {featuresWorldmap, featuresRolename, featuresAiHelper, false};
        Identifier[] cardTextures = {TEX_TOGGLE_WORLDMAP, TEX_TOGGLE_ROLENAME, TEX_TOGGLE_AIHELPER, TEX_TOGGLE_EMPTY};

        hoveredToggleIdx = -1;
        for (int i = 0; i < numCards; i++) {
            int cx = cardsStartX + i * (TOGGLE_CARD_W + CARD_GAP);
            boolean active = cardStates[i];
            boolean hovered = mouseX >= cx && mouseX <= cx + TOGGLE_CARD_W
                    && mouseY >= cardsY && mouseY <= cardsY + TOGGLE_CARD_H;
            if (hovered) hoveredToggleIdx = i;

            NineSliceRenderer.drawServerNineSlice(ctx, cx, cardsY, TOGGLE_CARD_W, TOGGLE_CARD_H);
            if (!active) ctx.fill(cx + 2, cardsY + 2, cx + TOGGLE_CARD_W - 2, cardsY + TOGGLE_CARD_H - 2, 0xAA000000);
            if (hovered) {
                ctx.fill(cx + 2, cardsY + 2, cx + TOGGLE_CARD_W - 2, cardsY + TOGGLE_CARD_H - 2, 0x22FFFFFF);
                ctx.drawBorder(cx, cardsY, TOGGLE_CARD_W, TOGGLE_CARD_H, 0x99FFD700);
            }

            int iconX = cx + (TOGGLE_CARD_W - TOGGLE_ICON_SIZE) / 2;
            int iconY = cardsY + 4;
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

            if (i == 3) {
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.25f, 0.25f, 0.25f, 0.7f);
                ctx.drawTexture(cardTextures[i], iconX, iconY, TOGGLE_ICON_SIZE, TOGGLE_ICON_SIZE,
                        0, 0, 20, 18, 20, 18);
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("?"),
                        cx + TOGGLE_CARD_W / 2, iconY + TOGGLE_ICON_SIZE / 2 - 4, 0xFF777788);
            } else {
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(
                        active ? 1f : 0.30f, active ? 1f : 0.30f, active ? 1f : 0.30f, active ? 1f : 0.85f);
                if (isToggleTextureAvailable(cardTextures[i])) {
                    ctx.drawTexture(cardTextures[i], iconX, iconY, TOGGLE_ICON_SIZE, TOGGLE_ICON_SIZE,
                            0, 0, 20, 18, 20, 18);
                } else {
                    com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    ctx.fill(iconX, iconY, iconX + TOGGLE_ICON_SIZE, iconY + TOGGLE_ICON_SIZE,
                            active ? 0xFF1E3A5F : 0xFF111122);
                    ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(cardLabels[i]),
                            cx + TOGGLE_CARD_W / 2, iconY + TOGGLE_ICON_SIZE / 2 - 4,
                            active ? 0xFFAABBFF : 0xFF444455);
                }
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }

            // Label: scale down to fit inside card width
            int labelColor = i == 3 ? 0xFF555566 : (active ? COLOR_LABEL : 0xFF666666);
            int labelY = cardsY + TOGGLE_CARD_H - 12;
            int rawLW = textRenderer.getWidth(cardLabels[i]);
            int maxLW = TOGGLE_CARD_W - 4;
            if (rawLW > maxLW) {
                float ls = (float) maxLW / rawLW;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(cx + TOGGLE_CARD_W / 2.0, labelY, 0);
                ctx.getMatrices().scale(ls, ls, 1);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(cardLabels[i]), 0, 0, labelColor);
                ctx.getMatrices().pop();
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(cardLabels[i]),
                        cx + TOGGLE_CARD_W / 2, labelY, labelColor);
            }
        }

        // Disclaimer box
        int disclaimerY = cardsY + TOGGLE_CARD_H + 8;
        int boxW = contentW - 12;
        int boxX = contentX + 6;
        int boxH = 36;
        NineSliceRenderer.drawServerNineSlice(ctx, boxX, disclaimerY, boxW, boxH);
        ctx.fill(boxX, disclaimerY, boxX + boxW, disclaimerY + 1, 0xCC997755);
        ctx.fill(boxX, disclaimerY + boxH - 1, boxX + boxW, disclaimerY + boxH, 0xCC997755);
        ctx.fill(boxX, disclaimerY, boxX + 1, disclaimerY + boxH, 0xCC997755);
        ctx.fill(boxX + boxW - 1, disclaimerY, boxX + boxW, disclaimerY + boxH, 0xCC997755);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("\u26A0  Inoffizielles Fanprojekt"),
                boxX + boxW / 2, disclaimerY + 5, 0xFFCCBB88);
        float dscale = 0.75f;
        String dLine = "Alle Rechte verbleiben bei den jeweiligen Eigent\u00FCmern.";
        float dlW = textRenderer.getWidth(dLine) * dscale;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(boxX + boxW / 2.0 - dlW / 2.0, disclaimerY + 19, 0);
        ctx.getMatrices().scale(dscale, dscale, 1);
        ctx.drawText(textRenderer, Text.literal(dLine), 0, 0, 0xFFAA9977, true);
        ctx.getMatrices().pop();

        renderParticles(ctx);
    }

    private void renderGifPreview(DrawContext ctx, int mouseX, int mouseY) {
        if (activeTab != Tab.FEATURES || hoveredToggleIdx < 0) return;

        String gifPath;
        String[] infoLines;
        switch (hoveredToggleIdx) {
            case 0:
                gifPath = "textures/gui/worldmapPreview.jpeg";
                infoLines = new String[]{
                        "Integriert die interaktive Karte",
                        "von Ottonien.com in Xaeros Worldmap.",
                        "\u00A77(Erfordert: Xaeros Worldmap)"
                };
                break;
            case 1:
                gifPath = null; // no preview image for rolename yet
                infoLines = new String[]{
                        "Zeigt Rollenamen statt oder",
                        "zus\u00E4tzlich dem Accountnamen."
                };
                break;
            case 2:
                gifPath = "textures/gui/aihelperPreview.gif";
                infoLines = new String[]{
                        "Der AI Helper hilft Rechtschreibung",
                        "zu korrigieren, Nachrichten zu",
                        "verfassen oder zu verfeinern.",
                        "\u00A77Keine Automatisierung."
                };
                break;
            case 3:
                // "Soon..." - just a simple tooltip, no GIF
                int tipW = 80;
                int tipX3 = frameX + FRAME_W + 6;
                if (tipX3 + tipW > this.width - 4) tipX3 = frameX - tipW - 6;
                int tipY3 = mouseY - 15;
                tipY3 = Math.max(4, Math.min(tipY3, this.height - 34 - 4));
                NineSliceRenderer.drawServerNineSlice(ctx, tipX3 - 5, tipY3 - 5, tipW + 10, 24 + 10);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Soon..."), tipX3 + tipW / 2, tipY3 + 7, 0xFF9B7653);
                return;
            default: return;
        }

        Identifier frame = null;
        int srcW = 0, srcH = 0;
        if (gifPath != null) {
            boolean isGif = gifPath.endsWith(".gif");
            GifFramePlayer.GifAnimation anim = isGif
                    ? GifFramePlayer.get(gifPath)
                    : GifFramePlayer.getStatic(gifPath);
            if (anim != null) {
                frame = anim.getCurrentFrame();
                srcW = anim.frameW;
                srcH = anim.frameH;
            }
        }

        float tScale = 0.75f;
        int prevW = 130;
        int gifH = 0;
        if (frame != null && srcW > 0 && srcH > 0) {
            gifH = prevW * srcH / srcW;
            gifH = Math.max(40, Math.min(gifH, 90));
        }

        // Measure scaled text width to ensure nothing overflows
        int scaledLineH = (int)(9 * tScale) + 1;
        int maxTextW = 0;
        for (String line : infoLines) {
            maxTextW = Math.max(maxTextW, (int)(textRenderer.getWidth(line) * tScale));
        }
        prevW = Math.max(prevW, maxTextW + 12);

        int textPadV = 4;
        int totalTextH = (int)(infoLines.length * scaledLineH) + textPadV + 2;
        int totalH = (gifH > 0 ? gifH + 4 : 0) + totalTextH;

        int prevX = frameX + FRAME_W + 6;
        if (prevX + prevW > this.width - 4) prevX = frameX - prevW - 6;
        int prevY = mouseY - totalH / 2;
        prevY = Math.max(4, Math.min(prevY, this.height - totalH - 4));

        // 9-slice background, tiled rather than stretched so the texture stays sharp at any width.
        NineSliceRenderer.drawServerNineSlice(ctx, prevX - 5, prevY - 5, prevW + 10, totalH + 10);

        if (frame != null && gifH > 0) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            ctx.drawTexture(frame, prevX, prevY, prevW, gifH,
                    0, 0, srcW, srcH, srcW, srcH);
        }

        // Info text at 75% scale
        int textY = prevY + (gifH > 0 ? gifH + 4 : 0) + textPadV;
        var matrices = ctx.getMatrices();
        for (String line : infoLines) {
            matrices.push();
            matrices.scale(tScale, tScale, 1f);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    net.minecraft.text.Text.literal(line),
                    (int)((prevX + prevW / 2) / tScale), (int)(textY / tScale), 0xFFDDCCAA);
            matrices.pop();
            textY += scaledLineH;
        }
    }

    private void renderParticles(DrawContext ctx) {
        particles.removeIf(p -> p.life <= 0f);
        for (FeatureParticle p : particles) {
            p.x += p.vx;
            p.vy += 0.09f;
            p.y += p.vy;
            p.life -= 0.020f;
            int alpha = Math.max(0, (int)(p.life * 220));
            ctx.fill((int)p.x - p.size, (int)p.y - p.size,
                     (int)p.x + p.size, (int)p.y + p.size,
                     (alpha << 24) | (p.color & 0xFFFFFF));
        }
    }

    private void spawnParticles(float cx, float cy) {
        java.util.Random rng = new java.util.Random();
        int[] palette = {0xFFFFD700, 0xFFFF8800, 0xFFFFFF44, 0xFFFF6600, 0xFFFFCC00, 0xFFFFEE55};
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * rng.nextDouble();
            float speed = 0.5f + rng.nextFloat() * 2.2f;
            particles.add(new FeatureParticle(
                    cx, cy,
                    (float)(Math.cos(angle) * speed),
                    (float)(Math.sin(angle) * speed) - 1.8f,
                    palette[rng.nextInt(palette.length)],
                    1 + rng.nextInt(2)));
        }
    }

    private boolean isToggleTextureAvailable(Identifier id) {
        String key = id.toString();
        if (!toggleTexChecked.contains(key)) {
            toggleTexChecked.add(key);
            if (MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent()) {
                toggleTexAvail.add(key);
            }
        }
        return toggleTexAvail.contains(key);
    }

    /** Get the hotkey value for a given slot index. */
    private int getHotkeyForSlot(int slot) {
        switch (slot) {
            case 0: return characterNameHotkey;
            case 1: return hilfeHotkey;
            case 2: return sprachHotkey;
            case 3: return offtopicHotkey;
            default: return -1;
        }
    }

    /** Set the hotkey value for a given slot index. */
    private void setHotkeyForSlot(int slot, int keyCode) {
        switch (slot) {
            case 0: characterNameHotkey = keyCode; break;
            case 1: hilfeHotkey = keyCode; break;
            case 2: sprachHotkey = keyCode; break;
            case 3: offtopicHotkey = keyCode; break;
        }
    }

    /** Render a hotkey button for the given slot at (hkX, hkY). */
    private void renderHotkeyButton(DrawContext ctx, int mouseX, int mouseY, int slot) {
        int hkX = hotkeyBtnX[slot];
        int hkY = hotkeyBtnY[slot];
        ctx.drawTexture(TEX_EMPTY, hkX, hkY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (listeningSlot == slot) {
            ctx.drawTexture(TEX_CURRENTMODE, hkX, hkY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        }
        String hotkeyText = "...";
        int hotkey = getHotkeyForSlot(slot);
        if (listeningSlot != slot && hotkey > 0) {
            String keyName = GLFW.glfwGetKeyName(hotkey, 0);
            hotkeyText = keyName != null ? keyName.toUpperCase() : "?";
        }
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hotkeyText),
                hkX + ICON_W / 2, hkY + 5, COLOR_TAB_ACTIVE);
        boolean hovered = mouseX >= hkX && mouseX <= hkX + ICON_W
                && mouseY >= hkY && mouseY <= hkY + ICON_H;
        if (hovered) ctx.fill(hkX, hkY, hkX + ICON_W, hkY + ICON_H, 0x33FFFFFF);
    }

    private void renderSectionHeader(DrawContext ctx, int x, int y, int w, String label) {
        ctx.fill(x, y, x + w, y + SECTION_HEADER_H, 0x44997744);
        ctx.fill(x, y + SECTION_HEADER_H - 1, x + w, y + SECTION_HEADER_H, 0xAA997744);
        ctx.drawTextWithShadow(textRenderer, Text.literal("\u25B6 " + label), x + 4, y + 2, 0xFFCCBB66);
    }

    /**
     * Render a setting row with on/off toggle and optional hotkey button.
     * hotkeySlot = -1 means no hotkey button.
     */
    private void renderToggleRow(DrawContext ctx, int mouseX, int mouseY,
                                  int x, int y, int w, int h,
                                  String name, String desc,
                                  boolean enabled, int toggleX, int toggleY, int hotkeySlot) {
        NineSliceRenderer.drawServerThreeTile(ctx, x, y, w, h);
        if (!enabled) ctx.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x88000000);

        int textColor = enabled ? COLOR_LABEL : 0xFF888888;
        int descColor = enabled ? COLOR_DESC : 0xFF666666;
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(name), x + 6, y + 5, textColor);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(desc), x + 6, y + 17, descColor);

        Identifier toggleTex = enabled ? TEX_ON : TEX_OFF;
        ctx.drawTexture(toggleTex, toggleX, toggleY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        boolean toggleHovered = mouseX >= toggleX && mouseX <= toggleX + ICON_W
                && mouseY >= toggleY && mouseY <= toggleY + ICON_H;
        if (toggleHovered) ctx.fill(toggleX, toggleY, toggleX + ICON_W, toggleY + ICON_H, 0x33FFFFFF);

        if (hotkeySlot >= 0) renderHotkeyButton(ctx, mouseX, mouseY, hotkeySlot);
    }

    /**
     * Render a hotkey-only setting row (no toggle, just the name/description and hotkey button).
     */
    private void renderHotkeyOnlyRow(DrawContext ctx, int mouseX, int mouseY,
                                      int x, int y, int w, int h,
                                      String name, String desc, int hotkeySlot) {
        NineSliceRenderer.drawServerThreeTile(ctx, x, y, w, h);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(name), x + 6, y + 5, COLOR_LABEL);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(desc), x + 6, y + 17, COLOR_DESC);
        renderHotkeyButton(ctx, mouseX, mouseY, hotkeySlot);
    }

    /** Draw a field block: label above, server-3-tile with 3-tile input inside. */
    private void renderFieldRow(DrawContext ctx, int y, String label) {
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(label), contentX + 2, y, COLOR_LABEL);
        int rowY = y + FIELD_LABEL_H;
        NineSliceRenderer.drawServerThreeTile(ctx, contentX, rowY, contentW, FIELD_ROW_H);
        // 3-tile-frame input inside, centered vertically: (31-18)/2 = 7
        NineSliceRenderer.drawThreeTile(ctx, contentX + 3, rowY + 7, contentW - 6, 18);
    }

    private void renderKontextTab(DrawContext ctx, int mouseX, int mouseY, int y0) {
        renderFieldRow(ctx, y0, "Charaktername:");
        renderFieldRow(ctx, y0 + FIELD_STEP, "Rolle / Stand:");
        renderFieldRow(ctx, y0 + FIELD_STEP * 2, "Titel (optional):");
        renderFieldRow(ctx, y0 + FIELD_STEP * 3, "Hintergrund:");
        renderFieldRow(ctx, y0 + FIELD_STEP * 4, "Anweisungen:");
        // Reposition text fields to follow scroll
        int inputYOff = FIELD_LABEL_H + 11;
        nameField.setY(y0 + inputYOff);
        roleField.setY(y0 + FIELD_STEP + inputYOff);
        titleField.setY(y0 + FIELD_STEP * 2 + inputYOff);
        backgroundField.setY(y0 + FIELD_STEP * 3 + inputYOff);
        instructionsField.setY(y0 + FIELD_STEP * 4 + inputYOff);

        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("z.B. Sprachfehler, Dialekt, Eigenarten"),
                contentX + 2, y0 + FIELD_STEP * 4 + FIELD_LABEL_H + FIELD_ROW_H + 2, COLOR_DESC);
    }

    private void renderLlmTab(DrawContext ctx, int mouseX, int mouseY, int y0) {
        // Reposition provider button and text fields to follow scroll
        int rowY1 = y0 + FIELD_LABEL_H;
        this.providerBtnX = contentX + 3;
        this.providerBtnY = rowY1 + 7;
        this.providerBtnW = contentW - 6;
        this.providerBtnH = 18;
        int inputYOff = FIELD_LABEL_H + 11;
        apiKeyField.setY(y0 + FIELD_STEP + inputYOff);
        this.eyeBtnY = y0 + FIELD_STEP + FIELD_LABEL_H + 7;
        apiUrlField.setY(y0 + FIELD_STEP * 2 + inputYOff);
        modelField.setY(y0 + FIELD_STEP * 3 + inputYOff);

        // Row 1: LLM Anbieter (custom styled clickable area)
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("LLM Anbieter:"), contentX + 2, y0, COLOR_LABEL);
        NineSliceRenderer.drawServerThreeTile(ctx, contentX, y0 + FIELD_LABEL_H, contentW, FIELD_ROW_H);
        NineSliceRenderer.drawThreeTile(ctx, providerBtnX, providerBtnY, providerBtnW, providerBtnH);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(getProviderLabel()),
                providerBtnX + providerBtnW / 2, providerBtnY + 5, COLOR_LABEL);
        boolean providerHovered = mouseX >= providerBtnX && mouseX <= providerBtnX + providerBtnW
                && mouseY >= providerBtnY && mouseY <= providerBtnY + providerBtnH;
        if (providerHovered) ctx.fill(providerBtnX, providerBtnY, providerBtnX + providerBtnW, providerBtnY + providerBtnH, 0x22FFFFFF);

        // zeile 2: API Key (mit auge-icon)
        renderFieldRow(ctx, y0 + FIELD_STEP, "API Key:");
        boolean eyeHovered = mouseX >= eyeBtnX && mouseX <= eyeBtnX + ICON_W
                && mouseY >= eyeBtnY && mouseY <= eyeBtnY + ICON_H;
        ctx.drawTexture(TEX_EYE, eyeBtnX, eyeBtnY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
        if (eyeHovered) ctx.fill(eyeBtnX, eyeBtnY, eyeBtnX + ICON_W, eyeBtnY + ICON_H, 0x33FFFFFF);
        if (!apiKeyVisible) ctx.fill(eyeBtnX + 2, eyeBtnY + ICON_H / 2,
                eyeBtnX + ICON_W - 2, eyeBtnY + ICON_H / 2 + 1, 0xFF7B5B3A);

        // zeile 3: API URL
        renderFieldRow(ctx, y0 + FIELD_STEP * 2, "API URL:");

        // zeile 4: Modell
        renderFieldRow(ctx, y0 + FIELD_STEP * 3, "Modell:");
    }

    private void renderInfoTab(DrawContext ctx, int mouseX, int mouseY, int y0) {
        int centerX = frameX + FRAME_W / 2;

        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("OttoPlus Mod"),
                centerX, y0 + 10, COLOR_LABEL);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Enhancement for Ottonien.com"),
                centerX, y0 + 24, COLOR_DESC);

        // Version in 3-tile-frame
        String versionStr = "OttoPlus v" + VERSION;
        int vTextW = this.textRenderer.getWidth(versionStr) + 16;
        int vX = centerX - vTextW / 2;
        this.versionY = y0 + 46;
        NineSliceRenderer.drawThreeTile(ctx, vX, versionY, vTextW, 14);
        int vColor = RoleplayStateManager.isDebugMode() ? 0xFF44FF44 : COLOR_VERSION;
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(versionStr),
                centerX, versionY + 3, vColor);

        // ---- Feedback / Contact section (single block) ----
        int feedbackY = versionY + 28;
        int feedbackW = contentW - 16;
        int feedbackX = centerX - feedbackW / 2;
        int feedbackH = 78;

        // Server-3-tile as proper 9-slice (tiles in both directions, no stretching)
        NineSliceRenderer.drawServerNineSlice(ctx, feedbackX, feedbackY, feedbackW, feedbackH);

        // Section title
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("\u00AB Kontakt \u00BB"),
                centerX, feedbackY + 8, COLOR_TITLE);

        // Feedback items with colored symbols
        Text feedbackLine = Text.literal("")
                .append(Text.literal("\u26A0 ").styled(s -> s.withColor(net.minecraft.text.TextColor.fromRgb(0xFF6655))))
                .append(Text.literal("Bug  ").styled(s -> s.withColor(net.minecraft.text.TextColor.fromRgb(COLOR_LABEL & 0xFFFFFF))))
                .append(Text.literal("\u2605 ").styled(s -> s.withColor(net.minecraft.text.TextColor.fromRgb(0x55FF55))))
                .append(Text.literal("Feature  ").styled(s -> s.withColor(net.minecraft.text.TextColor.fromRgb(COLOR_LABEL & 0xFFFFFF))))
                .append(Text.literal("\u270E ").styled(s -> s.withColor(net.minecraft.text.TextColor.fromRgb(0x55AAFF))))
                .append(Text.literal("Feedback").styled(s -> s.withColor(net.minecraft.text.TextColor.fromRgb(COLOR_LABEL & 0xFFFFFF))));
        int feedbackLineW = this.textRenderer.getWidth(feedbackLine);
        ctx.drawTextWithShadow(this.textRenderer, feedbackLine,
                centerX - feedbackLineW / 2, feedbackY + 24, 0xFFFFFFFF);

        // Discord contact in highlighted 3-tile frame
        String contactStr = "RUMPL3R";
        String discordLabel = "(Discord)";
        Text contactText = Text.literal("")
                .append(Text.literal(contactStr).styled(s -> s.withColor(net.minecraft.text.TextColor.fromRgb(COLOR_TITLE & 0xFFFFFF))))
                .append(Text.literal(" "))
                .append(Text.literal(discordLabel).styled(s -> s.withColor(net.minecraft.text.TextColor.fromRgb(0x55AAFF))));
        int contactTextW = this.textRenderer.getWidth(contactText);
        int contactTotalW = contactTextW + 20;
        int contactX = centerX - contactTotalW / 2;
        int contactY = feedbackY + 40;
        NineSliceRenderer.drawThreeTile(ctx, contactX, contactY, contactTotalW, 16);
        ctx.drawTextWithShadow(this.textRenderer, contactText,
                centerX - contactTextW / 2, contactY + 4, 0xFFFFFFFF);

        // Decorative separator
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("- \u00B7 -"),
                centerX, feedbackY + 62, 0xFF555555);
    }

    private void renderSpielerHeader(DrawContext ctx, int headerY) {
        int charColX = contentX + ACCOUNT_COL_W;
        int lockColX = contentX + contentW - LOCK_COL_W;
        int headerH = 18;
        NineSliceRenderer.drawThreeTile(ctx, contentX, headerY, ACCOUNT_COL_W - 2, headerH);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Account"),
                contentX + (ACCOUNT_COL_W - 2) / 2, headerY + 5, COLOR_DESC);
        NineSliceRenderer.drawThreeTile(ctx, charColX, headerY, lockColX - charColX - 2, headerH);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Charakter"),
                charColX + (lockColX - charColX - 2) / 2, headerY + 5, COLOR_DESC);
        NineSliceRenderer.drawThreeTile(ctx, lockColX, headerY, LOCK_COL_W, headerH);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Locked"),
                lockColX + LOCK_COL_W / 2, headerY + 5, COLOR_DESC);
        int searchY = headerY + headerH + 2;
        ctx.fill(contentX, searchY, contentX + contentW, searchY + 16, 0x22FFFFFF);
        if (spielerSearchQuery.isEmpty()) {
            ctx.drawText(this.textRenderer, "\uD83D\uDD0D Suchen...", contentX + 4, searchY + 4, 0x44FFFFFF, false);
        }
        if (spielerSearchField != null) {
            spielerSearchField.setX(contentX + 4);
            spielerSearchField.setY(searchY + 2);
            spielerSearchField.setWidth(contentW - 8);
            spielerSearchField.visible = true;
        }
    }

    private void renderSpielerScrollbarAndStats(DrawContext ctx) {
        java.util.List<PlayerNameList.PlayerEntry> dispList = filteredPlayerList != null ? filteredPlayerList : cachedPlayerList;
        int listAreaH = spielerListBottom - spielerListTop;
        int maxVisible = listAreaH / PLAYER_ROW_H;
        int maxScroll = Math.max(0, dispList.size() - maxVisible);
        if (dispList.size() > maxVisible && maxScroll > 0) {
            int scrollX = frameX + FRAME_W - 8;
            int scrollW = 4;
            ctx.fill(scrollX, spielerListTop, scrollX + scrollW, spielerListBottom, 0x33FFFFFF);
            int thumbH = Math.max(12, listAreaH * maxVisible / Math.max(1, dispList.size()));
            int thumbY = spielerListTop + (listAreaH - thumbH) * spielerScrollOffset / maxScroll;
            ctx.fill(scrollX, thumbY, scrollX + scrollW, thumbY + thumbH, 0xAAFFFFFF);
        }
        // Stats: entry count + known %
        int total = dispList.size();
        int known = 0;
        for (PlayerNameList.PlayerEntry e : dispList) {
            if (e.characterName != null && !"Unbekannt".equals(e.characterName) && !e.characterName.isEmpty()) known++;
        }
        int pct = total > 0 ? (known * 100 / total) : 0;
        String countStr = total + " Eintr\u00E4ge";
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(countStr),
                contentX, spielerListBottom + 3, COLOR_DESC);
        String knownStr = known + "/" + total + " (" + pct + "% bekannt)";
        int knownW = this.textRenderer.getWidth(knownStr);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(knownStr),
                contentX + contentW - knownW, spielerListBottom + 3, COLOR_DESC);
    }

    private void renderSpielerList(DrawContext ctx, int mouseX, int mouseY, int listStartY, boolean drawScrollbar) {
        if (spielerPopupVisible) return; // popup is on top, skip list to prevent text bleed
        if (cachedPlayerList == null) { cachedPlayerList = PlayerNameList.getAllEntries(); rebuildFilteredPlayerList(); }
        java.util.List<PlayerNameList.PlayerEntry> dispList = filteredPlayerList != null ? filteredPlayerList : cachedPlayerList;

        int charColX = contentX + ACCOUNT_COL_W;
        int lockColX = contentX + contentW - LOCK_COL_W;

        int listAreaH = spielerListBottom - spielerListTop;
        int maxVisible = listAreaH / PLAYER_ROW_H;

        int maxScroll = Math.max(0, dispList.size() - maxVisible);
        if (spielerScrollOffset > maxScroll) spielerScrollOffset = maxScroll;
        if (spielerScrollOffset < 0) spielerScrollOffset = 0;

        for (int i = 0; i < maxVisible && (i + spielerScrollOffset) < dispList.size(); i++) {
            int idx = i + spielerScrollOffset;
            PlayerNameList.PlayerEntry entry = dispList.get(idx);
            int rowY = listStartY + i * PLAYER_ROW_H;

            float rowOpacity = (i % 2 == 0) ? 0.5f : 0.75f;
            boolean isEditingThis = (spielerPopupEditIdx == idx);
            if (entry.locked) {
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.4f, 0.4f, 0.4f, rowOpacity);
            } else {
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, rowOpacity);
            }
            NineSliceRenderer.drawServerThreeTile(ctx, contentX, rowY, contentW, PLAYER_ROW_H);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            if (isEditingThis) ctx.fill(contentX, rowY, contentX + contentW, rowY + PLAYER_ROW_H, 0x22FFFFFF);

            ctx.drawTextWithShadow(this.textRenderer, Text.literal(entry.accountName),
                    contentX + 6, rowY + 10, COLOR_LABEL);

            // Character name
            boolean unknown = "Unbekannt".equals(entry.characterName);
            int nameColor = unknown ? 0xFFFF6666 : COLOR_LABEL;
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(entry.characterName),
                    charColX + 4, rowY + 4, nameColor);
            // Title
            boolean titleUnknown = entry.characterTitle == null || entry.characterTitle.isEmpty();
            int storedTitleCol = (entry.characterTitleColor > 0) ? entry.characterTitleColor : 0xA27F5F;
            int titleDisplayColor = titleUnknown ? 0xFFFF6666 : (0xFF000000 | storedTitleCol);
            String titleDisplayText = titleUnknown ? "Unbekannt" : entry.characterTitle;
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(titleDisplayText),
                    charColX + 4, rowY + 17, titleDisplayColor);

            // Hover hint: pencil icon in middle column
            if (!entry.locked && mouseX >= charColX && mouseX < lockColX
                    && mouseY >= rowY && mouseY < rowY + PLAYER_ROW_H && !spielerPopupVisible)
                ctx.fill(charColX, rowY, lockColX, rowY + PLAYER_ROW_H, 0x18FFFFFF);

            // Lock toggle
            int toggleX = lockColX + (LOCK_COL_W - ICON_W) / 2;
            int toggleY = rowY + (PLAYER_ROW_H - ICON_H) / 2;
            Identifier lockTex = entry.locked ? TEX_ON : TEX_OFF;
            ctx.drawTexture(lockTex, toggleX, toggleY, ICON_W, ICON_H, 0, 0, 20, 18, 20, 18);
            if (mouseX >= toggleX && mouseX <= toggleX + ICON_W
                    && mouseY >= toggleY && mouseY <= toggleY + ICON_H)
                ctx.fill(toggleX, toggleY, toggleX + ICON_W, toggleY + ICON_H, 0x33FFFFFF);
        }

        int lockColX2 = contentX + contentW - LOCK_COL_W;
        if (mouseX >= lockColX2 && mouseX <= lockColX2 + LOCK_COL_W
                && mouseY >= contentY && mouseY <= contentY + 18) {
            setPendingTooltip(java.util.List.of(
                    Text.literal("Deaktiviere dass f\u00FCr diesen Spieler"),
                    Text.literal("\u00C4nderungen im Namen bei"),
                    Text.literal("Begegnungen aktualisiert werden.")
            ), mouseX, mouseY);
        }
    }

    /** Draw a plain text label (no frame background). */
    private void drawPlainLabel(DrawContext ctx, String text, int x, int y) {
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(text), x, y, COLOR_LABEL);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + ICON_W
                && mouseY >= closeBtnY && mouseY <= closeBtnY + ICON_H) {
            closeScreen();
            return true;
        }
        // Sidebar tab clicks (check visible scroll bounds for non-Info tabs)
        int sideScrollTop = sidebarY + 12;
        int sideScrollBottom = sidebarY + FRAME_H - 12 - SIDE_TAB_H - 4;
        for (int i = 0; i < TAB_COUNT; i++) {
            Tab clickedTab = Tab.values()[i];
            if (!isTabEnabled(clickedTab)) continue;
            int tabTop = sideTabY[i];
            int tabBot = tabTop + SIDE_TAB_H;
            // Skip tabs scrolled outside visible area (Info is always visible)
            if (clickedTab != Tab.INFO && (tabBot < sideScrollTop || tabTop > sideScrollBottom)) continue;
            if (mouseX >= sidebarInnerX && mouseX <= sidebarInnerX + sidebarInnerW
                    && mouseY >= tabTop && mouseY <= tabBot) {
                autoSave();
                activeTab = clickedTab;
                updateTabVisibility();
                return true;
            }
        }
        // For scrollable tab content: translate mouseY into content-space
        int scroll = tabScrollPx[activeTab.ordinal()];
        // Only process content clicks if within the visible content area
        boolean inContentArea = mouseY >= contentY && mouseY < contentY + contentH;
        double cMouseY = mouseY + scroll; // content-space Y

        // Features tab clicks
        if (activeTab == Tab.FEATURES && inContentArea) {
            for (int i = 0; i < 4; i++) {
                int cx = toggleCardsStartX + i * (TOGGLE_CARD_W + toggleCardGap);
                if (mouseX >= cx && mouseX <= cx + TOGGLE_CARD_W
                        && cMouseY >= toggleCardsY && cMouseY <= toggleCardsY + TOGGLE_CARD_H) {
                    if (i == 3) return true; // "Soon" card - not toggleable
                    float cardCx = cx + TOGGLE_CARD_W / 2f;
                    float cardCy = toggleCardsY + TOGGLE_CARD_H / 2f;
                    switch (i) {
                        case 0:
                            featuresWorldmap = !featuresWorldmap;
                            if (featuresWorldmap) spawnParticles(cardCx, cardCy);
                            break;
                        case 1:
                            featuresRolename = !featuresRolename;
                            if (featuresRolename) spawnParticles(cardCx, cardCy);
                            break;
                        case 2:
                            featuresAiHelper = !featuresAiHelper;
                            if (featuresAiHelper) spawnParticles(cardCx, cardCy);
                            break;
                    }
                    autoSave();
                    return true;
                }
            }
        }
        // Weltkarte tab clicks
        if (activeTab == Tab.WELTKARTE && inContentArea) {
            if (mouseX >= allgemeinMapTogX && mouseX <= allgemeinMapTogX + ICON_W
                    && cMouseY >= allgemeinMapTogY && cMouseY <= allgemeinMapTogY + ICON_H) {
                featuresWorldmap = !featuresWorldmap;
                autoSave();
                return true;
            }
            if (mouseX >= hideChunksTogX && mouseX <= hideChunksTogX + ICON_W
                    && cMouseY >= hideChunksTogY && cMouseY <= hideChunksTogY + ICON_H) {
                hideChunksOnZoomOut = !hideChunksOnZoomOut;
                autoSave();
                return true;
            }
        }
        // Rollennamen tab clicks: toggle positions are stored in screen-space (y0-based),
        // so compare against mouseY directly, NOT cMouseY.
        if (activeTab == Tab.ROLLENNAMEN && inContentArea) {
            if (mouseX >= nametagTitleTogX && mouseX <= nametagTitleTogX + ICON_W
                    && mouseY >= nametagTitleTogY && mouseY <= nametagTitleTogY + ICON_H) {
                showTitleInNametag = !showTitleInNametag;
                autoSave();
                return true;
            }
            if (mouseX >= nametagRoleTogX && mouseX <= nametagRoleTogX + ICON_W
                    && mouseY >= nametagRoleTogY && mouseY <= nametagRoleTogY + ICON_H) {
                showRolenameInNametag = !showRolenameInNametag;
                autoSave();
                return true;
            }
            if (mouseX >= nametagAccTogX && mouseX <= nametagAccTogX + ICON_W
                    && mouseY >= nametagAccTogY && mouseY <= nametagAccTogY + ICON_H) {
                showAccountnameInNametag = !showAccountnameInNametag;
                autoSave();
                return true;
            }
            if (mouseX >= charNamesToggleX && mouseX <= charNamesToggleX + ICON_W
                    && mouseY >= charNamesToggleY && mouseY <= charNamesToggleY + ICON_H) {
                showCharacterNames = !showCharacterNames;
                autoSave();
                return true;
            }
            if (mouseX >= hotkeyBtnX[0] && mouseX <= hotkeyBtnX[0] + ICON_W
                    && mouseY >= hotkeyBtnY[0] && mouseY <= hotkeyBtnY[0] + ICON_H) {
                listeningSlot = (listeningSlot == 0) ? -1 : 0;
                return true;
            }
            if (mouseX >= tablistTogX && mouseX <= tablistTogX + ICON_W
                    && mouseY >= tablistTogY && mouseY <= tablistTogY + ICON_H) {
                showNamesInTablist = !showNamesInTablist;
                autoSave();
                return true;
            }
            if (mouseX >= nlHelpBtnX && mouseX <= nlHelpBtnX + ICON_W
                    && mouseY >= nlHelpBtnY && mouseY <= nlHelpBtnY + ICON_H) {
                nameLearningHelp = !nameLearningHelp;
                autoSave();
                return true;
            }
            if (mouseX >= nlOfftopicBtnX && mouseX <= nlOfftopicBtnX + ICON_W
                    && mouseY >= nlOfftopicBtnY && mouseY <= nlOfftopicBtnY + ICON_H) {
                nameLearningOfftopic = !nameLearningOfftopic;
                autoSave();
                return true;
            }
            if (mouseX >= showNamesVoiceTogX && mouseX <= showNamesVoiceTogX + ICON_W
                    && mouseY >= showNamesVoiceTogY && mouseY <= showNamesVoiceTogY + ICON_H) {
                showNamesVoice = !showNamesVoice;
                autoSave();
                CharacterNameResolver.refreshChatMessages(showCharacterNames);
                return true;
            }
            if (mouseX >= showNamesHelpTogX && mouseX <= showNamesHelpTogX + ICON_W
                    && mouseY >= showNamesHelpTogY && mouseY <= showNamesHelpTogY + ICON_H) {
                showNamesHelp = !showNamesHelp;
                autoSave();
                CharacterNameResolver.refreshChatMessages(showCharacterNames);
                return true;
            }
            if (mouseX >= showNamesOfftopicTogX && mouseX <= showNamesOfftopicTogX + ICON_W
                    && mouseY >= showNamesOfftopicTogY && mouseY <= showNamesOfftopicTogY + ICON_H) {
                showNamesOfftopic = !showNamesOfftopic;
                autoSave();
                CharacterNameResolver.refreshChatMessages(showCharacterNames);
                return true;
            }
        }
        // Chat tab clicks
        if (activeTab == Tab.CHAT && inContentArea) {
            if (mouseX >= chatHeadsTogX && mouseX <= chatHeadsTogX + ICON_W
                    && mouseY >= chatHeadsTogY && mouseY <= chatHeadsTogY + ICON_H) {
                showChatHeads = !showChatHeads;
                autoSave();
                return true;
            }
            for (int s = 1; s <= 3; s++) {
                if (mouseX >= hotkeyBtnX[s] && mouseX <= hotkeyBtnX[s] + ICON_W
                        && cMouseY >= hotkeyBtnY[s] && cMouseY <= hotkeyBtnY[s] + ICON_H) {
                    listeningSlot = (listeningSlot == s) ? -1 : s;
                    return true;
                }
            }
        }
        // Farben tab clicks
        if (activeTab == Tab.FARBEN) {
            // Color picker confirm/cancel (always intercept, even outside content area)
            if (colorPickerOpen) {
                if (mouseX >= cpConfirmX && mouseX <= cpConfirmX + ICON_W
                        && mouseY >= cpConfirmY && mouseY <= cpConfirmY + ICON_H) {
                    closeColorPicker(true); return true;
                }
                if (mouseX >= cpCancelX && mouseX <= cpCancelX + ICON_W
                        && mouseY >= cpCancelY && mouseY <= cpCancelY + ICON_H) {
                    closeColorPicker(false); return true;
                }
                // SV square drag-start
                if (mouseX >= cpSvX && mouseX < cpSvX + CP_SV
                        && mouseY >= cpSvY && mouseY < cpSvY + CP_SV) {
                    cpSat = (float)(mouseX - cpSvX) / CP_SV;
                    cpVal = 1f - (float)(mouseY - cpSvY) / CP_SV;
                    cpSat = Math.max(0, Math.min(1, cpSat));
                    cpVal = Math.max(0, Math.min(1, cpVal));
                    cpDraggingSV = true;
                    updatePickerFromHsv();
                    return true;
                }
                if (mouseX >= cpHueX && mouseX < cpHueX + CP_SV
                        && mouseY >= cpHueY && mouseY < cpHueY + CP_HH) {
                    cpHue = (float)(mouseX - cpHueX) / CP_SV;
                    cpHue = Math.max(0, Math.min(1, cpHue));
                    cpDraggingHue = true;
                    updatePickerFromHsv();
                    return true;
                }
                // Click outside picker = cancel
                if (mouseX < cpX || mouseX > cpX + CP_W || mouseY < cpY || mouseY > cpY + CP_H) {
                    closeColorPicker(false); return true;
                }
                return true; // absorb all clicks while picker is open
            }
            if (inContentArea) {
                // Color swatch click -> open picker
                for (int i = 0; i < 6; i++) {
                    int swatchY = (int)colorResetBtnY[i] - (COLOR_ROW_H - ICON_H) / 2 + (COLOR_ROW_H - 12) / 2;
                    if (mouseX >= contentX + 6 && mouseX < contentX + 18
                            && mouseY >= swatchY && mouseY < swatchY + 12) {
                        if (colorPickerOpen && colorPickerTargetIdx == i) { closeColorPicker(true); }
                        else { closeColorPicker(true); openColorPicker(i, contentX + 18, swatchY); }
                        return true;
                    }
                }
                for (int i = 0; i < 6; i++) {
                    if (mouseX >= colorResetBtnX && mouseX <= colorResetBtnX + ICON_W
                            && mouseY >= colorResetBtnY[i] && mouseY <= colorResetBtnY[i] + ICON_H) {
                        applyColorFromField(i, String.format("%06X", getDefaultColor(i)));
                        return true;
                    }
                }
                if (presetSaveCurBtnX >= 0 && mouseX >= presetSaveCurBtnX
                        && mouseX <= presetSaveCurBtnX + presetSaveCurBtnW
                        && mouseY >= presetSaveCurBtnY && mouseY <= presetSaveCurBtnY + 16) {
                    saveCurrentAsPreset(); return true;
                }
                // Preset apply / delete
                OttoTalkConfig pCfg = OttoTalkClient.getConfig();
                for (int i = 0; i < pCfg.customColorPresets.size() && i < MAX_CUSTOM_PRESETS; i++) {
                    if (mouseX >= presetApplyBtnX && mouseX <= presetApplyBtnX + ICON_W
                            && mouseY >= presetApplyBtnY[i] && mouseY <= presetApplyBtnY[i] + ICON_H) {
                        applyPreset(i); return true;
                    }
                    if (mouseX >= presetDeleteBtnX && mouseX <= presetDeleteBtnX + ICON_W
                            && mouseY >= presetDeleteBtnY[i] && mouseY <= presetDeleteBtnY[i] + ICON_H) {
                        deleteCustomPreset(i); return true;
                    }
                }
                // Dynamic color class: add button
                if (dynAddBtnX > 0 && mouseX >= dynAddBtnX && mouseX <= dynAddBtnX + dynAddBtnW
                        && mouseY >= dynAddBtnY && mouseY <= dynAddBtnY + 13) {
                    OttoTalkConfig dynCfg = OttoTalkClient.getConfig();
                    if (dynCfg.dynamicColorClasses.size() < MAX_DYNAMIC_CLASSES) {
                        dynCfg.dynamicColorClasses.add(new OttoTalkConfig.DynamicColorClass(
                                "Klasse " + (dynCfg.dynamicColorClasses.size() + 1), 0x888888));
                        autoSave();
                    }
                    return true;
                }
                // Dynamic color class: swatch / delete / label
                java.util.List<OttoTalkConfig.DynamicColorClass> dynList = OttoTalkClient.getConfig().dynamicColorClasses;
                int dynDelX = contentX + contentW - ICON_W - 4;
                for (int d = 0; d < dynList.size() && d < MAX_DYNAMIC_CLASSES; d++) {
                    int swy = dynDisplaySwatchY[d];
                    // Color swatch click
                    if (mouseX >= contentX + 6 && mouseX < contentX + 18 && mouseY >= swy && mouseY < swy + 12) {
                        if (colorPickerOpen && colorPickerTargetIdx == DYN_COLOR_BASE + d) closeColorPicker(true);
                        else { closeColorPicker(true); openColorPicker(DYN_COLOR_BASE + d, contentX + 18, swy); }
                        return true;
                    }
                    if (mouseX >= contentX + 22 && mouseX < dynDelX - 4
                            && mouseY >= dynDeleteBtnY[d] - (COLOR_ROW_H - ICON_H) / 2
                            && mouseY <= dynDeleteBtnY[d] - (COLOR_ROW_H - ICON_H) / 2 + COLOR_ROW_H) {
                        dynEditLabelIdx = d;
                        if (dynLabelField != null) { dynLabelField.setText(dynList.get(d).label); setFocused(dynLabelField); }
                        return true;
                    }
                    // Delete click
                    if (mouseX >= dynDelX && mouseX <= dynDelX + ICON_W
                            && mouseY >= dynDeleteBtnY[d] && mouseY <= dynDeleteBtnY[d] + ICON_H) {
                        OttoTalkClient.getConfig().dynamicColorClasses.remove(d);
                        if (dynEditLabelIdx == d) { dynEditLabelIdx = -1; if (dynLabelField != null) dynLabelField.visible = false; }
                        autoSave();
                        return true;
                    }
                }
            }
        }
        // Oberfläche tab clicks
        if (activeTab == Tab.OBERFLAECHE && inContentArea) {
            if (mouseX >= animToggleX && mouseX <= animToggleX + ICON_W
                    && cMouseY >= animToggleY && cMouseY <= animToggleY + ICON_H) {
                animationsEnabled = !animationsEnabled;
                autoSave();
                return true;
            }
        }
        // LLM tab clicks
        if (activeTab == Tab.LLM && inContentArea) {
            // Provider button click
            if (mouseX >= providerBtnX && mouseX <= providerBtnX + providerBtnW
                    && cMouseY >= providerBtnY && cMouseY <= providerBtnY + providerBtnH) {
                cycleProvider();
                autoSave();
                return true;
            }
            // Eye icon click
            if (mouseX >= eyeBtnX && mouseX <= eyeBtnX + ICON_W
                    && cMouseY >= eyeBtnY && cMouseY <= eyeBtnY + ICON_H) {
                apiKeyVisible = !apiKeyVisible;
                return true;
            }
        }
        // Info tab - version clicks for debug mode
        if (activeTab == Tab.INFO && inContentArea) {
            String versionStr = "OTTOTALK v" + VERSION;
            int vTextW = this.textRenderer.getWidth(versionStr) + 16;
            int vX = frameX + FRAME_W / 2 - vTextW / 2;
            if (mouseX >= vX && mouseX <= vX + vTextW
                    && cMouseY >= versionY && cMouseY <= versionY + 14) {
                long now = System.currentTimeMillis();
                if (now - lastVersionClickTime > 3000) versionClickCount = 0;
                lastVersionClickTime = now;
                versionClickCount++;
                if (versionClickCount >= 10) {
                    RoleplayStateManager.toggleDebugMode();
                    versionClickCount = 0;
                }
                return true;
            }
        }
        // Spieler tab clicks
        if (activeTab == Tab.SPIELER && cachedPlayerList != null) {
            // Popup intercepts all clicks while open
            if (spielerPopupVisible) {
                if (mouseX >= spielerPopupSaveBtnX && mouseX <= spielerPopupSaveBtnX + ICON_W
                        && mouseY >= spielerPopupSaveBtnY && mouseY <= spielerPopupSaveBtnY + ICON_H) {
                    saveSpielerEditPopup(); return true;
                }
                if (mouseX >= spielerPopupCancelBtnX && mouseX <= spielerPopupCancelBtnX + ICON_W
                        && mouseY >= spielerPopupCancelBtnY && mouseY <= spielerPopupCancelBtnY + ICON_H) {
                    cancelSpielerEditPopup(); return true;
                }
                // Color swatch click, cycle title color group
                if (mouseX >= spielerPopupSwatchX && mouseX <= spielerPopupSwatchX + 14
                        && mouseY >= spielerPopupSwatchY && mouseY <= spielerPopupSwatchY + 12) {
                    cycleTitleColorGroup(); return true;
                }
                // Click outside popup -> cancel
                if (mouseX < spielerPopupX || mouseX > spielerPopupX + SPOP_W
                        || mouseY < spielerPopupY || mouseY > spielerPopupY + SPOP_H) {
                    cancelSpielerEditPopup();
                    return true;
                }
                // Pass through to super so TextFieldWidgets inside the popup receive clicks
                return super.mouseClicked(mouseX, mouseY, button);
            }
            // Normal row clicks
            int charColX = contentX + ACCOUNT_COL_W;
            int lockColX = contentX + contentW - LOCK_COL_W;
            java.util.List<PlayerNameList.PlayerEntry> disp = filteredPlayerList != null ? filteredPlayerList : cachedPlayerList;
            int maxVisible = (spielerListBottom - spielerListTop) / PLAYER_ROW_H;
            for (int i = 0; i < maxVisible && (i + spielerScrollOffset) < disp.size(); i++) {
                int idx = i + spielerScrollOffset;
                int rowY = spielerListTop + i * PLAYER_ROW_H;
                int toggleX = lockColX + (LOCK_COL_W - ICON_W) / 2;
                int toggleY = rowY + (PLAYER_ROW_H - ICON_H) / 2;
                // Lock toggle
                if (mouseX >= toggleX && mouseX <= toggleX + ICON_W
                        && mouseY >= toggleY && mouseY <= toggleY + ICON_H) {
                    PlayerNameList.toggleLocked(disp.get(idx).accountName);
                    return true;
                }
                // Middle column click, opens popup (use original cachedPlayerList index for popup)
                if (!disp.get(idx).locked
                        && mouseX >= charColX && mouseX < lockColX
                        && mouseY >= rowY && mouseY < rowY + PLAYER_ROW_H) {
                    int realIdx = cachedPlayerList.indexOf(disp.get(idx));
                    openSpielerEditPopup(realIdx >= 0 ? realIdx : idx);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (colorPickerOpen) {
            int mx = (int) mouseX, my = (int) mouseY;
            if (cpDraggingSV) {
                cpSat = Math.max(0, Math.min(1f, (float)(mx - cpSvX) / CP_SV));
                cpVal = Math.max(0, Math.min(1f, 1f - (float)(my - cpSvY) / CP_SV));
                updatePickerFromHsv();
                return true;
            }
            if (cpDraggingHue) {
                cpHue = Math.max(0, Math.min(1f, (float)(mx - cpHueX) / CP_SV));
                updatePickerFromHsv();
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        cpDraggingSV = false;
        cpDraggingHue = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Returns the total pixel height of content for the given tab. */
    private int getTabContentHeight(Tab tab) {
        switch (tab) {
            case FEATURES:
                return 30 + TOGGLE_CARD_H + 8 + 36;
            case WELTKARTE:
                return 8 + 2 * SETTING_ROW_H + SETTING_ROW_GAP;
            case ROLLENNAMEN:
                // label(12) + section(88) + gap+divider(11) + 5 rows (added Chat Köpfe)
                return 8 + 12 + 88 + SETTING_ROW_GAP + 5 + 5 * SETTING_ROW_H + 4 * SETTING_ROW_GAP;
            case CHAT:
                return 8 + 4 * SETTING_ROW_H + 4 * SETTING_ROW_GAP;
            case FARBEN: {
                OttoTalkConfig fCfg = OttoTalkClient.getConfig();
                int nP = fCfg.customColorPresets.size();
                int nDyn = fCfg.dynamicColorClasses.size();
                return 8 + 14 + 6 * (COLOR_ROW_H + 3) + 6 + 14 + 14
                       + (nP > 0 ? nP * (COLOR_ROW_H + 3) : 20)
                       + 24 + 16 + nDyn * (COLOR_ROW_H + 3) + (nDyn == 0 ? 18 : 0);
            }
            case OBERFLAECHE:
                return 8 + SETTING_ROW_H;
            case KONTEXT:
                // 5 field blocks + hint text
                return FIELD_STEP * 5 + 14;
            case LLM:
                // 4 field blocks
                return FIELD_STEP * 4;
            case INFO:
                // title + desc + version + feedback block
                return 46 + 14 + 28 + 78 + 8;
            case SPIELER:
                // Spieler uses its own row-based scroll; report full list height
                if (cachedPlayerList == null) return contentH;
                return SPIELER_HEADER_H + cachedPlayerList.size() * PLAYER_ROW_H;
            default:
                return contentH;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Sidebar scroll
        if (mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_W
                && mouseY >= sidebarY && mouseY <= sidebarY + FRAME_H) {
            sidebarScrollPx -= (int)(verticalAmount * 10);
            int totalContent = getSidebarContentHeight();
            int infoY = sidebarY + FRAME_H - 12 - SIDE_TAB_H;
            int visibleH = (infoY - 4) - (sidebarY + 12);
            int maxScroll = Math.max(0, totalContent - visibleH);
            sidebarScrollPx = Math.max(0, Math.min(sidebarScrollPx, maxScroll));
            return true;
        }
        // Spieler tab: use row-based scroll directly
        if (activeTab == Tab.SPIELER && cachedPlayerList != null) {
            spielerScrollOffset -= (int) verticalAmount;
            int visibleH = spielerListBottom - spielerListTop;
            int maxVisible = visibleH / PLAYER_ROW_H;
            java.util.List<PlayerNameList.PlayerEntry> dl = filteredPlayerList != null ? filteredPlayerList : cachedPlayerList;
            int maxScroll = Math.max(0, dl.size() - maxVisible);
            spielerScrollOffset = Math.max(0, Math.min(spielerScrollOffset, maxScroll));
            return true;
        }
        // Other tabs: pixel-based scroll
        int tabIdx = activeTab.ordinal();
        int totalH = getTabContentHeight(activeTab);
        int maxScroll = Math.max(0, totalH - contentH);
        if (maxScroll > 0) {
            tabScrollPx[tabIdx] -= (int)(verticalAmount * 10);
            tabScrollPx[tabIdx] = Math.max(0, Math.min(tabScrollPx[tabIdx], maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Spieler popup: Enter = save, Escape = cancel
        if (spielerPopupVisible) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) { saveSpielerEditPopup(); return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { cancelSpielerEditPopup(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // Color picker: Escape = cancel
        if (colorPickerOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeColorPicker(false); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER)  { closeColorPicker(true);  return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (listeningSlot >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setHotkeyForSlot(listeningSlot, -1); // clear hotkey
            } else {
                setHotkeyForSlot(listeningSlot, keyCode);
            }
            listeningSlot = -1;
            autoSave();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void autoSave() {
        OttoTalkConfig config = OttoTalkClient.getConfig();
        boolean wasShowingNames = config.showCharacterNames;
        config.characterName = this.nameField.getText().trim();
        config.characterRole = this.roleField.getText().trim();
        config.characterTitle = this.titleField.getText().trim();
        config.characterBackground = this.backgroundField.getText().trim();
        config.additionalInstructions = this.instructionsField.getText().trim();
        config.apiProvider = this.selectedProvider;
        config.apiKey = this.apiKeyField.getText().trim();
        config.apiUrl = this.apiUrlField.getText().trim();
        config.model = this.modelField.getText().trim();
        config.enableAnimations = this.animationsEnabled;
        config.showCharacterNames = this.showCharacterNames;
        config.characterNameHotkey = this.characterNameHotkey;
        config.hilfeHotkey = this.hilfeHotkey;
        config.sprachHotkey = this.sprachHotkey;
        config.offtopicHotkey = this.offtopicHotkey;
        config.nameLearningVoice = true; // Rufen always active, not toggleable
        config.nameLearningHelp = this.nameLearningHelp;
        config.nameLearningOfftopic = this.nameLearningOfftopic;
        config.showNamesInVoice = this.showNamesVoice;
        config.showNamesInHelp = this.showNamesHelp;
        config.showNamesInOfftopic = this.showNamesOfftopic;
        config.showNamesInTablist = this.showNamesInTablist;
        config.showTitleInNametag = this.showTitleInNametag;
        config.showRolenameInNametag = this.showRolenameInNametag;
        config.showAccountnameInNametag = this.showAccountnameInNametag;
        config.mapOverlayEnabled = this.featuresWorldmap;
        config.hideChunksOnZoomOut = this.hideChunksOnZoomOut;
        config.aiHelperEnabled = this.featuresAiHelper;
        config.rolenameFeatureEnabled = this.featuresRolename;
        config.showChatHeads = this.showChatHeads;
        config.save();

        // Sync local player's role+title into PlayerNameList so the nametag mixin
        // and the Spieler tab both see the correct data immediately.
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player != null) {
            String acct = mc.player.getGameProfile().getName();
            com.ottotalk.context.PlayerNameList.ensurePlayer(acct);
            if (!config.characterName.isEmpty())
                com.ottotalk.context.PlayerNameList.setCharacterName(acct, config.characterName);
            com.ottotalk.context.PlayerNameList.setCharacterTitle(acct, config.characterTitle);
        }

        // Refresh existing chat messages when toggle changed
        if (this.showCharacterNames != wasShowingNames) {
            CharacterNameResolver.refreshChatMessages(this.showCharacterNames);
        }
    }

    private static class FeatureParticle {
        float x, y, vx, vy, life = 1f;
        int color, size;
        FeatureParticle(float x, float y, float vx, float vy, int color, int size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.color = color; this.size = size;
        }
    }

    @Override
    public void removed() {
        autoSave();
        super.removed();
    }

    private void closeScreen() {
        autoSave();
        MinecraftClient.getInstance().setScreen(new ChatScreen(""));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
