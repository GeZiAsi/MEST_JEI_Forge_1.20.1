package com.geziasi.mestjei.client.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.widgets.AETextField;

import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.Internal;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.overlay.ConfigButton;

import com.lhy.mest.api.client.MestModuleContext;
import com.lhy.mest.client.MestButtonSprites;
import com.lhy.mest.client.dock.ModulePanel;
import com.lhy.mest.client.dock.model.ModuleDefaults;

import com.geziasi.mestjei.client.JeiInput;
import com.geziasi.mestjei.client.JeiPanels;
import com.geziasi.mestjei.client.jei.JeiAccess;

/**
 * JEI's search bar as its own terminal module: a title strip holding the search box and JEI's
 * config button. It can float on its own or be spliced into any window (by default on top of the
 * ingredient list). The box edits JEI's own filter, so the text, syntax and results are JEI's.
 */
public final class JeiSearchPanel extends ModulePanel implements JeiTerminalPanel {
    public static final String ID = "mestjei:search";
    private static final int SEARCH_TOP = 4;
    private static final int SEARCH_HEIGHT = 12;
    private static final int BUTTON = 12;
    private static final int GAP = 2;
    private static final int LEFT = 6;
    private static final int MAX_SEARCH_LENGTH = 128;
    /** JEI's colour for a search that matches nothing. */
    private static final int NO_RESULTS_COLOR = 0xFFFF0000;
    /** JEI keeps one search history for the session; so do the panels. */
    private static final List<String> HISTORY = new ArrayList<>();
    private static final int HISTORY_SIZE = 100;

    private final MestModuleContext context;
    private final AETextField search;
    private final int textColor;
    @Nullable
    private ConfigButton configButton;
    @Nullable
    private IUserInputHandler configButtonInput;
    private int historyIndex = -1;

    public JeiSearchPanel(MestModuleContext context) {
        this.context = context;
        this.search = new AETextField(context.style(), Minecraft.getInstance().font, 0, 0, 0, 0);
        // As AE2's widget container sets up its search fields: AE2 draws the box itself.
        this.search.setBordered(false);
        this.search.setMaxLength(MAX_SEARCH_LENGTH);
        this.textColor = context.style().getColor(PaletteColor.TEXTFIELD_TEXT).toARGB();
        this.search.setPlaceholder(Component.translatable("gui.mestjei.search.placeholder"));
        this.search.setTooltipMessage(List.of(
                Component.translatable("gui.mestjei.search.tooltip"),
                Component.translatable("gui.mestjei.search.tooltip.prefixes").withStyle(ChatFormatting.GRAY),
                Component.translatable("gui.mestjei.search.tooltip.clear").withStyle(ChatFormatting.GRAY)));
        this.search.setValue(JeiAccess.filterText());
        this.search.setResponder(JeiAccess::setFilterText);
        JeiPanels.register(context.screen(), this);
    }

    @Override
    public MestModuleContext context() {
        return context;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("gui.mestjei.search");
    }

    @Override
    public int defaultWidth() {
        return JeiGridPanel.widthForColumns(9);
    }

    @Override
    public int defaultHeight() {
        return TITLE_BAR_HEIGHT + CONTENT_PADDING;
    }

    @Override
    public int minWidth() {
        return 80;
    }

    @Override
    public int minHeight() {
        return TITLE_BAR_HEIGHT + 3;
    }

    @Override
    public ModuleDefaults defaults() {
        return new ModuleDefaults(true, true, false, true);
    }

    @Override
    public Icon icon() {
        return Icon.SEARCH_JEI;
    }

    @Override
    public void layoutSlots() {
        layoutControls();
        if (!visible) {
            setSearchFocused(false);
        }
    }

    /** JEI's own config button (behaviour and tooltip); created once JEI has started. */
    @Nullable
    private ConfigButton configButton() {
        if (configButton == null && JeiAccess.isReady()) {
            configButton = ConfigButton.create(JeiSearchPanel::listShown, JeiAccess.toggleState(),
                    JeiAccess.keyMappings());
            configButtonInput = configButton.createInputHandler();
        }
        return configButton;
    }

    private static boolean listShown() {
        return JeiAccess.isReady() && JeiAccess.toggleState().isOverlayEnabled();
    }

    // --- Search ------------------------------------------------------------

    @Override
    public void tick() {
        // The filter is shared with JEI (and with every other screen showing JEI): follow it when
        // it is changed elsewhere, as JEI's own search box does.
        String filter = JeiAccess.filterText();
        if (!search.isFocused() && !filter.equals(search.getValue())) {
            search.setValue(filter);
        }
        search.tick();
    }

    public boolean isSearchFocused() {
        return visible && search.isFocused();
    }

    public void setSearchFocused(boolean focused) {
        boolean was = search.isFocused();
        search.setFocused(focused && visible);
        if (was && !search.isFocused()) {
            remember(search.getValue());
            historyIndex = -1;
        }
    }

    public boolean inSearch(double mx, double my) {
        return visible && search.visible && search.isMouseOver(mx, my);
    }

