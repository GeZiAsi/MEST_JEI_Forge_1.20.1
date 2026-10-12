package com.geziasi.mestjei.client.panel;

import java.util.List;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import appeng.client.Point;
import appeng.client.gui.widgets.Scrollbar;

import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import mezz.jei.gui.overlay.elements.IElement;

import com.lhy.mest.api.client.MestModuleContext;
import com.lhy.mest.client.dock.ModulePanel;

import com.geziasi.mestjei.client.JeiPanels;
import com.geziasi.mestjei.client.TerminalColors;
import com.geziasi.mestjei.client.jei.JeiAccess;
import com.geziasi.mestjei.client.jei.JeiGrid;

/**
 * A terminal panel showing a JEI ingredient grid: one recessed well in the terminal's slot colours
 * with JEI's own grid drawing the ingredients, and the ME list's scroller beside it.
 *
 * <p>The scroller is the ME list's (AE2's scrollbar on the terminal track), kept inside the panel
 * and pushed against its right edge so it takes as little room as possible.
 *
 * <p>Clicks, keys, cheat mode and bookmarking are not handled here: JEI's own input handlers act on
 * the ingredients this panel reports through JEI's overlays (see the overlay mixins), exactly as they
 * do for JEI's side lists.
 */
public abstract class JeiGridPanel extends ModulePanel implements JeiTerminalPanel {
    protected static final int SLOT = JeiGrid.SLOT;
    public static final int SLOT_SIZE = JeiGrid.SLOT;
    private static final int TRACK_WIDTH = 12;
    /** Gap between the track and the window's right edge: just inside the frame's 4px bevel. */
    private static final int TRACK_EDGE = 5;
    /** Gap between the ingredients and the track. */
    private static final int TRACK_GAP = 2;
    /** Right inset of the content so the grid ends {@link #TRACK_GAP} before the track. */
    private static final int RIGHT_INSET = TRACK_EDGE + TRACK_WIDTH + TRACK_GAP - CONTENT_PADDING;

    protected final MestModuleContext context;
    private final boolean searchable;
    private final Scrollbar scrollbar = new Scrollbar(Scrollbar.DEFAULT);
    private boolean scrollbarDragging;
    @Nullable
    private JeiGrid grid;
    @Nullable
    private IJeiRuntime gridRuntime;
    private int cols = 1;
    private int rows = 1;

    protected JeiGridPanel(MestModuleContext context, boolean searchable) {
        this.context = context;
        this.searchable = searchable;
        this.scrollbar.setCaptureMouseWheel(false);
        JeiPanels.register(context.screen(), this);
    }

    @Override
    public MestModuleContext context() {
        return context;
    }

    /** The list to show, in JEI's order. */
    protected abstract List<IElement<?>> elements();

    /** False while JEI itself would hide this list (overlay or bookmarks toggled off). */
    public abstract boolean listEnabled();

    /** Text shown in place of the grid when it has nothing to show, or null for none. */
    @Nullable
    protected abstract Component placeholder();

    // --- Geometry ----------------------------------------------------------

    /** Window width for a panel of {@code columns}. */
    public static int widthForColumns(int columns) {
        return 2 * CONTENT_PADDING + columns * SLOT + RIGHT_INSET;
    }

    public static int heightForRows(int rowCount) {
        return TITLE_BAR_HEIGHT + CONTENT_PADDING + rowCount * SLOT;
    }

    @Override
    public int preferredContentRightInset() {
        return RIGHT_INSET;
    }

    @Override
    public int minWidth() {
        return widthForColumns(2);
    }

    @Override
    public int minHeight() {
        return TITLE_BAR_HEIGHT + CONTENT_PADDING + 2 * SLOT;
    }

    @Override
    public boolean fillsContentArea() {
        return true;
    }

    @Override
    public boolean expandsVertically() {
        return true;
    }

    public int columns() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    private int gridLeft() {
        return contentLeft();
    }

    private int gridTop() {
        return contentTop();
    }

