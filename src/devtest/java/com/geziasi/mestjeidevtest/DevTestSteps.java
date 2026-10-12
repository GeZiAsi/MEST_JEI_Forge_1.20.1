package com.geziasi.mestjeidevtest;

import static com.geziasi.mestjeidevtest.MestJeiDevTest.check;
import static com.geziasi.mestjeidevtest.MestJeiDevTest.log;

import java.lang.reflect.Field;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.ForgeHooksClient;

import appeng.menu.slot.FakeSlot;

import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;

import com.lhy.mest.client.MESTScreen;

import com.geziasi.mestjei.client.JeiPanels;
import com.geziasi.mestjei.client.jei.JeiAccess;
import com.geziasi.mestjei.client.panel.JeiBookmarkPanel;
import com.geziasi.mestjei.client.panel.JeiGridPanel;
import com.geziasi.mestjei.client.panel.JeiHistoryPanel;
import com.geziasi.mestjei.client.panel.JeiIngredientListPanel;
import com.geziasi.mestjei.client.panel.JeiSearchPanel;

/** The checks run in an open terminal, in order. */
final class DevTestSteps {
    private static int bookmarksBefore;
    private static FakeSlot ghostSlot;
    private static String secondBookmark;
    private static int historyButtonX;
    private static boolean historyWasEnabled;
    private static String firstBeforeScroll;

    private DevTestSteps() {
    }

