package com.ottotalk.gui;

import com.ottotalk.OttoTalkClient;

public class RoleplayStateManager {
    public enum SpeechMode {
        REDE,       // direkte übersetzung: spieler tippt was, AI übersetzt ins mittelalterliche
        ANWEISUNG   // anweisungs-modus: spieler gibt nur ne aufgabe, AI generiert freien text
    }

    private static boolean isRoleplayModeActive = false;
    private static RoleplayOverlayScreen currentOverlay = null;
    private static boolean bypassInterception = false;
    private static SpeechMode speechMode = SpeechMode.REDE;
    private static boolean historyEnabled = true;
    private static boolean emoteModeEnabled = false;
    private static boolean debugMode = false;
    
    public static void toggleRoleplayMode() {
        isRoleplayModeActive = !isRoleplayModeActive;
        
        if (isRoleplayModeActive) {
            openRoleplayOverlay();
            ServerChatState.ensureVoiceChannel();
        } else {
            closeRoleplayOverlay();
        }
    }
    
    public static boolean isRoleplayModeActive() {
        return isRoleplayModeActive;
    }
    
    public static void enableRoleplayMode() {
        if (!isRoleplayModeActive) {
            toggleRoleplayMode();
        }
    }
    
    public static void disableRoleplayMode() {
        if (isRoleplayModeActive) {
            isRoleplayModeActive = false;
            closeRoleplayOverlay();
        }
    }
    
    private static void openRoleplayOverlay() {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen) {
                currentOverlay = new RoleplayOverlayScreen();
            }
        } catch (Exception e) {
        }
    }

    private static void closeRoleplayOverlay() {
        if (currentOverlay != null) {
            currentOverlay = null;
        }
    }
    
    public static RoleplayOverlayScreen getCurrentOverlay() {
        return currentOverlay;
    }
    
    public static boolean shouldRenderOverlay() {
        return isRoleplayModeActive && currentOverlay != null;
    }
    
    public static void setBypassInterception(boolean bypass) {
        bypassInterception = bypass;
    }
    
    public static boolean isBypassingInterception() {
        return bypassInterception;
    }

    // pending edit text: wenn gesetzt, packt der ChatScreenHandler den ins chat field
    private static String pendingEditText = null;

    public static void setPendingEditText(String text) {
        pendingEditText = text;
    }

    public static String consumePendingEditText() {
        String text = pendingEditText;
        pendingEditText = null;
        return text;
    }

    public static SpeechMode getSpeechMode() {
        return speechMode;
    }

    public static void toggleSpeechMode() {
        speechMode = (speechMode == SpeechMode.REDE) ? SpeechMode.ANWEISUNG : SpeechMode.REDE;
    }

    public static String getSpeechModeLabel() {
        return speechMode == SpeechMode.REDE ? "\u270D Rede" : "\u2699 Anweisung";
    }

    public static boolean isHistoryEnabled() {
        return historyEnabled;
    }

    public static void toggleHistory() {
        historyEnabled = !historyEnabled;
    }

    public static boolean isEmoteModeEnabled() {
        return emoteModeEnabled;
    }

    public static void toggleEmoteMode() {
        emoteModeEnabled = !emoteModeEnabled;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static void toggleDebugMode() {
        debugMode = !debugMode;
    }

}
