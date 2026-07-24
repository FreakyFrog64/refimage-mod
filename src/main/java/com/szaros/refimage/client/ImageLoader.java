package com.szaros.refimage.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.szaros.refimage.RefImageMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Fetches + decodes an image off-thread (from a URL or a local file), then
 * hops back onto the render thread to create the GPU texture (required —
 * texture upload must happen on the render thread).
 *
 * Format support comes from NativeImage.read, which uses stb_image under
 * the hood: PNG and JPEG both work out of the box. WEBP is NOT supported —
 * stb_image doesn't decode it, so a webp source will fail to load.
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
                return NativeImage.read(in);
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
                return NativeImage.read(in);
            }
        }, onSuccess, onError);
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
