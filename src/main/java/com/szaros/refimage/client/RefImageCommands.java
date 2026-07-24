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
 * All commands live under /refimg. Every image gets an auto id (img1,
 * img2, ...). Commands that don't take an id (pos, here, size, rotate,
 * opacity, toggle, remove, info) act on the "active" image — whichever
 * was loaded or /refimg select'ed most recently.
 *
 *   /refimg load <url>              - download + show an image, becomes active
 *   /refimg loadfile <path>         - load a local file instead, becomes active
 *   /refimg list                    - list all loaded images
 *   /refimg select <id>             - make an image active
 *   /refimg pos <x> <y> <z>         - move the active image (~ relative coords OK)
 *   /refimg here                    - place it at your feet, facing your direction
 *   /refimg size <width> <height>   - resize in blocks (can stretch)
 *   /refimg size <width>            - resize keeping the image's aspect ratio
 *   /refimg rotate <yaw> <pitch>    - orient it (pitch 90 = lie flat)
 *   /refimg opacity <0-100>         - set translucency
 *   /refimg toggle                  - show/hide without unloading
 *   /refimg remove [id]             - unload (active image, or a specific id)
 *   /refimg info                    - print the active image's settings
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
                .then(Commands.literal("select")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> select(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("pos")
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(ctx -> setPos(ctx, Vec3Argument.getVec3(ctx, "position")))))
                .then(Commands.literal("here")
                        .executes(RefImageCommands::placeHere))
                .then(Commands.literal("size")
                        .then(Commands.argument("width", FloatArgumentType.floatArg(0.1f))
                                .executes(ctx -> setSizeKeepAspect(ctx, FloatArgumentType.getFloat(ctx, "width")))
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
                        .executes(RefImageCommands::removeActive)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> removeById(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("info")
                        .executes(RefImageCommands::info))
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
                    msg(ctx, id + " loaded and selected. Try /refimg here.");
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
                    msg(ctx, id + " loaded and selected. Try /refimg here.");
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
            boolean active = img.id.equals(ReferenceImageManager.getActiveId());
            msg(ctx, (active ? "* " : "  ") + img.id + " - " + (img.visible ? "visible" : "hidden")
                    + " - " + img.source);
        }
        return 1;
    }

    private static int select(CommandContext<CommandSourceStack> ctx, String id) {
        if (!ReferenceImageManager.select(id)) {
            msg(ctx, "No image named " + id + ". Use /refimg list to see loaded images.");
            return 0;
        }
        msg(ctx, id + " selected.");
        return 1;
    }

    private static int setPos(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
        ReferenceImage img = active(ctx);
        if (img == null) return 0;
        img.x = pos.x;
        img.y = pos.y;
        img.z = pos.z;
        ReferenceImageManager.save();
        msg(ctx, String.format("%s position set to %.2f, %.2f, %.2f", img.id, pos.x, pos.y, pos.z));
        return 1;
    }

    private static int placeHere(CommandContext<CommandSourceStack> ctx) {
        ReferenceImage img = active(ctx);
        if (img == null) return 0;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        Vec3 pos = player.position();
        img.x = pos.x;
        img.y = pos.y;
        img.z = pos.z;
        img.yaw = player.getYRot();
        ReferenceImageManager.save();
        msg(ctx, img.id + " placed at your position, facing your current direction.");
        return 1;
    }

    private static int setSize(CommandContext<CommandSourceStack> ctx, float width, float height) {
        ReferenceImage img = active(ctx);
        if (img == null) return 0;
        img.width = width;
        img.height = height;
        ReferenceImageManager.save();
        msg(ctx, String.format("%s size set to %.2f x %.2f blocks", img.id, width, height));
        return 1;
    }

    private static int setSizeKeepAspect(CommandContext<CommandSourceStack> ctx, float width) {
        ReferenceImage img = active(ctx);
        if (img == null) return 0;
        float aspect = img.pixelWidth / (float) img.pixelHeight;
        img.width = width;
        img.height = width / aspect;
        ReferenceImageManager.save();
        msg(ctx, String.format("%s size set to %.2f x %.2f blocks (aspect kept)", img.id, img.width, img.height));
        return 1;
    }

    private static int setRotation(CommandContext<CommandSourceStack> ctx, float yaw, float pitch) {
        ReferenceImage img = active(ctx);
        if (img == null) return 0;
        img.yaw = yaw;
        img.pitch = pitch;
        ReferenceImageManager.save();
        msg(ctx, String.format("%s rotation set to yaw %.1f, pitch %.1f", img.id, yaw, pitch));
        return 1;
    }

    private static int setOpacity(CommandContext<CommandSourceStack> ctx, int percent) {
        ReferenceImage img = active(ctx);
        if (img == null) return 0;
        img.opacity = percent / 100f;
        ReferenceImageManager.save();
        msg(ctx, img.id + " opacity set to " + percent + "%");
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) {
        ReferenceImage img = active(ctx);
        if (img == null) return 0;
        img.visible = !img.visible;
        ReferenceImageManager.save();
        msg(ctx, img.id + (img.visible ? " shown." : " hidden."));
        return 1;
    }

    private static int removeActive(CommandContext<CommandSourceStack> ctx) {
        ReferenceImage img = active(ctx);
        if (img == null) return 0;
        ReferenceImageManager.remove(img.id);
        msg(ctx, img.id + " removed.");
        return 1;
    }

    private static int removeById(CommandContext<CommandSourceStack> ctx, String id) {
        if (ReferenceImageManager.get(id) == null) {
            msg(ctx, "No image named " + id);
            return 0;
        }
        ReferenceImageManager.remove(id);
        msg(ctx, id + " removed.");
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ReferenceImage img = active(ctx);
        if (img == null) return 0;
        msg(ctx, String.format(
                "%s: visible=%s pos=(%.2f, %.2f, %.2f) size=%.2fx%.2f yaw=%.1f pitch=%.1f opacity=%d%% source=%s",
                img.id, img.visible, img.x, img.y, img.z, img.width, img.height,
                img.yaw, img.pitch, (int) (img.opacity * 100), img.source));
        return 1;
    }

    private static ReferenceImage active(CommandContext<CommandSourceStack> ctx) {
        ReferenceImage img = ReferenceImageManager.getActive();
        if (img == null) {
            msg(ctx, "No image selected. Use /refimg load <url> first.");
        }
        return img;
    }

    private static void msg(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSystemMessage(Component.literal("[RefImage] " + text));
    }
}
