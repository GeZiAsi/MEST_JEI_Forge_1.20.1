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
import mezz.jei.gui.overlay.IngredientListOverlay;

import com.geziasi.mestjei.client.JeiPanels;

/**
 * In the terminal the ingredient-list panel stands in for JEI's hidden list: whatever JEI asks the
 * list about the mouse (to show recipes, bookmark, give items in cheat mode, hide in edit mode, or
 * for other mods through the API) is answered from the panel.
 */
@Mixin(value = IngredientListOverlay.class, remap = false)
public abstract class IngredientListOverlayMixin {
    @Inject(method = "getIngredientUnderMouse(DD)Ljava/util/stream/Stream;", at = @At("HEAD"), cancellable = true)
    private void mestjei$panelIngredients(double mouseX, double mouseY,
            CallbackInfoReturnable<Stream<IClickableIngredientInternal<?>>> cir) {
        if (JeiPanels.hidesJeiOverlays(Minecraft.getInstance().screen)) {
            cir.setReturnValue(JeiPanels.listIngredientsAt(mouseX, mouseY));
        }
    }

    @Inject(method = "getIngredientUnderMouse()Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void mestjei$panelIngredient(CallbackInfoReturnable<Optional<ITypedIngredient<?>>> cir) {
        if (JeiPanels.hidesJeiOverlays(Minecraft.getInstance().screen)) {
            cir.setReturnValue(JeiPanels.listIngredientUnderMouse());
        }
    }

    @Inject(method = "getIngredientUnderMouse(Lmezz/jei/api/ingredients/IIngredientType;)Ljava/lang/Object;",
            at = @At("HEAD"), cancellable = true)
    private <T> void mestjei$panelIngredientOfType(IIngredientType<T> type, CallbackInfoReturnable<T> cir) {
        if (JeiPanels.hidesJeiOverlays(Minecraft.getInstance().screen)) {
            cir.setReturnValue(JeiPanels.listIngredientUnderMouse()
                    .flatMap(ingredient -> ingredient.getIngredient(type))
                    .orElse(null));
        }
    }
}