    /** Against the right edge of the panel, whatever the grid's width. */
    private int trackLeft() {
        return x + width - TRACK_EDGE - TRACK_WIDTH;
    }

    private int trackTop() {
        return gridTop() + 1;
    }

    private int trackHeight() {
        return Math.max(1, rows * SLOT - 2);
    }

    @Override
    public void layoutSlots() {
        cols = Math.max(1, contentWidth() / SLOT);
        rows = Math.max(1, contentHeight() / SLOT);
        if (grid != null) {
            grid.layout(gridLeft(), gridTop(), cols, rows);
        }
        updateScrollbar();
    }

    public boolean inGrid(double mx, double my) {
        return visible && mx >= gridLeft() && mx < gridLeft() + cols * SLOT
                && my >= gridTop() && my < gridTop() + rows * SLOT;
    }

    // --- JEI grid ------------------------------------------------------------

    /** JEI's grid, rebuilt when JEI reloads (its ingredient manager and configs change then). */
    @Nullable
    public JeiGrid jeiGrid() {
        IJeiRuntime runtime = JeiAccess.runtime().orElse(null);
        if (runtime == null) {
            grid = null;
            gridRuntime = null;
            return null;
        }
        if (grid == null || gridRuntime != runtime) {
            gridRuntime = runtime;
            grid = JeiGrid.create(runtime, searchable);
            if (grid != null) {
                grid.layout(gridLeft(), gridTop(), cols, rows);
            }
        }
        return grid;
    }

    /** True when the grid is drawn and its ingredients can be used. */
    public boolean gridActive() {
        return visible && listEnabled() && jeiGrid() != null;
    }

