package com.ottotalk.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Zeichnet Texturen per 9-slice damit sie skalieren ohne dass die Ränder verzerren.
 */
public class NineSliceRenderer {

    private static final Identifier FRAME_TEXTURE = new Identifier("ottotalk", "textures/gui/otto_frame.png");
    public static final Identifier OPTION_FRAME = new Identifier("ottotalk", "textures/gui/option_frame.png");

    private static final int FRAME_W = 92;
    private static final int FRAME_H = 91;
    private static final int FRAME_BORDER = 12;

    public static final int OPTION_W = 92;
    public static final int OPTION_H = 91;
    public static final int OPTION_BORDER = 8;

    /**
     * Draw the otto_frame texture using 9-slice.
     */
    public static void draw(DrawContext context, int x, int y, int width, int height) {
        drawSliced(context, FRAME_TEXTURE, x, y, width, height, FRAME_W, FRAME_H, FRAME_BORDER);
    }

    /**
     * Draw the option_frame texture using 9-slice.
     */
    public static void drawOption(DrawContext context, int x, int y, int width, int height) {
        drawSliced(context, OPTION_FRAME, x, y, width, height, OPTION_W, OPTION_H, OPTION_BORDER);
    }

    /**
     * Draw any texture using 9-slice with the given parameters.
     */
    public static void drawSliced(DrawContext context, Identifier texture, int x, int y,
                                   int width, int height, int texW, int texH, int border) {
        int b = border;
        int itw = texW - 2 * b;
        int ith = texH - 2 * b;

        // 4 Corners (fixed size, no tiling)
        drawFixed(context, texture, x, y, b, b, 0, 0, texW, texH);
        drawFixed(context, texture, x + width - b, y, b, b, texW - b, 0, texW, texH);
        drawFixed(context, texture, x, y + height - b, b, b, 0, texH - b, texW, texH);
        drawFixed(context, texture, x + width - b, y + height - b, b, b, texW - b, texH - b, texW, texH);

        // Top edge (tile horizontally)
        tileH(context, texture, x + b, y, width - 2 * b, b, b, 0, itw, texW, texH);
        // Bottom edge (tile horizontally)
        tileH(context, texture, x + b, y + height - b, width - 2 * b, b, b, texH - b, itw, texW, texH);
        // Left edge (tile vertically)
        tileV(context, texture, x, y + b, b, height - 2 * b, 0, b, ith, texW, texH);
        // Right edge (tile vertically)
        tileV(context, texture, x + width - b, y + b, b, height - 2 * b, texW - b, b, ith, texW, texH);

        // Center (tile both directions)
        tileCenter(context, texture, x + b, y + b, width - 2 * b, height - 2 * b, b, b, itw, ith, texW, texH);
    }

    // --- Command suggestion 9-tile ---
    private static final Identifier COMMAND_TEXTURE = new Identifier("ottotalk", "textures/gui/command_9_tile.png");
    private static final int CMD_W = 48;
    private static final int CMD_H = 21;
    private static final int CMD_BORDER = 4;

    /**
     * Draw the command_9_tile.png texture using 9-slice.
     */
    public static void drawCommand(DrawContext context, int x, int y, int width, int height) {
        drawSliced(context, COMMAND_TEXTURE, x, y, width, height, CMD_W, CMD_H, CMD_BORDER);
    }

    // --- 3-tile horizontal frame ---
    private static final Identifier THREE_TILE_TEXTURE = new Identifier("ottotalk", "textures/gui/3-tile-frame.png");
    private static final int THREE_TILE_W = 35;
    private static final int THREE_TILE_H = 18;
    private static final int THREE_TILE_CAP = 5;

    /**
     * Draw the 3-tile-frame.png horizontally: left cap, tiled middle, right cap.
     * Height is stretched to match the target height.
     */
    public static void drawThreeTile(DrawContext context, int x, int y, int width, int height) {
        int cap = THREE_TILE_CAP;
        int texW = THREE_TILE_W;
        int texH = THREE_TILE_H;
        // Left cap
        context.drawTexture(THREE_TILE_TEXTURE, x, y, cap, height, 0, 0, cap, texH, texW, texH);
        // Middle (tiled)
        int middleW = width - 2 * cap;
        int tileW = texW - 2 * cap;
        if (middleW > 0 && tileW > 0) {
            int drawn = 0;
            while (drawn < middleW) {
                int drawW = Math.min(tileW, middleW - drawn);
                context.drawTexture(THREE_TILE_TEXTURE, x + cap + drawn, y, drawW, height,
                        cap, 0, drawW, texH, texW, texH);
                drawn += tileW;
            }
        }
        // Right cap
        context.drawTexture(THREE_TILE_TEXTURE, x + width - cap, y, cap, height,
                texW - cap, 0, cap, texH, texW, texH);
    }

