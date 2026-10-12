package com.geziasi.mestjei.client.jei;

import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;

import com.geziasi.mestjei.MestJei;

/** Hands the panels JEI's runtime once JEI has built its ingredient list and overlays. */
@JeiPlugin
public final class MestJeiPanelsPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(MestJei.MODID, "panels");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiAccess.setRuntime(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiAccess.setRuntime(null);
    }
}