    /** Keys while the search box has focus. Every key is consumed, as for the ME list search. */
    public boolean searchKeyPressed(int keyCode, int scanCode, int modifiers) {
        IInternalKeyMappings keys = JeiAccess.keyMappings();
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setSearchFocused(false);
            return true;
        }
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (keys.getPreviousSearch().isActiveAndMatches(key)) {
            navigateHistory(1);
            return true;
        }
        if (keys.getNextSearch().isActiveAndMatches(key)) {
            navigateHistory(-1);
            return true;
        }
        search.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    public boolean searchCharTyped(char character, int modifiers) {
        return isSearchFocused() && search.charTyped(character, modifiers);
    }

    private static void remember(String text) {
        if (text.isBlank()) {
            return;
        }
        HISTORY.remove(text);
        HISTORY.add(0, text);
        while (HISTORY.size() > HISTORY_SIZE) {
            HISTORY.remove(HISTORY.size() - 1);
        }
    }

    /** {@code direction} 1 goes back to older searches, -1 forward to newer ones. */
    private void navigateHistory(int direction) {
        if (HISTORY.isEmpty()) {
            return;
        }
        int next = Math.max(-1, Math.min(HISTORY.size() - 1, historyIndex + direction));
        if (next == historyIndex) {
            return;
        }
        historyIndex = next;
        search.setValue(next < 0 ? "" : HISTORY.get(next));
        search.moveCursorToEnd();
    }

    // --- Layout ------------------------------------------------------------

    private int configX() {
        return chromeButtonsLeft() - GAP - BUTTON;
    }

    private int configY() {
        return y + 3;
    }

    private int searchLeft() {
        return x + LEFT;
    }

    private int searchWidth() {
        return Math.max(0, configX() - GAP - searchLeft());
    }

    /** The strip is all search box: no title text. */
    @Override
    protected int titleRightInset() {
        return width;
    }

    private void layoutControls() {
        int width = searchWidth();
        search.setVisible(visible && width >= 24);
        search.move(new Point(searchLeft(), y + SEARCH_TOP));
        search.resize(width, SEARCH_HEIGHT);
        ConfigButton button = configButton();
        if (button != null) {
            button.updateBounds(new Rect2i(configX(), configY(), BUTTON, BUTTON));
        }
    }

    private boolean inConfigButton(double mx, double my) {
        return visible && mx >= configX() && mx < configX() + BUTTON && my >= configY() && my < configY() + BUTTON;
    }

    @Override
    public boolean inTitleBarControls(double mx, double my) {
        return super.inTitleBarControls(mx, my) || inSearch(mx, my) || inConfigButton(mx, my);
    }

    @Override
    public boolean isTopAt(double mx, double my) {
        return visible && context.dock().topLeafAt(mx, my) == this;
    }

    // --- Rendering ---------------------------------------------------------

    @Override
    public void renderBackgroundContent(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        layoutControls();
        boolean noResults = listShown() && JeiAccess.ingredientElements().isEmpty();
        search.setTextColor(noResults ? NO_RESULTS_COLOR : textColor);
        if (search.visible) {
            // AE2's box art stops at 128px; stretch it across the whole strip first.
            drawTextField(g, searchLeft(), y + SEARCH_TOP, searchWidth(), search.isFocused());
            search.render(g, mouseX, mouseY, partialTicks);
        }
        drawConfigButton(g, inConfigButton(mouseX, mouseY));
    }

    /** The terminal's title-bar button showing JEI's own config icon (red while cheating). */
    private void drawConfigButton(GuiGraphics g, boolean hovered) {
        int bx = configX();
        int by = configY() + (hovered ? 1 : 0);
        MestButtonSprites.terminalButton(hovered, false).dest(bx, by, BUTTON, BUTTON).blit(g);
        IDrawableStatic icon = null;
        if (JeiAccess.isReady()) {
            try {
                icon = JeiAccess.toggleState().isCheatItemsEnabled()
                        ? Internal.getTextures().getConfigButtonCheatIcon()
                        : Internal.getTextures().getConfigButtonIcon();
            } catch (RuntimeException e) {
                icon = null;
            }
        }
        if (icon == null) {
            Icon.WRENCH.getBlitter().dest(bx, by, BUTTON, BUTTON).blit(g);
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
        if (inConfigButton(mouseX, mouseY)) {
            ConfigButton button = configButton();
            if (button != null) {
                button.drawTooltips(g, mouseX, mouseY);
            }
        } else if (inSearch(mouseX, mouseY)) {
            g.renderComponentTooltip(Minecraft.getInstance().font, search.getTooltipMessage(), mouseX, mouseY);
        }
    }

    // --- Mouse -------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) {
            return false;
        }
        layoutControls();
        if (inSearch(mx, my)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                // JEI: right-click clears the search bar.
                search.setValue("");
            } else {
                search.mouseClicked(mx, my, button);
            }
            JeiInput.focusSearch(this);
            return true;
        }
        setSearchFocused(false);
        if (inConfigButton(mx, my)) {
            if (configButton() != null && configButtonInput != null) {
                Optional.ofNullable(JeiInput.immediate(mx, my, button))
                        .ifPresent(input -> configButtonInput.handleUserInput(context.screen(), input,
                                JeiAccess.keyMappings()));
            }
            return true;
        }
        return false;
    }
}
