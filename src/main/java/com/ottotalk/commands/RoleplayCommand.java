package com.ottotalk.commands;

import com.ottotalk.OttoTalkClient;
import com.ottotalk.gui.RoleplayOverlayScreen;
import com.ottotalk.gui.RoleplayStateManager;
import com.ottotalk.network.AIApiClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import com.ottotalk.context.ChatHistoryManager;

import java.util.List;

public class RoleplayCommand {
    
    public static void register(Object dispatcher) {
    }

    public static void registerAlternative() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("rp")
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        handleRoleplayRequest(message);
                        return 1;
                    })
                )
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("§6[OTTOTALK] §7Verwendung: /rp <nachricht>"));
                    return 1;
                })
            );
        });
        
    }
    
    
    public static void handleRoleplayRequest(String message) {

        // roleplay-modus automatisch aktivieren wenn er aus ist (z.b. bei direktem /rp aufruf)
        if (!RoleplayStateManager.isRoleplayModeActive()) {
            RoleplayStateManager.enableRoleplayMode();
        }

        RoleplayOverlayScreen overlay = RoleplayStateManager.getCurrentOverlay();
        if (overlay == null) {
            return;
        }

        overlay.setLoading();

        String context = RoleplayStateManager.isHistoryEnabled()
                ? ChatHistoryManager.getCheckedContextString() : "";

        AIApiClient apiClient = OttoTalkClient.getApiClient();
        apiClient.generateMedievalOptions(message, context).thenAccept(options -> {
            // muss aufm render thread laufen
            MinecraftClient.getInstance().execute(() -> {
                RoleplayOverlayScreen currentOverlay = RoleplayStateManager.getCurrentOverlay();
                if (currentOverlay != null && RoleplayStateManager.isRoleplayModeActive()) {
                    currentOverlay.setOptions(options);
                }
            });
        }).exceptionally(throwable -> {
            OttoTalkClient.LOGGER.error("Failed to generate roleplay options: " + throwable.getMessage());
            MinecraftClient.getInstance().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    Throwable cause = throwable;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    client.player.sendMessage(Text.literal("§c[OTTOTALK] " + cause.getMessage()), false);
                }
                RoleplayOverlayScreen currentOverlay = RoleplayStateManager.getCurrentOverlay();
                if (currentOverlay != null) {
                    currentOverlay.resetToIdle();
                }
            });
            return null;
        });
    }
}
