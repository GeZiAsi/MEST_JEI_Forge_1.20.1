package com.geziasi.mestjei.client.jei;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IIngredientGridConfig;
import mezz.jei.common.util.ImmutablePoint2i;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IDraggableIngredientInternal;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.overlay.elements.IElement;

/**
 * One of JEI's own {@code IngredientGrid}s, laid out inside a terminal panel.
 *
 * <p>Reusing JEI's grid keeps everything about the ingredients identical to JEI: the batched icon
 * rendering, hover highlight, edit-mode marks, tooltips (mod name, creative tabs, hints), the
 * clickable/draggable ingredients JEI's input handlers act on, and cheat-mode item deletion. The
 * class moved from {@code gui.overlay} to {@code gui.overlay.ingredients} in JEI 15.2x, with the same
 * constructor and methods, so it is reached by reflection.
 */
public final class JeiGrid {
    /** JEI's {@code INGREDIENT_WIDTH/HEIGHT}: a 16px icon plus 1px padding, the same as an AE2 slot. */
    public static final int SLOT = 18;

    private static final String[] GRID_CLASSES = {
            "mezz.jei.gui.overlay.ingredients.IngredientGrid",
            "mezz.jei.gui.overlay.IngredientGrid",
    };

    @Nullable
    private static final Api API = Api.resolve();

    private final Object grid;
    @Nullable
    private final IUserInputHandler deleteItemHandler;
    private ImmutableRect2i area = ImmutableRect2i.EMPTY;
    @Nullable
    private List<IElement<?>> shownList;
    private int shownFirst = -1;

    private JeiGrid(Object grid, @Nullable IUserInputHandler deleteItemHandler) {
        this.grid = grid;
        this.deleteItemHandler = deleteItemHandler;
    }

