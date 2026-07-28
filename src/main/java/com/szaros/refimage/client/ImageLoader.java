package com.szaros.refimage.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.szaros.refimage.RefImageMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Fetches an image off-thread (from a URL or a local file) and decodes it,
 * then hops back onto the render thread to create the GPU texture (required
 * — texture upload must happen on the render thread).
 *
 * Decoding goes straight through LWJGL's STBImage rather than
 * NativeImage.read(), because NativeImage.read() only accepts PNG (it
 * checks for a PNG file signature and throws "Bad PNG Signature" on
 * anything else) even though the decoder underneath supports more. Calling
 * STBImage directly gets PNG, JPEG, BMP, TGA, PSD, GIF (first frame), HDR
 * and PIC — everything stb_image supports. WEBP is still not supported;
 * that would need a completely different decoder.
 */
public class ImageLoader {

    public record LoadResult(ResourceLocation textureId, int width, int height) {}

    private static final AtomicInteger TEXTURE_COUNTER = new AtomicInteger();

    public static void loadFromUrl(String urlString, Consumer<LoadResult> onSuccess, Consumer<Throwable> onError) {
        run(() -> {
            URI uri = URI.create(urlString.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("URL must start with http:// or https://");
            }
            URL url = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (RefImageMod)");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(true);

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + status
                        + " — make sure this is a direct image link, not a webpage.");
            }
            try (InputStream in = connection.getInputStream()) {
                return decode(in);
            }
        }, onSuccess, onError);
    }

    public static void loadFromFile(String pathString, Consumer<LoadResult> onSuccess, Consumer<Throwable> onError) {
        run(() -> {
            Path path = Path.of(pathString.trim());
            if (!path.isAbsolute()) {
                // Relative paths resolve against the .minecraft / instance folder,
                // so e.g. "refimages/car.png" works without a full path.
                path = Minecraft.getInstance().gameDirectory.toPath().resolve(path);
            }
            if (!Files.exists(path)) {
                throw new IOException("File not found: " + path);
            }
            try (InputStream in = Files.newInputStream(path)) {
                return decode(in);
            }
        }, onSuccess, onError);
    }

    /** Decodes any stb_image-supported format (PNG/JPEG/BMP/TGA/PSD/GIF/HDR/PIC) into a NativeImage. */
    private static NativeImage decode(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        // stb_image needs a direct (native) buffer, not a heap byte[]-backed one.
        ByteBuffer fileBuffer = ByteBuffer.allocateDirect(bytes.length);
        fileBuffer.put(bytes);
        fileBuffer.flip();

        ByteBuffer decodedPixels;
        int width;
        int height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channelsInFile = stack.mallocInt(1);
            // Force 4 channels (RGBA) regardless of source format, so grayscale/RGB/CMYK
            // sources all come out the same shape.
            decodedPixels = STBImage.stbi_load_from_memory(fileBuffer, w, h, channelsInFile, 4);
            if (decodedPixels == null) {
                throw new IOException("Could not decode image: " + STBImage.stbi_failure_reason());
            }
            width = w.get(0);
            height = h.get(0);
        }

        try {
            // NativeImage's pixel layout is the same byte order (R,G,B,A per pixel) that
            // stb_image just produced, so this is a straight per-pixel repack into the
            // packed int format setPixelRGBA expects, not a channel reorder.
            NativeImage image = new NativeImage(width, height, true);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = (y * width + x) * 4;
                    int r = decodedPixels.get(i) & 0xFF;
                    int g = decodedPixels.get(i + 1) & 0xFF;
                    int b = decodedPixels.get(i + 2) & 0xFF;
                    int a = decodedPixels.get(i + 3) & 0xFF;
                    int packed = (a << 24) | (b << 16) | (g << 8) | r;
                    image.setPixelRGBA(x, y, packed);
                }
            }
            return image;
        } finally {
            STBImage.stbi_image_free(decodedPixels);
        }
    }

    private static void run(Callable<NativeImage> reader, Consumer<LoadResult> onSuccess, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return reader.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, Util.backgroundExecutor()).whenComplete((image, throwable) -> {
            // Always finish on the main/render thread — GPU texture upload requires it.
            Minecraft.getInstance().execute(() -> {
                if (throwable != null) {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    onError.accept(cause);
                    return;
                }
                try {
                    ResourceLocation texId = ResourceLocation.fromNamespaceAndPath(
                            RefImageMod.MODID, "reference_image_" + TEXTURE_COUNTER.incrementAndGet());
                    int w = image.getWidth();
                    int h = image.getHeight();
                    DynamicTexture texture = new DynamicTexture(image);
                    Minecraft.getInstance().getTextureManager().register(texId, texture);
                    onSuccess.accept(new LoadResult(texId, w, h));
                } catch (Exception e) {
                    onError.accept(e);
                }
            });
        });
    }
}
