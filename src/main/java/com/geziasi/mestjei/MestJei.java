package com.geziasi.mestjei;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import com.geziasi.mestjei.client.MestJeiClient;

/**
 * ME Spliced Terminal add-on: JEI's ingredient list and bookmarks as spliceable terminal panels.
 * Client only; on a dedicated server the mod loads and does nothing.
 */
@Mod(MestJei.MODID)
public final class MestJei {
    public static final String MODID = "mestjei";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MestJei() {
        // Nothing is sent over the network, so servers and clients need not both have it.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY,
                        (remoteVersion, fromServer) -> true));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MestJeiClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
