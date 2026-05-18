package com.ottotalk.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ottotalk.OttoTalkClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OttoTalkConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ottotalk.json");
    
    // API Configuration
    public String apiProvider = "gemini"; // "openai" or "gemini"
    public String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    public String apiKey = "YOUR_GEMINI_API_KEY_HERE";
    public String model = "gemini-2.0-flash";
    public int maxTokens = 300;
    public double temperature = 0.8;
    
    // UI Configuration
    public boolean enableCheckmarks = true;
    public int maxContextMessages = 30;
    public String checkmarkSymbol = "☐";
    public String checkedSymbol = "☑";
    
    // Medieval Language Settings
    public String languageStyle = "medieval_german";
    public boolean useTheeThous = false;
    
    // Animation
    public boolean enableAnimations = true;
    
    // im chat RP charakternamen statt account-namen anzeigen (Ottonien)
    public boolean showCharacterNames = false;
    public int characterNameHotkey = -1; // GLFW key code, -1 = none
    public int hilfeHotkey = -1;
    public int sprachHotkey = -1;
    public int offtopicHotkey = -1;
    
    // config version fürs migration (hochzählen wenn defaults sich ändern)
    public int configVersion = 1;

    // Map overlay
    public boolean mapOverlayEnabled = true;
    public boolean hideChunksOnZoomOut = false;

    // AI Helper (roleplay modus) global aktiv
    public boolean aiHelperEnabled = true;

    // rolename feature global aktiv (zeigt den button im extender)
    public boolean rolenameFeatureEnabled = true;

    // name learning channels (welche chat-kanäle erlauben dauerhaftes RP-namen speichern)
    public boolean nameLearningVoice = true;    // Reden/Fl\u00FCstern/Rufen (grouped)
    public boolean nameLearningHelp = false;
    public boolean nameLearningOfftopic = false;

    // pro-channel rolename ANZEIGE (unabhängig vom globalen showCharacterNames)
    public boolean showNamesInVoice = true;     // Reden/Fl\u00FCstern/Rufen
    public boolean showNamesInHelp = true;      // Hilfe channel
    public boolean showNamesInOfftopic = false; // Offtopic channel

    public boolean showNamesInTablist = false;    // Tablist (Tab key)

    // nametag visibility toggles
    public boolean showTitleInNametag = true;
    public boolean showRolenameInNametag = true;
    public boolean showAccountnameInNametag = true;

    // Ottonien display colors (anpassbar; defaults entsprechen der server reference palette)
    public static final int DEFAULT_COLOR_NAME              = 0xC7A87F;
    public static final int DEFAULT_COLOR_TITLE_DEFAULT     = 0xA27F5F;
    public static final int DEFAULT_COLOR_TITLE_VORKOSTER   = 0xEA7749;
    public static final int DEFAULT_COLOR_TITLE_ADEL        = 0xB1A354;
    public static final int DEFAULT_COLOR_TITLE_KLERUS      = 0xC7505E;
    public static final int DEFAULT_COLOR_TITLE_GAMEMASTER   = 0xBF4398;

    public int colorName            = DEFAULT_COLOR_NAME;
    public int colorTitleDefault    = DEFAULT_COLOR_TITLE_DEFAULT;
    public int colorTitleVorkoster  = DEFAULT_COLOR_TITLE_VORKOSTER;
    public int colorTitleAdel       = DEFAULT_COLOR_TITLE_ADEL;
    public int colorTitleKlerus      = DEFAULT_COLOR_TITLE_KLERUS;
    public int colorTitleGamemaster  = DEFAULT_COLOR_TITLE_GAMEMASTER;

    /** A user-defined color profile (all 5 palette colors). */
    public static class CustomColorPreset {
        public String name = "Preset";
        public int colorName            = DEFAULT_COLOR_NAME;
        public int colorTitleDefault    = DEFAULT_COLOR_TITLE_DEFAULT;
        public int colorTitleVorkoster  = DEFAULT_COLOR_TITLE_VORKOSTER;
        public int colorTitleAdel       = DEFAULT_COLOR_TITLE_ADEL;
        public int colorTitleKlerus      = DEFAULT_COLOR_TITLE_KLERUS;
        public int colorTitleGamemaster   = DEFAULT_COLOR_TITLE_GAMEMASTER;
        public CustomColorPreset() {}
        public CustomColorPreset(String name, OttoTalkConfig src) {
            this.name = name;
            this.colorName           = src.colorName;
            this.colorTitleDefault   = src.colorTitleDefault;
            this.colorTitleVorkoster = src.colorTitleVorkoster;
            this.colorTitleAdel      = src.colorTitleAdel;
            this.colorTitleKlerus    = src.colorTitleKlerus;
            this.colorTitleGamemaster    = src.colorTitleGamemaster;
        }
    }

    public List<CustomColorPreset> customColorPresets = new ArrayList<>();

    /** User-defined extra color class (server color to display color mapping with a custom label). */
    public static class DynamicColorClass {
        public String label = "Neue Klasse";
        public int displayColor = 0x888888;
        public DynamicColorClass() {}
        public DynamicColorClass(String label, int displayColor) {
            this.label = label; this.displayColor = displayColor;
        }
    }
    public List<DynamicColorClass> dynamicColorClasses = new ArrayList<>();

    /** kleinen player head vor chat messages bekannter spieler zeigen. */
    public boolean showChatHeads = false;

    /**
     * Maps a server-extracted title color to the user's customised color.
     * Strips alpha, uses fuzzy channel-distance matching (tolerance 10) for palette entries.
     */
    public int mapTitleColor(int serverColor) {
        if (serverColor <= 0) return colorTitleDefault;
        int rgb = serverColor & 0x00FFFFFF;
        if (colorClose(rgb, DEFAULT_COLOR_NAME))              return colorName;
        if (colorClose(rgb, DEFAULT_COLOR_TITLE_DEFAULT))     return colorTitleDefault;
        if (colorClose(rgb, DEFAULT_COLOR_TITLE_VORKOSTER))   return colorTitleVorkoster;
        if (colorClose(rgb, DEFAULT_COLOR_TITLE_ADEL))        return colorTitleAdel;
        if (colorClose(rgb, DEFAULT_COLOR_TITLE_KLERUS))      return colorTitleKlerus;
        if (colorClose(rgb, DEFAULT_COLOR_TITLE_GAMEMASTER))  return colorTitleGamemaster;
        return rgb;
    }

    private static boolean colorClose(int a, int b) {
        int dr = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
        int dg = Math.abs(((a >> 8)  & 0xFF) - ((b >> 8)  & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return (dr + dg + db) <= 10;
    }

    // Character Info
    public String characterName = "";
    public String characterRole = ""; // e.g. "Bauer", "Adliger", "Händler", "Ritter"
    public String characterTitle = ""; // optional title shown above role in nametag
    public String characterBackground = ""; // free text background info
    public String additionalInstructions = ""; // custom instructions for the LLM (e.g. speech quirks)
    
    public void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                OttoTalkConfig loaded = GSON.fromJson(json, OttoTalkConfig.class);
                copyFrom(loaded);
                migrate(loaded);
            } catch (IOException e) {
                OttoTalkClient.LOGGER.error("Failed to load configuration", e);
                save(); // Create default config
            }
        } else {
            save(); // Create default config
        }
    }
    
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(this);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            OttoTalkClient.LOGGER.error("Failed to save configuration", e);
        }
    }
    
    private static final String GEMINI_DEFAULT_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final String OPENAI_DEFAULT_URL  = "https://api.openai.com/v1/chat/completions";

    /** migrations anwenden wenn ne ältere config version geladen wird. */
    private void migrate(OttoTalkConfig loaded) {
        boolean changed = false;
        if (loaded.configVersion < 1) {
            // v0 -> v1: name learning defaults changed to voice-only
            this.nameLearningHelp = false;
            this.nameLearningOfftopic = false;
            this.configVersion = 1;
            changed = true;
        }
        // mismatched provider/URL fixen: was als provider angegeben ist gewinnt, URL/model wird passend gezogen
        if ("openai".equals(this.apiProvider)
                && this.apiUrl != null && this.apiUrl.contains("generativelanguage.googleapis.com")) {
            this.apiUrl = OPENAI_DEFAULT_URL;
            if (this.model == null || this.model.startsWith("gemini")) this.model = "gpt-4o-mini";
            changed = true;
        } else if ("gemini".equals(this.apiProvider)
                && this.apiUrl != null
                && (this.apiUrl.contains("api.openai.com") || this.apiUrl.contains("/v1/chat/completions"))) {
            this.apiUrl = GEMINI_DEFAULT_URL;
            if (this.model == null || this.model.startsWith("gpt")) this.model = "gemini-2.0-flash";
            changed = true;
        }
        if (changed) save();
    }

    private void copyFrom(OttoTalkConfig other) {
        this.apiProvider = other.apiProvider;
        this.apiUrl = other.apiUrl;
        this.apiKey = other.apiKey;
        this.model = other.model;
        this.maxTokens = other.maxTokens;
        this.temperature = other.temperature;
        this.enableCheckmarks = other.enableCheckmarks;
        this.maxContextMessages = other.maxContextMessages;
        this.checkmarkSymbol = other.checkmarkSymbol;
        this.checkedSymbol = other.checkedSymbol;
        this.languageStyle = other.languageStyle;
        this.useTheeThous = other.useTheeThous;
        this.characterName = other.characterName != null ? other.characterName : "";
        this.characterRole = other.characterRole != null ? other.characterRole : "";
        this.characterTitle = other.characterTitle != null ? other.characterTitle : "";
        this.characterBackground = other.characterBackground != null ? other.characterBackground : "";
        this.additionalInstructions = other.additionalInstructions != null ? other.additionalInstructions : "";
        this.enableAnimations = other.enableAnimations;
        this.showCharacterNames = other.showCharacterNames;
        this.characterNameHotkey = other.characterNameHotkey;
        this.hilfeHotkey = other.hilfeHotkey;
        this.sprachHotkey = other.sprachHotkey;
        this.offtopicHotkey = other.offtopicHotkey;
        this.configVersion = other.configVersion;
        this.nameLearningVoice = other.nameLearningVoice;
        this.nameLearningHelp = other.nameLearningHelp;
        this.nameLearningOfftopic = other.nameLearningOfftopic;
        this.showNamesInVoice = other.showNamesInVoice;
        this.showNamesInHelp = other.showNamesInHelp;
        this.showNamesInOfftopic = other.showNamesInOfftopic;
        this.showNamesInTablist = other.showNamesInTablist;
        this.showTitleInNametag = other.showTitleInNametag;
        this.showRolenameInNametag = other.showRolenameInNametag;
        this.showAccountnameInNametag = other.showAccountnameInNametag;
        this.colorName           = other.colorName > 0           ? other.colorName           : DEFAULT_COLOR_NAME;
        this.colorTitleDefault   = other.colorTitleDefault > 0   ? other.colorTitleDefault   : DEFAULT_COLOR_TITLE_DEFAULT;
        this.colorTitleVorkoster = other.colorTitleVorkoster > 0 ? other.colorTitleVorkoster : DEFAULT_COLOR_TITLE_VORKOSTER;
        this.colorTitleAdel      = other.colorTitleAdel > 0      ? other.colorTitleAdel      : DEFAULT_COLOR_TITLE_ADEL;
        this.colorTitleKlerus      = other.colorTitleKlerus > 0     ? other.colorTitleKlerus     : DEFAULT_COLOR_TITLE_KLERUS;
        this.colorTitleGamemaster  = other.colorTitleGamemaster > 0 ? other.colorTitleGamemaster : DEFAULT_COLOR_TITLE_GAMEMASTER;
        this.customColorPresets  = other.customColorPresets != null ? new ArrayList<>(other.customColorPresets) : new ArrayList<>();
        this.dynamicColorClasses = other.dynamicColorClasses != null ? new ArrayList<>(other.dynamicColorClasses) : new ArrayList<>();
        this.showChatHeads = other.showChatHeads;
        this.mapOverlayEnabled = other.mapOverlayEnabled;
        this.hideChunksOnZoomOut = other.hideChunksOnZoomOut;
        this.aiHelperEnabled = other.aiHelperEnabled;
        this.rolenameFeatureEnabled = other.rolenameFeatureEnabled;
    }
    
    public boolean isApiConfigured() {
        // lokale LLMs (LM Studio etc.) brauchen keinen echten API key
        if ("not-needed-for-local".equals(apiKey)) {
            return apiUrl != null && !apiUrl.trim().isEmpty();
        }
        return apiKey != null && 
               !apiKey.equals("YOUR_API_KEY_HERE") && 
               !apiKey.equals("YOUR_GEMINI_API_KEY_HERE") && 
               !apiKey.trim().isEmpty();
    }
}