    static void register(List<MestJeiDevTest.Step> steps) {
        steps.add(new MestJeiDevTest.Step("panels and hidden overlays", 20, "jei_01_terminal", mc -> {
            IJeiRuntime runtime = JeiAccess.runtime().orElse(null);
            check(runtime != null, "JEI runtime available");
            check(JeiPanels.current().size() == expectedPanels(), "{} JEI panels registered: {}", expectedPanels(),
                    JeiPanels.current());
            JeiHistoryPanel history = JeiPanels.find(JeiHistoryPanel.class);
            check(JeiAccess.historySupported() == (history != null),
                    "history window present exactly when JEI has a lookup history ({})", JeiAccess.historySupported());
            if (history != null) {
                MESTScreen terminal = history.context().screen();
                check(history.isTopAt(history.contentLeft() + 9, history.contentTop() + 9)
                        && history.x < terminal.width / 2 && history.y > terminal.height / 2,
                        "history window placed bottom-left and not covered ({},{})", history.x, history.y);
            }
            JeiSearchPanel searchPanel = JeiPanels.search();
            JeiIngredientListPanel listPanel = JeiPanels.ingredientList();
            check(searchPanel != null && listPanel != null && searchPanel.context().dock().isJoinedWindow(listPanel)
                    && searchPanel.y < listPanel.y && searchPanel.x == listPanel.x,
                    "search bar is spliced on top of the ingredient list ({} / {})",
                    searchPanel == null ? "-" : searchPanel.x + "," + searchPanel.y,
                    listPanel == null ? "-" : listPanel.x + "," + listPanel.y);
            check(listPanel != null && listPanel.outsideHitWidth() == 0,
                    "the list's scroller stays inside the window");
            for (JeiGridPanel panel : JeiPanels.grids()) {
                log("panel {} at {},{} {}x{} visible={} effective={} cols={} rows={} gridActive={}",
                        panel.id(), panel.x, panel.y, panel.width, panel.height, panel.visible,
                        panel.context().dock().isEffectivelyVisible(panel), panel.columns(), panel.rows(), panel.gridActive());
            }
            check(runtime != null && !runtime.getIngredientListOverlay().isListDisplayed(),
                    "JEI's own ingredient list is hidden in the terminal");
            check(runtime != null && runtime.getBookmarkOverlay() instanceof BookmarkOverlay overlay
                    && !overlay.isListDisplayed(), "JEI's own bookmark list is hidden in the terminal");
            JeiIngredientListPanel list = JeiPanels.ingredientList();
            check(list != null && list.gridActive(), "ingredient list panel shows JEI's grid");
            check(list != null && list.isTopAt(list.contentLeft() + 9, list.contentTop() + 9),
                    "ingredient list is not covered by another window at its first cell");
            JeiBookmarkPanel marks = bookmarkPanel();
            check(marks != null && marks.isTopAt(marks.contentLeft() + 9, marks.contentTop() + 9),
                    "bookmark panel is not covered by another window at its first cell");
            if (list != null && marks != null) {
                MESTScreen screen = list.context().screen();
                check(list.x > screen.width / 2 && marks.x + marks.width < screen.width / 2,
                        "list placed on the right ({}), bookmarks on the left ({})", list.x, marks.x);
            }
            check(!JeiAccess.ingredientElements().isEmpty(), "JEI list has {} ingredients",
                    JeiAccess.ingredientElements().size());
        }));
        steps.add(new MestJeiDevTest.Step("type in search", 30, "jei_02_search", mc -> {
            JeiSearchPanel list = JeiPanels.search();
            int[] point = searchPoint(list);
            check(point != null, "found the search box");
            if (point == null) {
                return;
            }
            click(mc, point[0], point[1], 0);
            check(list.isSearchFocused(), "search box focused by a click");
            for (char c : iron().toCharArray()) {
                typeChar(mc, c);
            }
        }));
        steps.add(new MestJeiDevTest.Step("search applied", 5, null, mc -> {
            check(iron().equals(JeiAccess.filterText()), "JEI filter text is the typed search: '{}'",
                    JeiAccess.filterText());
            int size = JeiAccess.ingredientElements().size();
            check(size > 0 && size < 200, "search narrowed the list to {} ingredients", size);
            // Typing a key JEI binds (R/U/A) into the box must not trigger JEI.
            key(mc, GLFW.GLFW_KEY_ENTER);
            check(!JeiPanels.search().isSearchFocused(), "Enter leaves the search box");
        }));
        steps.add(new MestJeiDevTest.Step("hover first ingredient", 20, "jei_03_tooltip", mc -> {
            JeiGridPanel list = JeiPanels.ingredientList();
            setMouse(mc, list.contentLeft() + 9, list.contentTop() + 9);
        }));
        steps.add(new MestJeiDevTest.Step("hovered ingredient visible to JEI", 2, null, mc -> {
            var hovered = JeiAccess.runtime().get().getIngredientListOverlay().getIngredientUnderMouse();
            check(hovered.isPresent(), "JEI API sees the panel's hovered ingredient: {}",
                    hovered.map(Object::toString).orElse("none"));
        }));
        steps.add(new MestJeiDevTest.Step("click shows recipes", 30, "jei_04_recipes", mc -> {
            JeiGridPanel list = JeiPanels.ingredientList();
            click(mc, list.contentLeft() + 9, list.contentTop() + 9, 0);
        }));
        steps.add(new MestJeiDevTest.Step("recipes open", 30, null, mc -> {
            check(mc.screen instanceof IRecipesGui, "left click opened JEI recipes, screen={}", mc.screen);
            if (mc.screen instanceof IRecipesGui) {
                mc.screen.onClose();
            }
        }));
        steps.add(new MestJeiDevTest.Step("back in terminal", 20, null, mc -> {
            check(mc.screen instanceof MESTScreen, "closing recipes returns to the terminal, screen={}", mc.screen);
            check(JeiPanels.current().size() == expectedPanels(), "panels registered after returning");
            JeiHistoryPanel history = JeiPanels.find(JeiHistoryPanel.class);
            if (history != null) {
                int size = JeiAccess.historyElements().size();
                check(size > 0, "looking up a recipe adds it to JEI's history ({})", size);
            }
            BookmarkList bookmarks = JeiAccess.bookmarkList();
            bookmarksBefore = bookmarks == null ? -1 : bookmarks.getElements().size();
            JeiGridPanel list = JeiPanels.ingredientList();
            setMouse(mc, list.contentLeft() + 9, list.contentTop() + 9);
        }));
        steps.add(new MestJeiDevTest.Step("history button", 10, "jei_04b_history_off", mc -> {
            JeiHistoryPanel history = JeiPanels.find(JeiHistoryPanel.class);
            if (history == null) {
                return;
            }
            historyWasEnabled = JeiAccess.historyEnabled();
            historyButtonX = historyButtonX(history);
            // JEI's lookup history starts switched off; its button in the title bar flips it.
            click(mc, historyButtonX, history.y + 9, 0);
        }));
        steps.add(new MestJeiDevTest.Step("history button flipped", 10, "jei_04c_history_on", mc -> {
            JeiHistoryPanel history = JeiPanels.find(JeiHistoryPanel.class);
            if (history == null) {
                return;
            }
            check(JeiAccess.historyEnabled() != historyWasEnabled, "the history button flips JEI's lookup history");
            if (!JeiAccess.historyEnabled()) {
                click(mc, historyButtonX, history.y + 9, 0);
            }
            setMouse(mc, history.contentLeft() + 9, history.contentTop() + 9);
        }));
        steps.add(new MestJeiDevTest.Step("history visible to JEI", 10, "jei_04d_history_tooltip", mc -> {
            if (JeiPanels.find(JeiHistoryPanel.class) == null) {
                return;
            }
            check(JeiAccess.historyEnabled(), "lookup history on");
            var hovered = JeiAccess.runtime().get().getBookmarkOverlay().getIngredientUnderMouse();
            check(hovered.isPresent(), "JEI API sees the hovered history ingredient: {}",
                    hovered.map(Object::toString).orElse("none"));
        }));
        steps.add(new MestJeiDevTest.Step("back to the list", 5, null, mc -> {
            JeiGridPanel list = JeiPanels.ingredientList();
            setMouse(mc, list.contentLeft() + 9, list.contentTop() + 9);
        }));
        steps.add(new MestJeiDevTest.Step("A bookmarks", 20, "jei_05_bookmarked", mc -> key(mc, GLFW.GLFW_KEY_A)));
        steps.add(new MestJeiDevTest.Step("bookmark added", 5, null, mc -> {
            BookmarkList bookmarks = JeiAccess.bookmarkList();
            int now = bookmarks == null ? -1 : bookmarks.getElements().size();
            check(now == bookmarksBefore + 1, "A added a JEI bookmark ({} -> {})", bookmarksBefore, now);
            JeiBookmarkPanel panel = bookmarkPanel();
            check(panel != null && panel.isTopAt(panel.contentLeft() + 9, panel.contentTop() + 9),
                    "bookmark panel is on top at its first cell");
            if (panel != null) {
                setMouse(mc, panel.contentLeft() + 9, panel.contentTop() + 9);
            }
        }));
        steps.add(new MestJeiDevTest.Step("bookmark tooltip", 20, "jei_06_bookmark_tooltip", mc -> {
            var hovered = JeiAccess.runtime().get().getBookmarkOverlay().getIngredientUnderMouse();
            check(hovered.isPresent(), "JEI API sees the hovered bookmark: {}",
                    hovered.map(Object::toString).orElse("none"));
        }));
        steps.add(new MestJeiDevTest.Step("A removes bookmark", 10, null, mc -> key(mc, GLFW.GLFW_KEY_A)));
        steps.add(new MestJeiDevTest.Step("bookmark removed", 5, null, mc -> {
            BookmarkList bookmarks = JeiAccess.bookmarkList();
            int now = bookmarks == null ? -1 : bookmarks.getElements().size();
            check(now == bookmarksBefore, "A on a bookmark removed it ({})", now);
        }));
        steps.add(new MestJeiDevTest.Step("bookmark window default size", 2, null, mc -> {
            JeiBookmarkPanel panel = bookmarkPanel();
            check(panel != null && panel.columns() == 3 && panel.rows() == 5,
                    "empty bookmark window is 3x5 ({}x{})", panel == null ? -1 : panel.columns(),
                    panel == null ? -1 : panel.rows());
            // Bookmark 16 ingredients of the (still filtered) list.
            JeiGridPanel list = JeiPanels.ingredientList();
            for (int i = 0; i < 16; i++) {
                int col = i % list.columns();
                int row = i / list.columns();
                setMouse(mc, list.contentLeft() + col * 18 + 9, list.contentTop() + row * 18 + 9);
                key(mc, GLFW.GLFW_KEY_A);
            }
        }));
        steps.add(new MestJeiDevTest.Step("bookmark window grew", 10, "jei_06b_bookmarks_grown", mc -> {
            BookmarkList bookmarks = JeiAccess.bookmarkList();
            int count = bookmarks == null ? -1 : bookmarks.getElements().size();
            JeiBookmarkPanel panel = bookmarkPanel();
            check(count == 16, "16 bookmarks added ({})", count);
            check(panel != null && panel.columns() == 3 && panel.rows() == 6,
                    "bookmark window follows the count: 3x6 for 16 ({}x{})",
                    panel == null ? -1 : panel.columns(), panel == null ? -1 : panel.rows());
        }));
        steps.add(new MestJeiDevTest.Step("press a bookmark", 10, null, mc -> {
            JeiBookmarkPanel panel = bookmarkPanel();
            BookmarkList bookmarks = JeiAccess.bookmarkList();
            secondBookmark = bookmarks.getElements().get(1).getTypedIngredient().toString();
            setMouse(mc, panel.contentLeft() + 18 + 9, panel.contentTop() + 9);
            press(mc, panel.contentLeft() + 18 + 9, panel.contentTop() + 9, 0);
        }));
        steps.add(new MestJeiDevTest.Step("drag it onto the first", 10, null, mc -> {
            JeiBookmarkPanel panel = bookmarkPanel();
            for (int i = 1; i <= 6; i++) {
                double x = panel.contentLeft() + 27 - 18 * i / 6.0;
                double y = panel.contentTop() + 9 + (i == 3 ? 12 : 0);
                setMouse(mc, x, y);
                drag(mc, x, y, 0);
            }
        }));
        steps.add(new MestJeiDevTest.Step("drop bookmark", 10, "jei_06c_bookmark_moved", mc -> {
            JeiBookmarkPanel panel = bookmarkPanel();
            release(mc, panel.contentLeft() + 9, panel.contentTop() + 9, 0);
        }));
        steps.add(new MestJeiDevTest.Step("bookmark moved", 2, null, mc -> {
            BookmarkList bookmarks = JeiAccess.bookmarkList();
            String first = bookmarks.getElements().get(0).getTypedIngredient().toString();
            check(first.equals(secondBookmark),
                    "dragging the second bookmark onto the first moved it to the front: {}", first);
            check(mc.screen instanceof MESTScreen, "dragging a bookmark does not open recipes");
            // A manual resize stops the window from following the count.
            JeiBookmarkPanel panel = bookmarkPanel();
            var dock = panel.context().dock();
            var workspace = dock.workspaceSnapshot();
            var roots = new java.util.ArrayList<>(workspace.roots());
            for (int i = 0; i < roots.size(); i++) {
                var root = roots.get(i);
                if (root.content() instanceof com.lhy.mest.client.dock.model.LeafNode leaf
                        && leaf.moduleId().equals(panel.id())) {
                    var b = root.bounds();
                    roots.set(i, root.withBounds(new com.lhy.mest.client.dock.model.DockRect(
                            b.x(), b.y(), b.width() + 5 * 18, b.height() + 2 * 18)));
                }
            }
            dock.applyEditedWorkspace(workspace.withRoots(roots));
        }));
        steps.add(new MestJeiDevTest.Step("manual size kept", 10, "jei_06d_manual_size", mc -> {
            JeiBookmarkPanel panel = bookmarkPanel();
            int cols = panel.columns();
            int rows = panel.rows();
            check(cols == 8 && rows == 8, "manual resize beyond the limits is allowed ({}x{})", cols, rows);
            // Clearing the bookmarks must not shrink a window the user sized.
            BookmarkList bookmarks = JeiAccess.bookmarkList();
            for (var element : List.copyOf(bookmarks.getElements())) {
                element.getBookmark().ifPresent(bookmarks::remove);
            }
        }));
        steps.add(new MestJeiDevTest.Step("manual size survives", 10, null, mc -> {
            JeiBookmarkPanel panel = bookmarkPanel();
            check(panel.columns() == 8 && panel.rows() == 8 && JeiAccess.bookmarkList().getElements().isEmpty(),
                    "after removing all bookmarks the manual size stays ({}x{})", panel.columns(), panel.rows());
        }));
        steps.add(new MestJeiDevTest.Step("clear the filter", 5, null, mc -> JeiAccess.setFilterText("")));
        steps.add(new MestJeiDevTest.Step("scroll the list", 5, null, mc -> {
            JeiGridPanel list = JeiPanels.ingredientList();
            setMouse(mc, list.contentLeft() + 9, list.contentTop() + 9);
            firstBeforeScroll = JeiAccess.runtime().get().getIngredientListOverlay().getIngredientUnderMouse()
                    .map(Object::toString).orElse("none");
            Screen screen = mc.screen;
            if (!ForgeHooksClient.onScreenMouseScrollPre(mc.mouseHandler, screen, -1)) {
                screen.mouseScrolled(list.contentLeft() + 9, list.contentTop() + 9, -1);
                ForgeHooksClient.onScreenMouseScrollPost(mc.mouseHandler, screen, -1);
            }
        }));
        steps.add(new MestJeiDevTest.Step("list scrolled", 5, null, mc -> {
            String now = JeiAccess.runtime().get().getIngredientListOverlay().getIngredientUnderMouse()
                    .map(Object::toString).orElse("none");
            check(!now.equals(firstBeforeScroll), "the wheel scrolls the list ({} -> {})", firstBeforeScroll, now);
        }));
        steps.add(new MestJeiDevTest.Step("cheat mode on", 10, null, mc -> {
            JeiAccess.toggleState().setCheatItemsEnabled(true);
            JeiAccess.setFilterText(diamond());
        }));
        steps.add(new MestJeiDevTest.Step("click in cheat mode", 15, "jei_06e_cheat", mc -> {
            JeiGridPanel list = JeiPanels.ingredientList();
            click(mc, list.contentLeft() + 9, list.contentTop() + 9, 0);
        }));
        steps.add(new MestJeiDevTest.Step("item given", 2, null, mc -> {
            var carried = mc.player.containerMenu.getCarried();
            boolean inInventory = mc.player.getInventory().items.stream()
                    .anyMatch(stack -> stack.getDescriptionId().contains("diamond"));
            check(!carried.isEmpty() || inInventory, "cheat-mode click gave the item (held: {})", carried);
            if (!carried.isEmpty()) {
                JeiGridPanel list = JeiPanels.ingredientList();
                setMouse(mc, list.contentLeft() + 9, list.contentTop() + 5 * 18 + 9);
                press(mc, list.contentLeft() + 9, list.contentTop() + 5 * 18 + 9, 0);
            }
        }));
        steps.add(new MestJeiDevTest.Step("held item deleted", 10, null, mc -> {
            check(mc.player.containerMenu.getCarried().isEmpty(), "clicking the list with a held item deletes it");
            JeiAccess.toggleState().setCheatItemsEnabled(false);
            JeiAccess.setFilterText(iron());
        }));
        steps.add(new MestJeiDevTest.Step("U shows uses", 30, null, mc -> {
            JeiGridPanel list = JeiPanels.ingredientList();
            setMouse(mc, list.contentLeft() + 9, list.contentTop() + 9);
            key(mc, GLFW.GLFW_KEY_U);
        }));
        steps.add(new MestJeiDevTest.Step("uses open", 30, null, mc -> {
            check(mc.screen instanceof IRecipesGui, "U opened JEI uses, screen={}", mc.screen);
            if (mc.screen instanceof IRecipesGui) {
                mc.screen.onClose();
            }
        }));
        steps.add(new MestJeiDevTest.Step("drag to a ghost slot", 10, null, mc -> {
            ghostSlot = null;
            if (!(mc.screen instanceof MESTScreen screen)) {
                check(false, "not in the terminal for the drag test");
                return;
            }
            for (Slot slot : screen.getMenu().slots) {
                if (slot instanceof FakeSlot && slot.x > 0) {
                    log("fake slot {} at {},{} active={} item={} top={}", slot.getClass().getSimpleName(), slot.x,
                            slot.y, slot.isActive(), slot.getItem(), JeiPanels.topAt(slot.x + 8, slot.y + 8));
                }
                if (slot instanceof FakeSlot fake && slot.isActive() && slot.x > 0 && slot.y > 0
                        && slot.getItem().isEmpty() && JeiPanels.topAt(slot.x + 8, slot.y + 8) == null) {
                    ghostSlot = fake;
                    break;
                }
            }
            if (ghostSlot == null) {
                log("SKIP no visible empty ghost slot for the drag test");
                return;
            }
            JeiGridPanel list = JeiPanels.ingredientList();
            double sx = list.contentLeft() + 9;
            double sy = list.contentTop() + 9;
            setMouse(mc, sx, sy);
            press(mc, sx, sy, 0);
        }));
        // JEI only starts a drag after its drag delay, so the moves come a few ticks later.
        steps.add(new MestJeiDevTest.Step("drag towards the slot", 10, "jei_07_ghost_drag", mc -> {
            if (ghostSlot == null) {
                return;
            }
            JeiGridPanel list = JeiPanels.ingredientList();
            double sx = list.contentLeft() + 9;
            double sy = list.contentTop() + 9;
            double tx = ghostSlot.x + 8;
            double ty = ghostSlot.y + 8;
            for (int i = 1; i <= 10; i++) {
                double x = sx + (tx - sx) * i / 10;
                double y = sy + (ty - sy) * i / 10;
                setMouse(mc, x, y);
                drag(mc, x, y, 0);
            }
        }));
        steps.add(new MestJeiDevTest.Step("drop on ghost slot", 20, "jei_08_ghost_drop", mc -> {
            if (ghostSlot == null) {
                return;
            }
            release(mc, ghostSlot.x + 8, ghostSlot.y + 8, 0);
        }));
        steps.add(new MestJeiDevTest.Step("ghost slot filled", 5, null, mc -> {
            if (ghostSlot != null) {
                check(!ghostSlot.getItem().isEmpty(), "dragging an ingredient set the ghost slot to {}",
                        ghostSlot.getItem());
                check(mc.screen instanceof MESTScreen, "a drag does not open recipes, screen={}", mc.screen);
            }
        }));
        steps.add(new MestJeiDevTest.Step("right-click clears search", 10, "jei_09_cleared", mc -> {
            int[] point = searchPoint(JeiPanels.search());
            if (point != null) {
                click(mc, point[0], point[1], 1);
            }
        }));
        steps.add(new MestJeiDevTest.Step("search cleared", 5, null, mc -> {
            check(JeiAccess.filterText().isEmpty(), "right-click cleared JEI's filter: '{}'", JeiAccess.filterText());
            key(mc, GLFW.GLFW_KEY_ESCAPE);
        }));
        steps.add(new MestJeiDevTest.Step("leave terminal", 20, null, mc -> {
            mc.player.closeContainer();
            mc.setScreen(new InventoryScreen(mc.player));
        }));
        steps.add(new MestJeiDevTest.Step("JEI back elsewhere", 20, "jei_10_inventory", mc -> {
            IJeiRuntime runtime = JeiAccess.runtime().orElse(null);
            check(runtime != null && runtime.getIngredientListOverlay().isListDisplayed(),
                    "JEI's own list shows again outside the terminal");
        }));
        steps.add(new MestJeiDevTest.Step("close inventory", 5, null, mc -> mc.setScreen(null)));
    }

