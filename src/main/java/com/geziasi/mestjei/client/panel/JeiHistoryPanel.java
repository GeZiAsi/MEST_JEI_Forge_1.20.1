package com.geziasi.mestjei.client.panel;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;

import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.overlay.elements.IElement;

import com.lhy.mest.api.client.MestModuleContext;
import com.lhy.mest.client.MestButtonSprites;
import com.lhy.mest.client.dock.model.ModuleDefaults;

import com.geziasi.mestjei.client.JeiInput;
import com.geziasi.mestjei.client.jei.JeiAccess;

/**
 * JEI's lookup history (JEI 15.4x): the ingredients whose recipes were looked up most recently,
 * newest first, as JEI shows it under the bookmarks. The title bar carries JEI's own history button
 * (turns the history on or off, same setting as JEI's).
 */
public final class JeiHistoryPanel extends JeiGridPanel {
    public static final String ID = "mestjei:lookup_history";
    private static final int BUTTON = 12;
    private static final int GAP = 2;

    private List<IElement<?>> elements = List.of();
    private int revision = -1;
    @Nullable
    private GuiIconToggleButton historyButton;
    @Nullable
    private IUserInputHandler historyButtonInput;

    public JeiHistoryPanel(MestModuleContext context) {
        super(context, false);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("gui.mestjei.lookup_history");
    }

    @Override
    public int defaultWidth() {
        return widthForColumns(5);
    }

    @Override
    public int defaultHeight() {
        return heightForRows(2);
    }

    @Override
    public int minHeight() {
        return heightForRows(1);
    }

    @Override
    public boolean expandsVertically() {
        return false;
    }

    @Override
    public ModuleDefaults defaults() {
        return new ModuleDefaults(true, true, false, true);
    }

    @Override
    public Icon icon() {
        return Icon.SEARCH_JEI;
    }

    /** JEI's own history-button clock, in the terminal's module buttons and the layout editor. */
    @Override
    public void renderIcon(GuiGraphics g, int x, int y, int w, int h, float opacity) {
        IDrawableStatic clock = JeiAccess.historyIcon(true);
        if (clock == null) {
            super.renderIcon(g, x, y, w, h, opacity);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, opacity);
        clock.draw(g, x + (w - clock.getWidth()) / 2, y + (h - clock.getHeight()) / 2);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // --- List ---------------------------------------------------------------

    /** Like the bookmarks, the history rebuilds its element list per call: re-read after changes. */
    @Override
    protected List<IElement<?>> elements() {
        int current = JeiAccess.bookmarkRevision();
        if (current != revision || elements.isEmpty()) {
            revision = current;
            List<IElement<?>> next = JeiAccess.historyElements();
            if (!next.equals(elements)) {
                elements = next;
                invalidateGrid();
            }
        }
        return elements;
    }

    @Override
    public boolean listEnabled() {
        return JeiAccess.isReady() && JeiAccess.historyEnabled();
    }

    @Nullable
    @Override
    protected Component placeholder() {
        if (!JeiAccess.isReady()) {
            return Component.translatable("gui.mestjei.jei_loading");
        }
        if (!JeiAccess.historyEnabled()) {
            return Component.translatable("gui.mestjei.lookup_history.disabled");
        }
        if (elements().isEmpty()) {
            return Component.translatable("gui.mestjei.lookup_history.empty");
        }
        return null;
    }

    // --- Title bar: JEI's history button ----------------------------------------------

    @Nullable
    private GuiIconToggleButton historyButton() {
        if (historyButton == null && JeiAccess.isReady()) {
            historyButton = JeiAccess.createHistoryButton();
            historyButtonInput = historyButton == null ? null : historyButton.createInputHandler();
        }
        return historyButton;
    }

    private int buttonX() {
        return chromeButtonsLeft() - GAP - BUTTON;
    }

    private int buttonY() {
        return y + 3;
    }

    private boolean inHistoryButton(double mx, double my) {
        return visible && mx >= buttonX() && mx < buttonX() + BUTTON && my >= buttonY() && my < buttonY() + BUTTON;
    }

    @Override
    protected int titleRightInset() {
        return chromeButtonsReserve() + BUTTON + 2 * GAP + 2;
    }

    @Override
    public boolean inTitleBarControls(double mx, double my) {
        return super.inTitleBarControls(mx, my) || inHistoryButton(mx, my);
    }

    @Override
    public void layoutSlots() {
        super.layoutSlots();
        GuiIconToggleButton button = historyButton();
        if (button != null) {
            button.updateBounds(new Rect2i(buttonX(), buttonY(), BUTTON, BUTTON));
        }
    }

    @Override
    public void renderBackgroundContent(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTicks) {
        super.renderBackgroundContent(g, font, mouseX, mouseY, partialTicks);
        if (visible) {
            drawHistoryButton(g, inHistoryButton(mouseX, mouseY));
        }
    }

    /** The terminal's title-bar button showing JEI's history icon, dimmed while the history is off. */
    private void drawHistoryButton(GuiGraphics g, boolean hovered) {
        GuiIconToggleButton button = historyButton();
        if (button != null) {
            button.updateBounds(new Rect2i(buttonX(), buttonY(), BUTTON, BUTTON));
        }
        int bx = buttonX();
        int by = buttonY() + (hovered ? 1 : 0);
        MestButtonSprites.terminalButton(hovered, false).dest(bx, by, BUTTON, BUTTON).blit(g);
        IDrawableStatic icon = JeiAccess.historyIcon(JeiAccess.historyEnabled());
        if (icon == null) {
            return;
        }
        int size = Math.max(icon.getWidth(), icon.getHeight());
        float scale = size > BUTTON - 2 ? (BUTTON - 2) / (float) size : 1.0F;
        g.pose().pushPose();
        g.pose().translate(bx + (BUTTON - icon.getWidth() * scale) / 2.0F,
                by + (BUTTON - icon.getHeight() * scale) / 2.0F, 0);
        g.pose().scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        icon.draw(g);
        g.pose().popPose();
    }

    @Override
    public void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (inHistoryButton(mouseX, mouseY)) {
            GuiIconToggleButton button = historyButton();
            if (button != null) {
                button.drawTooltips(g, mouseX, mouseY);
            }
            return;
        }
        super.renderTooltips(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (visible && inHistoryButton(mx, my)) {
            if (historyButton() != null && historyButtonInput != null) {
                Optional.ofNullable(JeiInput.immediate(mx, my, button))
                        .ifPresent(input -> historyButtonInput.handleUserInput(context.screen(), input,
                                JeiAccess.keyMappings()));
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
