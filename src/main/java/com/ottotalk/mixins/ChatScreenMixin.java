package com.ottotalk.mixins;

import com.ottotalk.gui.ChatCheckboxRenderer;
import com.ottotalk.gui.ChatScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injection in ChatScreen.render(): fill() no-op damit der schwarze Balken weg ist,
 * drawsBackground vom chatField aus, und am Ende unsere input_3_slice Textur drauf
 * mit re-rendered text obendrauf.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow protected TextFieldWidget chatField;

    @Inject(method = "init", at = @At("HEAD"))
    private void onInitStart(CallbackInfo ci) {
        ChatScreenHandler.resetGeometry();
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInitDone(CallbackInfo ci) {
        if (this.chatField != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            ChatScreenHandler.setupFromMixin(this.chatField,
                    client.getWindow().getScaledWidth(),
                    client.getWindow().getScaledHeight());
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private void redirectFill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        // no-op das schwarze halbtransparente Input-Background von MC unterdrücken
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void suppressWidgetBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.chatField != null) {
            this.chatField.setDrawsBackground(false);
        }
    }

    /**
     * Fix hover tooltips: redirect ALL getTextStyleAt calls in ChatScreen to use adjusted coords.
     * This fixes hover positions when chat text is shifted right (xShift) or up (yOffset).
     */
    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/ChatHud;getTextStyleAt(DD)Lnet/minecraft/text/Style;"))
    private Style ottotalk_fixAllHover(ChatHud chatHud, double mouseX, double mouseY) {
        return chatHud.getTextStyleAt(
                mouseX - ChatCheckboxRenderer.currentXShift,
                mouseY + ChatCheckboxRenderer.currentYOffset);
    }
}