    private int totalRows() {
        int size = elements().size();
        return (size + cols - 1) / cols;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - rows);
    }

    private void updateScrollbar() {
        int maxScroll = maxScroll();
        scrollbar.setRange(0, maxScroll, Math.max(1, rows / 6));
        scrollbar.setHeight(trackHeight());
        scrollbar.setPosition(new Point(trackLeft(), trackTop()));
    }

    private int scroll() {
        return Math.max(0, Math.min(maxScroll(), scrollbar.getCurrentScroll()));
    }

    protected void resetScroll() {
        scrollbar.setCurrentScroll(0);
    }

    /** Lays the grid out and fills it with the visible part of the list. */
    private void refreshGrid() {
        JeiGrid jeiGrid = jeiGrid();
        if (jeiGrid == null) {
            return;
        }
        jeiGrid.layout(gridLeft(), gridTop(), cols, rows);
        jeiGrid.show(scroll() * cols, listEnabled() ? elements() : List.of());
    }

    protected void invalidateGrid() {
        if (grid != null) {
            grid.invalidate();
        }
    }

    public Stream<IClickableIngredientInternal<?>> clickableAt(double mx, double my) {
        if (!gridActive() || !inGrid(mx, my)) {
            return Stream.empty();
        }
        refreshGrid();
        return grid.clickableAt(mx, my);
    }

    public Stream<IDraggableIngredientInternal<?>> draggableAt(double mx, double my) {
        if (!gridActive() || !inGrid(mx, my)) {
            return Stream.empty();
        }
        refreshGrid();
        return grid.draggableAt(mx, my);
    }

    public void tick() {
        if (gridActive()) {
            grid.tick();
        }
    }

    // --- Rendering -----------------------------------------------------------

    @Override
    public void renderBackgroundContent(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        updateScrollbar();
        drawWell(g);
        g.flush();
        com.lhy.mest.client.dock.Scrollbar.drawTerminalTrack(g, trackLeft(), trackTop(), trackHeight());
        g.flush();
        if (gridActive()) {
            refreshGrid();
            grid.draw(g, mouseX, mouseY);
        }
        g.flush();
        scrollbar.drawForegroundLayer(g, new Rect2i(0, 0, 0, 0), new Point(mouseX, mouseY));
        Component placeholder = placeholder();
        if (placeholder != null) {
            drawPlaceholder(g, font, placeholder);
        }
    }

    /** Tooltips are drawn after the whole screen so no window above can cover them. */
    public void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (gridActive() && inGrid(mouseX, mouseY)) {
            grid.drawTooltips(g, mouseX, mouseY);
        }
    }

    private void drawPlaceholder(GuiGraphics g, Font font, Component text) {
        int gridWidth = cols * SLOT;
        int gridHeight = rows * SLOT;
        List<FormattedCharSequence> lines = font.split(text, Math.max(10, gridWidth - 6));
        int lineHeight = font.lineHeight + 1;
        int maxLines = Math.max(1, (gridHeight - 4) / lineHeight);
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        int top = gridTop() + (gridHeight - lines.size() * lineHeight) / 2;
        int color = TerminalColors.text();
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int x = gridLeft() + (gridWidth - font.width(line)) / 2;
            g.drawString(font, line, x, top + i * lineHeight, color, false);
        }
    }

    /**
     * One recessed well behind the whole list in the terminal's slot colours (taken from the GUI
     * pack in use), with JEI's tight 18px packing on top instead of a frame per ingredient.
     */
    private void drawWell(GuiGraphics g) {
        int left = gridLeft();
        int top = gridTop();
        int right = left + cols * SLOT;
        int bottom = top + rows * SLOT;
        int shadow = TerminalColors.shadow();
        int light = TerminalColors.light();
        g.fill(left, top, right, bottom, TerminalColors.fill());
        g.fill(left, top, right - 1, top + 1, shadow);
        g.fill(left, top + 1, left + 1, bottom - 1, shadow);
        g.fill(left + 1, bottom - 1, right, bottom, light);
        g.fill(right - 1, top, right, bottom - 1, light);
    }

    // --- Mouse ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Presses on an ingredient never get here: JEI takes them first. The empty rest of the
        // panel still swallows the click so nothing underneath reacts.
        return visible && (inGrid(mx, my) || inScroller(mx, my));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!visible || scrollY == 0 || !(inGrid(mx, my) || inScroller(mx, my))) {
            return false;
        }
        scrollRows(-(int) Math.signum(scrollY));
        return true;
    }

    public void scrollRows(int delta) {
        updateScrollbar();
        scrollbar.setCurrentScroll(scroll() + delta);
    }

    public void scrollPages(int delta) {
        scrollRows(delta * Math.max(1, rows));
    }

    private boolean inScrollbar(double mx, double my) {
        Rect2i bounds = scrollbar.getBounds();
        return mx >= bounds.getX() && mx < bounds.getX() + bounds.getWidth()
                && my >= bounds.getY() && my < bounds.getY() + bounds.getHeight();
    }

    private boolean inScroller(double mx, double my) {
        if (inScrollbar(mx, my)) {
            return true;
        }
        return mx >= trackLeft() && mx < trackLeft() + TRACK_WIDTH
                && my >= trackTop() && my < trackTop() + trackHeight();
    }

    @Override
    public boolean scrollbarPressed(double mx, double my) {
        if (!visible) {
            return false;
        }
        updateScrollbar();
        if (!inScrollbar(mx, my)) {
            return false;
        }
        scrollbarDragging = scrollbar.onMouseDown(new Point((int) mx, (int) my), 0);
        return scrollbarDragging;
    }

    @Override
    public boolean scrollbarDragged(double mx, double my) {
        return scrollbarDragging && scrollbar.onMouseDrag(new Point((int) mx, (int) my), 0);
    }

    @Override
    public void scrollbarReleased() {
        if (scrollbarDragging) {
            scrollbar.onMouseUp(Point.ZERO, 0);
        }
        scrollbarDragging = false;
    }

    @Override
    public boolean scrollbarDragging() {
        return scrollbarDragging;
    }

    /** Whether this panel is the topmost one at the point in its terminal. */
    public boolean isTopAt(double mx, double my) {
        return visible && context.dock().topLeafAt(mx, my) == this;
    }

    protected static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
