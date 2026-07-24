package com.szaros.refimage.client;

import net.minecraft.resources.ResourceLocation;

/**
 * One loaded reference image. There can be multiple of these now, tracked
 * by ReferenceImageManager and keyed by `id` (e.g. "img1", "img2").
 *
 * Position is the BASE of the plane (like a poster leaning on the ground):
 * the image extends from `y` up to `y + height`, centered left/right on
 * `x`/`z` before yaw rotation is applied.
 */
public class ReferenceImage {
    public final String id;

    /** Either a URL or a local file path, whichever was used to load it — kept so it can be reloaded after a restart. */
    public String source;
    public boolean sourceIsFile;

    public ResourceLocation textureId;
    public boolean visible = true;

    public double x = 0;
    public double y = 0;
    public double z = 0;

    /** Degrees. 0/90/180/270 = facing south/west/north/east (vanilla yaw convention). */
    public float yaw = 0f;
    /** Degrees. Use 90 to lay the image flat (e.g. a top-down blueprint). */
    public float pitch = 0f;

    /** World-space size in blocks. */
    public float width = 4f;
    public float height = 3f;

    /** Native pixel size of the source image, used to keep new images unstretched and for aspect-locked resizing. */
    public int pixelWidth = 1;
    public int pixelHeight = 1;

    /** 0.0 (invisible) - 1.0 (opaque). */
    public float opacity = 0.5f;

    public ReferenceImage(String id) {
        this.id = id;
    }

    /** Sets width/height from the image's real pixel aspect ratio so it never starts stretched. */
    public void applyPixelSize(int pixelWidth, int pixelHeight, float targetLongEdge) {
        this.pixelWidth = Math.max(1, pixelWidth);
        this.pixelHeight = Math.max(1, pixelHeight);
        float aspect = this.pixelWidth / (float) this.pixelHeight;
        if (aspect >= 1f) {
            this.width = targetLongEdge;
            this.height = targetLongEdge / aspect;
        } else {
            this.height = targetLongEdge;
            this.width = targetLongEdge * aspect;
        }
    }
}