    /** The history button: the title-bar control left of the dock's own buttons. */
    private static int historyButtonX(JeiHistoryPanel history) {
        int right = history.x + history.width - 4;
        boolean inDockButtons = true;
        for (int x = right; x > history.x; x--) {
            boolean control = history.inTitleBarControls(x, history.y + 9);
            if (inDockButtons && !control && x < right - 2) {
                inDockButtons = false;
            } else if (!inDockButtons && control) {
                return x - 5;
            }
        }
        return right - 6;
    }

    private static int expectedPanels() {
        return JeiAccess.historySupported() ? 4 : 3;
    }

    /** Search words that match item names in the client's language. */
    private static boolean chinese() {
        return Minecraft.getInstance().options.languageCode.startsWith("zh");
    }

    private static String iron() {
        return chinese() ? "铁" : "iron";
    }

    private static String diamond() {
        return chinese() ? "钻石" : "diamond";
    }

    private static JeiBookmarkPanel bookmarkPanel() {
        return JeiPanels.bookmarks();
    }

    private static int[] searchPoint(JeiSearchPanel list) {
        if (list == null) {
            return null;
        }
        for (int x = list.x + list.width - 30; x > list.x; x -= 4) {
            if (list.inSearch(x, list.y + 9)) {
                return new int[] { x, list.y + 9 };
            }
        }
        return null;
    }

