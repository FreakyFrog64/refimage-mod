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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Fetches + decodes an image off-thread, then hops back onto the render
 * thread to create the GPU texture (required — texture upload must happen
 * on the render thread).
 */
public class ImageLoader {

    private static final AtomicInteger TEXTURE_COUNTER = new AtomicInteger();

    public static void load(String urlString, Consumer<ResourceLocation> onSuccess, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(() -> {
            try {
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
                    // NativeImage.read decodes PNG/JPG via stb_image. Anything else
                    // (webp, gif animations, etc.) will throw here.
                    return NativeImage.read(in);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, Util.backgroundExecutor()).whenComplete((image, throwable) -> {
            // Always finish on the main/render thread.
            Minecraft.getInstance().execute(() -> {
                if (throwable != null) {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    onError.accept(cause);
                    return;
                }
                try {
                    ResourceLocation texId = ResourceLocation.fromNamespaceAndPath(
                            RefImageMod.MODID, "reference_image_" + TEXTURE_COUNTER.incrementAndGet());
                    DynamicTexture texture = new DynamicTexture(image);
                    Minecraft.getInstance().getTextureManager().register(texId, texture);
                    onSuccess.accept(texId);
                } catch (Exception e) {
                    onError.accept(e);
                }
            });
        });
    }
}
