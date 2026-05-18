package com.ottotalk.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Lädt GIF dateien aus den mod resources und macht frame-by-frame playback,
 * indem jeder frame als DynamicTexture registriert wird.
 */
public class GifFramePlayer {

    public static final class GifAnimation {
        public final Identifier[] frames;
        public final int[] frameDelaysMs;
        public final int totalDurationMs;
        public final int frameW, frameH;

        GifAnimation(Identifier[] frames, int[] delays, int w, int h) {
            this.frames = frames;
            this.frameDelaysMs = delays;
            this.frameW = w;
            this.frameH = h;
            int t = 0;
            for (int d : delays) t += d;
            this.totalDurationMs = Math.max(1, t);
        }

        public Identifier getCurrentFrame() {
            if (frames == null || frames.length == 0) return null;
            long t = System.currentTimeMillis() % totalDurationMs;
            int elapsed = 0;
            for (int i = 0; i < frames.length; i++) {
                elapsed += frameDelaysMs[i];
                if (t < elapsed) return frames[i];
            }
            return frames[frames.length - 1];
        }
    }

    private static final Map<String, GifAnimation> cache = new HashMap<>();
    private static final Map<String, Boolean> loadAttempted = new HashMap<>();

    /** gibt die animation für den resource sub-path zurück (z.b. "textures/gui/worldmapPreview.gif"),
     *  oder null wenn nicht verfügbar. muss vom render thread aufgerufen werden. */
    public static GifAnimation get(String resourceSubPath) {
        if (cache.containsKey(resourceSubPath)) return cache.get(resourceSubPath);
        if (loadAttempted.getOrDefault(resourceSubPath, false)) return null;
        loadAttempted.put(resourceSubPath, true);
        GifAnimation anim = load(resourceSubPath);
        if (anim != null) cache.put(resourceSubPath, anim);
        return anim;
    }

    /** Loads a static image (JPEG, PNG, etc.) as a single-frame GifAnimation.
     *  Returns null if the resource doesn't exist or can't be read. */
    public static GifAnimation getStatic(String resourceSubPath) {
        if (cache.containsKey(resourceSubPath)) return cache.get(resourceSubPath);
        if (loadAttempted.getOrDefault(resourceSubPath, false)) return null;
        loadAttempted.put(resourceSubPath, true);
        GifAnimation anim = loadStatic(resourceSubPath);
        if (anim != null) cache.put(resourceSubPath, anim);
        return anim;
    }

    private static GifAnimation loadStatic(String resourceSubPath) {
        try {
            Identifier id = new Identifier("ottotalk", resourceSubPath);
            var res = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) return null;
            try (InputStream is = res.get().getInputStream()) {
                BufferedImage img = ImageIO.read(is);
                if (img == null) return null;
                int w = img.getWidth(), h = img.getHeight();
                NativeImage ni = bufferedToNative(img);
                NativeImageBackedTexture dt = new NativeImageBackedTexture(ni);
                String key = resourceSubPath.replaceAll("[^a-z0-9]", "_") + "_static";
                Identifier frameId = MinecraftClient.getInstance().getTextureManager()
                        .registerDynamicTexture("ottotalk_img_" + key, dt);
                return new GifAnimation(new Identifier[]{frameId}, new int[]{1000}, w, h);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static GifAnimation load(String resourceSubPath) {
        try {
            Identifier id = new Identifier("ottotalk", resourceSubPath);
            var res = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) return null;

            try (InputStream is = res.get().getInputStream()) {
                ImageInputStream iis = ImageIO.createImageInputStream(is);
                var readers = ImageIO.getImageReadersByFormatName("gif");
                if (!readers.hasNext()) return null;

                ImageReader reader = readers.next();
                reader.setInput(iis, false);
                int numFrames = reader.getNumImages(true);
                if (numFrames <= 0) { reader.dispose(); return null; }

                Identifier[] frameIds = new Identifier[numFrames];
                int[] delays = new int[numFrames];
                int fw = 0, fh = 0;

                // GIF frames akkumulieren (disposal/compositing per Graphics2D)
                BufferedImage canvas = null;

                for (int i = 0; i < numFrames; i++) {
                    BufferedImage frame = reader.read(i);
                    if (i == 0) {
                        fw = frame.getWidth();
                        fh = frame.getHeight();
                        canvas = new BufferedImage(fw, fh, BufferedImage.TYPE_INT_ARGB);
                    }
                    java.awt.Graphics2D g = canvas.createGraphics();
                    g.drawImage(frame, 0, 0, null);
                    g.dispose();

                    delays[i] = getFrameDelayMs(reader, i);

                    NativeImage ni = bufferedToNative(canvas);
                    NativeImageBackedTexture dt = new NativeImageBackedTexture(ni);
                    String key = resourceSubPath.replaceAll("[^a-z0-9]", "_") + "_f" + i;
                    frameIds[i] = MinecraftClient.getInstance().getTextureManager()
                            .registerDynamicTexture("ottotalk_gif_" + key, dt);
                }
                reader.dispose();
                return new GifAnimation(frameIds, delays, fw, fh);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static int getFrameDelayMs(ImageReader reader, int frameIndex) {
        try {
            IIOMetadata meta = reader.getImageMetadata(frameIndex);
            String fmt = meta.getNativeMetadataFormatName();
            Node root = meta.getAsTree(fmt);
            int cs = extractDelayCs(root);
            return cs > 0 ? cs * 10 : 100;
        } catch (Exception e) {
            return 100;
        }
    }

    private static int extractDelayCs(Node node) {
        if ("GraphicControlExtension".equals(node.getNodeName())) {
            NamedNodeMap attrs = node.getAttributes();
            if (attrs != null) {
                Node d = attrs.getNamedItem("delayTime");
                if (d != null) {
                    try { return Integer.parseInt(d.getNodeValue()); } catch (NumberFormatException ignored) {}
                }
            }
        }
        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            int v = extractDelayCs(node.getChildNodes().item(i));
            if (v > 0) return v;
        }
        return 0;
    }

    private static NativeImage bufferedToNative(BufferedImage bi) {
        NativeImage ni = new NativeImage(bi.getWidth(), bi.getHeight(), false);
        for (int y = 0; y < bi.getHeight(); y++) {
            for (int x = 0; x < bi.getWidth(); x++) {
                int argb = bi.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8)  & 0xFF;
                int b =  argb        & 0xFF;
                // NativeImage internal format ist ABGR
                ni.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return ni;
    }
}