    // --- Input as MouseHandler / KeyboardHandler deliver it ---------------------

    static void setMouse(Minecraft mc, double guiX, double guiY) {
        var window = mc.getWindow();
        double sx = guiX * window.getScreenWidth() / window.getGuiScaledWidth();
        double sy = guiY * window.getScreenHeight() / window.getGuiScaledHeight();
        GLFW.glfwSetCursorPos(window.getWindow(), sx, sy);
        try {
            Field xpos = MouseHandler.class.getDeclaredField("xpos");
            Field ypos = MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(mc.mouseHandler, sx);
            ypos.setDouble(mc.mouseHandler, sy);
        } catch (ReflectiveOperationException e) {
            log("could not set mouse position: {}", e);
        }
        if (mc.screen != null) {
            mc.screen.mouseMoved(guiX, guiY);
        }
    }

    static void press(Minecraft mc, double x, double y, int button) {
        Screen screen = mc.screen;
        if (!ForgeHooksClient.onScreenMouseClickedPre(screen, x, y, button)) {
            boolean handled = screen.mouseClicked(x, y, button);
            ForgeHooksClient.onScreenMouseClickedPost(screen, x, y, button, handled);
        }
    }

    static void release(Minecraft mc, double x, double y, int button) {
        Screen screen = mc.screen;
        if (!ForgeHooksClient.onScreenMouseReleasedPre(screen, x, y, button)) {
            boolean handled = screen.mouseReleased(x, y, button);
            ForgeHooksClient.onScreenMouseReleasedPost(screen, x, y, button, handled);
        }
    }