    // --- Server 3-tile horizontal frame ---
    private static final Identifier SERVER_THREE_TILE_TEXTURE = new Identifier("ottotalk", "textures/gui/server-3-tile.png");
    private static final int SERVER_THREE_TILE_W = 78;
    private static final int SERVER_THREE_TILE_H = 31;
    private static final int SERVER_THREE_TILE_CAP = 5;

    /**
     * Draw the server-3-tile.png horizontally: left cap, tiled middle, right cap.
     * Height is stretched to match the target height.
     */
    public static void drawServerThreeTile(DrawContext context, int x, int y, int width, int height) {
        int cap = SERVER_THREE_TILE_CAP;
        int texW = SERVER_THREE_TILE_W;
        int texH = SERVER_THREE_TILE_H;
        // Left cap
        context.drawTexture(SERVER_THREE_TILE_TEXTURE, x, y, cap, height, 0, 0, cap, texH, texW, texH);
        // Middle (tiled)
        int middleW = width - 2 * cap;
        int tileW = texW - 2 * cap;
        if (middleW > 0 && tileW > 0) {
            int drawn = 0;
            while (drawn < middleW) {
                int drawW = Math.min(tileW, middleW - drawn);
                context.drawTexture(SERVER_THREE_TILE_TEXTURE, x + cap + drawn, y, drawW, height,
                        cap, 0, drawW, texH, texW, texH);
                drawn += tileW;
            }
        }
        // Right cap
        context.drawTexture(SERVER_THREE_TILE_TEXTURE, x + width - cap, y, cap, height,
                texW - cap, 0, cap, texH, texW, texH);
    }

    /**
     * Draw the server-3-tile.png using full 9-slice (tiles in both directions).
     */
    public static void drawServerNineSlice(DrawContext context, int x, int y, int width, int height) {
        drawSliced(context, SERVER_THREE_TILE_TEXTURE, x, y, width, height,
                SERVER_THREE_TILE_W, SERVER_THREE_TILE_H, SERVER_THREE_TILE_CAP);
    }

    private static void drawFixed(DrawContext context, Identifier tex, int x, int y, int w, int h,
                                    int u, int v, int texW, int texH) {
        if (w <= 0 || h <= 0) return;
        context.drawTexture(tex, x, y, w, h, (float) u, (float) v, w, h, texW, texH);
    }

    private static void tileH(DrawContext context, Identifier tex, int x, int y, int targetW, int h,
                                int u, int v, int tileW, int texW, int texH) {
        if (targetW <= 0 || h <= 0 || tileW <= 0) return;
        int drawn = 0;
        while (drawn < targetW) {
            int drawW = Math.min(tileW, targetW - drawn);
            context.drawTexture(tex, x + drawn, y, drawW, h, (float) u, (float) v, drawW, h, texW, texH);
            drawn += tileW;
        }
    }

    private static void tileV(DrawContext context, Identifier tex, int x, int y, int w, int targetH,
                                int u, int v, int tileH, int texW, int texH) {
        if (w <= 0 || targetH <= 0 || tileH <= 0) return;
        int drawn = 0;
        while (drawn < targetH) {
            int drawH = Math.min(tileH, targetH - drawn);
            context.drawTexture(tex, x, y + drawn, w, drawH, (float) u, (float) v, w, drawH, texW, texH);
            drawn += tileH;
        }
    }

    private static void tileCenter(DrawContext context, Identifier tex, int x, int y, int targetW, int targetH,
                                    int u, int v, int tileW, int tileH, int texW, int texH) {
        if (targetW <= 0 || targetH <= 0 || tileW <= 0 || tileH <= 0) return;
        int drawnY = 0;
        while (drawnY < targetH) {
            int drawH = Math.min(tileH, targetH - drawnY);
            int drawnX = 0;
            while (drawnX < targetW) {
                int drawW = Math.min(tileW, targetW - drawnX);
                context.drawTexture(tex, x + drawnX, y + drawnY, drawW, drawH,
                        (float) u, (float) v, drawW, drawH, texW, texH);
                drawnX += tileW;
            }
            drawnY += tileH;
        }
    }
}
