package com.geziasi.mestjei.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.Rect2i;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import com.lhy.mest.client.MESTScreen;
import com.lhy.mest.client.dock.DockManager;
import com.lhy.mest.client.dock.model.DockRect;
import com.lhy.mest.client.dock.model.DockWorkspace;
import com.lhy.mest.client.dock.model.FloatingRoot;
import com.lhy.mest.client.dock.model.DockAxis;
import com.lhy.mest.client.dock.model.LeafNode;
import com.lhy.mest.client.dock.model.NodeIds;
import com.lhy.mest.client.dock.model.SplitNode;

import com.geziasi.mestjei.MestJei;
import com.geziasi.mestjei.client.panel.JeiBookmarkPanel;
import com.geziasi.mestjei.client.panel.JeiGridPanel;
import com.geziasi.mestjei.client.panel.JeiHistoryPanel;
import com.geziasi.mestjei.client.panel.JeiIngredientListPanel;
import com.geziasi.mestjei.client.panel.JeiSearchPanel;
import com.geziasi.mestjei.client.panel.JeiTerminalPanel;

/**
 * Where the JEI panels' windows go and how big the bookmark window is.
 *
 * <p>The first time the terminal shows them, the ingredient list goes along the right edge of the
 * screen and the bookmarks along the left, where JEI keeps them; the terminal itself would stack
 * every new window in a cascade over its own. The bookmark window starts small (3×5) and follows the
 * number of bookmarks, up to 5 columns and 10 rows, for as long as the user has not resized it.
 * Everything else about the windows is the terminal's layout, saved like any other window.
 */
final class JeiPlacement {
    private static final int MARGIN = 4;
    private static final int GAP = 6;
    private static final int MAX_LIST_COLUMNS = 12;
    private static final int MIN_COLUMNS = 2;
    private static final int MIN_ROWS = 2;
    /** A search strip spliced over the list: its title bar plus a little air. */
    private static final int SEARCH_SECTION_HEIGHT = 20;
    static final int BOOKMARK_COLUMNS = 3;
    static final int BOOKMARK_ROWS = 5;
    static final int BOOKMARK_MAX_COLUMNS = 5;
    static final int BOOKMARK_MAX_ROWS = 10;

    private static final String PLACED = "placed";
    private static final String BOOKMARK_WIDTH = "bookmarkAutoWidth";
    private static final String BOOKMARK_HEIGHT = "bookmarkAutoHeight";

    private JeiPlacement() {
    }

