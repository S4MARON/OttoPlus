package com.ottotalk.map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ottotalk.OttoTalkClient;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Zeichnet die Ottonien Lehen-Polygon-Borders über Xaeros World Map (GuiMap).
 * Nutzt Fabric ScreenEvents + Reflection sonst hätten wir ne Compile-Zeit Abhängigkeit auf Xaero.
 */
public class XaeroWorldMapOverlay {
    private static final String GUIMAP_CLASS = "xaero.map.gui.GuiMap";

    // gecachte Reflection Fields aus GuiMap
    private static Class<?> guiMapClass;
    private static Field cameraXField;
    private static Field cameraZField;
    private static Field scaleField;
    private static Field screenScaleField;
    private static Method getScaleMultiplierMethod;
    private static boolean reflectionFailed = false;

    private static boolean initialized = false;

    // Xaero chunk-discovery reflection (runtime-only, no compile dep).
    // API chain for XaerosWorldMap 1.39.x / MC 1.20.4:
    //   XaeroWorldMapCore.currentSession (static field)
    //     .getMapProcessor()       // WorldMapSession
    //     .getMapWorld()           // MapProcessor
    //     .getCurrentDimension()   // MapWorld
    //     .getLayeredMapRegions()  // MapDimension
    //     .getLeaf(rx, rz, level)  // LayeredRegionManager (null = undiscovered)
    private static boolean  xDiscInit      = false;
    private static boolean  xDiscOk        = false;
    private static java.lang.reflect.Field  xDiscSessionField = null; // XaeroWorldMapCore.currentSession
    private static Method   xDiscGetProcM  = null; // WorldMapSession.getMapProcessor()
    private static Method   xDiscGetWorldM = null; // MapProcessor.getMapWorld()
    private static Method   xDiscGetDimM   = null; // MapWorld.getCurrentDimension()
    private static Method   xDiscGetRegMgrM= null; // MapDimension.getLayeredMapRegions()
    private static Method   xDiscGetLeafM  = null; // LayeredRegionManager.getLeaf(int,int,int)
    private static int      xDiscCacheTick = 0;
    private static final java.util.Map<Long,Boolean> xDiscCache = new java.util.HashMap<>();

    // vorgebauter Border Edge Cache einmal beim Data Load gebaut nicht jedes Frame
    // jedes int[4] = {x1w, z1w, x2w, z2w} in World-Koordinaten beim Build deduped
    private static final java.util.List<int[]> edgeCache = new java.util.ArrayList<>();
    private static boolean edgeCacheValid = false;

    // Triangle Index Cache: Ear-Clip jedes Polygon nur EINMAL dann jedes Frame wiederverwenden
    // gespeichert als flaches int[] von World-Coord Vertex Indices: [i0,i1,i2, i0,i1,i2, ...]
    private static final java.util.Map<RegionData, int[]> triCache = new java.util.IdentityHashMap<>();

    // Sidebar Sorted-List Cache wird nur neu gebaut wenn sortMode oder Daten sich ändern
    private static final java.util.List<RegionData> cachedSortedEntries = new java.util.ArrayList<>();
    private static int cachedSortMode = -1;
    private static int cachedRegionCount = -1;

    // State für die Listen-Sidebar
    private static final Identifier SERVER_3_TILE     = new Identifier("ottotalk", "textures/gui/server-3-tile.png");
    private static final Identifier SERVER_3_TILE_EXT = new Identifier("ottotalk", "textures/gui/server-extender-3-tile.png");
    private static final Identifier EMPTY_BTN      = new Identifier("ottotalk", "textures/gui/empty.png");
    private static final Identifier CURRENTMODE_BTN = new Identifier("ottotalk", "textures/gui/currentmode.png");
    private static final int EMPTY_BTN_W = 20, EMPTY_BTN_H = 18;
    private static RegionData hoveredListRegion  = null;
    private static RegionData clickedListRegion  = null;
    private static Screen     activeGuiMapScreen = null;
    private static int        listScrollOffset   = 0;
    private static long       clickHighlightTime  = 0;
    // jedes Frame upgedated für den Scroll-Blocking Hit Test
    private static boolean listVisible = false;
    private static int listBoundsX0 = 0, listBoundsX1 = 0, listBoundsY0 = 0, listBoundsY1 = 0;
    // pro Frame gecachter Camera-State für Map Click zu List Selection
    private static double lastCameraX, lastCameraZ, lastScaleX, lastScaleZ, lastHalfW, lastHalfH;
    private static double lastEffScale;

    // Sort mode: 0=A-Z  1=N-S  2=Vassal
    private static int sortMode = 0;
    private static String hoveredVassalGroupKey = null;
    private static final java.util.List<int[]> sortButtonBoxes = new java.util.ArrayList<>();
    // Vassal Grouping Daten im Mode 2 jedes Frame neu gebaut
    private static final java.util.Map<String, java.util.List<RegionData>> vassalGroupMap   = new java.util.LinkedHashMap<>();
    private static final java.util.Map<RegionData, Integer>               groupColorMap      = new java.util.HashMap<>();
    private static final java.util.Map<RegionData, String>                groupKeyOfRegion   = new java.util.HashMap<>();
    // pro Gruppe halbtransparente Tint-Farben ARGB Alpha ~0x28 bis 0x38
    private static final int[] GROUP_TINTS = {
        0x30F5C842, 0x30FF6B6B, 0x306BFFB4, 0x306B9CFF,
        0x30FF9C6B, 0x30C46BFF, 0x306BFF6B, 0x30FF6BEA
    };

    // Map Texture Camera Kalibrierung sorgt dass das Hintergrundbild zur MC-Welt passt
    private static final double calOffsetX = 65;
    private static final double calOffsetZ = -50;
    private static final double calScaleX  = 0.9800;
    private static final double calScaleZ  = 1.0100;

    // Map Background Texturen 3-Layer System
    private static final Identifier MAP_LOWER_TEX            = new Identifier("ottotalk", "textures/map/otto-large_map_lower_layer.png");
    private static final Identifier MAP_LOWER_NODETAILS_TEX  = new Identifier("ottotalk", "textures/map/otto-large_map_lower_layer_nodetails.png");
    private static final Identifier MAP_UPPER_TEX             = new Identifier("ottotalk", "textures/map/otto-large_map_upper_layer.png");
    private static final Identifier MAP_UPPER_HIRES_TEX       = new Identifier("ottotalk", "textures/map/otto-large_map_upper_layer_high_res.png");
    private static final int        UPPER_TILE_COUNT          = 2; // 2x2 Tile Grid fürs Viewport Culling
    private static double mapWorldMinX = -10525;
    private static double mapWorldMaxX = 13559;
    private static double mapWorldMinZ = -7394;
    private static double mapWorldMaxZ = 7108;

    // Texture Calibration Offsets hardcoded nach der In-Game Kalibrierung
    private static final double texOffsetX = -650;
    private static final double texOffsetZ = 150;
    private static final double texScaleX = 0.99;
    private static final double texScaleZ = 0.98;
    private static final double texBrightness = 0.70;

    // Custom Shader für luminance-basierte Alpha dunkle Pixel werden transparent
    private static ShaderProgram mapCompositeShader;
    // Custom Shader für Borders die auf geladene Chunks geclipt werden
    private static ShaderProgram borderLoadedShader;
    // Custom Shader für Borders auf UNgeladenen dunklen Chunks invers vom obigen
    private static ShaderProgram borderUnloadedShader;
    // ob die Map-Texturen schon zur GPU vorgeladen wurden sonst friert das Ding beim ersten Use ein
    private static boolean texturesPreloaded = false;
    // true bis das allererste Map-Open durch ist für Initial Data Fetch + Cache Clear
    private static boolean firstMapOpen = true;
    // im AFTER_INIT auf true wird zurückgesetzt sobald Daten geladen sind steuert das Loading Overlay
    private static boolean loadingOverlayPending = false;
    // Timestamp vom letzten Periodic Data Refresh solang die Karte offen ist Live Player Activity Updates
    private static long lastAutoRefreshMs = 0;
    private static final long AUTO_REFRESH_INTERVAL_MS = 60_000;

    private static final Identifier COMPASS_TEXTURE = new Identifier("ottotalk", "textures/gui/compass.png");

    // Pixel-Art Border Color Palette 8 verschiedene gesättigte Farben jedes Lehen kriegt eine per ID Hash
    private static final int[] BORDER_PALETTE = {
        0xFF5588FF, // blue
        0xFFFF5544, // red
        0xFF44CC44, // green
        0xFFFFCC00, // gold
        0xFFCC44FF, // violet
        0xFF44DDCC, // teal
        0xFFFF8844, // orange
        0xFFFF66AA, // pink
    };

    // Framebuffer Copy Texture fürs Compositing per glCopyTexSubImage2D
    private static int copyTex = -1;
    private static int lastFbW = 0, lastFbH = 0;
    private static final Identifier COPY_TEX_ID = new Identifier("ottotalk", "fb_copy");
    private static boolean copyTexRegistered = false;
    private static boolean shaderFallbackLogged = false;


