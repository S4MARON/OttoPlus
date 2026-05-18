package com.ottotalk.mixins;

import com.ottotalk.gui.ChatScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.util.math.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin in ChatInputSuggestor$SuggestionWindow: z-level hochpushen damits über dem
 * channel switcher / AI helper liegt, X per matrix translate aufs input field schieben,
 * und scissor cut auf chat hud width.
 */
@Mixin(ChatInputSuggestor.SuggestionWindow.class)
public class CommandSuggestorMixin {

    @Shadow @Final private Rect2i area;

    @Inject(method = "render", at = @At("HEAD"))
    private void ottotalk_pushZ(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);

        // shift damit Alignment mit Input Field X passt
        int inputX = ChatScreenHandler.getInputFieldX();
        if (inputX > 0 && area.getX() < inputX) {
            context.getMatrices().translate(inputX - area.getX(), 0, 0);
        }

        // Breite per Scissor auf Chat-HUD-Width clippen
        int chatHudWidth = MinecraftClient.getInstance().inGameHud.getChatHud().getWidth();
        int left = Math.max(area.getX(), inputX);
        int right = left + chatHudWidth + 8;
        context.enableScissor(left, area.getY() - 2, right, area.getY() + area.getHeight() + 2);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ottotalk_popZ(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        context.disableScissor();
        context.getMatrices().pop();
    }
}