    static Path stateFile() {
        return FMLPaths.CONFIGDIR.get().resolve(MestJei.MODID).resolve("windows.properties");
    }

    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof MESTScreen screen)) {
            return;
        }
        try {
            placeNewPanels(screen);
        } catch (RuntimeException e) {
            MestJei.LOGGER.warn("Could not place the JEI panels; they keep the terminal's default spot", e);
        }
    }

    // --- First placement -----------------------------------------------------------

    private static void placeNewPanels(MESTScreen screen) {
        Properties state = readState();
        Set<String> placed = placedIds(state);
        JeiIngredientListPanel list = null;
        JeiBookmarkPanel bookmarks = null;
        JeiSearchPanel search = null;
        JeiHistoryPanel history = null;
        for (JeiTerminalPanel panel : JeiPanels.current()) {
            if (panel.context().screen() != screen || placed.contains(panel.id())) {
                continue;
            }
            if (panel instanceof JeiIngredientListPanel p) {
                list = p;
            } else if (panel instanceof JeiBookmarkPanel p) {
                bookmarks = p;
            } else if (panel instanceof JeiSearchPanel p) {
                search = p;
            } else if (panel instanceof JeiHistoryPanel p) {
                history = p;
            }
        }
        if (list == null && bookmarks == null && search == null && history == null) {
            return;
        }
        Rect2i terminal = screen.recipeViewerEnvelope();
        if (terminal.getWidth() <= 0 || terminal.getHeight() <= 0) {
            return;
        }
        DockManager dock = findDock(screen);
        if (dock == null) {
            return;
        }
        Set<String> moved = new LinkedHashSet<>();
        if (list != null) {
            DockRect bounds = listBounds(terminal, screen.width, screen.height);
            if (bounds != null && setOwnWindowBounds(dock, list.id(), bounds)) {
                moved.add(list.id());
            }
        }
        if (bookmarks != null) {
            DockRect bounds = bookmarkBounds(MARGIN, MARGIN, bookmarks.bookmarkCount(), terminal, screen.height);
            if (bounds != null && setOwnWindowBounds(dock, bookmarks.id(), bounds)) {
                moved.add(bookmarks.id());
                rememberAutoSize(state, bounds);
            }
        }
        if (history != null) {
            DockRect bounds = historyBounds(history, terminal, screen.height);
            if (bounds != null && setOwnWindowBounds(dock, history.id(), bounds)) {
                moved.add(history.id());
            }
        }
        // The search bar joins the list's window once the list has its place; until then it waits.
        boolean listPlaced = placed.contains(JeiIngredientListPanel.ID) || moved.contains(JeiIngredientListPanel.ID);
        if (search != null && listPlaced) {
            if (spliceOnTopOfList(dock)) {
                moved.add(search.id());
            } else {
                // The list is part of another window: the search bar gets a window of its own
                // beside the terminal instead.
                int x = Math.min(terminal.getX() + terminal.getWidth() + GAP,
                        Math.max(0, screen.width - search.defaultWidth() - MARGIN));
                if (setOwnWindowBounds(dock, search.id(),
                        new DockRect(x, MARGIN, search.defaultWidth(), search.defaultHeight()))) {
                    moved.add(search.id());
                }
            }
        }
        if (moved.isEmpty()) {
            return;
        }
        placed.addAll(moved);
        state.setProperty(PLACED, String.join(",", placed));
        writeState(state);
        MestJei.LOGGER.info("Placed JEI panels {} beside the terminal", moved);
    }

    private static DockManager findDock(MESTScreen screen) {
        for (JeiTerminalPanel panel : JeiPanels.current()) {
            if (panel.context().screen() == screen) {
                return panel.context().dock();
            }
        }
        return null;
    }

    /**
     * Joins the search bar's window onto the top of the ingredient list's window, as one spliced
     * window of the list's size: a search strip over the list.
     */
    private static boolean spliceOnTopOfList(DockManager dock) {
        DockWorkspace workspace = dock.workspaceSnapshot();
        List<FloatingRoot> roots = new ArrayList<>(workspace.roots());
        int searchIndex = ownWindow(roots, JeiSearchPanel.ID);
        int listIndex = ownWindow(roots, JeiIngredientListPanel.ID);
        if (searchIndex < 0 || listIndex < 0) {
            return false;
        }
        FloatingRoot searchRoot = roots.get(searchIndex);
        FloatingRoot listRoot = roots.get(listIndex);
        int height = listRoot.bounds().height();
        if (height <= SEARCH_SECTION_HEIGHT * 2) {
            return false;
        }
        double ratio = SEARCH_SECTION_HEIGHT / (double) height;
        SplitNode split = new SplitNode(NodeIds.deterministic("split", JeiSearchPanel.ID + "+" + JeiIngredientListPanel.ID),
                DockAxis.VERTICAL, ratio, searchRoot.content(), listRoot.content());
        roots.set(listIndex, listRoot.withContent(split));
        roots.remove(searchIndex);
        dock.applyEditedWorkspace(workspace.withRoots(roots));
        return true;
    }

    /** Index of the floating window that holds only this module, or -1. */
    private static int ownWindow(List<FloatingRoot> roots, String moduleId) {
        for (int i = 0; i < roots.size(); i++) {
            FloatingRoot root = roots.get(i);
            if (root.floating() && root.content() instanceof LeafNode leaf && leaf.moduleId().equals(moduleId)) {
                return i;
            }
        }
        return -1;
    }

    /** Along the right edge of the screen, as tall as the screen, up to 12 columns wide. */
    @Nullable
    private static DockRect listBounds(Rect2i terminal, int screenWidth, int screenHeight) {
        int left = terminal.getX() + terminal.getWidth() + GAP;
        int limit = screenWidth - MARGIN;
        int columns = MAX_LIST_COLUMNS;
        while (columns >= MIN_COLUMNS && JeiGridPanel.widthForColumns(columns) > limit - left) {
            columns--;
        }
        int rows = (screenHeight - 2 * MARGIN - JeiGridPanel.heightForRows(0)) / JeiGridPanel.SLOT_SIZE;
        if (columns < MIN_COLUMNS || rows < MIN_ROWS) {
            return null;
        }
        int width = JeiGridPanel.widthForColumns(columns);
        return new DockRect(limit - width, MARGIN, width, JeiGridPanel.heightForRows(rows));
    }

    /** Its own window in the bottom-left corner, where JEI shows the history under the bookmarks. */
    @Nullable
    private static DockRect historyBounds(JeiHistoryPanel panel, Rect2i terminal, int screenHeight) {
        int width = panel.defaultWidth();
        int height = panel.defaultHeight();
        int y = screenHeight - MARGIN - height;
        if (y < MARGIN || MARGIN + width > terminal.getX() - GAP) {
            return null;
        }
        return new DockRect(MARGIN, y, width, height);
    }

    // --- Bookmark window size --------------------------------------------------------

    /**
     * The bookmark window for {@code count} bookmarks: 3×5 at first, then taller up to 10 rows,
     * then wider up to 5 columns; past that the list scrolls.
     */
    @Nullable
    private static DockRect bookmarkBounds(int x, int y, int count, @Nullable Rect2i terminal, int screenHeight) {
        int columns = BOOKMARK_COLUMNS;
        int rows = BOOKMARK_ROWS;
        while (columns * rows < count) {
            if (rows < BOOKMARK_MAX_ROWS) {
                rows++;
            } else if (columns < BOOKMARK_MAX_COLUMNS) {
                columns++;
            } else {
                break;
            }
        }
        int fitRows = (screenHeight - y - MARGIN - JeiGridPanel.heightForRows(0)) / JeiGridPanel.SLOT_SIZE;
        rows = Math.min(rows, fitRows);
        if (rows < MIN_ROWS) {
            return null;
        }
        int width = JeiGridPanel.widthForColumns(columns);
        if (terminal != null && x + width > terminal.getX() - GAP && x < terminal.getX()) {
            // Keep the first placement clear of the terminal on narrow screens.
            while (columns > MIN_COLUMNS && x + JeiGridPanel.widthForColumns(columns) > terminal.getX() - GAP) {
                columns--;
            }
            width = JeiGridPanel.widthForColumns(columns);
        }
        return new DockRect(x, y, width, JeiGridPanel.heightForRows(rows));
    }

    /**
     * Grows or shrinks the bookmark window to its count, unless the user has resized it (its size
     * no longer matches the last automatic one) or it shares a window with other modules.
     */
    static void followBookmarkCount(JeiBookmarkPanel panel, int count) {
        Properties state = readState();
        String width = state.getProperty(BOOKMARK_WIDTH);
        String height = state.getProperty(BOOKMARK_HEIGHT);
        if (width == null || height == null || !panel.visible) {
            return;
        }
        if (panel.width != parse(width) || panel.height != parse(height)) {
            return;
        }
        MESTScreen screen = panel.context().screen();
        DockRect target = bookmarkBounds(panel.x, panel.y, count, null, screen.height);
        if (target == null || (target.width() == panel.width && target.height() == panel.height)) {
            return;
        }
        if (setOwnWindowBounds(panel.context().dock(), panel.id(), target)) {
            rememberAutoSize(state, target);
            writeState(state);
        }
    }

    // --- Workspace edits -------------------------------------------------------------

    /** Changes the bounds of the floating window that holds only this module; false if none. */
    private static boolean setOwnWindowBounds(DockManager dock, String moduleId, DockRect bounds) {
        DockWorkspace workspace = dock.workspaceSnapshot();
        List<FloatingRoot> roots = new ArrayList<>(workspace.roots());
        int index = ownWindow(roots, moduleId);
        if (index < 0) {
            return false;
        }
        roots.set(index, roots.get(index).withBounds(bounds));
        dock.applyEditedWorkspace(workspace.withRoots(roots));
        return true;
    }

    // --- State file --------------------------------------------------------------------

    private static void rememberAutoSize(Properties state, DockRect bounds) {
        state.setProperty(BOOKMARK_WIDTH, Integer.toString(bounds.width()));
        state.setProperty(BOOKMARK_HEIGHT, Integer.toString(bounds.height()));
    }

    private static Set<String> placedIds(Properties state) {
        Set<String> placed = new LinkedHashSet<>();
        for (String id : state.getProperty(PLACED, "").split(",")) {
            if (!id.isBlank()) {
                placed.add(id.trim());
            }
        }
        return placed;
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Properties readState() {
        Properties state = new Properties();
        Path file = stateFile();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                state.load(in);
            } catch (IOException e) {
                MestJei.LOGGER.warn("Could not read {}", file, e);
            }
        }
        return state;
    }

    private static void writeState(Properties state) {
        Path file = stateFile();
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                state.store(out, "MEST JEI panels: windows placed so far and the bookmark window's automatic size");
            }
        } catch (IOException e) {
            MestJei.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
