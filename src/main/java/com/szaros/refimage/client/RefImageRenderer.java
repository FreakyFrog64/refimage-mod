package com.szaros.refimage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.szaros.refimage.RefImageMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws every visible reference image as a plane anchored in world space
 * (not screen space), so each one moves with the world like Litematica's
 * ghost-block preview.
 *
 * Uses RenderType.entityTranslucentEmissive: translucent + full-bright
 * (ignores block/sky lighting, so the image's real colors always show), but
 * still depth-tested against the world, so blocks in front of it occlude it
 * normally — it is NOT an x-ray overlay.
 *
 * Rendered at AFTER_WEATHER (late in the pipeline, after clouds/particles)
 * so clouds don't paint over it — AFTER_TRANSLUCENT_BLOCKS (the earlier
 * stage this used at first) draws before clouds do, which is why they
 * showed up in front.
 *
 * Each quad is drawn twice with opposite winding/normals so it's visible
 * from both sides regardless of GPU backface culling.
 */
@EventBusSubscriber(modid = RefImageMod.MODID, value = Dist.CLIENT)
public class RefImageRenderer {

    private static final RenderLevelStageEvent.Stage RENDER_STAGE = RenderLevelStageEvent.Stage.AFTER_WEATHER;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RENDER_STAGE) {
            return;
        }
        ReferenceImageManager.ensureLoaded();
        if (ReferenceImageManager.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        for (ReferenceImage img : ReferenceImageManager.all()) {
            if (!img.visible || img.textureId == null) continue;
            renderOne(img, poseStack, camPos, bufferSource);
        }
    }

    private static void renderOne(ReferenceImage img, PoseStack poseStack, Vec3 camPos,
                                   MultiBufferSource.BufferSource bufferSource) {
        poseStack.pushPose();
        poseStack.translate(img.x - camPos.x, img.y - camPos.y, img.z - camPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(img.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(img.pitch));

        float halfWidth = img.width / 2f;
        float height = img.height;
        float alpha = img.opacity;

        RenderType renderType = RenderType.entityTranslucentEmissive(img.textureId);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        Matrix4f mat = poseStack.last().pose();
        int light = LightTexture.FULL_BRIGHT;

        // Front face (normal +Z)
        vertex(consumer, poseStack, mat, -halfWidth, 0, 0, 1, alpha, light, 0, 0, 1);
        vertex(consumer, poseStack, mat, -halfWidth, height, 0, 0, alpha, light, 0, 0, 1);
        vertex(consumer, poseStack, mat, halfWidth, height, 1, 0, alpha, light, 0, 0, 1);
        vertex(consumer, poseStack, mat, halfWidth, 0, 1, 1, alpha, light, 0, 0, 1);

        // Back face (normal -Z, reversed winding)
        vertex(consumer, poseStack, mat, halfWidth, 0, 1, 1, alpha, light, 0, 0, -1);
        vertex(consumer, poseStack, mat, halfWidth, height, 1, 0, alpha, light, 0, 0, -1);
        vertex(consumer, poseStack, mat, -halfWidth, height, 0, 0, alpha, light, 0, 0, -1);
        vertex(consumer, poseStack, mat, -halfWidth, 0, 0, 1, alpha, light, 0, 0, -1);

        bufferSource.endBatch(renderType);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack poseStack, Matrix4f mat,
                                float x, float y, float u, float v, float alpha, int light,
                                float nx, float ny, float nz) {
        consumer.addVertex(mat, x, y, 0)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(poseStack.last(), nx, ny, nz);
    }
}