    public static void register() {
        // Custom Shader registrieren
        net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback.EVENT.register(context -> {
            try {
                context.register(
                    new Identifier("ottotalk", "map_composite"),
                    VertexFormats.POSITION_TEXTURE,
                    program -> {
                        mapCompositeShader = program;
                    }
                );
            } catch (Exception e) {
                OttoTalkClient.LOGGER.warn("[OttoTalk] Failed to register map_composite shader: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            try {
                context.register(
                    new Identifier("ottotalk", "border_loaded"),
                    VertexFormats.POSITION_COLOR,
                    program -> {
                        borderLoadedShader = program;
                    }
                );
            } catch (Exception e) {
                OttoTalkClient.LOGGER.warn("[OttoTalk] Failed to register border_loaded shader: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            try {
                context.register(
                    new Identifier("ottotalk", "border_unloaded"),
                    VertexFormats.POSITION_COLOR,
                    program -> {
                        borderUnloadedShader = program;
                    }
                );
            } catch (Exception e) {
                OttoTalkClient.LOGGER.warn("[OttoTalk] Failed to register border_unloaded shader: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (isXaeroGuiMap(screen)) {
                initReflection(screen);
                if (firstMapOpen) {
                    // allererstes Öffnen in dieser Session: Fresh API Fetch erzwingen
                    // Custom Banner (textures/gui/banners/custom_<Name>.png) sind aus dem Godot Game extrahiert
                    // und im JAR gebundlet kein Runtime Download nötig
                    // bannerExistsCache ist beim JVM Start eh leer neue Banner brauchen
                    // nen neues JAR Build nach der Godot Extraction
                    firstMapOpen = false;
                    MapDataManager.getInstance().fetchData();
                } else {
                    // folgende Opens: nur neu fetchen wenn die Daten alt sind 5-Min Interval
                    MapDataManager.getInstance().refreshIfNeeded();
                }
                loadingOverlayPending = true;
                // alle Map-Texturen einmal vorladen sonst friert das mit nem GL Upload mittendrin ein
                if (!texturesPreloaded) {
                    texturesPreloaded = true;
                    client.getTextureManager().getTexture(MAP_LOWER_TEX);
                    client.getTextureManager().getTexture(MAP_LOWER_NODETAILS_TEX);
                    client.getTextureManager().getTexture(MAP_UPPER_TEX);
                    client.getTextureManager().getTexture(MAP_UPPER_HIRES_TEX);
                }
                ScreenEvents.beforeRender(screen).register((screen2, drawContext, mouseX, mouseY, delta) -> {
                    if (OttoTalkClient.getConfig().mapOverlayEnabled) hideXaeroWaypoints();
                });
                ScreenEvents.afterRender(screen).register((screen2, drawContext, mouseX, mouseY, delta) -> {
                    if (OttoTalkClient.getConfig().mapOverlayEnabled) restoreXaeroWaypoints();
                    renderOverlay(screen2, drawContext, mouseX, mouseY, delta);
                });
                ScreenMouseEvents.beforeMouseClick(screen).register((screen2, mouseX, mouseY, button) -> {
                    handleListClick(screen2, (int)mouseX, (int)mouseY);
                });
                ScreenMouseEvents.allowMouseScroll(screen).register((screen2, mouseX, mouseY, horiz, vert) -> {
                    if (listVisible && mouseX >= listBoundsX0 && mouseX <= listBoundsX1
                                    && mouseY >= listBoundsY0 && mouseY <= listBoundsY1) {
                        listScrollOffset = Math.max(0, listScrollOffset - (int)Math.signum(vert));
                        return false; // Scroll selbst behalten nicht an Xaero weiterleiten
                    }
                    return true;
                });
            }
        });
    }

    private static boolean isXaeroGuiMap(Screen screen) {
        if (screen == null) return false;
        try {
            String className = screen.getClass().getName();
            return className.equals(GUIMAP_CLASS) || className.startsWith(GUIMAP_CLASS + "$");
        } catch (Exception e) {
            return false;
        }
    }

    private static void initReflection(Screen screen) {
        if (initialized || reflectionFailed) return;
        try {
            guiMapClass = screen.getClass();
            while (guiMapClass != null && !guiMapClass.getName().equals(GUIMAP_CLASS)) {
                guiMapClass = guiMapClass.getSuperclass();
            }
            if (guiMapClass == null) guiMapClass = screen.getClass();

            cameraXField = guiMapClass.getDeclaredField("cameraX");
            cameraXField.setAccessible(true);
            cameraZField = guiMapClass.getDeclaredField("cameraZ");
            cameraZField.setAccessible(true);
            scaleField = guiMapClass.getDeclaredField("scale");
            scaleField.setAccessible(true);
            screenScaleField = guiMapClass.getDeclaredField("screenScale");
            screenScaleField.setAccessible(true);
            try {
                getScaleMultiplierMethod = guiMapClass.getDeclaredMethod("getScaleMultiplier");
                getScaleMultiplierMethod.setAccessible(true);
            } catch (Exception e2) {
                OttoTalkClient.LOGGER.warn("[OttonienMap] getScaleMultiplier method not found");
            }

            initialized = true;
        } catch (Exception e) {
            reflectionFailed = true;
            OttoTalkClient.LOGGER.error("[OttonienMap] Failed to init GuiMap reflection", e);
        }
    }

    private static void renderOverlay(Screen screen, DrawContext drawContext, int mouseX, int mouseY, float delta) {
        if (reflectionFailed || !initialized) return;
        if (!OttoTalkClient.getConfig().mapOverlayEnabled) return;

        MapDataManager manager = MapDataManager.getInstance();
        if (!manager.isDataLoaded()) {
            renderLoadingOverlay(drawContext, screen.width, screen.height);
            return;
        }
        loadingOverlayPending = false;
        // periodischer Live Refresh für player_gathering solang die Karte offen ist
        long now = System.currentTimeMillis();
        if (now - lastAutoRefreshMs > AUTO_REFRESH_INTERVAL_MS) {
            lastAutoRefreshMs = now;
            manager.refreshIfNeeded();
        }

        try {
            double cameraX = cameraXField.getDouble(screen);
            double cameraZ = cameraZField.getDouble(screen);
            double scale = scaleField.getDouble(screen);
            double screenScale = screenScaleField.getDouble(screen);

            int screenWidth = screen.width;
            int screenHeight = screen.height;
            double halfW = screenWidth / 2.0;
            double halfH = screenHeight / 2.0;

            double scaleMultiplier = 1.0;
            if (getScaleMultiplierMethod != null) {
                try {
                    Object result = getScaleMultiplierMethod.invoke(screen);
                    if (result instanceof Number) scaleMultiplier = ((Number) result).doubleValue();
                } catch (Exception ignored) {}
            }

            // Xaero rendert die Karte mit: scale * scaleMultiplier / screenScale
            double effScale = (scale * scaleMultiplier) / screenScale;

            activeGuiMapScreen = screen;

            // Camera State cachen für den Click Handler Screen zu World Umrechnung
            double scX = effScale; // effScale is symmetric for X and Z here
            lastCameraX = cameraX;
            lastCameraZ = cameraZ;
            lastScaleX  = effScale;
            lastScaleZ  = effScale;
            lastHalfW   = halfW;
            lastHalfH   = halfH;
            lastEffScale = effScale;

            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer textRenderer = client.textRenderer;

            // --- shared Fade/Brightness Werte vorberechnen ---
            float lowerAlpha   = (float) Math.max(0.0, Math.min(1.0, 1.0 - (effScale - 0.50) / 0.20));
            float overallAlpha = (float) Math.max(0.0, Math.min(1.0, 1.0 - (effScale - 5.4)  / 0.4));
            float skyBr        = (client.world != null) ? client.world.getSkyBrightness(1.0f) : 1.0f;
            float mapBrightness = (float) texBrightness * (0.3f + 0.7f * skyBr) * lowerAlpha;
            // Wappen auf der Karte: voll ab effScale>=0.10 fadet bis 0 bei 0.06
            float wappenAlpha  = (float) Math.max(0.0, Math.min(1.0, (effScale - 0.06) / 0.04)) * overallAlpha;
            // Sidebar Liste fadet rein unter effScale 0.09 voll bei 0.05
            float listAlpha    = (float) Math.max(0.0, Math.min(1.0, (0.09 - effScale) / 0.04)) * overallAlpha;

            // sichtbare World-Bounds erweitert um Offset/Scale
            double worldW = screenWidth / effScale;
            double worldH = screenHeight / effScale;
            double margin = 2000;
            double minX = cameraX - worldW / 2 - margin;
            double maxX = cameraX + worldW / 2 + margin;
            double minZ = cameraZ - worldH / 2 - margin;
            double maxZ = cameraZ + worldH / 2 + margin;

            List<RegionData> visibleRegions = manager.getRegionsInBounds(minX, minZ, maxX, maxZ);
            if (visibleRegions.isEmpty()) return;

            // === unloaded schwarze Bereiche mit der Map-Textur füllen Xaero bleibt zu 100% intakt ===
            // im Höhlen/Untertage-Modus skippen Sky nicht sichtbar überm Spieler = unter Dach/Block
            boolean playerUnderground = (client.player != null && client.world != null
                    && !client.world.isSkyVisible(client.player.getBlockPos()));
            if (!playerUnderground) {
                MinecraftClient mc = MinecraftClient.getInstance();
                int fbW = mc.getWindow().getFramebufferWidth();
                int fbH = mc.getWindow().getFramebufferHeight();

                // geladene Chunks beim Rauszoomen verstecken Screen dunkel malen dann behandelt
                // der Luminance Shader den ganzen Viewport als "unloaded" und die Map-Textur
                // deckt alles ab fadet rein zwischen effScale 0.08 und 0.04
                boolean hideChunks = OttoTalkClient.getConfig().hideChunksOnZoomOut;
                if (hideChunks && effScale < 0.08) {
                    float fade = (float) Math.max(0.0, Math.min(1.0, (0.08 - effScale) / 0.04));
                    int alpha = (int)(fade * 255);
                    drawContext.fill(0, 0, screenWidth, screenHeight, (alpha << 24));
                }

                // Schritt 1: den aktuellen Screen Xaeros komplettes Rendering in ne Textur kopieren
                // diese Copy nutzt der Shader um dunkle/unloaded Pixel zu erkennen
                ensureCopyTex(fbW, fbH);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getFramebuffer().fbo);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTex);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, fbW, fbH);

                // Schritt 2: Map-Textur mit dem Dual-Sampler Shader zeichnen
                // KEIN Screen Clear! Shader gibt alpha=1 nur dort wo der Screen dunkel ist unloaded
                // Terrain UI Marker Tooltips alles unangetastet
                // Map-Textur hat ihre eigene Kalibrierung damit das Bild richtig sitzt
                double adjCamX = (cameraX - calOffsetX) / calScaleX;
                double adjCamZ = (cameraZ - calOffsetZ) / calScaleZ;
                double adjScaleX = effScale * calScaleX;
                double adjScaleZ = effScale * calScaleZ;
                // DetailBlend voller Detail zu nodetails wenn man rauszoomt 0.05 bis 0.15 fadet früh
                float detailBlend = (float) Math.max(0.0, Math.min(1.0, 1.0 - (effScale - 0.05) / 0.10));
                // Stage 1 Upper Layer fadet raus 0.30 bis 0.50
                float upperAlpha  = (float) Math.max(0.0, Math.min(1.0, 1.0 - (effScale - 0.30) / 0.20));
                // lowerAlpha und overallAlpha wurden weiter oben im äusseren Scope deklariert
                // Upper Layer wird separat als Tiles gerendert LOD + Viewport Culling
                drawMapOverUnloaded(drawContext, adjCamX, adjCamZ, adjScaleX, adjScaleZ, halfW, halfH, detailBlend, 0.0f, lowerAlpha, overallAlpha);
                drawUpperLayerTiled(drawContext, adjCamX, adjCamZ, adjScaleX, adjScaleZ, halfW, halfH, upperAlpha, effScale);

                // Schritt 3: Xaeros UI Widgets oben drüber wieder rendern Compass skippen der wird ersetzt
                for (net.minecraft.client.gui.Element child : screen.children()) {
                    if (child instanceof net.minecraft.client.gui.Drawable drawable) {
                        String cn = child.getClass().getSimpleName().toLowerCase();
                        if (cn.contains("compass")) continue;
                        drawable.render(drawContext, mouseX, mouseY, delta);
                    }
                }

                // Schritt 4: Waypoint Marker oben drüber als eigener Layer rendern Shader trifft die nicht
                // Xaeros Waypoints wurden im beforeRender versteckt und im afterRender vor diesem Call wiederhergestellt
                redrawWaypointMarkers(drawContext, cameraX, cameraZ, effScale, halfW, halfH, mouseX, mouseY);

                // Schritt 5: eigener Compass verdeckt Xaeros Top-Right Compass
                renderCustomCompass(drawContext, screenWidth);
            }

            // POST-SHADER Borders:
            //   border_loaded   + weiss  zu nur auf entdeckten Chunks hell im COPY_TEX_ID
            //   border_unloaded + braun  zu nur auf unentdeckten Chunks dunkel im COPY_TEX_ID
            // Fallback wenn keine Shader: Single Semi-Transparent Pass
            int wAlpha = (int)(Math.max(0.05f, Math.min(0.20f, (float)(effScale / 0.09) * 0.20f)) * 255);
            if (borderLoadedShader != null && borderUnloadedShader != null) {
                renderAllBordersDoublePass(drawContext, cameraX, cameraZ, effScale, effScale, halfW, halfH,
                                           (wAlpha << 24) | 0x00FFFFFF, 0x803A2008);
            } else {
                renderBordersImmediate(drawContext.getMatrices().peek().getPositionMatrix(),
                                       (wAlpha << 24) | 0x00FFFFFF,
                                       cameraX, cameraZ, effScale, effScale, halfW, halfH);
            }

            // Hover Fill weisser Glow auf gehoverten/geklickten Lehen
            renderHoverFill(drawContext, visibleRegions, cameraX, cameraZ, effScale, effScale, halfW, halfH, effScale, mouseX, mouseY);

            // Live Player Activity Glow VOR den Wappen gerendert damit er dahinter erscheint
            renderPlayerActivity(drawContext, visibleRegions, cameraX, cameraZ, effScale, halfW, halfH, overallAlpha);

            // Lehen-Wappen + Labels auf der Karte fadet bei niedrigem Zoom wird durch Sidebar ersetzt
            renderLehenWappen(drawContext, visibleRegions, cameraX, cameraZ, effScale, halfW, halfH,
                              screenWidth, screenHeight, mouseX, mouseY, wappenAlpha, mapBrightness);

            // Sidebar Lehen-Liste sichtbar wenn weit rausgezoomt
            if (listAlpha > 0f) {
                renderLehenList(drawContext, screen, visibleRegions, screenWidth, screenHeight,
                                listAlpha, cameraX, cameraZ, effScale, halfW, halfH, mouseX, mouseY);
            }

            // Compass HUD Wappen zeigt die Lehen-Info in Bildmitte links neben dem Compass wenn reingezoomt
            if (effScale > 0.5) {
                renderCompassWappen(drawContext, visibleRegions, cameraX, cameraZ, screenWidth);
            }


        } catch (Exception e) {
            OttoTalkClient.LOGGER.warn("[OttoTalk] renderOverlay exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }


    private static void ensureCopyTex(int w, int h) {
        if (copyTex == -1) {
            copyTex = GL11.glGenTextures();
        }
        if (w != lastFbW || h != lastFbH) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            lastFbW = w;
            lastFbH = h;
        }
    }

    /**
     * Map-Textur direkt zeichnen ohne Luminance Shader mit gegebener Alpha.
     * wird genutzt um geladene Chunks transparent zu überdecken beim weit Rauszoomen.
     */
    private static void drawMapDirectOverlay(DrawContext drawContext, double cameraX, double cameraZ,
                                              double scaleX, double scaleZ, double halfW, double halfH,
                                              float alpha, float detailBlend) {
        if (alpha <= 0.0f) return;

        Identifier tex = (detailBlend > 0.5f) ? MAP_LOWER_NODETAILS_TEX : MAP_LOWER_TEX;

        // gleiche Kalibrierung wie bei drawMapOverUnloaded die Upper Tiles müssen pixel-perfect
        // mit dem Lower Layer alignen sonst sieht man am LOD-Übergang die Seams
        double cx = (mapWorldMinX + mapWorldMaxX) / 2.0 + texOffsetX;
        double cz = (mapWorldMinZ + mapWorldMaxZ) / 2.0 + texOffsetZ;
        double hw = ((mapWorldMaxX - mapWorldMinX) / 2.0) * texScaleX;
        double hz = ((mapWorldMaxZ - mapWorldMinZ) / 2.0) * texScaleZ;

        float x1 = (float) ((cx - hw - cameraX) * scaleX + halfW);
        float y1 = (float) ((cz - hz - cameraZ) * scaleZ + halfH);
        float x2 = (float) ((cx + hw - cameraX) * scaleX + halfW);
        float y2 = (float) ((cz + hz - cameraZ) * scaleZ + halfH);

        // Nacht-Helligkeit
        MinecraftClient mc = MinecraftClient.getInstance();
        float sky = (mc.world != null) ? mc.world.getSkyBrightness(1.0f) : 1.0f;
        float nightFactor = (float) texBrightness * (0.3f + 0.7f * sky);

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(nightFactor, nightFactor, nightFactor, alpha);

        Matrix4f matrix = drawContext.getMatrices().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(matrix, x1, y1, 0).texture(0, 0).next();
        buffer.vertex(matrix, x1, y2, 0).texture(0, 1).next();
        buffer.vertex(matrix, x2, y2, 0).texture(1, 1).next();
        buffer.vertex(matrix, x2, y1, 0).texture(1, 0).next();
        tessellator.draw();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /**
     * Map-Textur NUR über dunkle/unloaded Bereiche zeichnen per Dual-Sampler Shader.
     * Sampler0 = Map-Textur Sampler1 = Screen Copy für Darkness Detection.
     * der Screen wird NIE gecleart Xaeros UI Marker und Terrain bleiben 100% intakt.
     */
    private static void drawMapOverUnloaded(DrawContext drawContext, double cameraX, double cameraZ,
                                             double scaleX, double scaleZ, double halfW, double halfH,
                                             float detailBlend, float upperAlpha, float lowerAlpha, float overallAlpha) {
        // Copy Texture Wrapper registrieren einmal
        if (!copyTexRegistered) {
            MinecraftClient.getInstance().getTextureManager().registerTexture(COPY_TEX_ID, new AbstractTexture() {
                @Override
                public void load(ResourceManager manager) {}
                @Override
                public int getGlId() { return copyTex; }
            });
            copyTexRegistered = true;
        }

        double cx = (mapWorldMinX + mapWorldMaxX) / 2.0 + texOffsetX;
        double cz = (mapWorldMinZ + mapWorldMaxZ) / 2.0 + texOffsetZ;
        double hw = ((mapWorldMaxX - mapWorldMinX) / 2.0) * texScaleX;
        double hz = ((mapWorldMaxZ - mapWorldMinZ) / 2.0) * texScaleZ;

        float x1 = (float) ((cx - hw - cameraX) * scaleX + halfW);
        float y1 = (float) ((cz - hz - cameraZ) * scaleZ + halfH);
        float x2 = (float) ((cx + hw - cameraX) * scaleX + halfW);
        float y2 = (float) ((cz + hz - cameraZ) * scaleZ + halfH);

        // Shader muss da sein ohne den geht das Dual-Sampler Masking nicht
        if (mapCompositeShader == null) {
            if (!shaderFallbackLogged) {
                OttoTalkClient.LOGGER.warn("[OttoTalk] map_composite shader NULL, map overlay disabled");
                shaderFallbackLogged = true;
            }
            return;
        }

        RenderSystem.setShader(() -> mapCompositeShader);
        RenderSystem.setShaderTexture(0, MAP_LOWER_TEX);           // Sampler0 = lower (full detail)
        RenderSystem.setShaderTexture(1, COPY_TEX_ID);             // Sampler1 = screen copy
        RenderSystem.setShaderTexture(2, MAP_UPPER_TEX);           // Sampler2 = upper layer
        RenderSystem.setShaderTexture(3, MAP_LOWER_NODETAILS_TEX); // Sampler3 = lower (no detail)

        // FadeScale Ring-Radien im Shader skalieren mit dem Zoom damit der Fade ne konstante Welt-Distanz abdeckt
        net.minecraft.client.gl.GlUniform fadeScaleUniform = mapCompositeShader.getUniform("FadeScale");
        if (fadeScaleUniform != null) {
            fadeScaleUniform.set((float) scaleX);
        }

        // HudGuardFB oben/unten die HUD Zone Coords + Zoom aus dem Compositor ausschliessen
        net.minecraft.client.gl.GlUniform hudGuardUniform = mapCompositeShader.getUniform("HudGuardFB");
        if (hudGuardUniform != null) {
            double scaleFactor = MinecraftClient.getInstance().getWindow().getScaleFactor();
            hudGuardUniform.set((float)(14.0 * scaleFactor));
        }

        // NightBrightness 0.70 Basis-Helligkeit mal Sky Factor 0.70 war im Shader hardcoded jetzt hier
        net.minecraft.client.gl.GlUniform nightUniform = mapCompositeShader.getUniform("NightBrightness");
        if (nightUniform != null) {
            MinecraftClient mc2 = MinecraftClient.getInstance();
            float skyBrightness = (mc2.world != null) ? mc2.world.getSkyBrightness(1.0f) : 1.0f;
            float nightFactor = (float) texBrightness * (0.3f + 0.7f * skyBrightness);
            nightUniform.set(nightFactor);
        }

        net.minecraft.client.gl.GlUniform detailBlendUniform = mapCompositeShader.getUniform("DetailBlend");
        if (detailBlendUniform != null) detailBlendUniform.set(detailBlend);

        net.minecraft.client.gl.GlUniform upperAlphaUniform = mapCompositeShader.getUniform("UpperAlpha");
        if (upperAlphaUniform != null) upperAlphaUniform.set(upperAlpha);

        net.minecraft.client.gl.GlUniform lowerAlphaUniform = mapCompositeShader.getUniform("LowerAlpha");
        if (lowerAlphaUniform != null) lowerAlphaUniform.set(lowerAlpha);

        net.minecraft.client.gl.GlUniform overallAlphaUniform = mapCompositeShader.getUniform("OverallAlpha");
        if (overallAlphaUniform != null) overallAlphaUniform.set(overallAlpha);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc(); // SRC_ALPHA, ONE_MINUS_SRC_ALPHA
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Matrix4f matrix = drawContext.getMatrices().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        // Quad auf den vollen Screen erweitern mit extrapolierten UVs
        // texCoord ausserhalb [0,1] = ausserhalb der Map-Bounds der Shader zeigt da die Fill Color
        int screenW = (int)(halfW * 2);
        int screenH = (int)(halfH * 2);
        float uLeft   = (float)((0       - x1) / (x2 - x1));
        float uRight  = (float)((screenW - x1) / (x2 - x1));
        float vTop    = (float)((0       - y1) / (y2 - y1));
        float vBottom = (float)((screenH - y1) / (y2 - y1));

        buffer.vertex(matrix, 0,       0,       0).texture(uLeft,  vTop   ).next();
        buffer.vertex(matrix, 0,       screenH, 0).texture(uLeft,  vBottom).next();
        buffer.vertex(matrix, screenW, screenH, 0).texture(uRight, vBottom).next();
        buffer.vertex(matrix, screenW, 0,       0).texture(uRight, vTop   ).next();

        tessellator.draw();
        RenderSystem.disableBlend();
    }

    /**
     * Draw the upper map layer in an NxN tile grid for performance.
     * Only tiles that intersect the current viewport are rendered.
     * Switches to high-res texture when zoomed in (effScale > 0.15).
     * Uses standard alpha blend, borders/labels are visible over all terrain.
     */
    private static void drawUpperLayerTiled(DrawContext drawContext, double cameraX, double cameraZ,
                                             double scaleX, double scaleZ, double halfW, double halfH,
                                             float upperAlpha, double effScale) {
        if (upperAlpha <= 0.0f) return;

        Identifier texId = (effScale > 0.15) ? MAP_UPPER_HIRES_TEX : MAP_UPPER_TEX;

        // Textur World Bounds gleiche Kalibrierung wie drawMapOverUnloaded
        double cx = (mapWorldMinX + mapWorldMaxX) / 2.0 + texOffsetX;
        double cz = (mapWorldMinZ + mapWorldMaxZ) / 2.0 + texOffsetZ;
        double hw = ((mapWorldMaxX - mapWorldMinX) / 2.0) * texScaleX;
        double hz = ((mapWorldMaxZ - mapWorldMinZ) / 2.0) * texScaleZ;
        double texMinX = cx - hw, texMaxX = cx + hw;
        double texMinZ = cz - hz, texMaxZ = cz + hz;
        double texW = texMaxX - texMinX;
        double texH = texMaxZ - texMinZ;

        int screenW = (int)(halfW * 2);
        int screenH = (int)(halfH * 2);
        int N = UPPER_TILE_COUNT;

        if (mapCompositeShader == null) return;

        // map_composite Shader nutzen damit Tiles auf dunkle/unloaded Bereiche geclipt werden wie der Lower Layer
        RenderSystem.setShader(() -> mapCompositeShader);
        RenderSystem.setShaderTexture(0, texId);       // Sampler0 = upper tex as "lower" input
        RenderSystem.setShaderTexture(1, COPY_TEX_ID); // Sampler1 = screen copy for dark detection
        RenderSystem.setShaderTexture(2, texId);       // Sampler2 = same (UpperAlpha=0, unused)
        RenderSystem.setShaderTexture(3, texId);       // Sampler3 = same (DetailBlend=1, unused)

        net.minecraft.client.gl.GlUniform u;
        if ((u = mapCompositeShader.getUniform("FadeScale"))    != null) u.set((float) scaleX);
        if ((u = mapCompositeShader.getUniform("DetailBlend"))  != null) u.set(1.0f);
        if ((u = mapCompositeShader.getUniform("UpperAlpha"))   != null) u.set(0.0f);
        if ((u = mapCompositeShader.getUniform("LowerAlpha"))   != null) u.set(1.0f);
        if ((u = mapCompositeShader.getUniform("OverallAlpha")) != null) u.set(upperAlpha);
        if ((u = mapCompositeShader.getUniform("HudGuardFB"))   != null) {
            double sf = MinecraftClient.getInstance().getWindow().getScaleFactor();
            u.set((float)(14.0 * sf));
        }
        if ((u = mapCompositeShader.getUniform("NightBrightness")) != null) {
            MinecraftClient mc2 = MinecraftClient.getInstance();
            float sky = (mc2.world != null) ? mc2.world.getSkyBrightness(1.0f) : 1.0f;
            u.set((float) texBrightness * (0.3f + 0.7f * sky)); // same as lower layer
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Matrix4f matrix = drawContext.getMatrices().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        boolean started = false;

        for (int ty = 0; ty < N; ty++) {
            for (int tx = 0; tx < N; tx++) {
                double tMinX = texMinX + tx       * texW / N;
                double tMaxX = texMinX + (tx + 1) * texW / N;
                double tMinZ = texMinZ + ty       * texH / N;
                double tMaxZ = texMinZ + (ty + 1) * texH / N;

                float sx0 = (float)((tMinX - cameraX) * scaleX + halfW);
                float sx1 = (float)((tMaxX - cameraX) * scaleX + halfW);
                float sy0 = (float)((tMinZ - cameraZ) * scaleZ + halfH);
                float sy1 = (float)((tMaxZ - cameraZ) * scaleZ + halfH);

                // Viewport Culling Tiles die komplett off-Screen sind skippen
                if (sx1 < 0 || sx0 > screenW || sy1 < 0 || sy0 > screenH) continue;

                float u0 = (float) tx       / N;
                float u1 = (float)(tx + 1) / N;
                float v0 = (float) ty       / N;
                float v1 = (float)(ty + 1) / N;

                if (!started) {
                    buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
                    started = true;
                }
                buffer.vertex(matrix, sx0, sy0, 0).texture(u0, v0).next();
                buffer.vertex(matrix, sx0, sy1, 0).texture(u0, v1).next();
                buffer.vertex(matrix, sx1, sy1, 0).texture(u1, v1).next();
                buffer.vertex(matrix, sx1, sy0, 0).texture(u1, v0).next();
            }
        }

        if (started) tessellator.draw();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    private static int worldToScreenX(double worldX, double cameraX, double scale, double halfW) {
        return (int) ((worldX - cameraX) * scale + halfW);
    }

    private static int worldToScreenY(double worldZ, double cameraZ, double scale, double halfH) {
        return (int) ((worldZ - cameraZ) * scale + halfH);
    }

    /** einmaliges Reflection Setup für die Xaero 1.39.x Chunk-Discovery API */
    private static void initXaeroDiscovery() {
        xDiscInit = true;
        try {
            Class<?> coreClass = Class.forName("xaero.map.core.XaeroWorldMapCore");
            xDiscSessionField = coreClass.getField("currentSession"); // static

            // den Rest über ein Live Session-Objekt proben
            Object session = xDiscSessionField.get(null);
            if (session == null) return; // noch nicht initialisiert nächster Call versucht es wieder

            xDiscGetProcM   = session.getClass().getMethod("getMapProcessor");
            Object proc     = xDiscGetProcM.invoke(session);
            if (proc == null) return;

            xDiscGetWorldM  = proc.getClass().getMethod("getMapWorld");
            Object world    = xDiscGetWorldM.invoke(proc);
            if (world == null) return;

            xDiscGetDimM    = world.getClass().getMethod("getCurrentDimension");
            Object dim      = xDiscGetDimM.invoke(world);
            if (dim == null) return;

            xDiscGetRegMgrM = dim.getClass().getMethod("getLayeredMapRegions");
            Object regMgr   = xDiscGetRegMgrM.invoke(dim);
            if (regMgr == null) return;

            xDiscGetLeafM   = regMgr.getClass().getMethod("getLeaf", int.class, int.class, int.class);
            xDiscOk = true;
        } catch (Exception e) {
            xDiscOk = false;
        }
    }

    /**
     * gibt true zurück wenn die Xaero Map-Region in der (worldX, worldZ) liegt existiert
     * also wenn der Spieler schon mindestens einen Chunk dort besucht hat.
     * Fallback bei jedem Reflection-Fehler ist false.
     */
    private static boolean isWorldCoordDiscovered(int worldX, int worldZ) {
        // Xaero Regions sind 512x512 Blocks (2^9)
        int rx = worldX >> 9;
        int rz = worldZ >> 9;
        long key = ((long)(rx + 32768)) << 32 | ((rz + 32768) & 0xFFFFFFFFL);

        // periodischer Flush damit neu entdeckte Chunks sichtbar werden ohne die Karte neu aufzumachen
        if (++xDiscCacheTick > 300) { xDiscCache.clear(); xDiscCacheTick = 0; }

        Boolean cached = xDiscCache.get(key);
        if (cached != null) return cached;

        // neu initialisieren wenn noch nicht passiert oder wenn die Session beim letzten Mal nicht ready war
        if (!xDiscInit || (!xDiscOk && xDiscSessionField != null)) {
            xDiscInit = false; xDiscOk = false;
            initXaeroDiscovery();
        }
        if (!xDiscOk) { xDiscCache.put(key, false); return false; }

        boolean result = false;
        try {
            // jedes Mal neu navigieren damit Welt/Dimension Wechsel berücksichtigt werden
            Object session = xDiscSessionField.get(null);
            Object proc    = (session != null) ? xDiscGetProcM.invoke(session)   : null;
            Object world   = (proc    != null) ? xDiscGetWorldM.invoke(proc)     : null;
            Object dim     = (world   != null) ? xDiscGetDimM.invoke(world)      : null;
            Object regMgr  = (dim     != null) ? xDiscGetRegMgrM.invoke(dim)     : null;
            if (regMgr != null) {
                // getLeaf(x, z, level) Level 0 ist Oberfläche
                Object leaf = xDiscGetLeafM.invoke(regMgr, rx, rz, 0);
                result = (leaf != null);
            }
        } catch (Exception e) {
            // API Mismatch bis zum nächsten Retry deaktivieren
            xDiscOk = false;
        }
        xDiscCache.put(key, result);
        return result;
    }

    /**
     * Baut den globalen Unique-Edge Cache aus ALLEN geladenen Regionen.
     * wird einmal gerufen wenn die Daten zum ersten Mal laden durch nen MapDataManager Reload invalidiert.
     */
    private static void buildEdgeCache() {
        edgeCache.clear();
        MapDataManager mgr = MapDataManager.getInstance();
        if (!mgr.isDataLoaded()) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (RegionData r : mgr.getRegions()) {
            if (!r.hasPolygon()) continue;
            int[][] poly = r.getPolygon();
            int len = poly.length;
            for (int i = 0; i < len; i++) {
                int next = (i + 1) % len;
                int x1 = poly[i][0], z1 = poly[i][1];
                int x2 = poly[next][0], z2 = poly[next][1];
                String key = (x1 < x2 || (x1 == x2 && z1 <= z2))
                    ? x1 + "," + z1 + "," + x2 + "," + z2
                    : x2 + "," + z2 + "," + x1 + "," + z1;
                if (seen.add(key)) edgeCache.add(new int[]{x1, z1, x2, z2});
            }
        }
        edgeCacheValid = true;
    }

    /**
     * Zeichnet alle Border Edges direkt per GameRenderer::getPositionColorProgram.
     * nutzt Oriented Floating-Point Quads einer pro Dash für glatte sub-pixel-genaue Linien.
     * Immediate Tessellator Draw Vertices landen sofort im Framebuffer safe fürs Pre-Capture.
     */
    private static void renderBordersImmediate(Matrix4f matrix, int argb,
                                                double cameraX, double cameraZ,
                                                double scaleX, double scaleZ,
                                                double halfW, double halfH) {
        if (!edgeCacheValid || edgeCache.isEmpty()) buildEdgeCache();
        if (edgeCache.isEmpty()) return;

        double zoom = Math.min(scaleX, scaleZ);
        int lineWidth = zoom >= 2.0 ? 2 : 1;
        float dashOn  = zoom < 0.2f ? 2.5f : zoom < 0.8f ? 4.5f : zoom < 2.0f ? 7.5f : 11.5f;
        float dashOff = zoom < 0.2f ? 2.5f : zoom < 0.8f ? 3.5f : zoom < 2.0f ? 4.5f :  6.5f;
        float hw = lineWidth * 0.5f;
        float period = dashOn + dashOff;
        float sw = (float)(halfW * 2), sh = (float)(halfH * 2);

        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        boolean started = false;

        for (int[] e : edgeCache) {
            float ax = (float)((e[0] - cameraX) * scaleX + halfW);
            float ay = (float)((e[1] - cameraZ) * scaleZ + halfH);
            float bx = (float)((e[2] - cameraX) * scaleX + halfW);
            float by = (float)((e[3] - cameraZ) * scaleZ + halfH);
            if (Math.max(ax, bx) < -lineWidth || Math.min(ax, bx) > sw + lineWidth) continue;
            if (Math.max(ay, by) < -lineWidth || Math.min(ay, by) > sh + lineWidth) continue;

            float edgeDx = bx - ax, edgeDy = by - ay;
            float len = (float)Math.sqrt(edgeDx * edgeDx + edgeDy * edgeDy);
            if (len < 0.3f) continue;

            float px = -edgeDy / len * hw;
            float py =  edgeDx / len * hw;

            if (!started) { buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR); started = true; }

            float t = 0;
            while (t < len) {
                float t1 = Math.min(t + dashOn, len);
                if (t1 - t > 0.1f) {
                    float n0 = t / len, n1 = t1 / len;
                    float x0 = ax + edgeDx * n0, y0 = ay + edgeDy * n0;
                    float x1 = ax + edgeDx * n1, y1 = ay + edgeDy * n1;
                    buf.vertex(matrix, x0 + px, y0 + py, 0).color(r, g, b, a).next();
                    buf.vertex(matrix, x0 - px, y0 - py, 0).color(r, g, b, a).next();
                    buf.vertex(matrix, x1 - px, y1 - py, 0).color(r, g, b, a).next();
                    buf.vertex(matrix, x1 + px, y1 + py, 0).color(r, g, b, a).next();
                }
                t += period;
            }
        }
        if (started) tess.draw();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Rendert Borders in zwei gebatchten Tessellator Passes mit GLSL Shadern.
     * Pass 1 (colorDiscovered) nutzt border_loaded discardet auf dunklen/unentdeckten Pixeln.
     * Pass 2 (colorUndiscovered) nutzt border_unloaded discardet auf hellen/entdeckten Pixeln.
     * jeder Pass = EIN GPU Draw Call für alle Edges kein per-pixel drawContext.fill() Overhead.
     */
    private static void renderAllBordersDoublePass(DrawContext drawContext,
                                                    double cameraX, double cameraZ,
                                                    double scaleX, double scaleZ,
                                                    double halfW, double halfH,
                                                    int colorDiscovered, int colorUndiscovered) {
        if (!edgeCacheValid || edgeCache.isEmpty()) buildEdgeCache();
        if (edgeCache.isEmpty()) return;

        double zoom = Math.min(scaleX, scaleZ);
        int lineWidth = zoom >= 2.0 ? 2 : 1;
        // Dash Längen in Screen Pixeln kontinuierlich fürs Oriented-Quad Rendering
        float dashOn  = zoom < 0.2f ? 2.5f : zoom < 0.8f ? 4.5f : zoom < 2.0f ? 7.5f  : 11.5f;
        float dashOff = zoom < 0.2f ? 2.5f : zoom < 0.8f ? 3.5f : zoom < 2.0f ? 4.5f  : 6.5f;
        float sw = (float)(halfW * 2), sh = (float)(halfH * 2);
        Matrix4f matrix = drawContext.getMatrices().peek().getPositionMatrix();

        int fbW = MinecraftClient.getInstance().getWindow().getFramebufferWidth();
        int fbH = MinecraftClient.getInstance().getWindow().getFramebufferHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Sampler0 = Screen Copy für die Luminance Detection
        RenderSystem.setShaderTexture(0, COPY_TEX_ID);

        if (colorDiscovered != 0 && borderLoadedShader != null)
            drawBorderBatch(matrix, borderLoadedShader, colorDiscovered, fbW, fbH,
                            lineWidth, dashOn, dashOff, sw, sh,
                            cameraX, cameraZ, scaleX, scaleZ, halfW, halfH);

        if (colorUndiscovered != 0 && borderUnloadedShader != null)
            drawBorderBatch(matrix, borderUnloadedShader, colorUndiscovered, fbW, fbH,
                            lineWidth, dashOn, dashOff, sw, sh,
                            cameraX, cameraZ, scaleX, scaleZ, halfW, halfH);

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Single batched GPU draw call for one border color pass using the given shader. */
    private static void drawBorderBatch(Matrix4f matrix, ShaderProgram shader, int argb,
                                         int fbW, int fbH,
                                         int lineWidth, float dashOn, float dashOff,
                                         float sw, float sh,
                                         double cameraX, double cameraZ,
                                         double scaleX, double scaleZ,
                                         double halfW, double halfH) {
        net.minecraft.client.gl.Uniform sizeU = shader.getUniform("ScreenSize");
        if (sizeU != null) sizeU.set((float) fbW, (float) fbH);
        RenderSystem.setShader(() -> shader);
        RenderSystem.disableCull();

        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        boolean started = false;

        float hw = lineWidth * 0.5f; // half line-width for perpendicular offset
        float period = dashOn + dashOff;

        for (int[] e : edgeCache) {
            float ax = (float)((e[0] - cameraX) * scaleX + halfW);
            float ay = (float)((e[1] - cameraZ) * scaleZ + halfH);
            float bx = (float)((e[2] - cameraX) * scaleX + halfW);
            float by = (float)((e[3] - cameraZ) * scaleZ + halfH);
            if (Math.max(ax, bx) < -lineWidth || Math.min(ax, bx) > sw + lineWidth) continue;
            if (Math.max(ay, by) < -lineWidth || Math.min(ay, by) > sh + lineWidth) continue;

            float edgeDx = bx - ax, edgeDy = by - ay;
            float len = (float)Math.sqrt(edgeDx * edgeDx + edgeDy * edgeDy);
            if (len < 0.3f) continue;

            // perpendikulärer Einheitsvektor skaliert mit Half-Width
            float px = -edgeDy / len * hw;
            float py =  edgeDx / len * hw;

            if (!started) { buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR); started = true; }

            // Oriented Dash Quads entlang der Edge in kontinuierlichen Float-Koordinaten
            float t = 0;
            while (t < len) {
                float t1 = Math.min(t + dashOn, len);
                if (t1 - t > 0.1f) {
                    float n0 = t  / len, n1 = t1 / len;
                    float x0 = ax + edgeDx * n0, y0 = ay + edgeDy * n0;
                    float x1 = ax + edgeDx * n1, y1 = ay + edgeDy * n1;
                    buf.vertex(matrix, x0 + px, y0 + py, 0).color(r, g, b, a).next();
                    buf.vertex(matrix, x0 - px, y0 - py, 0).color(r, g, b, a).next();
                    buf.vertex(matrix, x1 - px, y1 - py, 0).color(r, g, b, a).next();
                    buf.vertex(matrix, x1 + px, y1 + py, 0).color(r, g, b, a).next();
                }
                t += period;
            }
        }
        if (started) tess.draw();
        RenderSystem.enableCull();
    }

    /**
     * Rendert Lehen Borders als pixel-perfect Bresenham Linien.
     * colorOverride == 0  zu pro Edge Discovery Color weiss entdeckt / braun unentdeckt.
     * colorOverride != 0  zu dieses ARGB für jede Edge nehmen.
     * Breite 1px bei Zoom < 2.0 2px bei Zoom >= 2.0.
     * Shared Edges sind deduped.
     */
    private static void renderAllBorders(DrawContext drawContext, List<RegionData> regions,
                                          double cameraX, double cameraZ, double scaleX, double scaleZ,
                                          double halfW, double halfH, int colorOverride) {
        double zoom = Math.min(scaleX, scaleZ);
        int lineWidth = zoom >= 2.0 ? 2 : 1;
        // zoom-adaptive Dash on-Pixel off-Pixel
        int dashOn  = zoom < 0.2 ? 3 : zoom < 0.8 ? 5 : zoom < 2.0 ? 8  : 12;
        int dashOff = zoom < 0.2 ? 3 : zoom < 0.8 ? 4 : zoom < 2.0 ? 5  : 7;
        float sw = (float)(halfW * 2), sh = (float)(halfH * 2);
        java.util.Set<String> drawnEdges = new java.util.HashSet<>();

        // pro Edge Discovery Colors nur genutzt wenn colorOverride == 0
        final int COLOR_DISCOVERED   = 0xEEFFFFFF; // bright white
        final int COLOR_UNDISCOVERED = 0xEE6B5040; // desaturated dark brown

        for (RegionData region : regions) {
            if (!region.hasPolygon()) continue;

            int[][] poly = region.getPolygon();
            int len = poly.length;
            for (int i = 0; i < len; i++) {
                int next = (i + 1) % len;
                int x1w = poly[i][0], z1w = poly[i][1];
                int x2w = poly[next][0], z2w = poly[next][1];
                String edgeKey = (x1w < x2w || (x1w == x2w && z1w <= z2w))
                    ? x1w+"_"+z1w+"_"+x2w+"_"+z2w
                    : x2w+"_"+z2w+"_"+x1w+"_"+z1w;
                if (!drawnEdges.add(edgeKey)) continue;

                int ax = (int)((x1w - cameraX) * scaleX + halfW);
                int ay = (int)((z1w - cameraZ) * scaleZ + halfH);
                int bx = (int)((x2w - cameraX) * scaleX + halfW);
                int by = (int)((z2w - cameraZ) * scaleZ + halfH);

                // Edges die komplett off-Screen sind cullen
                if (Math.max(ax, bx) < -lineWidth || Math.min(ax, bx) > sw + lineWidth) continue;
                if (Math.max(ay, by) < -lineWidth || Math.min(ay, by) > sh + lineWidth) continue;

                // Farbe Override nehmen wenns gibt sonst per-Edge Discovery Check
                int color;
                if (colorOverride != 0) {
                    color = colorOverride;
                } else {
                    int midX = (x1w + x2w) >> 1;
                    int midZ = (z1w + z2w) >> 1;
                    color = isWorldCoordDiscovered(midX, midZ) ? COLOR_DISCOVERED : COLOR_UNDISCOVERED;
                }

                drawPixelLine(drawContext, ax, ay, bx, by, lineWidth, color, dashOn, dashOff);
            }
        }
    }

    /** Bresenham pixel-perfect Linie mit zoom-adaptivem Dashing. dashOff=0 zu durchgezogen */
    private static void drawPixelLine(DrawContext ctx, int x0, int y0, int x1, int y1,
                                       int w, int color, int dashOn, int dashOff) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy, pixel = 0, period = dashOn + dashOff;
        for (int step = 0; step < dx + dy + 2; step++) {
            if (pixel % period < dashOn) {
                ctx.fill(x0, y0, x0 + w, y0 + w, color);
            }
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 <  dx) { err += dx; y0 += sy; }
            pixel++;
        }
    }

    /**
     * weisser Radial-Gradient Glow + scharfer pulsierender Ring hinter jedem aktiven Lehen-Wappen.
     * beide Elemente animieren in Grösse und Alpha mehr Spieler = stärkerer Effekt.
     * vor dem Wappen gerendert damit es dahinter sitzt.
     */
    private static void renderPlayerActivity(DrawContext ctx, List<RegionData> regions,
                                              double cameraX, double cameraZ, double effScale,
                                              double halfW, double halfH, float overallAlpha) {
        if (overallAlpha <= 0f) return;
        long t = System.currentTimeMillis();
        // Glow Pulse 0 zu 1 ~900ms Periode
        float pulse     = (float)(Math.sin(t / 900.0 * Math.PI) * 0.5 + 0.5);
        // Ring Pulse Offset um halbe Periode damit der Ring sich ausdehnt während der Glow schrumpft
        float ringPulse = (float)(Math.sin(t / 900.0 * Math.PI + Math.PI * 0.3) * 0.5 + 0.5);

        org.joml.Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(net.minecraft.client.render.GameRenderer::getPositionColorProgram);
        RenderSystem.disableCull();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        final int SEGMENTS = 48;

        for (RegionData r : regions) {
            int count = r.getPlayerGatheringCount();
            if (count <= 0) continue;

            double cx = r.getX(), cz = r.getZ();
            if (r.hasPolygon()) {
                int[][] poly = r.getPolygon();
                double sumX = 0, sumZ = 0;
                for (int[] v : poly) { sumX += v[0]; sumZ += v[1]; }
                cx = sumX / poly.length;
                cz = sumZ / poly.length;
            }
            float sx = (float)(halfW + (cx - cameraX) * effScale);
            float sz = (float)(halfH + (cz - cameraZ) * effScale);

            float intensity = (float)(1.0 - Math.pow(0.7, count));
            float baseR = (float) Math.max(6, Math.min(16, 6 + count * 2 + effScale * 12));

            // 1. radialer Gradient Glow Mitte opak Rand transparent
            float glowR   = baseR * (0.78f + 0.22f * pulse);
            float centerA = intensity * (0.50f + 0.45f * pulse) * overallAlpha; // max ~95%

            buf.begin(net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLE_FAN,
                      net.minecraft.client.render.VertexFormats.POSITION_COLOR);
            buf.vertex(matrix, sx, sz, 0).color(1f, 1f, 1f, centerA).next();
            for (int i = 0; i <= SEGMENTS; i++) {
                float angle = (float)(i * 2.0 * Math.PI / SEGMENTS);
                buf.vertex(matrix, sx + glowR * (float)Math.cos(angle),
                                   sz + glowR * (float)Math.sin(angle), 0)
                   .color(1f, 1f, 1f, 0f).next();
            }
            tess.draw();

            // 2. scharfer weisser Ring TRIANGLE_STRIP kein Blur
            float ringR    = baseR * 1.40f * (0.72f + 0.28f * ringPulse); // larger than glow, pulses
            float ringThick = Math.max(1.5f, 2.5f * intensity);
            float ringA    = intensity * (0.60f + 0.35f * ringPulse) * overallAlpha;
            float outerR   = ringR + ringThick;
            float innerR   = ringR - ringThick;

            buf.begin(net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLE_STRIP,
                      net.minecraft.client.render.VertexFormats.POSITION_COLOR);
            for (int i = 0; i <= SEGMENTS; i++) {
                float angle = (float)(i * 2.0 * Math.PI / SEGMENTS);
                float cosA = (float)Math.cos(angle), sinA = (float)Math.sin(angle);
                buf.vertex(matrix, sx + outerR * cosA, sz + outerR * sinA, 0)
                   .color(1f, 1f, 1f, ringA).next();
                buf.vertex(matrix, sx + innerR * cosA, sz + innerR * sinA, 0)
                   .color(1f, 1f, 1f, ringA).next();
            }
            tess.draw();
        }
        RenderSystem.enableCull();
    }

    /**
     * Hover Fill bei Zoom < 0.3x füllt das gehoverte Lehen-Polygon weiss an den Rändern transparent.
     * nutzt Ear-Clipping Triangulation damit konkave/komplexe Polygone richtig behandelt werden.
     * überall sichtbar auch in unentdeckten Bereichen.
     */
    private static void renderHoverFill(DrawContext drawContext, List<RegionData> visibleRegions,
                                         double cameraX, double cameraZ, double scaleX, double scaleZ,
                                         double halfW, double halfH, double effScale, int mouseX, int mouseY) {
        if (effScale >= 0.3) return;

        double worldMX = (mouseX - halfW) / scaleX + cameraX;
        double worldMZ = (mouseY - halfH) / scaleZ + cameraZ;
        RegionData mouseHovered = null;
        for (RegionData region : visibleRegions) {
            if (region.hasPolygon() && pointInPolygon(worldMX, worldMZ, region.getPolygon())) {
                mouseHovered = region;
                break;
            }
        }

        // die geklickte Region wird zuerst gerendert damit ihr stärkstes Highlight oben bleibt während es ausfadet
        if (clickedListRegion != null && clickedListRegion.hasPolygon()) {
            long elapsed = System.currentTimeMillis() - clickHighlightTime;
            float ca;
            if (elapsed < 3000) {
                ca = 1.0f;
            } else if (elapsed < 3500) {
                ca = 1.0f - (elapsed - 3000) / 500.0f;
            } else {
                ca = 0f;
                clickedListRegion = null;
            }
            if (ca > 0f) {
                fillRegionPoly(drawContext, clickedListRegion, cameraX, cameraZ, scaleX, scaleZ,
                               halfW, halfH, (int)(ca * 0x50) << 24 | 0xFFE0A0);
            }
        }
        // Vassal Modus IMMER alle Group Tints zeichnen gebatcht in EINEN GPU Draw Call
        if (sortMode == 2 && !vassalGroupMap.isEmpty()) {
            java.util.List<RegionData> batchRegions = new java.util.ArrayList<>();
            java.util.List<Integer>    batchColors  = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, java.util.List<RegionData>> ge : vassalGroupMap.entrySet()) {
                boolean isHoveredGroup = ge.getKey().equals(hoveredVassalGroupKey);
                for (RegionData gr : ge.getValue()) {
                    if (gr == clickedListRegion || !gr.hasPolygon()) continue;
                    Integer tint = groupColorMap.get(gr);
                    int col;
                    if (isHoveredGroup && gr == hoveredListRegion)
                        col = (tint != null) ? ((tint & 0x00FFFFFF) | 0x55000000) : 0x55E8D888;
                    else if (isHoveredGroup)
                        col = (tint != null) ? ((tint & 0x00FFFFFF) | 0x38000000) : 0x38C8E070;
                    else
                        col = (tint != null) ? ((tint & 0x00FFFFFF) | 0x20000000) : 0x20C8E070;
                    batchRegions.add(gr);
                    batchColors.add(col);
                }
            }
            if (!batchRegions.isEmpty()) {
                int[] colArr = new int[batchColors.size()];
                for (int i = 0; i < colArr.length; i++) colArr[i] = batchColors.get(i);
                batchFillPolygons(drawContext, batchRegions, colArr,
                        cameraX, cameraZ, scaleX, scaleZ, halfW, halfH);
            }
        } else if (hoveredListRegion != null && hoveredListRegion != clickedListRegion && hoveredListRegion.hasPolygon()) {
            // nicht-Vassal Modus: Standard Einzel-Highlight
            fillRegionPoly(drawContext, hoveredListRegion, cameraX, cameraZ, scaleX, scaleZ,
                           halfW, halfH, 0x28F5E6C8);
        }
        RegionData hovered = mouseHovered;
        if (hovered == null) return;

        int[][] poly = hovered.getPolygon();
        int n = poly.length;
        if (n < 3) return;

        // in Screen Coords umrechnen
        float[] sx = new float[n], sy = new float[n];
        float sumX = 0, sumY = 0, maxDist = 0;
        for (int i = 0; i < n; i++) {
            sx[i] = (float)((poly[i][0] - cameraX) * scaleX + halfW);
            sy[i] = (float)((poly[i][1] - cameraZ) * scaleZ + halfH);
            sumX += sx[i]; sumY += sy[i];
        }
        float centX = sumX / n, centY = sumY / n;
        for (int i = 0; i < n; i++) {
            float d = (float)Math.sqrt((sx[i]-centX)*(sx[i]-centX) + (sy[i]-centY)*(sy[i]-centY));
            if (d > maxDist) maxDist = d;
        }
        if (maxDist < 1f) return;

        float minY = sy[0], maxY = sy[0];
        for (int i = 1; i < n; i++) {
            if (sy[i] < minY) minY = sy[i];
            if (sy[i] > maxY) maxY = sy[i];
        }
        int fillColor = 0x28F5E6C8; // warmes Beige ~16% Flat Alpha

        int startRow = (int) minY, endRow = (int) maxY;
        int numRows  = endRow - startRow + 1;

        // Scanline Fill aufm originalen Polygon Even-Odd Regel ein fill() pro Span pro Row
        for (int ri = 0; ri < numRows; ri++) {
            float yf = (startRow + ri) + 0.5f;
            java.util.ArrayList<Float> xs = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float y1 = sy[i], y2 = sy[j];
                if ((y1 <= yf && y2 > yf) || (y2 <= yf && y1 > yf)) {
                    xs.add(sx[i] + (yf - y1) / (y2 - y1) * (sx[j] - sx[i]));
                }
            }
            java.util.Collections.sort(xs);
            for (int k = 0; k + 1 < xs.size(); k += 2) {
                int x1 = (int) xs.get(k).floatValue();
                int x2 = (int) xs.get(k + 1).floatValue() + 1;
                if (x1 < x2) drawContext.fill(x1, startRow + ri, x2, startRow + ri + 1, fillColor);
            }
        }
    }

    /** Scanline-fills a region's polygon on screen with a solid color. */
    private static void fillRegionPoly(DrawContext drawContext, RegionData region,
                                        double cameraX, double cameraZ, double scaleX, double scaleZ,
                                        double halfW, double halfH, int fillColor) {
        int[][] poly = region.getPolygon();
        int n = poly.length;
        if (n < 3) return;
        float[] sx = new float[n], sy = new float[n];
        for (int i = 0; i < n; i++) {
            sx[i] = (float)((poly[i][0] - cameraX) * scaleX + halfW);
            sy[i] = (float)((poly[i][1] - cameraZ) * scaleZ + halfH);
        }
        float minY = sy[0], maxY = sy[0];
        for (int i = 1; i < n; i++) { if (sy[i] < minY) minY = sy[i]; if (sy[i] > maxY) maxY = sy[i]; }
        int startRow = (int) minY, endRow = (int) maxY;
        for (int ri = 0; ri <= endRow - startRow; ri++) {
            float yf = (startRow + ri) + 0.5f;
            java.util.ArrayList<Float> xs = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float y1 = sy[i], y2 = sy[j];
                if ((y1 <= yf && y2 > yf) || (y2 <= yf && y1 > yf))
                    xs.add(sx[i] + (yf - y1) / (y2 - y1) * (sx[j] - sx[i]));
            }
            java.util.Collections.sort(xs);
            for (int k = 0; k + 1 < xs.size(); k += 2) {
                int x1 = (int) xs.get(k).floatValue();
                int x2 = (int) xs.get(k + 1).floatValue() + 1;
                if (x1 < x2) drawContext.fill(x1, startRow + ri, x2, startRow + ri + 1, fillColor);
            }
        }
    }

