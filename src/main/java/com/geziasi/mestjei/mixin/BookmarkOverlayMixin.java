package com.geziasi.mestjei.mixin;

import java.util.Optional;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;

import com.geziasi.mestjei.client.JeiPanels;

/** The bookmark panel's counterpart of {@link IngredientListOverlayMixin}. */
@Mixin(value = BookmarkOverlay.class, remap = false)
public abstract class BookmarkOverlayMixin {
    @Inject(method = "getIngredientUnderMouse(DD)Ljava/util/stream/Stream;", at = @At("HEAD"), cancellable = true)
    private void mestjei$panelBookmarks(double mouseX, double mouseY,
            CallbackInfoReturnable<Stream<IClickableIngredientInternal<?>>> cir) {
        if (JeiPanels.hidesJeiOverlays(Minecraft.getInstance().screen)) {
            cir.setReturnValue(JeiPanels.bookmarksAt(mouseX, mouseY));
        }
    }

    @Inject(method = "getIngredientUnderMouse()Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void mestjei$panelBookmark(CallbackInfoReturnable<Optional<ITypedIngredient<?>>> cir) {
        if (JeiPanels.hidesJeiOverlays(Minecraft.getInstance().screen)) {
            cir.setReturnValue(JeiPanels.bookmarkUnderMouse());
        }
    }

    @Inject(method = "getIngredientUnderMouse(Lmezz/jei/api/ingredients/IIngredientType;)Ljava/lang/Object;",
            at = @At("HEAD"), cancellable = true)
    private <T> void mestjei$panelBookmarkOfType(IIngredientType<T> type, CallbackInfoReturnable<T> cir) {
        if (JeiPanels.hidesJeiOverlays(Minecraft.getInstance().screen)) {
            cir.setReturnValue(JeiPanels.bookmarkUnderMouse()
                    .flatMap(ingredient -> ingredient.getIngredient(type))
                    .orElse(null));
        }
    }
}
