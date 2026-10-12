package com.geziasi.mestjei.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;

import com.lhy.mest.client.MESTScreen;

import com.geziasi.mestjei.client.jei.JeiAccess;
import com.geziasi.mestjei.client.jei.JeiGrid;
import com.geziasi.mestjei.client.panel.JeiBookmarkPanel;
import com.geziasi.mestjei.client.panel.JeiGridPanel;
import com.geziasi.mestjei.client.panel.JeiSearchPanel;
import com.geziasi.mestjei.client.panel.JeiTerminalPanel;

/**
 * Screen events for the JEI panels of an open terminal.
 *
 * <p>Clicks and keys on ingredients reach JEI directly (it sees the panels' ingredients through its
 * overlays). This class covers what JEI's hidden overlays used to do themselves: the search box's
 * keyboard, dragging ingredients onto ghost slots, rearranging bookmarks by dragging, deleting a held
 * item in cheat mode, tooltips, and the bookmark/list page keys.
 */
public final class JeiInput {
    /** JEI's ghost-target colours. */
    private static final int TARGET_COLOR = 0x4013FF0A;
    private static final int TARGET_HOVER_COLOR = 0x80144419;
    /** Above every terminal window (they stack 400 apart); the screen cleared depth already. */
    private static final float OVERLAY_Z = 600.0F;

    @Nullable
    private static Drag drag;

    private JeiInput() {
    }

