package com.szaros.refimage.client;

import net.minecraft.resources.ResourceLocation;

/**
 * Holds the state for a single reference image. Deliberately a plain static
 * holder (not a full multi-instance system) to keep the first version simple
 * — one image at a time is enough for lining up a car shape.
 *
 * Position is the BASE of the plane (like a poster leaning on the ground):
 * the image extends from `y` up to `y + height`, centered left/right on
 * `x`/`z` before yaw rotation is applied.
 */
public class ReferenceImageState {
    public static boolean visible = false;
    public static ResourceLocation textureId = null;
    public static String lastUrl = null;

    public static double x = 0;
    public static double y = 0;
    public static double z = 0;

    /** Degrees. 0/90/180/270 = facing south/west/north/east (vanilla yaw convention). */
    public static float yaw = 0f;
    /** Degrees. Use 90 to lay the image flat (e.g. a top-down blueprint). */
    public static float pitch = 0f;

    /** World-space size in blocks. */
    public static float width = 4f;
    public static float height = 3f;

    /** 0.0 (invisible) - 1.0 (opaque). */
    public static float opacity = 0.5f;

    private ReferenceImageState() {}
}