    /**
     * Returns cached ear-clipped triangle indices for a region polygon.
     * Each triple [i0,i1,i2] refers to indices in region.getPolygon().
     * Result is a flat int[] of length 3*numTriangles.
     */
    private static int[] getTriangles(RegionData region) {
        int[] cached = triCache.get(region);
        if (cached != null) return cached;
        int[][] poly = region.getPolygon();
        int n = poly.length;
        if (n < 3) { triCache.put(region, new int[0]); return new int[0]; }
        // Detect winding: compute signed area; positive = CCW, negative = CW
        double signedArea = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            signedArea += (double)poly[i][0] * poly[j][1] - (double)poly[j][0] * poly[i][1];
        }
        boolean ccw = signedArea > 0; // if CCW, valid ear has cross > 0; CW means cross < 0

        // Ear-clipping triangulation
        java.util.ArrayList<Integer> idx = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) idx.add(i);
        java.util.ArrayList<Integer> tris = new java.util.ArrayList<>(n * 3);
        int safety = n * n + 10;
        while (idx.size() > 3 && safety-- > 0) {
            int sz = idx.size();
            boolean found = false;
            for (int i = 0; i < sz; i++) {
                int a = idx.get((i - 1 + sz) % sz);
                int b = idx.get(i);
                int c = idx.get((i + 1) % sz);
                double ax = poly[a][0], az = poly[a][1];
                double bx = poly[b][0], bz = poly[b][1];
                double cx = poly[c][0], cz = poly[c][1];
                // Must be convex ear (sign matches polygon winding)
                double cross = (bx - ax) * (cz - az) - (bz - az) * (cx - ax);
                if (ccw ? cross <= 0 : cross >= 0) continue;
                // No other point inside triangle
                boolean ear = true;
                for (int j = 0; j < sz; j++) {
                    if (j == (i - 1 + sz) % sz || j == i || j == (i + 1) % sz) continue;
                    int p = idx.get(j);
                    double px = poly[p][0], pz = poly[p][1];
                    if (pointInTriangle(px, pz, ax, az, bx, bz, cx, cz)) { ear = false; break; }
                }
                if (!ear) continue;
                tris.add(a); tris.add(b); tris.add(c);
                idx.remove(i);
                found = true;
                break;
            }
            if (!found) break; // degenerate polygon
        }
        if (idx.size() == 3) { tris.add(idx.get(0)); tris.add(idx.get(1)); tris.add(idx.get(2)); }
        int[] result = new int[tris.size()];
        for (int i = 0; i < tris.size(); i++) result[i] = tris.get(i);
        triCache.put(region, result);
        return result;
    }

    private static boolean pointInTriangle(double px, double pz,
                                            double ax, double az, double bx, double bz, double cx, double cz) {
        double d1 = (px-bx)*(az-bz)-(ax-bx)*(pz-bz);
        double d2 = (px-cx)*(bz-cz)-(bx-cx)*(pz-cz);
        double d3 = (px-ax)*(cz-az)-(cx-ax)*(pz-az);
        boolean hasNeg = d1<0 || d2<0 || d3<0;
        boolean hasPos = d1>0 || d2>0 || d3>0;
        return !(hasNeg && hasPos);
    }

    /**
     * Batched GPU fill of multiple regions in a single BufferBuilder draw.
     * Uses the position-color shader (GL_TRIANGLES, no texture).
     * entries: list of [region, argbColor] pairs.
     */
    private static void batchFillPolygons(DrawContext drawContext,
                                           java.util.List<RegionData> regions,
                                           int[] colors,
                                           double cameraX, double cameraZ,
                                           double scaleX, double scaleZ,
                                           double halfW, double halfH) {
        if (regions.isEmpty()) return;
        // Pending DrawContext Draw Calls noch flushen bevor wir Raw GL feuern
        drawContext.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f mat = drawContext.getMatrices().peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        boolean anyVerts = false;
        for (int ri = 0; ri < regions.size(); ri++) {
            RegionData region = regions.get(ri);
            int col = colors[ri];
            int a = (col >> 24) & 0xFF;
            int r = (col >> 16) & 0xFF;
            int g = (col >>  8) & 0xFF;
            int b = (col      ) & 0xFF;
            int[][] poly = region.getPolygon();
            int[] tris = getTriangles(region);
            if (tris.length < 3) continue;
            for (int t = 0; t + 2 < tris.length; t += 3) {
                for (int k = 0; k < 3; k++) {
                    int[] pt = poly[tris[t + k]];
                    float sx = (float)((pt[0] - cameraX) * scaleX + halfW);
                    float sy = (float)((pt[1] - cameraZ) * scaleZ + halfH);
                    buf.vertex(mat, sx, sy, 0f).color(r, g, b, a).next();
                    anyVerts = true;
                }
            }
        }
        if (anyVerts) tess.draw();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // server-3-tile.png 78x31 Caps 12px. server-extender-3-tile.png 65x30
    private static final int S3T_W     = 78,  S3T_H     = 31, S3T_CAP = 12;
    private static final int S3T_EXT_W = 65,  S3T_EXT_H = 30;
    private static final int S3T_RENDER_H = 30; // gemeinsame Render-Höhe

    /** Draws a horizontal 3-tile bar: caps + tiled center from server-3-tile.png; extender unused. */
    private static void drawServer3Tile(DrawContext ctx, int x, int y, int width, int renderH, float alpha) {
        if (width < S3T_CAP * 2) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        // linke Cap
        ctx.drawTexture(SERVER_3_TILE, x, y, 0, 0, S3T_CAP, renderH, S3T_W, S3T_H);
        // Mitte den Center-Bereich von server-3-tile.png pixel-perfect tilen
        int midSrcW = S3T_W - 2 * S3T_CAP; // 78 - 24 = 54 px
        int mx = x + S3T_CAP, mEnd = x + width - S3T_CAP;
        for (int cx2 = mx; cx2 < mEnd; ) {
            int dw = Math.min(midSrcW, mEnd - cx2);
            ctx.drawTexture(SERVER_3_TILE, cx2, y, S3T_CAP, 0, dw, renderH, S3T_W, S3T_H);
            cx2 += dw;
        }
        // rechte Cap
        ctx.drawTexture(SERVER_3_TILE, x + width - S3T_CAP, y, S3T_W - S3T_CAP, 0, S3T_CAP, renderH, S3T_W, S3T_H);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // pro Frame Liste Hit-Boxes fürs Click Handling  [x0, y0, x1, y1, regionIndex]
    private static final java.util.List<int[]> listHitBoxes = new java.util.ArrayList<>();

    /**
     * Draws a pixel-art icon centred at (cx, cy) for the given sort mode.
     * mode 0 = A-Z (three descending bars)
     * mode 1 = N-to-S (downward arrow)
     * mode 2 = Vassal (branching tree)
     */
    private static void drawSortIcon(DrawContext ctx, int cx, int cy, int mode, int col, float s) {
        int a1 = Math.max(1, (int)(1 * s)), a2 = Math.max(1, (int)(2 * s));
        int a3 = Math.max(1, (int)(3 * s)), a4 = Math.max(1, (int)(4 * s));
        int a5 = Math.max(1, (int)(5 * s));
        switch (mode) {
            case 0: // Three horizontal bars of decreasing length, "sort" icon
                ctx.fill(cx - a4, cy - a3,        cx + a4, cy - a3 + a1, col);
                ctx.fill(cx - a3, cy,              cx + a3, cy + a1,      col);
                ctx.fill(cx - a2, cy + a3,         cx + a2, cy + a3 + a1, col);
                break;
            case 1: // Downward arrow, compass / N to S
                ctx.fill(cx - a1, cy - a4,         cx + a1, cy + a1,      col);
                ctx.fill(cx - a3, cy + a1,         cx + a3, cy + a1 + a1, col);
                ctx.fill(cx - a2, cy + a1 + a1,    cx + a2, cy + a1*3,    col);
                ctx.fill(cx - a1, cy + a1*3,       cx + a1, cy + a4,      col);
                break;
            case 2: // Branching feudal tree, Vassal hierarchy
                ctx.fill(cx - a1, cy + a2,         cx + a1, cy + a5,      col);
                ctx.fill(cx - a1, cy - a1,         cx + a1, cy + a2,      col);
                ctx.fill(cx - a5, cy - a1,         cx - a2, cy,           col);
                ctx.fill(cx + a2, cy - a1,         cx + a5, cy,           col);
                ctx.fill(cx - a4, cy - a3,         cx - a3, cy - a1,      col);
                ctx.fill(cx + a3, cy - a3,         cx + a4, cy - a1,      col);
                ctx.fill(cx - a1, cy - a3,         cx + a1, cy - a1,      col);
                break;
            case 3: // Pulse/activity: centre dot + 4 radiating dots
                ctx.fill(cx - a1, cy - a1,         cx + a1, cy + a1,      col); // centre
                ctx.fill(cx - a1, cy - a4,         cx + a1, cy - a4 + a1, col); // top
                ctx.fill(cx - a1, cy + a3,         cx + a1, cy + a3 + a1, col); // bottom
                ctx.fill(cx - a4, cy - a1,         cx - a4 + a1, cy + a1, col); // left
                ctx.fill(cx + a3, cy - a1,         cx + a3 + a1, cy + a1, col); // right
                break;
        }
    }

    /** Returns the top-level liege region key for a faction (or own region if unlanded/no lord). */
    private static String vassalGroupKey(FactionData f) {
        String lrr = f.getLordRegionRecursive();
        return (lrr != null && !lrr.isEmpty()) ? lrr : f.getRegion();
    }

    /**
     * Sorts raw entries according to sortMode and (re)builds vassal group maps.
     * Returns the flat ordered list (no nulls). Result is cached until sortMode or list size changes.
     */
    private static java.util.List<RegionData> buildSortedEntries(java.util.List<RegionData> raw) {
        if (sortMode == cachedSortMode && raw.size() == cachedRegionCount && !cachedSortedEntries.isEmpty()) {
            return cachedSortedEntries;
        }
        cachedSortMode = sortMode;
        cachedRegionCount = raw.size();
        cachedSortedEntries.clear();
        java.util.List<RegionData> entries = new java.util.ArrayList<>(raw);
        vassalGroupMap.clear();
        groupColorMap.clear();
        groupKeyOfRegion.clear();
        switch (sortMode) {
            case 1: // N to S (lower Z = more north)
                entries.sort((a, b) -> Integer.compare(a.getZ(), b.getZ()));
                break;
            case 3: // Activity (player_gathering descending, active first)
                entries.sort((a, b) -> Integer.compare(b.getPlayerGatheringCount(), a.getPlayerGatheringCount()));
                break;
            case 2: { // Vassal grouping
                java.util.Map<String, java.util.List<RegionData>> groups = new java.util.LinkedHashMap<>();
                for (RegionData r : entries) {
                    groups.computeIfAbsent(vassalGroupKey(r.getFaction()), k -> new java.util.ArrayList<>()).add(r);
                }
                java.util.List<java.util.Map.Entry<String, java.util.List<RegionData>>> gl = new java.util.ArrayList<>(groups.entrySet());
                gl.sort((a, b) -> {
                    int ra = a.getValue().stream().mapToInt(r -> r.getFaction().getRankPriority()).max().orElse(0);
                    int rb = b.getValue().stream().mapToInt(r -> r.getFaction().getRankPriority()).max().orElse(0);
                    int c = Integer.compare(rb, ra);
                    return c != 0 ? c : a.getKey().compareTo(b.getKey());
                });
                entries.clear();
                int ci = 0;
                for (java.util.Map.Entry<String, java.util.List<RegionData>> ge : gl) {
                    ge.getValue().sort((a, b) -> {
                        int rp = Integer.compare(b.getFaction().getRankPriority(), a.getFaction().getRankPriority());
                        return rp != 0 ? rp : a.getFaction().getName().compareToIgnoreCase(b.getFaction().getName());
                    });
                    int col = GROUP_TINTS[ci % GROUP_TINTS.length];
                    vassalGroupMap.put(ge.getKey(), ge.getValue());
                    for (RegionData r : ge.getValue()) {
                        groupColorMap.put(r, col);
                        groupKeyOfRegion.put(r, ge.getKey());
                        entries.add(r);
                    }
                    ci++;
                }
                break;
            }
            default: // A-Z
                entries.sort((a, b) -> a.getFaction().getName().compareToIgnoreCase(b.getFaction().getName()));
        }
        cachedSortedEntries.addAll(entries);
        return cachedSortedEntries;
    }

    /**
     * Renders the left-side Lehen list sidebar when zoomed far out.
     * Single-column scrollable layout; server-3-tile for each row; scrollbar on right.
     */
    private static void renderLehenList(DrawContext drawContext, Screen screen,
                                         List<RegionData> visibleRegions, int screenWidth, int screenHeight,
                                         float listAlpha, double cameraX, double cameraZ, double effScale,
                                         double halfW, double halfH, int mouseX, int mouseY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        java.util.List<RegionData> raw = new java.util.ArrayList<>();
        for (RegionData r : visibleRegions) { if (r.hasFaction()) raw.add(r); }
        java.util.List<RegionData> entries = buildSortedEntries(raw);

        listHitBoxes.clear();
        sortButtonBoxes.clear();
        hoveredListRegion  = null;
        hoveredVassalGroupKey = null;

        // responsive Scale Referenz = 540px GUI Höhe 1080p @ GUI Scale 2
        // auf [0.80, 1.40] clampen damit das UI bei allen GUI-Scale Settings proportional bleibt
        final float uiScale = Math.max(0.80f, Math.min(1.40f, screenHeight / 540.0f));

        final int SCROLLBAR_W   = Math.max(3, (int)(4  * uiScale));
        final int SCROLLBAR_GAP = Math.max(1, (int)(2  * uiScale));
        final int COL_W         = Math.max(110, (int)(160 * uiScale));
        final int PAD           = Math.max(2,  (int)(3  * uiScale));
        final int ICON_SIZE     = Math.max(14, (int)(20 * uiScale));
        final int BOX_H         = Math.max(22, (int)(S3T_RENDER_H * uiScale));
        final int LIST_TOP      = Math.max(6,  (int)(8  * uiScale));
        final int LIST_X        = (int)(30 * uiScale) + SCROLLBAR_W + SCROLLBAR_GAP;
        final int SCROLLBAR_X   = LIST_X - SCROLLBAR_W - SCROLLBAR_GAP;
        // BTN_W/H bleiben auf native Textur-Grösse sonst liest Scaling die UVs falsch und cropt
        final int BTN_W         = EMPTY_BTN_W;  // 20 px
        final int BTN_H         = EMPTY_BTN_H;  // 18 px
        final int BTN_GAP       = Math.max(1,  (int)(2  * uiScale));
        final int BTN_X         = LIST_X + COL_W + Math.max(3, (int)(4 * uiScale));

        int availH    = screenHeight - LIST_TOP; // fill to screen bottom
        int visCount  = Math.max(1, availH / BOX_H);
        int totalCount = entries.size();

        // Listen-Bounds fürs Scroll-Blocking exposen Sort Buttons inkludiert
        listVisible  = true;
        listBoundsX0 = SCROLLBAR_X;
        listBoundsX1 = BTN_X + BTN_W;
        listBoundsY0 = LIST_TOP;
        listBoundsY1 = LIST_TOP + Math.min(visCount, totalCount) * BOX_H;

        listScrollOffset = Math.max(0, Math.min(listScrollOffset, Math.max(0, totalCount - visCount)));

        // Sort Buttons empty.png als Base currentmode.png als Active-State Overlay
        // plus ein Pixel-Art Icon in jedem Button zentriert
        for (int m = 0; m < 4; m++) {
            int btnY = LIST_TOP + m * (BTN_H + BTN_GAP);
            boolean act = (sortMode == m);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, listAlpha);
            drawContext.drawTexture(EMPTY_BTN, BTN_X, btnY, 0, 0, BTN_W, BTN_H, EMPTY_BTN_W, EMPTY_BTN_H);
            if (act) drawContext.drawTexture(CURRENTMODE_BTN, BTN_X, btnY, 0, 0, BTN_W, BTN_H, EMPTY_BTN_W, EMPTY_BTN_H);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            // Pixel-Art Icon im Button zentriert
            int icx = BTN_X + BTN_W / 2;
            int icy = btnY + BTN_H / 2;
            int icol = applyAlpha(act ? 0xFFFFE0A0 : 0xFF1A0A04, listAlpha); // inactive: near-black for contrast
            drawSortIcon(drawContext, icx, icy, m, icol, uiScale);
            sortButtonBoxes.add(new int[]{BTN_X, btnY, BTN_X + BTN_W, btnY + BTN_H, m});
        }

        if (totalCount > visCount) {
            int trackH = visCount * BOX_H;
            int thumbH = Math.max(12, trackH * visCount / totalCount);
            int thumbY = LIST_TOP + (trackH - thumbH) * listScrollOffset / Math.max(1, totalCount - visCount);
            drawContext.fill(SCROLLBAR_X, LIST_TOP, SCROLLBAR_X + SCROLLBAR_W, LIST_TOP + trackH,
                             applyAlpha(0xFF2A2218, listAlpha));
            drawContext.fill(SCROLLBAR_X, thumbY, SCROLLBAR_X + SCROLLBAR_W, thumbY + thumbH,
                             applyAlpha(0xFFB89060, listAlpha));
        }

        final int GROUP_GAP = 4; // px gap between vassal bundles
        int curY = LIST_TOP;     // dynamic Y tracks group gaps
        for (int vi = 0; vi < visCount; vi++) {
            int i = vi + listScrollOffset;
            if (i >= totalCount || curY >= screenHeight - 8) break;
            RegionData region = entries.get(i);
            FactionData fac   = region.getFaction();

            int x = LIST_X;

            // Gruppen-Gap Vassal Modus dunkler Spacer vor dem ersten Item ner neuen Gruppe
            if (sortMode == 2 && i > 0) {
                String gk  = groupKeyOfRegion.get(region);
                String pgk = groupKeyOfRegion.get(entries.get(i - 1));
                if (gk != null && !gk.equals(pgk)) {
                    drawContext.fill(x, curY, x + COL_W, curY + GROUP_GAP,
                                     applyAlpha(0xFF0D0B09, listAlpha));
                    curY += GROUP_GAP;
                }
            }
            int y = curY;

            // Hover Detection
            boolean itemHovered = mouseX >= x && mouseX <= x + COL_W
                               && mouseY >= y && mouseY <= y + BOX_H;
            if (itemHovered) {
                hoveredListRegion = region;
                if (sortMode == 2) hoveredVassalGroupKey = groupKeyOfRegion.get(region);
            }

            float bgAlpha = listAlpha * (itemHovered || region == clickedListRegion ? 1.0f : 0.75f);

            // Hintergrund erst dunkler Fill dann Tile drüber
            drawContext.fill(x, y, x + COL_W, y + BOX_H, (int)(bgAlpha * 0xB8) << 24 | 0x1E1A12);
            drawServer3Tile(drawContext, x, y, COL_W, BOX_H, bgAlpha);

            // Vassal Group Tint halbtransparenter Farb-Overlay AUF den Container volle Breite
            if (sortMode == 2) {
                Integer tint = groupColorMap.get(region);
                if (tint != null) {
                    // Tint Overlay über volle Breite nach dem Tile gezeichnet damits oben drüber liegt
                    drawContext.fill(x, y, x + COL_W, y + BOX_H, applyAlpha(tint, listAlpha * 0.6f));
                    // 3px opaker linker Streifen damit man die Gruppe klar erkennt
                    int stripeColor = (tint & 0x00FFFFFF) | (applyAlpha(0xFF000000, listAlpha));
                    drawContext.fill(x, y, x + 3, y + BOX_H, stripeColor);
                }
            }

            // Player Activity Pulse weisser Glow Overlay auf aktiven Reihen
            int actCount = region.getPlayerGatheringCount();
            if (actCount > 0) {
                float actPulse = 0.3f + 0.5f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 700.0));
                float actIntensity = (float)(1.0 - Math.pow(0.7, actCount));
                int actAlpha = (int)(actIntensity * actPulse * listAlpha * 80);
                drawContext.fill(x, y, x + COL_W, y + BOX_H, (actAlpha << 24) | 0x00FFFFFF);
                // kleiner pulsierender Dot am rechten Rand
                int dotA = (int)(actIntensity * actPulse * listAlpha * 220);
                int dotX = x + COL_W - 7;
                int dotY = y + (BOX_H - 5) / 2;
                drawContext.fill(dotX, dotY, dotX + 5, dotY + 5, (dotA << 24) | 0x00FFFFFF);
            }

            String banner = fac.getBanner();
            int iconOffX = (sortMode == 2) ? 5 : 0; // Offset rechts vom Streifen
            int iconY = y + (BOX_H - ICON_SIZE) / 2;
            if (banner != null && !banner.equals("kein_wappen")) {
                RenderSystem.setShaderColor(1f, 1f, 1f, listAlpha);
                drawBanner(drawContext, banner, x + PAD + iconOffX, iconY, ICON_SIZE);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                drawEmptyBanner(drawContext, x + PAD + iconOffX, iconY, ICON_SIZE, listAlpha);
            }

            // Text Block vertikal zentriert
            int tx = x + PAD + iconOffX + ICON_SIZE + 4;
            curY += BOX_H;
            String rank  = fac.getRankName();
            String fname = fac.getName();
            String lname = region.getName();
            float rs = 0.60f, fs = 0.78f, ls = 0.60f;
            int totalTH = 0;
            if (rank  != null && !rank.isEmpty())  totalTH += (int)(8 * rs) + 1;
            if (fname != null && !fname.isEmpty()) totalTH += (int)(8 * fs) + 1;
            if (lname != null && !lname.isEmpty()) totalTH += (int)(8 * ls);
            int ty = y + (BOX_H - totalTH) / 2;

            if (rank != null && !rank.isEmpty()) {
                drawContext.getMatrices().push();
                drawContext.getMatrices().translate(tx, ty, 0);
                drawContext.getMatrices().scale(rs, rs, 1f);
                drawContext.drawText(tr, rank, 0, 0, applyAlpha(0xFF999999, listAlpha), false);
                drawContext.getMatrices().pop();
                ty += (int)(8 * rs) + 1;
            }
            if (fname != null && !fname.isEmpty()) {
                drawContext.getMatrices().push();
                drawContext.getMatrices().translate(tx, ty, 0);
                drawContext.getMatrices().scale(fs, fs, 1f);
                drawContext.drawText(tr, fname, 0, 0, applyAlpha(0xFFFFFFFF, listAlpha), false);
                drawContext.getMatrices().pop();
                ty += (int)(8 * fs) + 1;
            }
            if (lname != null && !lname.isEmpty()) {
                drawContext.getMatrices().push();
                drawContext.getMatrices().translate(tx, ty, 0);
                drawContext.getMatrices().scale(ls, ls, 1f);
                drawContext.drawText(tr, lname, 0, 0, applyAlpha(0xFF888888, listAlpha), false);
                drawContext.getMatrices().pop();
                int badgeTx = tx + (int)(tr.getWidth(lname) * ls) + 3;
                drawAttributeBadgesLeft(drawContext, tr, region, badgeTx, ty, listAlpha);
            }

            listHitBoxes.add(new int[]{x, y, x + COL_W, y + BOX_H, i});
        }
    }

    /** Like drawAttributeBadges but left-anchored instead of right-anchored. */
    private static void drawAttributeBadgesLeft(DrawContext ctx, TextRenderer tr, RegionData region,
                                                  int x, int y, float alpha) {
        java.util.List<String[]> badges = new java.util.ArrayList<>();
        String agri = region.getAgriculture();
        if (agri != null && !agri.isEmpty()) {
            int col = switch (agri.toLowerCase()) {
                case "feld"  -> 0xFF3D7D2B; case "wild" -> 0xFF7D4D2B;
                case "erz"   -> 0xFFCC8833; case "fisch"-> 0xFF2B5D9D; default -> 0xFF555555; };
            badges.add(new String[]{agri, String.valueOf(col)});
        }
        String mines = region.getMines();
        if (mines != null && !mines.isEmpty()) {
            int col = switch (mines.toLowerCase()) {
                case "kohle" -> 0xFF333333; case "eisen"-> 0xFF7D7D8D;
                case "salz"  -> 0xFF9DAAAA; default      -> 0xFF555555; };
            badges.add(new String[]{mines, String.valueOf(col)});
        }
        String bonus = region.getBonus();
        if (bonus != null && !bonus.isEmpty()) {
            int col = switch (bonus.toLowerCase()) {
                case "honig" -> 0xFFBB8800; case "torf"-> 0xFF665544; default -> 0xFF555555; };
            badges.add(new String[]{bonus, String.valueOf(col)});
        }
        float bs = 0.60f; int badgeH = (int)(6 * bs) + 2, pad = 2, gap = 2;
        for (String[] badge : badges) {
            String label = badge[0];
            int bgColor = (int) Long.parseLong(badge[1]);
            int bw = (int)(tr.getWidth(label) * bs) + pad * 2;
            ctx.fill(x, y, x + bw, y + badgeH, (bgColor & 0x00FFFFFF) | (applyAlpha(0xFF000000, alpha)));
            int tw = (int)(tr.getWidth(label) * bs);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + (bw - tw) / 2, y + 1, 0);
            ctx.getMatrices().scale(bs, bs, 1f);
            ctx.drawText(tr, label, 0, 0, applyAlpha(0xFFFFFFFF, alpha), false);
            ctx.getMatrices().pop();
            x += bw + gap;
        }
        // Player Activity Dot nach den Perks
        int actCount = region.getPlayerGatheringCount();
        if (actCount > 0) {
            float actPulse = 0.4f + 0.6f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 900.0));
            float actIntensity = (float)(1.0 - Math.pow(0.7, actCount));
            int dotAlpha = (int)(actIntensity * actPulse * alpha * 255);
            // kleine weisse Pille mit dem Count
            String actLabel = "\u25cf " + activityLabel(actCount);
            int aw = (int)(tr.getWidth(actLabel) * bs) + pad * 2;
            ctx.fill(x, y, x + aw, y + badgeH, (dotAlpha << 24) | 0x00444444);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + pad, y + 1, 0);
            ctx.getMatrices().scale(bs, bs, 1f);
            ctx.drawText(tr, actLabel, 0, 0, (dotAlpha << 24) | 0x00FFFFFF, false);
            ctx.getMatrices().pop();
        }
    }

    /** mappt den player_gathering Count auf nen deutschen Activity-Label */
    private static String activityLabel(int count) {
        if (count <= 1) return "Aktiv";
        if (count <= 3) return "sehr Aktiv";
        if (count <= 6) return "extrem Aktiv";
        return "\u00fcbertrieben Aktiv";
    }

    /** multipliziert die Alpha-Komponente einer ARGB Farbe mit nem Float-Faktor */
    private static int applyAlpha(int argb, float alpha) {
        int a = (int)(((argb >> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Mouse-Klick Handler für die Lehen-Listen Sidebar und die Sort Buttons */
    private static void handleListClick(Screen screen, int mouseX, int mouseY) {
        // Sort Buttons zuerst prüfen
        for (int[] btn : sortButtonBoxes) {
            if (mouseX >= btn[0] && mouseX <= btn[2] && mouseY >= btn[1] && mouseY <= btn[3]) {
                sortMode = btn[4];
                listScrollOffset = 0;
                return;
            }
        }
        // Listeneinträge checken
        for (int[] box : listHitBoxes) {
            if (mouseX >= box[0] && mouseX <= box[2] && mouseY >= box[1] && mouseY <= box[3]) {
                MapDataManager manager = MapDataManager.getInstance();
                if (!manager.isDataLoaded()) return;
                java.util.List<RegionData> raw = new java.util.ArrayList<>();
                for (RegionData r : manager.getRegions()) { if (r.hasFaction()) raw.add(r); }
                java.util.List<RegionData> entries = buildSortedEntries(raw);
                int idx = box[4];
                if (idx < 0 || idx >= entries.size()) return;
                RegionData region = entries.get(idx);
                selectRegionInList(region, entries, true);
                return;
            }
        }
        // Map Polygon Klick nur Highlight + Liste scrollen Kamera NICHT zentrieren
        if (listVisible && lastEffScale < 0.3 && lastEffScale > 0) {
            double worldMX = (mouseX - lastHalfW) / lastScaleX + lastCameraX;
            double worldMZ = (mouseY - lastHalfH) / lastScaleZ + lastCameraZ;
            MapDataManager manager = MapDataManager.getInstance();
            if (!manager.isDataLoaded()) return;
            for (RegionData region : manager.getRegions()) {
                if (region.hasPolygon() && pointInPolygon(worldMX, worldMZ, region.getPolygon())) {
                    java.util.List<RegionData> raw = new java.util.ArrayList<>();
                    for (RegionData r : manager.getRegions()) { if (r.hasFaction()) raw.add(r); }
                    java.util.List<RegionData> entries = buildSortedEntries(raw);
                    selectRegionInList(region, entries, false);
                    return;
                }
            }
        }
    }

    /** ne Region in der Sidebar Liste auswählen: highlighten hinscrollen und optional Kamera zentrieren */
    private static void selectRegionInList(RegionData region, java.util.List<RegionData> entries, boolean centerCamera) {
        boolean wasSelected = (clickedListRegion == region);
        clickedListRegion = wasSelected ? null : region;
        clickHighlightTime = System.currentTimeMillis();

        // Liste so scrollen dass die ausgewählte Region sichtbar ist
        if (clickedListRegion != null) {
            int idx = entries.indexOf(clickedListRegion);
            if (idx >= 0) {
                listScrollOffset = Math.max(0, idx - 3); // show a few entries above
            }
        }

        // Sidebar clicks recentre the camera; map clicks must NOT (they would move the cursor
        // out from under the user's mouse, which is jarring).
        if (centerCamera && clickedListRegion != null && activeGuiMapScreen != null && cameraXField != null) {
            try {
                double sc    = scaleField.getDouble(activeGuiMapScreen);
                double scrSc = screenScaleField.getDouble(activeGuiMapScreen);
                double scMul = 1.0;
                if (getScaleMultiplierMethod != null) {
                    try { Object r2 = getScaleMultiplierMethod.invoke(activeGuiMapScreen);
                          if (r2 instanceof Number) scMul = ((Number) r2).doubleValue();
                    } catch (Exception ignored2) {}
                }
                double eff     = (sc * scMul) / scrSc;
                int    sw      = activeGuiMapScreen.width;
                double offsetX = (sw * 0.10) / eff;
                cameraXField.setDouble(activeGuiMapScreen, region.getX() - offsetX);
                cameraZField.setDouble(activeGuiMapScreen, region.getZ());
            } catch (Exception ignored) {}
        }
    }

    /**
     * Renders faction coat of arms (Wappen) centered on polygon centroid.
     * Always shows faction name, Lehen name and rank, no hover required.
     */
    private static void renderLehenWappen(DrawContext drawContext, List<RegionData> visibleRegions,
                                           double cameraX, double cameraZ, double effScale,
                                           double halfW, double halfH, int screenWidth, int screenHeight,
                                           int mouseX, int mouseY, float wappenAlpha, float mapBrightness) {
        if (effScale > 0.5) return;
        if (wappenAlpha <= 0f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Icon size: larger when zoomed out, capped smaller at max zoom-out
        int iconSize = Math.max(12, Math.min(16, (int)(12 + (0.15 - effScale) * 25)));

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        for (RegionData region : visibleRegions) {
            if (!region.hasFaction()) continue;
            String banner = region.getFaction().getBanner();
            boolean emptyBanner = (banner == null || banner.equals("kein_wappen"));

            // Use polygon centroid if available, fall back to API coords
            double cx = region.getX(), cz = region.getZ();
            if (region.hasPolygon()) {
                int[][] poly = region.getPolygon();
                double sumX = 0, sumZ = 0;
                for (int[] v : poly) { sumX += v[0]; sumZ += v[1]; }
                cx = sumX / poly.length;
                cz = sumZ / poly.length;
            }

            int sx = (int)((cx - cameraX) * effScale + halfW);
            int sy = (int)((cz - cameraZ) * effScale + halfH);
            if (sx < -iconSize || sx > screenWidth + iconSize) continue;
            if (sy < -iconSize || sy > screenHeight + iconSize) continue;

            int dx = sx - iconSize / 2, dy = sy - iconSize / 2;

            if (emptyBanner) {
                drawEmptyBanner(drawContext, dx, dy, iconSize, wappenAlpha * mapBrightness);
            } else {
                RenderSystem.setShaderColor(mapBrightness, mapBrightness, mapBrightness, wappenAlpha);
                drawBanner(drawContext, banner, dx, dy, iconSize);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }

            // Labels: always visible when effScale >= 0.15; hover-only when < 0.15
            boolean hovered = mouseX >= dx && mouseX <= dx + iconSize && mouseY >= dy && mouseY <= dy + iconSize;
            boolean showLabels = effScale >= 0.15 || hovered;

            if (showLabels) {
                FactionData fac = region.getFaction();
                float labelScale = Math.max(0.65f, Math.min(0.85f, (float)(0.15 / effScale)));

                // Rank above (small)
                String rank = (fac != null && fac.getRankName() != null) ? fac.getRankName() : null;
                if (rank != null && !rank.isEmpty()) {
                    float rs = labelScale * 0.75f;
                    int rw = (int)(tr.getWidth(rank) * rs);
                    drawContext.getMatrices().push();
                    drawContext.getMatrices().translate(sx - rw / 2, dy - (int)(8 * rs) - 2, 0);
                    drawContext.getMatrices().scale(rs, rs, 1f);
                    drawContext.drawText(tr, rank, 0, 0, 0xFFAAAAAA, true);
                    drawContext.getMatrices().pop();
                }

                // Faction name below icon
                String factionName = (fac != null && fac.getName() != null) ? fac.getName() : null;
                if (factionName != null) {
                    float fs = labelScale;
                    int fw = (int)(tr.getWidth(factionName) * fs);
                    int fy = dy + iconSize + 2;
                    drawContext.getMatrices().push();
                    drawContext.getMatrices().translate(sx - fw / 2, fy, 0);
                    drawContext.getMatrices().scale(fs, fs, 1f);
                    drawContext.drawText(tr, factionName, 0, 0, 0xFFFFFFFF, true);
                    drawContext.getMatrices().pop();

                    // Lehen name below faction name (even smaller)
                    String regionName = region.getName();
                    if (regionName != null && !regionName.isEmpty()) {
                        float ls = fs * 0.75f;
                        int lw = (int)(tr.getWidth(regionName) * ls);
                        int ly = fy + (int)(9 * fs);
                        drawContext.getMatrices().push();
                        drawContext.getMatrices().translate(sx - lw / 2, ly, 0);
                        drawContext.getMatrices().scale(ls, ls, 1f);
                        drawContext.drawText(tr, regionName, 0, 0, 0xFF888888, false);
                        drawContext.getMatrices().pop();
                    }
                }
            }
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * When zoomed in (effScale > 1.1): renders the Wappen + faction info of the center-screen Lehen
     * to the left of the compass in the top-right corner.
     */
    private static void renderCompassWappen(DrawContext ctx, List<RegionData> visibleRegions,
                                             double cameraX, double cameraZ, int screenWidth) {
        RegionData centerRegion = null;
        for (RegionData region : visibleRegions) {
            if (region.hasPolygon() && pointInPolygon(cameraX, cameraZ, region.getPolygon())) {
                centerRegion = region;
                break;
            }
        }
        if (centerRegion == null || !centerRegion.hasFaction()) return;

        FactionData fac = centerRegion.getFaction();
        String banner = (fac != null) ? fac.getBanner() : null;
        boolean emptyBanner = (banner == null || banner.equals("kein_wappen"));

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int iconSize = 28;
        int compassX = screenWidth - 47; // left edge of compass (screenWidth - 45 - 2)
        int compassCenterY = 24;         // vertical center of compass (2 + 45/2)

        // Icon: directly left of compass, vertically centered
        int iconX = compassX - 6 - iconSize;
        int iconY = compassCenterY - iconSize / 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (emptyBanner) {
            drawEmptyBanner(ctx, iconX, iconY, iconSize, 1f);
        } else {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            drawBanner(ctx, banner, iconX, iconY, iconSize);
        }

        // Text block: right-aligned, left of icon
        String factionName = (fac.getName() != null) ? fac.getName() : "";
        String rank        = (fac.getRankName() != null) ? fac.getRankName() : "";
        String lehenName   = (centerRegion.getName() != null) ? centerRegion.getName() : "";

        float fs = 0.85f; // faction name scale
        float rs = 0.65f; // rank scale
        float ls = 0.65f; // lehen name scale

        int totalTextH = (int)(8 * rs) + 2 + (int)(8 * fs) + 2 + (int)(8 * ls);
        int textY = compassCenterY - totalTextH / 2;
        int textRight = iconX - 5;

        if (!rank.isEmpty()) {
            int rw = (int)(tr.getWidth(rank) * rs);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(textRight - rw, textY, 0);
            ctx.getMatrices().scale(rs, rs, 1f);
            ctx.drawText(tr, rank, 0, 0, 0xFFAAAAAA, true);
            ctx.getMatrices().pop();
            textY += (int)(8 * rs) + 2;
        }
        if (!factionName.isEmpty()) {
            int fw = (int)(tr.getWidth(factionName) * fs);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(textRight - fw, textY, 0);
            ctx.getMatrices().scale(fs, fs, 1f);
            ctx.drawText(tr, factionName, 0, 0, 0xFFFFFFFF, true);
            ctx.getMatrices().pop();
            textY += (int)(8 * fs) + 2;
        }
        if (!lehenName.isEmpty()) {
            int lw = (int)(tr.getWidth(lehenName) * ls);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(textRight - lw, textY, 0);
            ctx.getMatrices().scale(ls, ls, 1f);
            ctx.drawText(tr, lehenName, 0, 0, 0xFF888888, false);
            ctx.getMatrices().pop();
            textY += (int)(8 * ls) + 3;
        }

        // Attribute badges row: agriculture, mines, bonus, fertility
        drawAttributeBadges(ctx, tr, centerRegion, textRight, textY);

        // Player activity indicator next to perks
        int compassActCount = centerRegion.getPlayerGatheringCount();
        if (compassActCount > 0) {
            float actPulse = 0.4f + 0.6f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 900.0));
            float actIntensity = (float)(1.0 - Math.pow(0.7, compassActCount));
            int dotAlpha = (int)(actIntensity * actPulse * 255);
            float bs = 0.60f;
            String actLabel = "\u25cf " + activityLabel(compassActCount);
            int aw = (int)(tr.getWidth(actLabel) * bs) + 4;
            int ay = textY + 10;
            ctx.fill(textRight - aw, ay, textRight, ay + 8, (int)(actIntensity * 0.5f * 255) << 24 | 0x00222222);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(textRight - aw + 2, ay + 1, 0);
            ctx.getMatrices().scale(bs, bs, 1f);
            ctx.drawText(tr, actLabel, 0, 0, (dotAlpha << 24) | 0x00FFFFFF, false);
            ctx.getMatrices().pop();
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Draws small colored attribute badges for a region's resources, right-aligned to xRight. */
    private static void drawAttributeBadges(DrawContext ctx, TextRenderer tr, RegionData region, int xRight, int y) {
        // Collect badges: [label, bgColor]
        java.util.List<String[]> badges = new java.util.ArrayList<>();

        String agri = region.getAgriculture();
        if (agri != null && !agri.isEmpty()) {
            int col = switch (agri.toLowerCase()) {
                case "feld"  -> 0xFF3D7D2B;
                case "wild"  -> 0xFF7D4D2B;
                case "erz"   -> 0xFFCC8833;
                case "fisch" -> 0xFF2B5D9D;
                default      -> 0xFF555555;
            };
            badges.add(new String[]{agri, String.valueOf(col)});
        }
        String mines = region.getMines();
        if (mines != null && !mines.isEmpty()) {
            int col = switch (mines.toLowerCase()) {
                case "kohle" -> 0xFF333333;
                case "eisen" -> 0xFF7D7D8D;
                case "salz"  -> 0xFF9DAAAA;
                default      -> 0xFF555555;
            };
            badges.add(new String[]{mines, String.valueOf(col)});
        }
        String bonus = region.getBonus();
        if (bonus != null && !bonus.isEmpty()) {
            int col = switch (bonus.toLowerCase()) {
                case "honig" -> 0xFFBB8800;
                case "torf"  -> 0xFF665544;
                default      -> 0xFF556655;
            };
            badges.add(new String[]{bonus, String.valueOf(col)});
        }
        int fertility = region.getFertility();
        if (fertility > 0) {
            String fertStr = "Fert. " + fertility;
            int col = fertility >= 30 ? 0xFF228822 : fertility >= 10 ? 0xFF887722 : 0xFF664422;
            badges.add(new String[]{fertStr, String.valueOf(col)});
        }

        if (badges.isEmpty()) return;

        float bs = 0.60f; // badge text scale
        int badgeH = 7;
        int pad = 2;
        int gap = 2;

        // Calculate total width to right-align the row
        int totalW = 0;
        for (int i = 0; i < badges.size(); i++) {
            int bw = (int)(tr.getWidth(badges.get(i)[0]) * bs) + pad * 2;
            totalW += bw + (i > 0 ? gap : 0);
        }

        int bx = xRight - totalW;
        for (String[] badge : badges) {
            String label = badge[0];
            int bgColor  = (int) Long.parseLong(badge[1]);
            int bw = (int)(tr.getWidth(label) * bs) + pad * 2;
            // Background fill
            ctx.fill(bx, y, bx + bw, y + badgeH, bgColor | 0xFF000000);
            // Label text centered in badge
            int tw = (int)(tr.getWidth(label) * bs);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(bx + (bw - tw) / 2, y + 1, 0);
            ctx.getMatrices().scale(bs, bs, 1f);
            ctx.drawText(tr, label, 0, 0, 0xFFFFFFFF, false);
            ctx.getMatrices().pop();
            bx += bw + gap;
        }
    }


    /** Draws a horizontal 3-tile frame. Texture is 35x18: left=9px, mid=17px, right=9px. */
    private static void drawThreeTileFrame(DrawContext ctx, int x, int y, int width) {
        final int H = 18, L = 9, M = 17, R = 9, TW = 35;
        ctx.drawTexture(TILE_FRAME_TEX, x,           y, 0,    0, L, H, TW, H);
        int midEnd = x + width - R;
        for (int mx = x + L; mx < midEnd; ) {
            int dw = Math.min(M, midEnd - mx);
            ctx.drawTexture(TILE_FRAME_TEX, mx, y, L, 0, dw, H, TW, H);
            mx += dw;
        }
        ctx.drawTexture(TILE_FRAME_TEX, x + width - R, y, L + M, 0, R, H, TW, H);
    }

    /**
     * Draws a single banner at (dx, dy) with given pixel size.
     * Custom banners (e.g. "Reichenau_Blau_Golden"): single texture.
     * Generic banners (e.g. "geviert_rot_silber"): banner_bg tinted color1,
     *   pattern tinted color2, layer_1 frame on top.
     */
    private static void drawBanner(DrawContext ctx, String banner, int dx, int dy, int size) {
        String norm = banner
            .replace("ä","ae").replace("ö","oe").replace("ü","ue").replace("ß","ss")
            .replace("Ä","Ae").replace("Ö","Oe").replace("Ü","Ue");
        String[] parts = norm.split("_");
        String first = parts.length > 0 ? parts[0] : "";
        if (first.isEmpty()) return;

        // Try custom texture first (works for any case, e.g. custom_ottonien.png)
        Identifier customTex = new Identifier("ottotalk", "textures/gui/banners/custom_" + first + ".png");
        if (bannerResourceExists(customTex)) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            ctx.drawTexture(customTex, dx, dy, 0, 0, size, size, size, size);
        } else if (!Character.isUpperCase(first.charAt(0))) {
            // Generic composite banner, desaturated to match heraldic muted palette
            float[] c1 = desaturateBanner(parts.length > 1 ? bannerColor(parts[1]) : new float[]{0.8f,0.8f,0.8f});
            float[] c2 = desaturateBanner(parts.length > 2 ? bannerColor(parts[2]) : c1);

            // Layer 1: outer shadow/border, already dark, draw untinted
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            ctx.drawTexture(BANNER_BG, dx, dy, 0, 0, size, size, size, size);

            // Layer 2: inner fill (white mask) tinted with field color c1
            RenderSystem.setShaderColor(c1[0], c1[1], c1[2], 1f);
            ctx.drawTexture(BANNER_FRAME, dx, dy, 0, 0, size, size, size, size);

            // Layer 3: heraldic charge pattern (white mask) tinted with c2 (skip for einfarbig)
            if (!first.equals("einfarbig")) {
                Identifier patTex = new Identifier("ottotalk", "textures/gui/banners/" + first + ".png");
                RenderSystem.setShaderColor(c2[0], c2[1], c2[2], 1f);
                ctx.drawTexture(patTex, dx, dy, 0, 0, size, size, size, size);
            }
        }
    }

    /** Maps German heraldic color name to RGB float[3]. */
    private static float[] bannerColor(String name) {
        switch (name.toLowerCase()) {
            case "rot":     return new float[]{0.85f, 0.18f, 0.18f};
            case "silber":  return new float[]{0.75f, 0.75f, 0.75f};
            case "golden":  case "gold": return new float[]{0.86f, 0.66f, 0.12f};
            case "blau":    return new float[]{0.12f, 0.38f, 0.72f};
            case "gruen":   return new float[]{0.18f, 0.62f, 0.22f};
            case "schwarz": return new float[]{0.15f, 0.15f, 0.15f};
            case "weiss":   case "weis": return new float[]{0.93f, 0.93f, 0.93f};
            case "gelb":    return new float[]{0.93f, 0.85f, 0.12f};
            case "purpur":  return new float[]{0.55f, 0.12f, 0.55f};
            case "braun":   return new float[]{0.52f, 0.30f, 0.10f};
            default:        return new float[]{0.80f, 0.80f, 0.80f};
        }
    }

    private static final Identifier KEIN_BANNER = new Identifier("ottotalk", "textures/gui/banners/kein_banner.png");

    /**
     * Draws the kein_banner.png texture for Lehen/factions without a banner.
     */
    private static void drawEmptyBanner(DrawContext ctx, int dx, int dy, int size, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(KEIN_BANNER, dx, dy, 0, 0, size, size, 16, 16);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Checks (and caches) whether a banner texture resource actually exists in the mod JAR. */
    private static boolean bannerResourceExists(Identifier id) {
        String key = id.getPath();
        if (bannerExistsCache.contains(key)) return true;
        if (bannerMissingCache.contains(key)) return false;
        boolean exists = MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent();
        (exists ? bannerExistsCache : bannerMissingCache).add(key);
        return exists;
    }

    /** Reduces saturation of a banner color by 40% (blends 40% toward perceived gray). */
    private static float[] desaturateBanner(float[] c) {
        float gray = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2];
        float f = 0.60f; // keep 60% saturation
        return new float[]{ gray + (c[0] - gray) * f, gray + (c[1] - gray) * f, gray + (c[2] - gray) * f };
    }

    private static float vFactor(int ci, int ri, int[] colMinRi, int[] colMaxRi, int numCols, int fadeW) {
        if (ci < 0 || ci >= numCols || colMinRi[ci] == Integer.MAX_VALUE) return 0f;
        int vUp = ri - colMinRi[ci], vDown = colMaxRi[ci] - ri;
        float vt = Math.min(1.0f, (Math.min(vUp, vDown) + 1) / (float) fadeW);
        return vt * vt * (3f - 2f * vt); // smoothstep
    }

    /** Returns true if pixel center px is inside any of the given float spans. */
    private static boolean pixelInSpans(float[] rx1s, float[] rx2s, float px) {
        for (int k = 0; k < rx1s.length; k++) {
            if (px > rx1s[k] && px < rx2s[k]) return true;
        }
        return false;
    }

    /**
     * Convex hull via Jarvis March. Fills hx/hy with hull vertices, returns hull size.
     * In screen space (Y-down), picks the most clockwise next point each step.
     */
    private static int convexHullOf(float[] px, float[] py, int n, float[] hx, float[] hy) {
        if (n < 3) { System.arraycopy(px, 0, hx, 0, n); System.arraycopy(py, 0, hy, 0, n); return n; }
        // Andrew's monotone chain starts from the lexicographically smallest point.
        int start = 0;
        for (int i = 1; i < n; i++) {
            if (px[i] < px[start] || (px[i] == px[start] && py[i] < py[start])) start = i;
        }
        int hn = 0, cur = start;
        do {
            hx[hn] = px[cur]; hy[hn] = py[cur]; hn++;
            int next = -1;
            for (int i = 0; i < n; i++) {
                if (i == cur) continue;
                if (next == -1) { next = i; continue; }
                // Cross product in screen space (Y-down): negative = more CW (hull direction)
                float cross = (px[next] - px[cur]) * (py[i] - py[cur])
                            - (py[next] - py[cur]) * (px[i] - px[cur]);
                if (cross < 0) next = i;
            }
            if (next == -1) break;
            cur = next;
        } while (cur != start && hn < n);
        return hn;
    }

    /** Ear-clipping polygon triangulation. Works for simple (non-self-intersecting) polygons. */
    private static java.util.List<int[]> earClipTriangulate(float[] xs, float[] ys) {
        int n = xs.length;
        java.util.List<int[]> triangles = new java.util.ArrayList<>();
        if (n < 3) return triangles;

        java.util.ArrayList<Integer> idx = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) idx.add(i);

        // Compute signed area to detect winding; ensure CCW in screen-space (y-down)
        double area = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += (double)xs[i] * ys[j] - (double)xs[j] * ys[i];
        }
        if (area < 0) java.util.Collections.reverse(idx); // make CCW

        int limit = n * n + 10;
        int iters = 0;
        while (idx.size() > 2 && iters++ < limit) {
            int sz = idx.size();
            boolean earFound = false;
            for (int i = 0; i < sz; i++) {
                int pi = (i - 1 + sz) % sz, ni = (i + 1) % sz;
                int a = idx.get(pi), b = idx.get(i), c = idx.get(ni);
                if (!isConvexVertex(xs, ys, a, b, c)) continue;
                boolean hasInner = false;
                for (int k = 0; k < sz; k++) {
                    if (k == pi || k == i || k == ni) continue;
                    if (ptInTri(xs[idx.get(k)], ys[idx.get(k)],
                                xs[a], ys[a], xs[b], ys[b], xs[c], ys[c])) {
                        hasInner = true; break;
                    }
                }
                if (hasInner) continue;
                triangles.add(new int[]{a, b, c});
                idx.remove(i);
                earFound = true;
                break;
            }
            if (!earFound) break;
        }
        return triangles;
    }

    private static boolean isConvexVertex(float[] xs, float[] ys, int a, int b, int c) {
        // Cross product (b-a)x(c-a); positive = CCW in y-down screen space
        return (xs[b]-xs[a])*(ys[c]-ys[a]) - (ys[b]-ys[a])*(xs[c]-xs[a]) > 0;
    }

    private static boolean ptInTri(float px, float py,
                                    float ax, float ay, float bx, float by, float cx, float cy) {
        float d1 = (px-bx)*(ay-by) - (ax-bx)*(py-by);
        float d2 = (px-cx)*(by-cy) - (bx-cx)*(py-cy);
        float d3 = (px-ax)*(cy-ay) - (cx-ax)*(py-ay);
        boolean hasNeg = d1 < 0 || d2 < 0 || d3 < 0;
        boolean hasPos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(hasNeg && hasPos);
    }

    /**
     * Render label at region center.
     */
    private static void renderLabel(DrawContext drawContext, TextRenderer textRenderer, RegionData region,
                                     double cameraX, double cameraZ, double scale,
                                     double halfW, double halfH, int screenWidth, int screenHeight) {
        int cx = worldToScreenX(region.getX(), cameraX, scale, halfW);
        int cy = worldToScreenY(region.getZ(), cameraZ, scale, halfH);

        if (cx < -100 || cx > screenWidth + 100 || cy < -50 || cy > screenHeight + 50) return;

        FactionData faction = region.getFaction();
        int color = faction != null ? faction.getRankColor() : 0xFF808080;

        // Only show labels when zoomed in enough
        if (scale > 0.3) {
            String label = faction != null ? faction.getName() : region.getName();
            int textWidth = textRenderer.getWidth(label);
            int textX = cx - textWidth / 2;
            int textY = cy - 5;
            drawContext.fill(textX - 2, textY - 1, textX + textWidth + 2, textY + 10, 0xAA000000);
            drawContext.drawText(textRenderer, label, textX, textY, color, true);
        }

        // Rank only appears once we are close enough that the label has room; below 0.8x the
        // two stacked lines would overlap surrounding regions.
        if (scale > 0.8 && faction != null) {
            String rank = faction.getRankName();
            int rankWidth = textRenderer.getWidth(rank);
            int rankX = cx - rankWidth / 2;
            int rankY = cy + 7;
            drawContext.fill(rankX - 2, rankY - 1, rankX + rankWidth + 2, rankY + 10, 0xAA000000);
            drawContext.drawText(textRenderer, rank, rankX, rankY, 0xFFCCCCCC, true);
        }
    }

    private static void drawRect(DrawContext drawContext, int x1, int y1, int x2, int y2, int color) {
        drawContext.fill(x1, y1, x2, y1 + 1, color);
        drawContext.fill(x1, y2 - 1, x2, y2, color);
        drawContext.fill(x1, y1, x1 + 1, y2, color);
        drawContext.fill(x2 - 1, y1, x2, y2, color);
    }

    /**
     * Render tooltip for the region whose polygon contains the mouse position,
     * or nearest center point.
     */
    private static void renderHoveredTooltip(DrawContext drawContext, TextRenderer textRenderer,
                                              List<RegionData> visibleRegions,
                                              double cameraX, double cameraZ, double scaleX, double scaleZ,
                                              double halfW, double halfH,
                                              int screenWidth, int screenHeight,
                                              int mouseX, int mouseY) {
        // Convert mouse to world coords for point-in-polygon test (separate X/Z scale)
        double worldMX = (mouseX - halfW) / scaleX + cameraX;
        double worldMZ = (mouseY - halfH) / scaleZ + cameraZ;

        RegionData hovered = null;

        // First try point-in-polygon test
        for (RegionData region : visibleRegions) {
            if (region.hasPolygon() && pointInPolygon(worldMX, worldMZ, region.getPolygon())) {
                hovered = region;
                break;
            }
        }

        // Fallback: nearest center
        if (hovered == null) {
            double closestDist = Double.MAX_VALUE;
            double hitRadiusWorld = 30 / Math.min(scaleX, scaleZ); // 30 screen pixels
            for (RegionData region : visibleRegions) {
                double dx = worldMX - region.getX();
                double dz = worldMZ - region.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < hitRadiusWorld && dist < closestDist) {
                    closestDist = dist;
                    hovered = region;
                }
            }
        }

        if (hovered == null) return;

        FactionData faction = hovered.getFaction();
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 8;

        int lineHeight = 11;
        int lineCount = 3;
        if (faction != null) lineCount += 4;
        if (faction != null && faction.hasLord()) lineCount++;

        int tooltipWidth = 180;
        int tooltipHeight = lineCount * lineHeight + 8;

        if (tooltipX + tooltipWidth > screenWidth) tooltipX = mouseX - tooltipWidth - 12;
        if (tooltipY + tooltipHeight > screenHeight) tooltipY = screenHeight - tooltipHeight;
        if (tooltipY < 0) tooltipY = 0;

        drawContext.fill(tooltipX - 3, tooltipY - 3, tooltipX + tooltipWidth + 3, tooltipY + tooltipHeight + 3, 0xEE000000);
        drawRect(drawContext, tooltipX - 3, tooltipY - 3, tooltipX + tooltipWidth + 3, tooltipY + tooltipHeight + 3, 0xFF555555);

        int y = tooltipY;

        drawContext.drawText(textRenderer, "\u00A7e" + hovered.getName(), tooltipX, y, 0xFFFFFF00, true);
        y += lineHeight;

        drawContext.drawText(textRenderer, "\u00A77X: " + hovered.getX() + " Z: " + hovered.getZ(), tooltipX, y, 0xFF888888, true);
        y += lineHeight;

        String resources = "";
        if (hovered.getAgriculture() != null && !hovered.getAgriculture().isEmpty())
            resources += hovered.getAgriculture();
        if (hovered.getMines() != null && !hovered.getMines().isEmpty())
            resources += (resources.isEmpty() ? "" : " | ") + hovered.getMines();
        if (!resources.isEmpty())
            drawContext.drawText(textRenderer, "\u00A77" + resources, tooltipX, y, 0xFF888888, true);
        y += lineHeight;

        if (faction != null) {
            drawContext.drawText(textRenderer, "\u00A7f" + faction.getName(), tooltipX, y, faction.getRankColor(), true);
            y += lineHeight;
            drawContext.drawText(textRenderer, "\u00A77Rang: \u00A7f" + faction.getRankName(), tooltipX, y, 0xFFCCCCCC, true);
            y += lineHeight;
            drawContext.drawText(textRenderer, "\u00A77Anf\u00FChrer: \u00A7f" + faction.getLeaderName(), tooltipX, y, 0xFFCCCCCC, true);
            y += lineHeight;
            drawContext.drawText(textRenderer, "\u00A77Mitglieder: \u00A7f" + faction.getMemberCount(), tooltipX, y, 0xFFCCCCCC, true);
            y += lineHeight;
            if (faction.hasLord() && faction.getLordName() != null) {
                drawContext.drawText(textRenderer, "\u00A77Lehnsherr: \u00A7f" + faction.getLordName(), tooltipX, y, 0xFFCCCCCC, true);
            }
        } else {
            drawContext.drawText(textRenderer, "\u00A78Unbeansprucht", tooltipX, y, 0xFF666666, true);
        }
    }

    // --- Waypoint reflection state ---
    private static boolean wpInitDone = false;
    private static boolean wpInitFailed = false;
    private static boolean wpDebugLogged = false; // reset each time to re-capture field dump
    private static java.lang.reflect.Method wpGetSession;
    private static java.lang.reflect.Method wpGetWpMgr;
    private static java.lang.reflect.Method wpGetWaypoints;   // getWaypoints()->WaypointSet on WaypointsManager
    private static java.lang.reflect.Field  wpSetListField;   // the List<Waypoint> field inside WaypointSet hierarchy
    @SuppressWarnings("unchecked")
    private static java.util.List<Object> savedWpListContents; // saved waypoint list before Xaero render

    @SuppressWarnings("unchecked")
    private static java.util.List<Object> getWpList() {
        try {
            Object session = wpGetSession.invoke(null);
            if (session == null) return null;
            Object mgr = wpGetWpMgr.invoke(session);
            if (mgr == null) return null;
            Object wpSet = wpGetWaypoints.invoke(mgr);
            if (wpSet == null) return null;
            return (java.util.List<Object>) wpSetListField.get(wpSet);
        } catch (Exception e) { return null; }
    }

    private static void initWaypointReflectionIfNeeded() {
        if (wpInitDone || wpInitFailed) return;
        try {
            Class<?> sc = Class.forName("xaero.common.XaeroMinimapSession");
            for (java.lang.reflect.Method m : sc.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    m.setAccessible(true);
                    if (m.invoke(null) != null) { wpGetSession = m; break; }
                }
            }
            if (wpGetSession == null) { wpInitFailed = true; return; }

            Object session = wpGetSession.invoke(null);
            if (session == null) return;

            for (Class<?> c = session.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().toLowerCase().contains("waypointsmanager")) {
                        m.setAccessible(true); wpGetWpMgr = m; break;
                    }
                }
                if (wpGetWpMgr != null) break;
            }
            if (wpGetWpMgr == null) { wpInitFailed = true; return; }

            Object mgr = wpGetWpMgr.invoke(session);
            if (mgr == null) { wpInitFailed = true; return; }

            for (Class<?> c = mgr.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && m.getName().equals("getWaypoints")) {
                        m.setAccessible(true); wpGetWaypoints = m; break;
                    }
                }
                if (wpGetWaypoints != null) break;
            }
            if (wpGetWaypoints == null) { wpInitFailed = true; return; }

            Object wpSet = wpGetWaypoints.invoke(mgr);
            if (wpSet == null) return;

            for (Class<?> c = wpSet.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object v = f.get(wpSet);
                    if (wpSetListField == null && v instanceof java.util.List) { wpSetListField = f; }
                }
            }
            if (wpSetListField == null) { wpInitFailed = true; return; }

            wpInitDone = true;
        } catch (ClassNotFoundException e) {
            wpInitFailed = true;
        } catch (Exception e) {
            OttoTalkClient.LOGGER.warn("[OttoTalk] WP init error: " + e);
            wpInitFailed = true;
        }
    }

    private static void hideXaeroWaypoints() {
        initWaypointReflectionIfNeeded();
        if (!wpInitDone || wpSetListField == null) return;
        try {
            java.util.List<Object> list = getWpList();
            if (list == null || list.isEmpty()) return;
            savedWpListContents = new java.util.ArrayList<>(list);
            list.clear();
        } catch (Exception ignored) {}
    }

    private static void restoreXaeroWaypoints() {
        if (savedWpListContents == null) return;
        try {
            java.util.List<Object> list = getWpList();
            if (list != null) {
                list.clear();
                list.addAll(savedWpListContents);
            }
            savedWpListContents = null;
        } catch (Exception ignored) {}
    }

    /**
     * Re-render Xaero waypoint markers on top after compositing.
     * Unaffected by the map shader, always visible regardless of chunk load state.
     */
    private static void redrawWaypointMarkers(DrawContext drawContext,
                                               double cameraX, double cameraZ,
                                               double effScale, double halfW, double halfH,
                                               int mouseX, int mouseY) {
        initWaypointReflectionIfNeeded();
        if (!wpInitDone) return;

        boolean debugLog = !wpDebugLogged;
        wpDebugLogged = true;
        try {
            java.util.List<Object> wpList = getWpList();
            if (wpList == null) return;
            if (debugLog) OttoTalkClient.LOGGER.info("[OttoTalk] WP list size: " + wpList.size());

            int wpCount = 0;
            for (Object wp : wpList) {
                if (debugLog && wpCount == 0) {
                    OttoTalkClient.LOGGER.info("[OttoTalk] First WP class: " + wp.getClass().getName());
                    for (Class<?> c = wp.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            f.setAccessible(true);
                            try { OttoTalkClient.LOGGER.info("[OttoTalk]   [" + c.getSimpleName() + "] " + f.getName() + " = " + f.get(wp)); }
                            catch (Exception ex) {}
                        }
                    }
                }
                renderWaypoint(wp, cameraX, cameraZ, effScale, halfW, halfH, drawContext, mouseX, mouseY);
                wpCount++;
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            if (debugLog) {
                OttoTalkClient.LOGGER.info("[OttoTalk] WP rendered: " + wpCount);
                // Color distribution for debugging
                int[] colorCounts = new int[16];
                for (Object wpDbg : wpList) {
                    int c = 14;
                    try {
                        for (Class<?> cl = wpDbg.getClass(); cl != null && cl != Object.class; cl = cl.getSuperclass()) {
                            for (java.lang.reflect.Field f : cl.getDeclaredFields()) {
                                if ((f.getName().equals("color") || f.getName().equals("colorIndex"))) {
                                    f.setAccessible(true);
                                    if (f.getType() == int.class) c = f.getInt(wpDbg);
                                    else if (f.getType().isEnum()) { Object e = f.get(wpDbg); if (e != null) c = colorIndexFromName(e.toString()); }
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    if (c >= 0 && c < 16) colorCounts[c]++;
                }
                StringBuilder sb = new StringBuilder("[OttoTalk] WP color dist:");
                for (int i = 0; i < 16; i++) if (colorCounts[i] > 0) sb.append(" ").append(i).append("x").append(colorCounts[i]);
                OttoTalkClient.LOGGER.info(sb.toString());
            }
        } catch (Exception e) {
            if (debugLog) OttoTalkClient.LOGGER.warn("[OttoTalk] WP render error: " + e);
        }
    }

    private static int colorIndexFromName(String name) {
        return switch (name) {
            case "BLACK"        -> 0;
            case "DARK_BLUE"    -> 1;
            case "DARK_GREEN"   -> 2;
            case "DARK_AQUA"    -> 3;
            case "DARK_RED"     -> 4;
            case "DARK_PURPLE"  -> 5;
            case "GOLD"         -> 6;
            case "GRAY"         -> 7;
            case "DARK_GRAY"    -> 8;
            case "BLUE"         -> 9;
            case "GREEN"        -> 10;
            case "AQUA"         -> 11;
            case "RED"          -> 12;
            case "LIGHT_PURPLE" -> 13;
            case "YELLOW"       -> 14;
            case "WHITE"        -> 15;
            default             -> 15;
        };
    }

    private static java.lang.reflect.Method findNoArgMethod(Class<?> cls, String keyword) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getName().toLowerCase().contains(keyword)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static java.lang.reflect.Method findNoArgMethodByReturnKeyword(Class<?> cls, String keyword) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getName().toLowerCase().contains(keyword)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static Iterable<?> toIterable(Object obj) {
        try {
            if (obj instanceof Iterable<?> it) return it;
            if (obj instanceof java.util.Map<?,?> m) return m.values();
            for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof Iterable<?> it) return it;
                if (v instanceof java.util.Map<?,?> m) return m.values();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static final Identifier MARKER_TEXTURE  = new Identifier("ottotalk", "textures/gui/marker.png");
    private static final Identifier DEATH_TEXTURE   = new Identifier("ottotalk", "textures/gui/deathpoint.png");
    private static final Identifier BANNER_BG        = new Identifier("ottotalk", "textures/gui/banners/banner_bg.png");
    private static final Identifier BANNER_FRAME     = new Identifier("ottotalk", "textures/gui/banners/layer_1.png");
    private static final Identifier TILE_FRAME_TEX   = new Identifier("ottotalk", "textures/gui/3-tile-frame.png");
    private static final java.util.Set<String> bannerExistsCache = new java.util.HashSet<>();
    private static final java.util.Set<String> bannerMissingCache = new java.util.HashSet<>();

    private static final int[][] XAERO_COLORS = {
        {80,80,160},  // 0  BLACK     -> slate blue (visible on all backgrounds)
        {0,0,136},    // 1  DARK_BLUE
        {0,136,0},    // 2  DARK_GREEN
        {0,136,136},  // 3  DARK_AQUA
        {136,0,0},    // 4  DARK_RED
        {136,0,136},  // 5  DARK_PURPLE
        {204,136,0},  // 6  GOLD
        {136,136,136},// 7  GRAY
        {68,68,68},   // 8  DARK_GRAY
        {68,68,204},  // 9  BLUE
        {68,204,68},  // 10 GREEN
        {68,204,204}, // 11 AQUA
        {204,68,68},  // 12 RED
        {204,68,204}, // 13 LIGHT_PURPLE
        {204,204,68}, // 14 YELLOW
        {180,180,180} // 15 WHITE, mapped to light gray (not pure white)
    };

    private static void renderWaypoint(Object wp,
                                        double cameraX, double cameraZ, double effScale,
                                        double halfW, double halfH, DrawContext drawContext,
                                        int mouseX, int mouseY) {
        try {
            int wpX = 0, wpZ = 0, color = 14;
            String name = "", initials = "";
            boolean hasX = false, hasZ = false, hasColor = false, hasActualColor = false, hasName = false, hasInitials = false;
            for (Class<?> c = wp.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    f.setAccessible(true);
                    String fn = f.getName();
                    Class<?> ft = f.getType();
                    if (!hasX && fn.equals("x") && ft == int.class) { wpX = f.getInt(wp); hasX = true; }
                    else if (!hasZ && fn.equals("z") && ft == int.class) { wpZ = f.getInt(wp); hasZ = true; }
                    else if (!hasActualColor && fn.equals("actualColor") && ft == int.class) { color = f.getInt(wp); hasActualColor = true; hasColor = true; }
                    else if (!hasColor && (fn.equals("color") || fn.equals("colorIndex") || fn.equals("colour"))) {
                        if (ft == int.class) { color = f.getInt(wp); hasColor = true; }
                        else if (ft.isEnum()) { Object e = f.get(wp); if (e != null) { color = colorIndexFromName(e.toString()); hasColor = true; } }
                    }
                    else if (!hasName && fn.equals("name") && ft == String.class) { Object v = f.get(wp); if (v != null) { name = (String)v; hasName = true; } }
                    else if (!hasInitials && fn.equals("initials") && ft == String.class) { Object v = f.get(wp); if (v != null) { initials = (String)v; hasInitials = true; } }
                }
                if (hasX && hasZ && hasActualColor && hasName && hasInitials) break;
            }

            float sx = (float)((wpX - cameraX) * effScale + halfW);
            float sy = (float)((wpZ - cameraZ) * effScale + halfH);

            boolean isDeath = name.contains("deathpoint");
            int[] rgb = (color >= 0 && color < XAERO_COLORS.length) ? XAERO_COLORS[color] : XAERO_COLORS[15];

            // Scale marker with zoom: significantly smaller when zoomed far out
            float zoomScale = (float) Math.max(0.3, Math.min(1.2, effScale * 1.2));
            int mw, mh;
            mw = (int)(20 * zoomScale); mh = (int)(26 * zoomScale); // 20:26 aspect for both death and marker
            int tx = (int)(sx - mw / 2f);
            int ty = (int)(sy - mh / 2f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // Markers dim at night so they don't punch through the world-map's dusk gradient.
            MinecraftClient mc3 = MinecraftClient.getInstance();
            float skyB = (mc3.world != null) ? mc3.world.getSkyBrightness(1.0f) : 1.0f;
            float nightF = 0.3f + 0.7f * skyB;
            RenderSystem.setShaderColor(nightF, nightF, nightF, 1f);
            drawContext.drawTexture(isDeath ? DEATH_TEXTURE : MARKER_TEXTURE, tx, ty, 0, 0, mw, mh, mw, mh);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            // Letter, skip for death waypoints
            String letter = !initials.isEmpty() ? initials : (!name.isEmpty() ? String.valueOf(name.charAt(0)).toUpperCase() : "");
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            if (!isDeath && !letter.isEmpty()) {
                // Night-tint letter and outline colors
                int lb = Math.min(255, (int)(255 * nightF));
                int letterColor = (0xFF << 24) | (lb << 16) | (lb << 8) | lb;
                int or_ = (int)(rgb[0] * nightF), og_ = (int)(rgb[1] * nightF), ob_ = (int)(rgb[2] * nightF);
                int outline = 0xFF000000 | (or_ << 16) | (og_ << 8) | ob_;
                int iw = tr.getWidth(letter);
                drawContext.getMatrices().push();
                try {
                    float letterY = ty + mh * 0.35f;
                    drawContext.getMatrices().translate(sx, letterY, 0);
                    drawContext.getMatrices().scale(zoomScale, zoomScale, 1f);
                    int ox = -(int)(iw / 2f);
                    int oy = 0;
                    drawContext.drawText(tr, letter, ox - 1, oy,     outline,     false);
                    drawContext.drawText(tr, letter, ox + 1, oy,     outline,     false);
                    drawContext.drawText(tr, letter, ox,     oy - 1, outline,     false);
                    drawContext.drawText(tr, letter, ox,     oy + 1, outline,     false);
                    drawContext.drawText(tr, letter, ox,     oy,     letterColor, false);
                } finally {
                    drawContext.getMatrices().pop();
                }
            }

            // Name label below marker, only on hover, scale with zoom
            boolean hovered = Math.abs(mouseX - sx) <= mw && Math.abs(mouseY - sy) <= mh;
            String displayName = isDeath ? "Todespunkt" : name;
            if (hovered && !displayName.isEmpty()) {
                float nameScale = Math.max(0.65f, Math.min(1.0f, zoomScale));
                int nw = (int)(tr.getWidth(displayName) * nameScale);
                int ny = ty + mh + 2;
                drawContext.getMatrices().push();
                drawContext.getMatrices().translate((int)(sx - nw / 2f), ny, 0);
                drawContext.getMatrices().scale(nameScale, nameScale, 1f);
                drawContext.drawText(tr, displayName, 0, 0, 0xFFFFFFFF, true);
                drawContext.getMatrices().pop();
            }
        } catch (Exception ignored) {}
    }

    /** Pulsing "Karte lädt..." Overlay solang MapDataManager.isLoading() true ist */
    private static void renderLoadingOverlay(DrawContext ctx, int w, int h) {
        float pulse = 0.6f + 0.4f * (float)Math.abs(Math.sin(System.currentTimeMillis() / 500.0));
        int cx = w / 2, cy = h / 2;
        ctx.fill(cx - 80, cy - 14, cx + 80, cy + 14, 0xBB000000);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String text = "Karte lädt...";
        int tw = tr.getWidth(text);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx - tw / 2f, cy - 4, 0);
        ctx.drawText(tr, text, 0, 0, (int)(pulse * 255) << 24 | 0x00DDBBAA, false);
        ctx.getMatrices().pop();
    }

    /**
     * Render a custom rotated compass in the top-right corner, covering Xaero's built-in compass.
     * Compass.png (45x45) rotates by player yaw so N marker tracks world north.
     */
    private static void renderCustomCompass(DrawContext drawContext, int screenWidth) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        int cSize = 40; // rendered size
        int cx = screenWidth - cSize - 3;
        int cy = 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        drawContext.drawTexture(COMPASS_TEXTURE, cx, cy, 0, 0, cSize, cSize, cSize, cSize);
    }

    /**
     * Ray-casting point-in-polygon test.
     */
    private static boolean pointInPolygon(double px, double pz, int[][] polygon) {
        boolean inside = false;
        int n = polygon.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon[i][0], zi = polygon[i][1];
            double xj = polygon[j][0], zj = polygon[j][1];
            if ((zi > pz) != (zj > pz) && px < (xj - xi) * (pz - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }
}
