package com.geziasi.mestjei.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

import com.lhy.mest.api.client.RegisterMestModulesEvent;

import com.geziasi.mestjei.client.jei.JeiAccess;
import com.geziasi.mestjei.client.panel.JeiBookmarkPanel;
import com.geziasi.mestjei.client.panel.JeiHistoryPanel;
import com.geziasi.mestjei.client.panel.JeiIngredientListPanel;
import com.geziasi.mestjei.client.panel.JeiSearchPanel;

/** Client wiring: the terminal modules and the screen events they need. */
public final class MestJeiClient {
    private MestJeiClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(MestJeiClient::registerModules);
        modBus.addListener(TerminalColors::registerReloadListener);
        JeiInput.register(MinecraftForge.EVENT_BUS);
        MinecraftForge.EVENT_BUS.addListener(JeiPlacement::onScreenInit);
    }

    private static void registerModules(RegisterMestModulesEvent event) {
        event.register(JeiIngredientListPanel.ID, JeiIngredientListPanel::new);
        event.register(JeiBookmarkPanel.ID, JeiBookmarkPanel::new);
        event.register(JeiSearchPanel.ID, JeiSearchPanel::new);
        // JEI's lookup history exists from JEI 15.4x on; older JEI gets no history window.
        event.register(JeiHistoryPanel.ID,
                context -> JeiAccess.historySupported() ? new JeiHistoryPanel(context) : null);
    }
}
