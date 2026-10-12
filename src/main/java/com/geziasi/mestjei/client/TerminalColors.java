package com.geziasi.mestjei.client;

import java.io.InputStream;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

import com.geziasi.mestjei.MestJei;

/**
 * The terminal's slot colours, read from AE2's {@code guis/terminal.png} as the active resource
 * packs provide it, so the JEI wells match whatever GUI pack the terminal is drawn with.
 *
 * <p>Sampled from the ME grid art: a slot interior, the grid's top-left edge and its bottom-right
 * edge. Stock AE2 gives #8B8B8B with a #373737/#FFFFFF bevel.
 */
public final class TerminalColors {
    private static final ResourceLocation TERMINAL = new ResourceLocation("ae2", "textures/guis/terminal.png");
    private static final int FALLBACK_FILL = 0xFF8B8B8B;
    private static final int FALLBACK_SHADOW = 0xFF373737;
    private static final int FALLBACK_LIGHT = 0xFFFFFFFF;

    private static boolean loaded;
    private static int fill = FALLBACK_FILL;
    private static int shadow = FALLBACK_SHADOW;
    private static int light = FALLBACK_LIGHT;

    private TerminalColors() {
    }

    static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> loaded = false);
    }

    public static int fill() {
        load();
        return fill;
    }

    public static int shadow() {
        load();
        return shadow;
    }

    public static int light() {
        load();
        return light;
    }

    /** Text colour that reads on the well: dark on light wells, light on dark ones. */
    public static int text() {
        int c = fill();
        int luminance = (((c >> 16) & 0xFF) * 299 + ((c >> 8) & 0xFF) * 587 + (c & 0xFF) * 114) / 1000;
        return luminance > 140 ? 0xFF413F54 : 0xFFE8E8E8;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        fill = FALLBACK_FILL;
        shadow = FALLBACK_SHADOW;
        light = FALLBACK_LIGHT;
        var resource = Minecraft.getInstance().getResourceManager().getResource(TERMINAL);
        if (resource.isEmpty()) {
            return;
        }
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            if (image.getWidth() < 170 || image.getHeight() < 72) {
                return;
            }
            fill = argb(image, 12, 22);
            shadow = argb(image, 7, 17);
            light = argb(image, 168, 70);
        } catch (Exception e) {
            MestJei.LOGGER.warn("Could not read the terminal colours from {}", TERMINAL, e);
        }
    }

    /** NativeImage stores ABGR. */
    private static int argb(NativeImage image, int x, int y) {
        int abgr = image.getPixelRGBA(x, y);
        int b = (abgr >> 16) & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int r = abgr & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
