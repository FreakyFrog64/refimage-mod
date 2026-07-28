package com.szaros.refimage.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.szaros.refimage.RefImageMod;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Tracks every loaded reference image, keyed by an auto-generated id like
 * "img1". There's no "active image" concept (v3) — every command that acts
 * on an image takes its name as an explicit argument.
 *
 * Also saves/loads to config/refimage-mod.json so images survive relogs and
 * full game restarts. Textures themselves aren't saved (can't serialize a
 * GPU handle) — instead the source URL/file path is saved and the image is
 * re-downloaded/re-read in the background next time the game starts.
 */
public class ReferenceImageManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ReferenceImage> IMAGES = new LinkedHashMap<>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static boolean loaded = false;

    private ReferenceImageManager() {}

    /** Loads saved images from disk. Safe to call repeatedly — only does anything once per game session. */
    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        loadFromDisk();
    }

    public static String nextId() {
        return "img" + NEXT_ID.getAndIncrement();
    }

    public static ReferenceImage create(String id) {
        ReferenceImage img = new ReferenceImage(id);
        IMAGES.put(id, img);
        return img;
    }

    public static ReferenceImage get(String id) {
        return IMAGES.get(id);
    }

    public static void remove(String id) {
        ReferenceImage img = IMAGES.remove(id);
        if (img != null && img.textureId != null) {
            Minecraft.getInstance().getTextureManager().release(img.textureId);
        }
        save();
    }

    public static Collection<ReferenceImage> all() {
        return IMAGES.values();
    }

    public static boolean isEmpty() {
        return IMAGES.isEmpty();
    }

    // ---- persistence ----

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("refimage-mod.json");
    }

    public static void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            List<SavedEntry> entries = new ArrayList<>();
            for (ReferenceImage img : IMAGES.values()) {
                entries.add(SavedEntry.from(img));
            }
            SavedFile file = new SavedFile();
            file.nextId = NEXT_ID.get();
            file.images = entries;
            Files.writeString(path, GSON.toJson(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            RefImageMod.LOGGER.error("Failed to save refimage-mod.json", e);
        }
    }

    private static void loadFromDisk() {
        Path path = configPath();
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            SavedFile file = GSON.fromJson(json, SavedFile.class);
            if (file == null || file.images == null) return;

            for (SavedEntry entry : file.images) {
                ReferenceImage img = entry.toReferenceImage();
                IMAGES.put(img.id, img);
                reload(img);
            }
            if (file.nextId > NEXT_ID.get()) {
                NEXT_ID.set(file.nextId);
            }
        } catch (Exception e) {
            RefImageMod.LOGGER.error("Failed to load refimage-mod.json", e);
        }
    }

    /** Re-downloads/re-reads a restored image's texture without changing its saved position/size/etc. */
    private static void reload(ReferenceImage img) {
        Consumer<ImageLoader.LoadResult> onSuccess = result -> {
            img.textureId = result.textureId();
            img.pixelWidth = result.width();
            img.pixelHeight = result.height();
        };
        Consumer<Throwable> onError = err ->
                RefImageMod.LOGGER.warn("Could not reload saved image '{}': {}", img.id, err.getMessage());

        if (img.sourceIsFile) {
            ImageLoader.loadFromFile(img.source, onSuccess, onError);
        } else {
            ImageLoader.loadFromUrl(img.source, onSuccess, onError);
        }
    }

    private static class SavedFile {
        int nextId = 1;
        List<SavedEntry> images;
    }

    private static class SavedEntry {
        String id;
        String source;
        boolean sourceIsFile;
        double x, y, z;
        float yaw, pitch, width, height, opacity;
        boolean visible;
        int pixelWidth, pixelHeight;

        static SavedEntry from(ReferenceImage img) {
            SavedEntry e = new SavedEntry();
            e.id = img.id;
            e.source = img.source;
            e.sourceIsFile = img.sourceIsFile;
            e.x = img.x;
            e.y = img.y;
            e.z = img.z;
            e.yaw = img.yaw;
            e.pitch = img.pitch;
            e.width = img.width;
            e.height = img.height;
            e.opacity = img.opacity;
            e.visible = img.visible;
            e.pixelWidth = img.pixelWidth;
            e.pixelHeight = img.pixelHeight;
            return e;
        }

        ReferenceImage toReferenceImage() {
            ReferenceImage img = new ReferenceImage(id);
            img.source = source;
            img.sourceIsFile = sourceIsFile;
            img.x = x;
            img.y = y;
            img.z = z;
            img.yaw = yaw;
            img.pitch = pitch;
            img.width = width;
            img.height = height;
            img.opacity = opacity;
            img.visible = visible;
            img.pixelWidth = Math.max(1, pixelWidth);
            img.pixelHeight = Math.max(1, pixelHeight);
            return img;
        }
    }
}
