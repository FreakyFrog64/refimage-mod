package com.szaros.refimage.client;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.szaros.refimage.RefImageMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * All commands live under /refimg. Paste the image URL straight into the
 * chat/command bar after "/refimg load " (Ctrl+V works normally there).
 *
 *   /refimg load <url>          - download + show an image (direct link to a .png/.jpg)
 *   /refimg pos <x> <y> <z>     - move it (accepts ~ relative coords too)
 *   /refimg here                - place it at your feet, facing your current direction
 *   /refimg size <width> <height> - resize in blocks
 *   /refimg rotate <yaw> <pitch>  - orient it (pitch 90 = lie flat)
 *   /refimg opacity <0-100>     - set translucency
 *   /refimg toggle              - show/hide without unloading
 *   /refimg remove              - unload the image and free the texture
 *   /refimg info                - print current settings
 */
@EventBusSubscriber(modid = RefImageMod.MODID, value = Dist.CLIENT)
public class RefImageCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("refimg")
                .then(Commands.literal("load")
                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                .executes(ctx -> load(ctx, StringArgumentType.getString(ctx, "url")))))
                .then(Commands.literal("pos")
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(ctx -> setPos(ctx, Vec3Argument.getVec3(ctx, "position")))))
                .then(Commands.literal("here")
                        .executes(RefImageCommands::placeHere))
                .then(Commands.literal("size")
                        .then(Commands.argument("width", FloatArgumentType.floatArg(0.1f))
                                .then(Commands.argument("height", FloatArgumentType.floatArg(0.1f))
                                        .executes(ctx -> setSize(ctx,
                                                FloatArgumentType.getFloat(ctx, "width"),
                                                FloatArgumentType.getFloat(ctx, "height"))))))
                .then(Commands.literal("rotate")
                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                .then(Commands.argument("pitch", FloatArgumentType.floatArg())
                                        .executes(ctx -> setRotation(ctx,
                                                FloatArgumentType.getFloat(ctx, "yaw"),
                                                FloatArgumentType.getFloat(ctx, "pitch"))))))
                .then(Commands.literal("opacity")
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> setOpacity(ctx, IntegerArgumentType.getInteger(ctx, "percent")))))
                .then(Commands.literal("toggle")
                        .executes(RefImageCommands::toggle))
                .then(Commands.literal("remove")
                        .executes(RefImageCommands::remove))
                .then(Commands.literal("info")
                        .executes(RefImageCommands::info))
        );
    }

    private static int load(CommandContext<CommandSourceStack> ctx, String url) {
        msg(ctx, "Loading image...");
        ImageLoader.load(url,
                texId -> {
                    if (ReferenceImageState.textureId != null) {
                        Minecraft.getInstance().getTextureManager().release(ReferenceImageState.textureId);
                    }
                    ReferenceImageState.textureId = texId;
                    ReferenceImageState.visible = true;
                    ReferenceImageState.lastUrl = url;
                    msg(ctx, "Loaded. Try /refimg here, or /refimg pos <x> <y> <z>.");
                },
                error -> msg(ctx, "Failed to load: " + error.getMessage())
        );
        return 1;
    }

    private static int setPos(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
        ReferenceImageState.x = pos.x;
        ReferenceImageState.y = pos.y;
        ReferenceImageState.z = pos.z;
        msg(ctx, String.format("Position set to %.2f, %.2f, %.2f", pos.x, pos.y, pos.z));
        return 1;
    }

    private static int placeHere(CommandContext<CommandSourceStack> ctx) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        Vec3 pos = player.position();
        ReferenceImageState.x = pos.x;
        ReferenceImageState.y = pos.y;
        ReferenceImageState.z = pos.z;
        ReferenceImageState.yaw = player.getYRot();
        msg(ctx, "Placed at your position, facing your current direction.");
        return 1;
    }

    private static int setSize(CommandContext<CommandSourceStack> ctx, float width, float height) {
        ReferenceImageState.width = width;
        ReferenceImageState.height = height;
        msg(ctx, String.format("Size set to %.2f x %.2f blocks", width, height));
        return 1;
    }

    private static int setRotation(CommandContext<CommandSourceStack> ctx, float yaw, float pitch) {
        ReferenceImageState.yaw = yaw;
        ReferenceImageState.pitch = pitch;
        msg(ctx, String.format("Rotation set to yaw %.1f, pitch %.1f", yaw, pitch));
        return 1;
    }

    private static int setOpacity(CommandContext<CommandSourceStack> ctx, int percent) {
        ReferenceImageState.opacity = percent / 100f;
        msg(ctx, "Opacity set to " + percent + "%");
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) {
        ReferenceImageState.visible = !ReferenceImageState.visible;
        msg(ctx, ReferenceImageState.visible ? "Image shown." : "Image hidden.");
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        if (ReferenceImageState.textureId != null) {
            Minecraft.getInstance().getTextureManager().release(ReferenceImageState.textureId);
        }
        ReferenceImageState.textureId = null;
        ReferenceImageState.visible = false;
        msg(ctx, "Removed.");
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        msg(ctx, String.format(
                "visible=%s pos=(%.2f, %.2f, %.2f) size=%.2fx%.2f yaw=%.1f pitch=%.1f opacity=%d%% url=%s",
                ReferenceImageState.visible,
                ReferenceImageState.x, ReferenceImageState.y, ReferenceImageState.z,
                ReferenceImageState.width, ReferenceImageState.height,
                ReferenceImageState.yaw, ReferenceImageState.pitch,
                (int) (ReferenceImageState.opacity * 100),
                ReferenceImageState.lastUrl));
        return 1;
    }

    private static void msg(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSystemMessage(Component.literal("[RefImage] " + text));
    }
}
