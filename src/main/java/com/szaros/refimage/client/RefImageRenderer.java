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
 * Draws the reference image as a plane anchored in world space (not screen
 * space), so it moves with the world like Litematica's ghost-block preview.
 *
 * Uses RenderType.entityTranslucentEmissive: translucent + full-bright
 * (ignores block/sky lighting, so the image's real colors always show), but
 * still depth-tested against the world, so blocks in front of it occlude it
 * normally — it is NOT an x-ray overlay.
 *
 * The quad is drawn twice with opposite winding/normals so it's visible
 * from both sides regardless of GPU backface culling.
 */
@EventBusSubscriber(modid = RefImageMod.MODID, value = Dist.CLIENT)
public class RefImageRenderer {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ReferenceImageState.visible || ReferenceImageState.textureId == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(
                ReferenceImageState.x - camPos.x,
                ReferenceImageState.y - camPos.y,
                ReferenceImageState.z - camPos.z
        );
        poseStack.mulPose(Axis.YP.rotationDegrees(ReferenceImageState.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(ReferenceImageState.pitch));

        float halfWidth = ReferenceImageState.width / 2f;
        float height = ReferenceImageState.height;
        float alpha = ReferenceImageState.opacity;

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType renderType = RenderType.entityTranslucentEmissive(ReferenceImageState.textureId);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        Matrix4f mat = poseStack.last().pose();
        int light = LightTexture.FULL_BRIGHT;

        // Front face (normal +Z)
        vertex(consumer, poseStack, mat, -halfWidth, 0, 0, 1, alpha, light, 0, 0, 1);
        vertex(consumer, poseStack, mat, -halfWidth, height, 0, 0, alpha, light, 0, 0, 1);
        vertex(consumer, poseStack, mat, halfWidth, height, 1, 0, alpha, light, 0, 0, 1);
        vertex(consumer, poseStack, mat, halfWidth, 0, 1, 1, alpha, light, 0, 0, 1);

        // Back face (normal -Z, reversed winding so it faces the other way)
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
