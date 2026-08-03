package com.szaros.refimage.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.szaros.refimage.RefImageMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Tracks every loaded reference image, keyed by an auto-generated id like
 * "img1". There's no "active image" concept — every command that acts on
 * an image takes its name as an explicit argument.
 *
 * Images are scoped per world/server: each singleplayer save and each
 * multiplayer server address gets its own config/refimage-mod/<key>.json,
 * so images loaded in one world don't show up in another. ensureLoaded()
 * is called every render frame (cheap — it's a string comparison in the
 * common case) and reloads automatically whenever the detected world/server
 * key changes, so switching worlds "just works" without needing to hook
 * join/leave events directly.
 */
public class ReferenceImageManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ReferenceImage> IMAGES = new LinkedHashMap<>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static boolean loaded = false;
    private static String loadedWorldKey = null;

    private ReferenceImageManager() {}

    /** Loads (or reloads, if the world/server changed) images for whichever world you're currently in. */
    public static synchronized void ensureLoaded() {
        String key = currentWorldKey();
        if (loaded && key.equals(loadedWorldKey)) return;

        clearInMemory();
        loadedWorldKey = key;
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

    private static void clearInMemory() {
        for (ReferenceImage img : IMAGES.values()) {
            if (img.textureId != null) {
                Minecraft.getInstance().getTextureManager().release(img.textureId);
            }
        }
        IMAGES.clear();
        NEXT_ID.set(1);
    }

    // ---- per-world identification ----

    /**
     * A stable-ish key for "which world/server am I in right now":
     * "sp_<save folder name>" for singleplayer/LAN, "mp_<server address>"
     * for multiplayer. This is the part I'm least certain compiles cleanly
     * as-is (getWorldPath/LevelResource specifically) — if it doesn't,
     * the fallback below (world display name) is the easy substitute.
     */
    private static String currentWorldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            try {
                String folderName = mc.getSingleplayerServer()
                        .getWorldPath(LevelResource.ROOT)
                        .getFileName()
                        .toString();
                return sanitize("sp_" + folderName);
            } catch (Exception e) {
                return "sp_unknown";
            }
        }
        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null) {
            return sanitize("mp_" + server.ip.toLowerCase(Locale.ROOT));
        }
        return "unknown";
    }

    private static String sanitize(String key) {
        return key.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ---- persistence ----

    private static Path configPath() {
        String key = loadedWorldKey != null ? loadedWorldKey : currentWorldKey();
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("refimage-mod").resolve(key + ".json");
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
            RefImageMod.LOGGER.error("Failed to save refimage-mod config", e);
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
            RefImageMod.LOGGER.error("Failed to load refimage-mod config", e);
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