    static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, JeiInput::onMousePressed);
        bus.addListener(EventPriority.HIGHEST, JeiInput::onMouseDragged);
        bus.addListener(EventPriority.HIGHEST, JeiInput::onMouseReleased);
        bus.addListener(EventPriority.HIGHEST, JeiInput::onKeyPressed);
        bus.addListener(EventPriority.HIGHEST, JeiInput::onCharTyped);
        bus.addListener(EventPriority.LOW, JeiInput::onRenderPost);
        bus.addListener(JeiInput::onScreenClosing);
        bus.addListener(JeiInput::onClientTick);
    }

    public static boolean isDragging() {
        return drag != null && drag.started;
    }

    /** Current mouse position in GUI coordinates. */
    public static double[] mouse() {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        double x = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth());
        double y = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight());
        return new double[] { x, y };
    }

    /** A JEI mouse input that acts at once (JEI otherwise simulates on press, acts on release). */
    @Nullable
    public static UserInput immediate(double mouseX, double mouseY, int button) {
        if (button < 0) {
            return null;
        }
        return new UserInput(InputConstants.Type.MOUSE.getOrCreate(button), mouseX, mouseY, 0, InputType.IMMEDIATE);
    }

    /** Focuses the JEI search box, taking focus from the terminal's own ME search. */
    public static void focusSearch(JeiSearchPanel panel) {
        MESTScreen screen = panel.context().screen();
        if (screen.meListPanel() != null) {
            screen.meListPanel().setSearchFocused(false);
        }
        screen.setFocused(null);
        panel.setSearchFocused(true);
    }

    @Nullable
    private static MESTScreen terminal(Screen screen) {
        return screen instanceof MESTScreen terminal && !JeiPanels.current().isEmpty() ? terminal : null;
    }

    // --- Mouse -----------------------------------------------------------------

    private static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        MESTScreen screen = terminal(event.getScreen());
        if (screen == null) {
            return;
        }
        double mx = event.getMouseX();
        double my = event.getMouseY();
        JeiSearchPanel search = JeiPanels.search();
        if (search != null && search.isSearchFocused() && !search.inSearch(mx, my)) {
            search.setSearchFocused(false);
        }
        cancelDrag();
        if (event.getButton() != InputConstants.MOUSE_BUTTON_LEFT) {
            return;
        }
        JeiGridPanel panel = JeiPanels.gridAt(mx, my);
        if (panel == null || !panel.gridActive() || !panel.inGrid(mx, my)) {
            return;
        }
        if (!screen.getMenu().getCarried().isEmpty()) {
            if (deleteHeldItem(screen, panel, mx, my)) {
                event.setCanceled(true);
            }
            return;
        }
        panel.draggableAt(mx, my).findFirst().ifPresent(draggable -> drag = new Drag(panel, draggable, mx, my));
    }

    /** JEI cheat mode: clicking the list with a held item deletes it. */
    private static boolean deleteHeldItem(Screen screen, JeiGridPanel panel, double mx, double my) {
        JeiGrid grid = panel.gridActive() ? panel.jeiGrid() : null;
        IUserInputHandler handler = grid == null ? null : grid.deleteItemHandler();
        UserInput input = immediate(mx, my, InputConstants.MOUSE_BUTTON_LEFT);
        return handler != null && input != null
                && handler.handleUserInput(screen, input, JeiAccess.keyMappings()).isPresent();
    }

    private static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        Drag current = drag;
        if (current == null || terminal(event.getScreen()) == null) {
            return;
        }
        if (!current.started && current.canStart(event.getMouseX(), event.getMouseY())) {
            current.start(event.getScreen());
        }
        if (current.started) {
            event.setCanceled(true);
        }
    }

    private static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        Drag current = drag;
        if (current == null || event.getButton() != InputConstants.MOUSE_BUTTON_LEFT) {
            return;
        }
        drag = null;
        if (!current.started) {
            // A plain click: JEI shows the recipes on release, as it always does.
            current.complete();
            return;
        }
        current.drop(event.getMouseX(), event.getMouseY());
        current.complete();
        // The press was JEI's pending "show recipes" click; the drop replaces it.
        event.setCanceled(true);
    }

    private static void cancelDrag() {
        if (drag != null) {
            drag.complete();
            drag = null;
        }
    }

    private static void onScreenClosing(ScreenEvent.Closing event) {
        cancelDrag();
    }

    // --- Keyboard ----------------------------------------------------------------

    private static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (terminal(event.getScreen()) == null) {
            return;
        }
        JeiSearchPanel search = JeiPanels.search();
        if (search != null && search.isSearchFocused()) {
            search.searchKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            event.setCanceled(true);
            return;
        }
        if (!JeiAccess.isReady()) {
            return;
        }
        InputConstants.Key key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        IInternalKeyMappings keys = JeiAccess.keyMappings();
        if (search != null && search.visible && keys.getFocusSearch().isActiveAndMatches(key)) {
            focusSearch(search);
            event.setCanceled(true);
            return;
        }
        double[] mouse = mouse();
        JeiGridPanel hovered = JeiPanels.gridAt(mouse[0], mouse[1]);
        if (hovered != null && hovered.gridActive()) {
            if (keys.getNextPage().isActiveAndMatches(key)) {
                hovered.scrollPages(1);
                event.setCanceled(true);
            } else if (keys.getPreviousPage().isActiveAndMatches(key)) {
                hovered.scrollPages(-1);
                event.setCanceled(true);
            }
        }
    }

    private static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (terminal(event.getScreen()) == null) {
            return;
        }
        JeiSearchPanel search = JeiPanels.search();
        if (search != null && search.isSearchFocused()) {
            search.searchCharTyped(event.getCodePoint(), event.getModifiers());
            event.setCanceled(true);
        }
    }

    // --- Rendering and ticks -----------------------------------------------------

    private static void onRenderPost(ScreenEvent.Render.Post event) {
        MESTScreen screen = terminal(event.getScreen());
        if (screen == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int mx = event.getMouseX();
        int my = event.getMouseY();
        g.pose().pushPose();
        try {
            g.pose().translate(0.0F, 0.0F, OVERLAY_Z);
            Drag current = drag;
            if (current != null && current.started) {
                current.render(g, mx, my);
            } else {
                JeiTerminalPanel panel = JeiPanels.topAt(mx, my);
                if (panel != null && screen.getMenu().getCarried().isEmpty()) {
                    panel.renderTooltips(g, mx, my);
                } else if (panel instanceof JeiGridPanel grid && grid.inGrid(mx, my)) {
                    // Cheat mode shows "delete item" over the list while an item is held.
                    panel.renderTooltips(g, mx, my);
                }
            }
        } finally {
            g.pose().popPose();
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (JeiTerminalPanel panel : JeiPanels.current()) {
            panel.tick();
            if (panel instanceof JeiBookmarkPanel bookmarks && bookmarks.takeCountChange()) {
                JeiPlacement.followBookmarkCount(bookmarks, bookmarks.bookmarkCount());
            }
        }
    }

    // --- Dragging ------------------------------------------------------------------

    /**
     * An ingredient dragged out of a panel: onto a JEI ghost target of the terminal (pattern
     * encoding slots, filters), or, for a bookmark, onto another bookmark to move it there.
     */
    private static final class Drag {
        private final JeiGridPanel source;
        private final IDraggableIngredientInternal<?> dragged;
        private final double startX;
        private final double startY;
        private final long canStartAt;
        private boolean started;
        private final List<GhostTargets<?>> ghosts = new ArrayList<>();

        Drag(JeiGridPanel source, IDraggableIngredientInternal<?> dragged, double startX, double startY) {
            this.source = source;
            this.dragged = dragged;
            this.startX = startX;
            this.startY = startY;
            this.canStartAt = System.currentTimeMillis() + JeiAccess.clientConfig().getDragDelayMs();
        }

        /** JEI's rule: after the drag delay, once the mouse is out of the slot and 8px away. */
        boolean canStart(double mx, double my) {
            if (System.currentTimeMillis() < canStartAt) {
                return false;
            }
            var origin = dragged.getArea();
            if (origin.contains(mx, my)) {
                return false;
            }
            double cx = origin.getX() + origin.getWidth() / 2.0;
            double cy = origin.getY() + origin.getHeight() / 2.0;
            double dx = cx - mx;
            double dy = cy - my;
            return dx * dx + dy * dy > 64.0;
        }

        void start(Screen screen) {
            started = true;
            IJeiRuntime runtime = JeiAccess.runtime().orElse(null);
            if (runtime == null) {
                return;
            }
            collect(screen, runtime, dragged.getTypedIngredient());
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private <T> void collect(Screen screen, IJeiRuntime runtime, ITypedIngredient<T> ingredient) {
            List<IGhostIngredientHandler<Screen>> handlers = runtime.getScreenHelper().getGhostIngredientHandlers(screen);
            for (IGhostIngredientHandler<Screen> handler : handlers) {
                try {
                    List<IGhostIngredientHandler.Target<T>> targets = handler.getTargetsTyped(screen, ingredient, true);
                    ghosts.add(new GhostTargets(handler, targets, ingredient));
                } catch (RuntimeException e) {
                    JeiAccess.reportOnce("ghost handler " + handler.getClass().getName(), e);
                }
            }
        }

        private boolean rearrangesBookmarks() {
            return source instanceof JeiBookmarkPanel && dragged.getElement().getBookmark().isPresent()
                    && JeiAccess.clientConfig().isDragToRearrangeBookmarksEnabled();
        }

        void render(GuiGraphics g, int mx, int my) {
            RenderSystem.disableDepthTest();
            for (GhostTargets<?> ghost : ghosts) {
                if (ghost.handler.shouldHighlightTargets()) {
                    for (var target : ghost.targets) {
                        Rect2i area = target.getArea();
                        boolean hovered = area.contains(mx, my);
                        g.fill(RenderType.guiOverlay(), area.getX(), area.getY(), area.getX() + area.getWidth(),
                                area.getY() + area.getHeight(), hovered ? TARGET_HOVER_COLOR : TARGET_COLOR);
                    }
                }
            }
            RenderSystem.enableDepthTest();
            drawIngredient(g, dragged.getTypedIngredient(), mx - 8, my - 8);
        }

        private static <T> void drawIngredient(GuiGraphics g, ITypedIngredient<T> ingredient, int x, int y) {
            IJeiRuntime runtime = JeiAccess.runtime().orElse(null);
            if (runtime == null) {
                return;
            }
            IIngredientRenderer<T> renderer = runtime.getIngredientManager().getIngredientRenderer(ingredient.getType());
            g.pose().pushPose();
            try {
                g.pose().translate(x, y, 0.0F);
                renderer.render(g, ingredient.getIngredient());
            } catch (RuntimeException e) {
                JeiAccess.reportOnce("drag render", e);
            } finally {
                g.pose().popPose();
            }
        }

        void drop(double mx, double my) {
            if (rearrangesBookmarks() && moveBookmark(mx, my)) {
                return;
            }
            for (GhostTargets<?> ghost : ghosts) {
                if (ghost.acceptAt(mx, my)) {
                    return;
                }
            }
        }

        /** Drops a bookmark onto another one in the same panel: JEI moves it to that position. */
        private boolean moveBookmark(double mx, double my) {
            if (!(source instanceof JeiBookmarkPanel panel) || !panel.isTopAt(mx, my)) {
                return false;
            }
            BookmarkList list = panel.bookmarkList();
            IBookmark moved = dragged.getElement().getBookmark().orElse(null);
            if (list == null || moved == null) {
                return false;
            }
            IBookmark target = panel.draggableAt(mx, my).findFirst()
                    .flatMap(d -> d.getElement().getBookmark())
                    .orElse(null);
            if (target == null || target.equals(moved)) {
                return target != null;
            }
            list.moveBookmark(target, moved, 0);
            return true;
        }

        void complete() {
            for (GhostTargets<?> ghost : ghosts) {
                try {
                    ghost.handler.onComplete();
                } catch (RuntimeException e) {
                    JeiAccess.reportOnce("ghost handler onComplete", e);
                }
            }
            ghosts.clear();
        }
    }

    private record GhostTargets<T>(
            IGhostIngredientHandler<?> handler,
            List<IGhostIngredientHandler.Target<T>> targets,
            ITypedIngredient<T> ingredient) {
        boolean acceptAt(double mx, double my) {
            for (IGhostIngredientHandler.Target<T> target : targets) {
                Rect2i area = target.getArea();
                if (mx >= area.getX() && mx < area.getX() + area.getWidth()
                        && my >= area.getY() && my < area.getY() + area.getHeight()) {
                    target.accept(ingredient.getIngredient());
                    return true;
                }
            }
            return false;
        }
    }
}