    /**
     * @param searchable true for the ingredient list (edit-mode marks, creative-tab tooltips),
     *                   false for bookmarks, as JEI builds them.
     */
    @Nullable
    public static JeiGrid create(IJeiRuntime runtime, boolean searchable) {
        if (API == null) {
            return null;
        }
        try {
            var configs = Internal.getJeiClientConfigs();
            IIngredientGridConfig base = searchable ? configs.getIngredientListConfig() : configs.getBookmarkListConfig();
            Object grid = API.constructor.newInstance(
                    runtime.getIngredientManager(),
                    unbounded(base),
                    configs.getIngredientFilterConfig(),
                    configs.getClientConfig(),
                    Internal.getClientToggleState(),
                    Internal.getServerConnection(),
                    Internal.getKeyMappings(),
                    runtime.getJeiHelpers().getColorHelper(),
                    searchable);
            Object handler = API.getInputHandler.invoke(grid);
            return new JeiGrid(grid, handler instanceof IUserInputHandler h ? h : null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            JeiAccess.reportOnce("IngredientGrid constructor", unwrap(e));
            return null;
        }
    }

    /**
     * JEI's grid config caps the rows and columns of its side overlays; inside a panel the user
     * sizes the window, so only those limits are lifted. Alignment and the rest stay JEI's.
     */
    private static IIngredientGridConfig unbounded(IIngredientGridConfig base) {
        return (IIngredientGridConfig) Proxy.newProxyInstance(IIngredientGridConfig.class.getClassLoader(),
                new Class<?>[] { IIngredientGridConfig.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getMaxColumns", "getMaxRows" -> 1000;
                    case "getMinColumns", "getMinRows" -> 1;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "MEST panel grid config of " + base;
                    default -> {
                        try {
                            yield method.invoke(base, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                });
    }

    /** Places a {@code cols}×{@code rows} grid of 18px cells with its top-left corner at (x, y). */
    public void layout(int x, int y, int cols, int rows) {
        int width = Math.max(0, cols) * SLOT;
        int height = Math.max(0, rows) * SLOT;
        // Compared by value: ImmutableRect2i only became a record in later JEI versions.
        if (area.getX() == x && area.getY() == y && area.getWidth() == width && area.getHeight() == height) {
            return;
        }
        ImmutableRect2i next = new ImmutableRect2i(x, y, width, height);
        area = next;
        call(API.updateBounds, next, Set.<ImmutableRect2i>of(), (ImmutablePoint2i) null);
        // updateBounds rebuilds the slots empty.
        shownList = null;
    }

    /** Shows {@code elements} starting at {@code first}; cheap when nothing changed. */
    public void show(int first, List<IElement<?>> elements) {
        int start = Math.max(0, Math.min(first, elements.size()));
        if (elements == shownList && start == shownFirst) {
            return;
        }
        shownList = elements;
        shownFirst = start;
        call(API.set, start, elements);
    }

    /** Forces the next {@link #show} to refill the slots (bookmark lists are rebuilt per call). */
    public void invalidate() {
        shownList = null;
    }

    public void draw(GuiGraphics graphics, int mouseX, int mouseY) {
        call(API.draw, Minecraft.getInstance(), graphics, mouseX, mouseY);
    }

    public void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        call(API.drawTooltips, Minecraft.getInstance(), graphics, mouseX, mouseY);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return area.contains(mouseX, mouseY);
    }

    @SuppressWarnings("unchecked")
    public Stream<IClickableIngredientInternal<?>> clickableAt(double mouseX, double mouseY) {
        Object result = call(API.clickable, mouseX, mouseY);
        return result instanceof Stream<?> stream ? (Stream<IClickableIngredientInternal<?>>) stream : Stream.empty();
    }

    @SuppressWarnings("unchecked")
    public Stream<IDraggableIngredientInternal<?>> draggableAt(double mouseX, double mouseY) {
        Object result = call(API.draggable, mouseX, mouseY);
        return result instanceof Stream<?> stream ? (Stream<IDraggableIngredientInternal<?>>) stream : Stream.empty();
    }

    /** JEI's cheat-mode "click the list with a held item to delete it". */
    @Nullable
    public IUserInputHandler deleteItemHandler() {
        return deleteItemHandler;
    }

    /** Animates recipe bookmarks and the like (JEI 15.4x); absent in older JEI. */
    public void tick() {
        if (API.tick != null) {
            call(API.tick);
        }
    }

    @Nullable
    private Object call(Method method, Object... args) {
        try {
            return method.invoke(grid, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A failure inside JEI's grid must not take the terminal screen down with it.
            JeiAccess.reportOnce("IngredientGrid." + method.getName(), unwrap(e));
            return null;
        }
    }

    private static Throwable unwrap(Throwable e) {
        return e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
    }

    public static boolean available() {
        return API != null;
    }

    private record Api(
            Constructor<?> constructor,
            Method updateBounds,
            Method set,
            Method draw,
            Method drawTooltips,
            Method clickable,
            Method draggable,
            Method getInputHandler,
            @Nullable Method tick) {
        @Nullable
        static Api resolve() {
            Class<?> type = null;
            for (String name : GRID_CLASSES) {
                try {
                    type = Class.forName(name, false, JeiGrid.class.getClassLoader());
                    break;
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (type == null) {
                JeiAccess.reportOnce("IngredientGrid class", null);
                return null;
            }
            try {
                Constructor<?> constructor = null;
                for (Constructor<?> candidate : type.getConstructors()) {
                    if (candidate.getParameterCount() == 9) {
                        constructor = candidate;
                    }
                }
                if (constructor == null) {
                    throw new NoSuchMethodException(type.getName() + ".<init>(9 args)");
                }
                Method tick;
                try {
                    tick = type.getMethod("tick");
                } catch (NoSuchMethodException e) {
                    tick = null;
                }
                return new Api(
                        constructor,
                        type.getMethod("updateBounds", ImmutableRect2i.class, Set.class, ImmutablePoint2i.class),
                        type.getMethod("set", int.class, List.class),
                        type.getMethod("draw", Minecraft.class, GuiGraphics.class, int.class, int.class),
                        type.getMethod("drawTooltips", Minecraft.class, GuiGraphics.class, int.class, int.class),
                        type.getMethod("getIngredientUnderMouse", double.class, double.class),
                        type.getMethod("getDraggableIngredientUnderMouse", double.class, double.class),
                        type.getMethod("getInputHandler"),
                        tick);
            } catch (ReflectiveOperationException | RuntimeException e) {
                JeiAccess.reportOnce("IngredientGrid methods", e);
                return null;
            }
        }
    }
}
