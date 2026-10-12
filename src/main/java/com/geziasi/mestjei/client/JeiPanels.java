package com.geziasi.mestjei.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.input.IClickableIngredientInternal;

import com.lhy.mest.client.MESTScreen;

import com.geziasi.mestjei.client.jei.JeiGrid;
import com.geziasi.mestjei.client.panel.JeiBookmarkPanel;
import com.geziasi.mestjei.client.panel.JeiGridPanel;
import com.geziasi.mestjei.client.panel.JeiHistoryPanel;
import com.geziasi.mestjei.client.panel.JeiIngredientListPanel;
import com.geziasi.mestjei.client.panel.JeiSearchPanel;
import com.geziasi.mestjei.client.panel.JeiTerminalPanel;

/** The JEI panels of each open terminal screen, and what JEI's hooks ask about them. */
public final class JeiPanels {
    private static final Map<Screen, List<JeiTerminalPanel>> PANELS = new WeakHashMap<>();

    private JeiPanels() {
    }

    /** Panels are built once per screen; the newest one with an id replaces an older one. */
    public static void register(MESTScreen screen, JeiTerminalPanel panel) {
        List<JeiTerminalPanel> panels = PANELS.computeIfAbsent(screen, s -> new ArrayList<>());
        panels.removeIf(existing -> existing.id().equals(panel.id()));
        panels.add(panel);
    }

    /**
     * While a terminal is open JEI's own ingredient list and bookmarks are hidden: these panels
     * take their place. Without a usable JEI grid nothing would replace them, so they stay.
     */
    public static boolean hidesJeiOverlays(@Nullable Screen screen) {
        return screen instanceof MESTScreen && JeiGrid.available();
    }

    public static List<JeiTerminalPanel> current() {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof MESTScreen)) {
            return List.of();
        }
        List<JeiTerminalPanel> panels = PANELS.get(screen);
        return panels == null ? List.of() : panels;
    }

    public static List<JeiGridPanel> grids() {
        List<JeiGridPanel> grids = new ArrayList<>(2);
        for (JeiTerminalPanel panel : current()) {
            if (panel instanceof JeiGridPanel grid) {
                grids.add(grid);
            }
        }
        return grids;
    }

    /** The JEI panel that is the topmost window at the point, if any. */
    @Nullable
    public static JeiTerminalPanel topAt(double mouseX, double mouseY) {
        for (JeiTerminalPanel panel : current()) {
            if (panel.isTopAt(mouseX, mouseY)) {
                return panel;
            }
        }
        return null;
    }

    /** The grid panel that is the topmost window at the point, if any. */
    @Nullable
    public static JeiGridPanel gridAt(double mouseX, double mouseY) {
        return topAt(mouseX, mouseY) instanceof JeiGridPanel grid ? grid : null;
    }

    @Nullable
    public static <T extends JeiTerminalPanel> T find(Class<T> type) {
        for (JeiTerminalPanel panel : current()) {
            if (type.isInstance(panel)) {
                return type.cast(panel);
            }
        }
        return null;
    }

    @Nullable
    public static JeiIngredientListPanel ingredientList() {
        return find(JeiIngredientListPanel.class);
    }

    @Nullable
    public static JeiSearchPanel search() {
        return find(JeiSearchPanel.class);
    }

    @Nullable
    public static JeiBookmarkPanel bookmarks() {
        return find(JeiBookmarkPanel.class);
    }

    /** Ingredients under the mouse in the ingredient-list panel, for JEI's input handlers. */
    public static Stream<IClickableIngredientInternal<?>> listIngredientsAt(double mouseX, double mouseY) {
        return JeiInput.isDragging() || !(topAt(mouseX, mouseY) instanceof JeiIngredientListPanel panel)
                ? Stream.empty()
                : panel.clickableAt(mouseX, mouseY);
    }

    /**
     * Ingredients under the mouse in the bookmark or lookup-history panel, for JEI's input handlers.
     * JEI keeps its history inside the bookmark overlay, so both answer for it.
     */
    public static Stream<IClickableIngredientInternal<?>> bookmarksAt(double mouseX, double mouseY) {
        if (JeiInput.isDragging()) {
            return Stream.empty();
        }
        JeiTerminalPanel top = topAt(mouseX, mouseY);
        return top instanceof JeiBookmarkPanel || top instanceof JeiHistoryPanel
                ? ((JeiGridPanel) top).clickableAt(mouseX, mouseY)
                : Stream.empty();
    }

    public static Optional<ITypedIngredient<?>> listIngredientUnderMouse() {
        double[] mouse = JeiInput.mouse();
        return listIngredientsAt(mouse[0], mouse[1]).findFirst().map(c -> c.getTypedIngredient());
    }

    public static Optional<ITypedIngredient<?>> bookmarkUnderMouse() {
        double[] mouse = JeiInput.mouse();
        return bookmarksAt(mouse[0], mouse[1]).findFirst().map(c -> c.getTypedIngredient());
    }
}
