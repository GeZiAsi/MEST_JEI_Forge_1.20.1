package com.geziasi.mestjei.client.panel;

import net.minecraft.client.gui.GuiGraphics;

import com.lhy.mest.api.client.MestModuleContext;

/** What the screen hooks need from every JEI panel of a terminal. */
public interface JeiTerminalPanel {
    String id();

    MestModuleContext context();

    /** Whether this panel is the topmost window at the point in its terminal. */
    boolean isTopAt(double mouseX, double mouseY);

    /** Tooltips are drawn after the whole screen so no window above can cover them. */
    void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY);

    void tick();
}
