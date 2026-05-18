package com.ottotalk.mixins;

import com.ottotalk.OttoTalkClient;
import com.ottotalk.context.PlayerNameList;
import com.ottotalk.gui.ServerChatState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt die vanilla Spieler-Nametags durch nen 3-Zeilen Layout (titel oben klein,
 * rolle in der mitte, account unten klein - je nachdem was in der config an ist).
 * Bleibt durch Blöcke sichtbar wie das original. Nur auf Ottonien aktiv wenn showCharacterNames an ist.
 */
@Mixin(EntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void ottotalk_renderCustomNametag(Entity entity, Text text,
                                               MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                               int light, CallbackInfo ci) {
        // nur für Spieler-Entities
        if (!(entity instanceof PlayerEntity player)) return;
        com.ottotalk.config.OttoTalkConfig cfg = OttoTalkClient.getConfig();
        if (!cfg.showCharacterNames) return;
        if (!ServerChatState.isOnOttonien()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        // beim eigenen Spieler in First Person nicht rendern
        if (player == client.player && client.options.getPerspective().isFirstPerson()) return;

        String accountName = player.getGameProfile().getName();
        if (accountName == null || accountName.isEmpty()) return;

        // sicherstellen dass der Spieler in der Persistent List ist für Late Joiner
        PlayerNameList.ensurePlayer(accountName);

        boolean isLocalPlayer = (player == client.player);
        String characterName;
        String characterTitle;
        if (isLocalPlayer) {
            // für den eigenen Spieler halt die lokal konfigurierte Role/Title nutzen
            characterName  = cfg.characterRole  != null && !cfg.characterRole.isEmpty()
                    ? cfg.characterRole  : PlayerNameList.getCharacterName(accountName);
            characterTitle = cfg.characterTitle != null && !cfg.characterTitle.isEmpty()
                    ? cfg.characterTitle : PlayerNameList.getCharacterTitle(accountName);
        } else {
            characterName  = PlayerNameList.getCharacterName(accountName);
            characterTitle = PlayerNameList.getCharacterTitle(accountName);
        }

        // original Display Color aus dem Text rausziehen kommt vom Server
        int displayColor = 0xFFFFFFFF;
        Style style = text.getStyle();
        if (style != null) {
            TextColor tc = style.getColor();
            if (tc != null) displayColor = 0xFF000000 | tc.getRgb();
        }

        // Ottonien Display Colors aus der Config
        int nameRgb      = cfg.colorName;
        int charColor    = 0xFF000000 | nameRgb;
        int accColor     = (displayColor & 0x00FFFFFF) | 0xAA000000;
        int charColorST  = (nameRgb & 0x00FFFFFF) | 0x20000000;
        int accColorST   = (displayColor & 0x00FFFFFF) | 0x20000000;

        // Title: use stored server color mapped through cfg overrides (works for all players incl. local)
        int storedTitleRgb = PlayerNameList.getCharacterTitleColor(accountName);
        int titleRgb     = cfg.mapTitleColor(storedTitleRgb);
        int titleColor   = (titleRgb & 0x00FFFFFF) | 0xCC000000;
        int titleColorST = (titleRgb & 0x00FFFFFF) | 0x20000000;

        TextRenderer textRenderer = client.textRenderer;
        float bgOpacity = client.options.getTextBackgroundOpacity(0.25f);
        int bgColor = (int)(bgOpacity * 255.0f) << 24;

        boolean showTitle   = cfg.showTitleInNametag && characterTitle != null && !characterTitle.isEmpty();
        boolean showRole    = cfg.showRolenameInNametag;
        boolean showAccount = cfg.showAccountnameInNametag;

        // Dynamic bottom-anchored layout, equal 2px gaps, bottom of block fixed at Y_ANCHOR.
        // Positive y = toward player body; negative y = away from player body.
        // Line heights: full-scale text = 9px, 0.75-scale text ~ 7px.
        final int GAP      = 2;
        final int FULL_H   = 9;   // role name at 1x scale
        final int SMALL_H  = 7;   // title/account at 0.75 scale (ceil(9*0.75))
        final int Y_ANCHOR = 0;   // bottom anchor at vanilla nametag origin (entity.getHeight()+0.5)

        int curY = Y_ANCHOR;
        int accountTY = Integer.MIN_VALUE;
        if (showAccount) {
            accountTY = curY;
            int nextH = showRole ? FULL_H : (showTitle ? SMALL_H : 0);
            curY = curY - GAP - nextH;
        }
        int roleY = 0;
        if (showRole) {
            roleY = curY;
            int nextH = showTitle ? SMALL_H : 0;
            curY = curY - GAP - nextH;
        }
        int titleTY = Integer.MIN_VALUE;
        if (showTitle) {
            titleTY = curY;
        }

        matrices.push();

        float yOffset = entity.getHeight() + 0.5f;
        matrices.translate(0.0, yOffset, 0.0);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // ---- Rollenname Hauptzeile ----
        if (showRole) {
            float charX = -textRenderer.getWidth(characterName) / 2.0f;
            textRenderer.draw(characterName, charX, roleY, charColorST, false, matrix, vertexConsumers,
                    TextRenderer.TextLayerType.SEE_THROUGH, bgColor, light);
            textRenderer.draw(characterName, charX, roleY, charColor, false, matrix, vertexConsumers,
                    TextRenderer.TextLayerType.NORMAL, bgColor, light);
        }

        // ---- Titel oben klein ----
        if (showTitle) {
            matrices.push();
            matrices.translate(0, titleTY, 0);
            matrices.scale(0.75f, 0.75f, 0.75f);
            Matrix4f titleMatrix = matrices.peek().getPositionMatrix();
            float titleX = -textRenderer.getWidth(characterTitle) / 2.0f;
            textRenderer.draw(characterTitle, titleX, 0, titleColorST, false, titleMatrix, vertexConsumers,
                    TextRenderer.TextLayerType.SEE_THROUGH, bgColor, light);
            textRenderer.draw(characterTitle, titleX, 0, titleColor, false, titleMatrix, vertexConsumers,
                    TextRenderer.TextLayerType.NORMAL, bgColor, light);
            matrices.pop();
        }

        // ---- Account-Name unten klein ----
        if (showAccount) {
            matrices.push();
            matrices.translate(0, accountTY, 0);
            matrices.scale(0.75f, 0.75f, 0.75f);
            Matrix4f accMatrix = matrices.peek().getPositionMatrix();
            float accX = -textRenderer.getWidth(accountName) / 2.0f;
            textRenderer.draw(accountName, accX, 0, accColorST, false, accMatrix, vertexConsumers,
                    TextRenderer.TextLayerType.SEE_THROUGH, bgColor, light);
            textRenderer.draw(accountName, accX, 0, accColor, false, accMatrix, vertexConsumers,
                    TextRenderer.TextLayerType.NORMAL, bgColor, light);
            matrices.pop();
        }

        matrices.pop();
        ci.cancel();
    }

}
