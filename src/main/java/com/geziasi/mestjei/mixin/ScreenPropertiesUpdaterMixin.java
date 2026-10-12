package com.geziasi.mestjei.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.screens.Screen;

import mezz.jei.gui.overlay.ScreenPropertiesCache;

import com.geziasi.mestjei.client.JeiPanels;

/**
 * Hides JEI's ingredient list, bookmarks and their buttons in the terminal: both overlays decide
 * whether a screen has room for them from this cache, so for the terminal it records "no screen".
 * JEI's input handling asks the screen helper directly and keeps working.
 */
@Mixin(value = ScreenPropertiesCache.Updater.class, remap = false)
public abstract class ScreenPropertiesUpdaterMixin {
    @ModifyVariable(method = "updateScreen", at = @At("HEAD"), argsOnly = true)
    private Screen mestjei$hideInTerminal(Screen screen) {
        return JeiPanels.hidesJeiOverlays(screen) ? null : screen;
    }
}
