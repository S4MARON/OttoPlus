package com.ottotalk;

import com.ottotalk.commands.RoleplayCommand;
import com.ottotalk.config.OttoTalkConfig;
import com.ottotalk.context.ContextManager;
import com.ottotalk.gui.ChatScreenHandler;
import com.ottotalk.gui.RoleplayStateManager;
import com.ottotalk.gui.ServerChatState;
import com.ottotalk.map.MapDataManager;
import com.ottotalk.map.XaeroMinimapOverlay;
import com.ottotalk.map.XaeroWorldMapOverlay;
import com.ottotalk.network.AIApiClient;
import net.fabricmc.api.ClientModInitializer;
import com.ottotalk.context.CharacterNameResolver;
import com.ottotalk.context.ChatHistoryManager;
import com.ottotalk.context.PlayerNameList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OttoTalkClient implements ClientModInitializer {
    public static final String MOD_ID = "ottotalk";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static OttoTalkConfig config;
    private static ContextManager contextManager;
    private static AIApiClient apiClient;
    private static boolean wasNameHotkeyPressed = false;
    private static boolean wasHilfeHotkeyPressed = false;
    private static boolean wasSprachHotkeyPressed = false;
    private static boolean wasOfftopicHotkeyPressed = false;
    // tick-basierte delayed actions (sonst gibts Thread.sleep auf dem render thread, no go)
    private static int pendingSendSCommandTicks = -1;
    private static int pendingSyncPlayersTicks = -1;
    
    @Override
    public void onInitializeClient() {

        config = new OttoTalkConfig();
        config.load();

        PlayerNameList.load();

        contextManager = new ContextManager();

        apiClient = new AIApiClient(config);

        try {
            RoleplayCommand.registerAlternative();
        } catch (Exception e) {
            LOGGER.error("Could not register commands: " + e.getMessage());
        }

        ChatScreenHandler.register();

        XaeroWorldMapOverlay.register();
        XaeroMinimapOverlay.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerChatState.resetOnJoin();

            // map daten direkt beim join holen damit sie schon da sind wenn die karte aufgeht
            MapDataManager.getInstance().fetchData();
            XaeroMinimapOverlay.onJoin();

            if (ServerChatState.isOnOttonien()) {
                pendingSendSCommandTicks = 20;
                pendingSyncPlayersTicks = 60;
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ChatHistoryManager.clear();
            CharacterNameResolver.clear();
        });

        // Register BOTH CHAT and GAME events - some servers send player chat as GAME messages.
        // Deduplication in addRawMessage() prevents double-counting.
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String text = message.getString();
            CharacterNameResolver.extractFromMessage(message, shouldPersistNames(text));
            ChatHistoryManager.addRawMessage(text);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            // action bar (overlay) messages überspringen
            if (overlay) return;
            String text = message.getString();
            CharacterNameResolver.extractFromMessage(message, shouldPersistNames(text));
            ChatHistoryManager.addRawMessage(text);
        });


        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ServerChatState.tick();

            if (pendingSendSCommandTicks > 0) {
                pendingSendSCommandTicks--;
                if (pendingSendSCommandTicks == 0) {
                    pendingSendSCommandTicks = -1;
                    try {
                        if (client.player != null) {
                            client.player.networkHandler.sendChatCommand("s");
                        }
                    } catch (Exception e) {
                    }
                }
            }
            if (pendingSyncPlayersTicks > 0) {
                pendingSyncPlayersTicks--;
                if (pendingSyncPlayersTicks == 0) {
                    pendingSyncPlayersTicks = -1;
                    try {
                        PlayerNameList.syncOnlinePlayers();
                    } catch (Exception e) {
                    }
                }
            }

            if (client.currentScreen != null) {
                wasNameHotkeyPressed = false;
                wasHilfeHotkeyPressed = false;
                wasSprachHotkeyPressed = false;
                wasOfftopicHotkeyPressed = false;
                return;
            }
            long window = client.getWindow().getHandle();

            if (config.characterNameHotkey > 0) {
                boolean pressed = GLFW.glfwGetKey(window, config.characterNameHotkey) == GLFW.GLFW_PRESS;
                if (pressed && !wasNameHotkeyPressed) {
                    config.showCharacterNames = !config.showCharacterNames;
                    config.save();
                    CharacterNameResolver.refreshChatMessages(config.showCharacterNames);
                }
                wasNameHotkeyPressed = pressed;
            }

            if (config.hilfeHotkey > 0) {
                boolean pressed = GLFW.glfwGetKey(window, config.hilfeHotkey) == GLFW.GLFW_PRESS;
                if (pressed && !wasHilfeHotkeyPressed) {
                    boolean wasWriting = ServerChatState.getHelpState() == ServerChatState.ChannelState.CURRENTLY_WRITING;
                    ServerChatState.clickHelp();
                    // chat nur beim aktivieren aufmachen, nicht beim verlassen
                    if (!wasWriting) {
                        client.setScreen(new net.minecraft.client.gui.screen.ChatScreen(""));
                    }
                }
                wasHilfeHotkeyPressed = pressed;
            }

            if (config.sprachHotkey > 0) {
                boolean pressed = GLFW.glfwGetKey(window, config.sprachHotkey) == GLFW.GLFW_PRESS;
                if (pressed && !wasSprachHotkeyPressed) {
                    ServerChatState.clickVoiceRange();
                    // voice aktiviert immer das writing, also chat immer aufmachen
                    client.setScreen(new net.minecraft.client.gui.screen.ChatScreen(""));
                }
                wasSprachHotkeyPressed = pressed;
            }

            if (config.offtopicHotkey > 0) {
                boolean pressed = GLFW.glfwGetKey(window, config.offtopicHotkey) == GLFW.GLFW_PRESS;
                if (pressed && !wasOfftopicHotkeyPressed) {
                    boolean wasWriting = ServerChatState.getOfftopicState() == ServerChatState.ChannelState.CURRENTLY_WRITING;
                    ServerChatState.clickOfftopic();
                    // Open chat only when activating (not when leaving)
                    if (!wasWriting) {
                        client.setScreen(new net.minecraft.client.gui.screen.ChatScreen(""));
                    }
                }
                wasOfftopicHotkeyPressed = pressed;
            }
        });

        ClientSendMessageEvents.ALLOW_COMMAND.register((command) -> {
            ServerChatState.handleOutgoingCommand(command);
            return true;
        });

        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            // interception überspringen wenn ne ausgewählte roleplay option gesendet wird
            if (RoleplayStateManager.isBypassingInterception()) {
                return true;
            }
            if (RoleplayStateManager.isRoleplayModeActive() && !message.startsWith("/")) {
                try {
                    RoleplayCommand.handleRoleplayRequest(message);
                    return false;
                } catch (Exception e) {
                    LOGGER.error("Failed to process roleplay message: " + e.getMessage());
                }
            }
            return true;
        });
        
        
        LOGGER.info("OTTOTALK v" + net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("ottotalk").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?") + " initialized");
    }
    
    private static boolean shouldPersistNames(String text) {
        if (text == null || text.isEmpty()) return false;
        String clean = text.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "");
        if (clean.startsWith("[Sprechen]") || clean.startsWith("[Fl\u00FCstern]") || clean.startsWith("[Rufen]")) return config.nameLearningVoice;
        if (clean.startsWith("[H]") || clean.startsWith("[Hilfe]")) return config.nameLearningHelp;
        if (clean.startsWith("[OOC]") || clean.startsWith("[O]") || clean.startsWith("[Offtopic]")) return config.nameLearningOfftopic;
        // Unknown format \u2014 do NOT persist to avoid unintended learning
        return false;
    }

    public static boolean shouldShowNamesForMessage(String text) {
        if (text == null) return false;
        String clean = text.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "");
        if (clean.startsWith("[Sprechen]") || clean.startsWith("[Fl\u00FCstern]") || clean.startsWith("[Rufen]")) return config.showNamesInVoice;
        if (clean.startsWith("[H]") || clean.startsWith("[Hilfe]")) return config.showNamesInHelp;
        if (clean.startsWith("[OOC]") || clean.startsWith("[O]") || clean.startsWith("[Offtopic]")) return config.showNamesInOfftopic;
        return true;
    }

    public static OttoTalkConfig getConfig() {
        return config;
    }
    
    public static ContextManager getContextManager() {
        return contextManager;
    }
    
    public static AIApiClient getApiClient() {
        return apiClient;
    }
}