    static void drag(Minecraft mc, double x, double y, int button) {
        Screen screen = mc.screen;
        if (!ForgeHooksClient.onScreenMouseDragPre(screen, x, y, button, 1, 1)) {
            screen.mouseDragged(x, y, button, 1, 1);
            ForgeHooksClient.onScreenMouseDragPost(screen, x, y, button, 1, 1);
        }
    }

    static void click(Minecraft mc, double x, double y, int button) {
        setMouse(mc, x, y);
        press(mc, x, y, button);
        Screen after = mc.screen;
        if (after != null) {
            release(mc, x, y, button);
        }
    }

    static void key(Minecraft mc, int keyCode) {
        Screen screen = mc.screen;
        int scanCode = GLFW.glfwGetKeyScancode(keyCode);
        if (!ForgeHooksClient.onScreenKeyPressedPre(screen, keyCode, scanCode, 0)) {
            if (!screen.keyPressed(keyCode, scanCode, 0)) {
                ForgeHooksClient.onScreenKeyPressedPost(screen, keyCode, scanCode, 0);
            }
        }
    }

    static void typeChar(Minecraft mc, char c) {
        Screen screen = mc.screen;
        if (!ForgeHooksClient.onScreenCharTypedPre(screen, c, 0)) {
            if (!screen.charTyped(c, 0)) {
                ForgeHooksClient.onScreenCharTypedPost(screen, c, 0);
            }
        }
    }
}
