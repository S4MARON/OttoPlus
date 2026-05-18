package com.ottotalk.gui;

import com.ottotalk.OttoTalkClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public class ServerChatState {

    public enum VoiceRange { RUFEN, SPRECHEN, FLUSTERN }

    public enum ChannelState { DISABLED, ACTIVE, CURRENTLY_WRITING }

    private static VoiceRange voiceRange = VoiceRange.SPRECHEN;
    private static boolean voiceWriting = true; // voice range ist immer mindestens aktiv
    private static ChannelState helpState = ChannelState.ACTIVE;
    private static ChannelState offtopicState = ChannelState.ACTIVE;

    private static boolean serverOverlayVisible = true;

    public static boolean isServerOverlayVisible() {
        return serverOverlayVisible && isOnOttonien();
    }

    public static void toggleServerOverlay() {
        serverOverlayVisible = !serverOverlayVisible;
    }

    /**
     * Reset state on server join/rejoin. Player auto-joins help and offtopic;
     * default writing channel is Sprechen.
     */
    public static void resetOnJoin() {
        voiceRange = VoiceRange.SPRECHEN;
        voiceWriting = true;
        helpState = ChannelState.ACTIVE;
        offtopicState = ChannelState.ACTIVE;
        serverOverlayVisible = true;
    }

    public static boolean isOnOttonien() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getCurrentServerEntry() == null) return false;
        ServerInfo info = client.getCurrentServerEntry();
        String address = info.address.toLowerCase();
        return address.contains("ottonien");
    }

    public static VoiceRange getVoiceRange() {
        return voiceRange;
    }

    public static boolean isVoiceWriting() {
        return voiceWriting;
    }

    /**
     * Wenn nicht currently_writing: writing wieder rein (command nochmal schicken).
     * Wenn currently_writing: in den nächsten modus wechseln und neuen command schicken.
     */
    public static void clickVoiceRange() {
        if (!voiceWriting) {
            voiceWriting = true;
            clearOtherWriting("voice");
            sendVoiceCommand();
        } else {
            switch (voiceRange) {
                case RUFEN: voiceRange = VoiceRange.SPRECHEN; break;
                case SPRECHEN: voiceRange = VoiceRange.FLUSTERN; break;
                case FLUSTERN: voiceRange = VoiceRange.RUFEN; break;
            }
            clearOtherWriting("voice");
            sendVoiceCommand();
        }
    }

    private static void sendVoiceCommand() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        switch (voiceRange) {
            case RUFEN: client.player.networkHandler.sendCommand("r"); break;
            case SPRECHEN: client.player.networkHandler.sendCommand("s"); break;
            case FLUSTERN: client.player.networkHandler.sendCommand("f"); break;
        }
    }

    public static ChannelState getHelpState() {
        return helpState;
    }

    /**
     * DISABLED/ACTIVE -> CURRENTLY_WRITING (send /h).
     * CURRENTLY_WRITING zu DISABLED (send /leave h).
     */
    public static void clickHelp() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        switch (helpState) {
            case DISABLED:
                helpState = ChannelState.CURRENTLY_WRITING;
                clearOtherWriting("help");
                client.player.networkHandler.sendCommand("h");
                break;
            case ACTIVE:
                helpState = ChannelState.CURRENTLY_WRITING;
                clearOtherWriting("help");
                client.player.networkHandler.sendCommand("h");
                break;
            case CURRENTLY_WRITING:
                helpState = ChannelState.DISABLED;
                client.player.networkHandler.sendCommand("leave h");
                voiceWriting = true;
                sendVoiceCommand();
                break;
        }
    }

    public static ChannelState getOfftopicState() {
        return offtopicState;
    }

    /**
     * DISABLED/ACTIVE wechselt zu CURRENTLY_WRITING (send /o).
     * CURRENTLY_WRITING -> DISABLED (send /leave o).
     */
    public static void clickOfftopic() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        switch (offtopicState) {
            case DISABLED:
                offtopicState = ChannelState.CURRENTLY_WRITING;
                clearOtherWriting("offtopic");
                client.player.networkHandler.sendCommand("o");
                break;
            case ACTIVE:
                offtopicState = ChannelState.CURRENTLY_WRITING;
                clearOtherWriting("offtopic");
                client.player.networkHandler.sendCommand("o");
                break;
            case CURRENTLY_WRITING:
                offtopicState = ChannelState.DISABLED;
                client.player.networkHandler.sendCommand("leave o");
                voiceWriting = true;
                sendVoiceCommand();
                break;
        }
    }

    /**
     * When a button enters currently_writing, other buttons that were currently_writing
     * drop to ACTIVE (they stay in the channel but are no longer writing there).
     * Voice range drops its writing flag.
     */
    private static void clearOtherWriting(String source) {
        if (!"voice".equals(source)) {
            voiceWriting = false;
        }
        if (!"help".equals(source)) {
            if (helpState == ChannelState.CURRENTLY_WRITING) {
                helpState = ChannelState.ACTIVE;
            }
        }
        if (!"offtopic".equals(source)) {
            if (offtopicState == ChannelState.CURRENTLY_WRITING) {
                offtopicState = ChannelState.ACTIVE;
            }
        }
    }

    /**
     * Process an outgoing command and update state as if the button was pressed.
     * Returns true if the command was recognized and state was updated.
     */
    public static boolean handleOutgoingCommand(String command) {
        String cmd = command.trim().toLowerCase();
        String voiceCmd = voiceRange == VoiceRange.SPRECHEN ? "s" : voiceRange == VoiceRange.FLUSTERN ? "f" : "r";
        switch (cmd) {
            case "s":
                voiceRange = VoiceRange.SPRECHEN;
                voiceWriting = true;
                clearOtherWriting("voice");
                return true;
            case "f":
                voiceRange = VoiceRange.FLUSTERN;
                voiceWriting = true;
                clearOtherWriting("voice");
                return true;
            case "r":
                voiceRange = VoiceRange.RUFEN;
                voiceWriting = true;
                clearOtherWriting("voice");
                return true;
            case "h":
                helpState = ChannelState.CURRENTLY_WRITING;
                clearOtherWriting("help");
                return true;
            case "leave h":
                boolean wasHelpWriting = (helpState == ChannelState.CURRENTLY_WRITING);
                helpState = ChannelState.DISABLED;
                if (wasHelpWriting && !voiceWriting && offtopicState != ChannelState.CURRENTLY_WRITING) {
                    voiceWriting = true;
                }
                // immer den voice-command schicken damit der server den richtigen modus kennt
                sendDelayedVoiceCommand(voiceCmd);
                return true;
            case "o":
                offtopicState = ChannelState.CURRENTLY_WRITING;
                clearOtherWriting("offtopic");
                return true;
            case "leave o":
                boolean wasOfftopicWriting = (offtopicState == ChannelState.CURRENTLY_WRITING);
                offtopicState = ChannelState.DISABLED;
                if (wasOfftopicWriting && !voiceWriting && helpState != ChannelState.CURRENTLY_WRITING) {
                    voiceWriting = true;
                }
                // Always send the voice command so the server knows the correct mode
                sendDelayedVoiceCommand(voiceCmd);
                return true;
            default:
                return false;
        }
    }

    private static String pendingVoiceCommand = null;
    private static int pendingDelayTicks = 0;

    private static void sendDelayedVoiceCommand(String voiceCmd) {
        pendingVoiceCommand = voiceCmd;
        pendingDelayTicks = 5; // ~250ms verzögerung damit der leave command zuerst durch ist
    }

    public static void tick() {
        if (pendingVoiceCommand != null) {
            if (pendingDelayTicks > 0) {
                pendingDelayTicks--;
            } else {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && client.player.networkHandler != null) {
                    client.player.networkHandler.sendCommand(pendingVoiceCommand);
                } else {
                }
                pendingVoiceCommand = null;
            }
        }
    }

    private static int lastOverlayHeight = 0;

    public static void setLastOverlayHeight(int height) {
        lastOverlayHeight = height;
    }

    public static int getLastOverlayHeight() {
        return shouldRender() ? lastOverlayHeight : 0;
    }

    public static String getIndicatorTexturePath() {
        if (helpState == ChannelState.CURRENTLY_WRITING) return "textures/gui/indicator_helpmode.png";
        if (offtopicState == ChannelState.CURRENTLY_WRITING) return "textures/gui/indicator_offtopic.png";
        switch (voiceRange) {
            case RUFEN: return "textures/gui/indicator_rufen.png";
            case FLUSTERN: return "textures/gui/indicator_fluestern.png";
            default: return "textures/gui/indicator_sprechen.png";
        }
    }

    /**
     * sicherstellen dass der spieler in einem voice-kanal schreibt (sprechen/flüstern/rufen).
     * wird beim aktivieren vom AI Helper aufgerufen, sonst landet das in help/offtopic.
     */
    public static void ensureVoiceChannel() {
        if (!isOnOttonien()) return;
        if (helpState == ChannelState.CURRENTLY_WRITING || offtopicState == ChannelState.CURRENTLY_WRITING) {
            if (helpState == ChannelState.CURRENTLY_WRITING) helpState = ChannelState.ACTIVE;
            if (offtopicState == ChannelState.CURRENTLY_WRITING) offtopicState = ChannelState.ACTIVE;
            voiceWriting = true;
            sendVoiceCommand();
        }
    }

    public static boolean shouldRender() {
        return isServerOverlayVisible();
    }
}
