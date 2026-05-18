package com.ottotalk.map;

import com.ottotalk.OttoTalkClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Zeigt den aktuellen Lehen-Namen aufm HUD in der Nähe der Minimap.
 * Nutzt Fabrics HudRenderCallback also brauchts keine Xaero-Abhängigkeit.
 */
public class XaeroMinimapOverlay {

    // Lade-Anzeige mindestens MIN_LOADER_MS nach dem JOIN zeigen auch wenn der Fetch fix ist
    private static long loaderShowUntilMs = 0;
    private static final long MIN_LOADER_MS = 4000;

    /** Call on server JOIN to arm the minimum-display timer for the loading indicator. */
    public static void onJoin() {
        loaderShowUntilMs = System.currentTimeMillis() + MIN_LOADER_MS;
    }

    // server-3-tile.png: 78x31, caps 12px, same texture as map overlay sidebar rows
    private static final net.minecraft.util.Identifier SERVER_3_TILE =
            new net.minecraft.util.Identifier("ottotalk", "textures/gui/server-3-tile.png");
    private static final int S3T_W = 78, S3T_H = 31, S3T_CAP = 12;

    /**
     * Zeichnet eine horizontale 3-Tile Bar auf der natürlichen Texturhöhe (S3T_H = 31px).
     * Caps sind pixel-perfect die Mitte tilet ohne zu stretchen.
     */
    private static void draw3Tile(DrawContext ctx, int x, int y, int width, float alpha) {
        if (width < S3T_CAP * 2) return;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        // linke Cap volle natürliche Höhe
        ctx.drawTexture(SERVER_3_TILE, x, y, 0, 0, S3T_CAP, S3T_H, S3T_W, S3T_H);
        // Mitte pixel-perfect tilen keine vertikale Skalierung
        int midSrcW = S3T_W - 2 * S3T_CAP;
        int mx = x + S3T_CAP, mEnd = x + width - S3T_CAP;
        for (int cx2 = mx; cx2 < mEnd; ) {
            int dw = Math.min(midSrcW, mEnd - cx2);
            ctx.drawTexture(SERVER_3_TILE, cx2, y, S3T_CAP, 0, dw, S3T_H, S3T_W, S3T_H);
            cx2 += dw;
        }
        // Right cap
        ctx.drawTexture(SERVER_3_TILE, x + width - S3T_CAP, y, S3T_W - S3T_CAP, 0, S3T_CAP, S3T_H, S3T_W, S3T_H);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Register the HUD overlay renderer.
     * Call this from OttoTalkClient.onInitializeClient().
     */
    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!OttoTalkClient.getConfig().mapOverlayEnabled) return;
            renderLehenHUD(drawContext);
        });
    }

    /**
     * Lade-Anzeige aufs HUD rendern.
     * Sichtbar solang Daten lädt ODER innerhalb von MIN_LOADER_MS nach dem JOIN damit man es sieht
     * auch wenn das Internet flott ist und der Fetch durch ist bevor man die Karte aufmacht.
     */
    private static void renderLehenHUD(DrawContext drawContext) {
        long now = System.currentTimeMillis();
        boolean showLoader = MapDataManager.getInstance().isLoading() || now < loaderShowUntilMs;
        if (!showLoader) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        // nicht über offene Screens drüberzeichnen z.B. die Weltkarte selbst
        if (mc.currentScreen != null) return;

        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        float pulse = 0.55f + 0.45f * (float) Math.abs(Math.sin(now / 500.0));
        String text = "Karte lädt...";
        int tw = tr.getWidth(text);
        int barW = tw + 24;
        int barX = sw / 2 - barW / 2;
        int barY = sh / 2 - S3T_H / 2;

        draw3Tile(drawContext, barX, barY, barW, pulse);
        int textX = sw / 2 - tw / 2;
        int textY = barY + (S3T_H - 8) / 2;
        drawContext.drawText(tr, text, textX, textY, 0xFFEEDDBB, true);
    }

}
