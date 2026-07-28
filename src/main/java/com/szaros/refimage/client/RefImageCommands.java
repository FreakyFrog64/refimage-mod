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
 * All commands live under /refimg. v3: no more "active image" / select —
 * every command that touches an image takes its name (img1, img2, ...) as
 * an explicit trailing argument.
 *
 *   /refimg load <url>                    - download + show an image, reports its name
 *   /refimg loadfile <path>               - load a local file instead
 *   /refimg list                          - list all loaded images and their names
 *   /refimg pos <x> <y> <z> <name>        - move it (~ relative coords OK)
 *   /refimg here <name>                   - place at your feet, yaw snapped to 0/90/180/270, pitch reset to 0
 *   /refimg size <width> <length> <name>  - resize in blocks, can stretch
 *   /refimg scale <width> <name>          - resize in blocks, keeping aspect ratio
 *   /refimg rotate <yaw> <pitch> <name>   - orient it (pitch 90 = lie flat)
 *   /refimg opacity <0-100> <name>        - set translucency
 *   /refimg toggle <name>                 - show/hide without unloading
 *   /refimg remove <name>                 - unload and free the texture
 *   /refimg info <name>                   - print current settings
 */
@EventBusSubscriber(modid = RefImageMod.MODID, value = Dist.CLIENT)
public class RefImageCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ReferenceImageManager.ensureLoaded();

        event.getDispatcher().register(Commands.literal("refimg")
                .then(Commands.literal("load")
                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                .executes(ctx -> load(ctx, StringArgumentType.getString(ctx, "url")))))
                .then(Commands.literal("loadfile")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> loadFile(ctx, StringArgumentType.getString(ctx, "path")))))
                .then(Commands.literal("list")
                        .executes(RefImageCommands::list))
                .then(Commands.literal("pos")
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> setPos(ctx,
                                                Vec3Argument.getVec3(ctx, "position"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("here")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> placeHere(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("size")
                        .then(Commands.argument("width", FloatArgumentType.floatArg(0.1f))
                                .then(Commands.argument("length", FloatArgumentType.floatArg(0.1f))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> setSize(ctx,
                                                        FloatArgumentType.getFloat(ctx, "width"),
                                                        FloatArgumentType.getFloat(ctx, "length"),
                                                        StringArgumentType.getString(ctx, "name")))))))
                .then(Commands.literal("scale")
                        .then(Commands.argument("width", FloatArgumentType.floatArg(0.1f))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> setScale(ctx,
                                                FloatArgumentType.getFloat(ctx, "width"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("rotate")
                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                .then(Commands.argument("pitch", FloatArgumentType.floatArg())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> setRotation(ctx,
                                                        FloatArgumentType.getFloat(ctx, "yaw"),
                                                        FloatArgumentType.getFloat(ctx, "pitch"),
                                                        StringArgumentType.getString(ctx, "name")))))))
                .then(Commands.literal("opacity")
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> setOpacity(ctx,
                                                IntegerArgumentType.getInteger(ctx, "percent"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("toggle")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> toggle(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> remove(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "name")))))
        );
    }

    private static int load(CommandContext<CommandSourceStack> ctx, String url) {
        String id = ReferenceImageManager.nextId();
        ReferenceImage img = ReferenceImageManager.create(id);
        img.source = url;
        img.sourceIsFile = false;
        msg(ctx, "Loading " + id + "...");
        ImageLoader.loadFromUrl(url,
                result -> {
                    img.textureId = result.textureId();
                    img.applyPixelSize(result.width(), result.height(), 4f);
                    img.visible = true;
                    ReferenceImageManager.save();
                    msg(ctx, id + " loaded. Try /refimg here " + id);
                },
                error -> {
                    ReferenceImageManager.remove(id);
                    msg(ctx, "Failed to load: " + error.getMessage());
                });
        return 1;
    }

    private static int loadFile(CommandContext<CommandSourceStack> ctx, String path) {
        String id = ReferenceImageManager.nextId();
        ReferenceImage img = ReferenceImageManager.create(id);
        img.source = path;
        img.sourceIsFile = true;
        msg(ctx, "Loading " + id + " from disk...");
        ImageLoader.loadFromFile(path,
                result -> {
                    img.textureId = result.textureId();
                    img.applyPixelSize(result.width(), result.height(), 4f);
                    img.visible = true;
                    ReferenceImageManager.save();
                    msg(ctx, id + " loaded. Try /refimg here " + id);
                },
                error -> {
                    ReferenceImageManager.remove(id);
                    msg(ctx, "Failed to load: " + error.getMessage());
                });
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        if (ReferenceImageManager.isEmpty()) {
            msg(ctx, "No images loaded.");
            return 1;
        }
        for (ReferenceImage img : ReferenceImageManager.all()) {
            msg(ctx, img.id + " - " + (img.visible ? "visible" : "hidden") + " - " + img.source);
        }
        return 1;
    }

    private static int setPos(CommandContext<CommandSourceStack> ctx, Vec3 pos, String name) {
        ReferenceImage img = require(ctx, name);
        if (img == null) return 0;
        img.x = pos.x;
        img.y = pos.y;
        img.z = pos.z;
        ReferenceImageManager.save();
        msg(ctx, String.format("%s position set to %.2f, %.2f, %.2f", img.id, pos.x, pos.y, pos.z));
        return 1;
    }

    private static int placeHere(CommandContext<CommandSourceStack> ctx, String name) {
        ReferenceImage img = require(ctx, name);
        if (img == null) return 0;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;

        Vec3 pos = player.position();
        img.x = pos.x;
        img.y = pos.y;
        img.z = pos.z;

        // Snap to the nearest cardinal direction (0/90/180/270) — fine-tuning
        // beyond that is what /refimg rotate is for. Pitch resets to level.
        float rounded = Math.round(player.getYRot() / 90f) * 90f;
        rounded = ((rounded % 360f) + 360f) % 360f;
        img.yaw = rounded;
        img.pitch = 0f;

        ReferenceImageManager.save();
        msg(ctx, String.format("%s placed at your position, facing %.0f°.", img.id, img.yaw));
        return 1;
    }

    private static int setSize(CommandContext<CommandSourceStack> ctx, float width, float length, String name) {
        ReferenceImage img = require(ctx, name);
        if (img == null) return 0;
        img.width = width;
        img.height = length;
        ReferenceImageManager.save();
        msg(ctx, String.format("%s size set to %.2f x %.2f blocks", img.id, width, length));
        return 1;
    }

    private static int setScale(CommandContext<CommandSourceStack> ctx, float width, String name) {
        ReferenceImage img = require(ctx, name);
        if (img == null) return 0;
        float aspect = img.pixelWidth / (float) img.pixelHeight;
        img.width = width;
        img.height = width / aspect;
        ReferenceImageManager.save();
        msg(ctx, String.format("%s scaled to %.2f x %.2f blocks", img.id, img.width, img.height));
        return 1;
    }

    private static int setRotation(CommandContext<CommandSourceStack> ctx, float yaw, float pitch, String name) {
        ReferenceImage img = require(ctx, name);
        if (img == null) return 0;
        img.yaw = yaw;
        img.pitch = pitch;
        ReferenceImageManager.save();
        msg(ctx, String.format("%s rotation set to yaw %.1f, pitch %.1f", img.id, yaw, pitch));
        return 1;
    }

    private static int setOpacity(CommandContext<CommandSourceStack> ctx, int percent, String name) {
        ReferenceImage img = require(ctx, name);
        if (img == null) return 0;
        img.opacity = percent / 100f;
        ReferenceImageManager.save();
        msg(ctx, img.id + " opacity set to " + percent + "%");
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, String name) {
        ReferenceImage img = require(ctx, name);
        if (img == null) return 0;
        img.visible = !img.visible;
        ReferenceImageManager.save();
        msg(ctx, img.id + (img.visible ? " shown." : " hidden."));
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, String name) {
        ReferenceImage img = require(ctx, name);
        if (img == null) return 0;
        ReferenceImageManager.remove(name);
        msg(ctx, name + " removed.");
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String name) {
        ReferenceImage img = require(ctx, name);
        if (img == null) return 0;
        msg(ctx, String.format(
                "%s: visible=%s pos=(%.2f, %.2f, %.2f) size=%.2fx%.2f yaw=%.1f pitch=%.1f opacity=%d%% source=%s",
                img.id, img.visible, img.x, img.y, img.z, img.width, img.height,
                img.yaw, img.pitch, (int) (img.opacity * 100), img.source));
        return 1;
    }

    private static ReferenceImage require(CommandContext<CommandSourceStack> ctx, String name) {
        ReferenceImage img = ReferenceImageManager.get(name);
        if (img == null) {
            msg(ctx, "No image named " + name + ". Use /refimg list to see loaded images.");
        }
        return img;
    }

    private static void msg(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSystemMessage(Component.literal("[RefImage] " + text));
    }
}
