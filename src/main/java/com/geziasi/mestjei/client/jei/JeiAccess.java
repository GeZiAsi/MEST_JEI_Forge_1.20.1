package com.geziasi.mestjei.client.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IJeiClientConfigs;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.overlay.elements.IElement;

import com.geziasi.mestjei.MestJei;

/**
 * The JEI objects the panels share with JEI itself: the runtime, the ingredient filter behind the
 * search box, the bookmark list, the toggle state (cheat/edit mode) and the key mappings.
 *
 * <p>Everything named here exists unchanged in JEI 15.19 through 15.49. The filter and bookmark
 * list are private fields of JEI's API wrappers, so they are found by type, not by name.
 */
public final class JeiAccess {
    private static final Map<String, Boolean> REPORTED = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<Class<?>, Field>> FIELDS = new ConcurrentHashMap<>();
    private static final Set<Object> LISTENED_SOURCES = Collections.newSetFromMap(new WeakHashMap<>());

    @Nullable
    private static volatile IJeiRuntime runtime;
    /** Bumped whenever a JEI list we listen to reports a change. */
    private static volatile int bookmarkRevision;

    private JeiAccess() {
    }

    static void setRuntime(@Nullable IJeiRuntime value) {
        runtime = value;
        bookmarkRevision++;
    }

    public static Optional<IJeiRuntime> runtime() {
        return Optional.ofNullable(runtime);
    }

    public static boolean isReady() {
        return runtime != null;
    }

    public static IClientToggleState toggleState() {
        return Internal.getClientToggleState();
    }

    public static IInternalKeyMappings keyMappings() {
        return Internal.getKeyMappings();
    }

    public static IJeiClientConfigs clientConfigs() {
        return Internal.getJeiClientConfigs();
    }

    public static IClientConfig clientConfig() {
        return clientConfigs().getClientConfig();
    }

    // --- Search -------------------------------------------------------------

    public static String filterText() {
        IJeiRuntime rt = runtime;
        return rt == null ? "" : rt.getIngredientFilter().getFilterText();
    }

    public static void setFilterText(String text) {
        IJeiRuntime rt = runtime;
        if (rt != null && !text.equals(rt.getIngredientFilter().getFilterText())) {
            rt.getIngredientFilter().setFilterText(text);
        }
    }

    /**
     * The filtered, sorted ingredient list exactly as JEI shows it. JEI caches it until the filter
     * text or the ingredient set changes, so the same list instance comes back until then.
     */
    public static List<IElement<?>> ingredientElements() {
        IJeiRuntime rt = runtime;
        if (rt == null) {
            return List.of();
        }
        IngredientFilter filter = fieldOfType(rt.getIngredientFilter(), IngredientFilter.class);
        if (filter == null) {
            return List.of();
        }
        try {
            return filter.getElements();
        } catch (RuntimeException e) {
            reportOnce("filter.getElements", e);
            return List.of();
        }
    }

    // --- Bookmarks ------------------------------------------------------------

    @Nullable
    public static BookmarkList bookmarkList() {
        IJeiRuntime rt = runtime;
        if (rt == null) {
            return null;
        }
        BookmarkList list = fieldOfType(rt.getBookmarkOverlay(), BookmarkList.class);
        if (list != null) {
            listenForChanges(list);
        }
        return list;
    }

    /** Changes whenever the bookmark list or lookup history reports an edit (add, remove, move, reload). */
    public static int bookmarkRevision() {
        return bookmarkRevision;
    }

    // --- Lookup history (JEI 15.4x) -----------------------------------------------

    private static final String HISTORY_OVERLAY = "mezz.jei.gui.overlay.bookmarks.history.LookupHistoryOverlay";
    private static final String HISTORY_BUTTON = "mezz.jei.gui.overlay.bookmarks.history.LookupHistoryButton";
    @Nullable
    private static final Class<?> HISTORY_OVERLAY_CLASS = classOrNull(HISTORY_OVERLAY);

    /** JEI keeps a lookup history (recently looked-up ingredients) from 15.4x on. */
    public static boolean historySupported() {
        return HISTORY_OVERLAY_CLASS != null;
    }

    /** The lookup history's elements, newest first, or an empty list. */
    public static List<IElement<?>> historyElements() {
        Object source = lookupHistory();
        if (source == null) {
            return List.of();
        }
        try {
            Object elements = source.getClass().getMethod("getElements").invoke(source);
            @SuppressWarnings("unchecked")
            List<IElement<?>> list = elements instanceof List<?> l ? (List<IElement<?>>) l : List.of();
            return list;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reportOnce("LookupHistory.getElements", e);
            return List.of();
        }
    }

    /** The JEI object holding the history list (it lives in the bookmark overlay). */
    @Nullable
    private static Object lookupHistory() {
        IJeiRuntime rt = runtime;
        if (rt == null || HISTORY_OVERLAY_CLASS == null) {
            return null;
        }
        Object overlay = fieldOfType(rt.getBookmarkOverlay(), HISTORY_OVERLAY_CLASS);
        if (overlay == null) {
            return null;
        }
        try {
            Object history = overlay.getClass().getMethod("getLookupHistory").invoke(overlay);
            if (history != null) {
                listenForChanges(history);
            }
            return history;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reportOnce("LookupHistoryOverlay.getLookupHistory", e);
            return null;
        }
    }

    /** JEI's "lookup history" setting (the history button toggles it). */
    public static boolean historyEnabled() {
        if (!historySupported()) {
            return false;
        }
        try {
            Object value = IClientConfig.class.getMethod("isLookupHistoryEnabled").invoke(clientConfig());
            return Boolean.TRUE.equals(value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reportOnce("IClientConfig.isLookupHistoryEnabled", e);
            return false;
        }
    }

    /** JEI's own history toggle button (behaviour and tooltip), or null before JEI 15.4x. */
    @Nullable
    public static mezz.jei.gui.elements.GuiIconToggleButton createHistoryButton() {
        try {
            Class<?> type = Class.forName(HISTORY_BUTTON, false, JeiAccess.class.getClassLoader());
            Object button = type.getMethod("create", IClientConfig.class).invoke(null, clientConfig());
            return button instanceof mezz.jei.gui.elements.GuiIconToggleButton toggle ? toggle : null;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reportOnce("LookupHistoryButton.create", e);
            return null;
        }
    }

    /** JEI's history button icon, lit or dimmed as the setting is, or null before JEI 15.4x. */
    @Nullable
    public static mezz.jei.api.gui.drawable.IDrawableStatic historyIcon(boolean enabled) {
        try {
            Object textures = Internal.getTextures();
            Object icon = textures.getClass()
                    .getMethod(enabled ? "getHistoryButtonEnabledICon" : "getHistoryButtonDisabledIcon")
                    .invoke(textures);
            return icon instanceof mezz.jei.api.gui.drawable.IDrawableStatic drawable ? drawable : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name, false, JeiAccess.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /**
     * Registers a change listener on a JEI grid source. Its listener interface moved between JEI
     * versions ({@code gui.overlay} → {@code gui.overlay.ingredients}), so it is implemented with a
     * proxy of whatever type {@code addSourceListChangedListener} takes.
     */
    private static void listenForChanges(Object source) {
        synchronized (LISTENED_SOURCES) {
            if (!LISTENED_SOURCES.add(source)) {
                return;
            }
        }
        try {
            Method add = null;
            for (Method method : source.getClass().getMethods()) {
                if (method.getName().equals("addSourceListChangedListener") && method.getParameterCount() == 1) {
                    add = method;
                    break;
                }
            }
            if (add == null) {
                reportOnce("addSourceListChangedListener", null);
                return;
            }
            Class<?> listenerType = add.getParameterTypes()[0];
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] { listenerType },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "MEST JEI panel listener";
                        default -> {
                            bookmarkRevision++;
                            yield null;
                        }
                    });
            add.invoke(source, listener);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reportOnce("listenForChanges", e);
        }
    }

    // --- Reflection helpers ---------------------------------------------------

    @Nullable
    @SuppressWarnings("unchecked")
    static <T> T fieldOfType(Object owner, Class<T> type) {
        Field field = FIELDS.computeIfAbsent(owner.getClass(), c -> new ConcurrentHashMap<>())
                .computeIfAbsent(type, t -> findField(owner.getClass(), t));
        if (field == null) {
            reportOnce("field " + type.getSimpleName() + " in " + owner.getClass().getName(), null);
            return null;
        }
        try {
            return (T) field.get(owner);
        } catch (IllegalAccessException e) {
            reportOnce("read " + field, e);
            return null;
        }
    }

    @Nullable
    private static Field findField(Class<?> owner, Class<?> type) {
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    public static void reportOnce(String what, @Nullable Throwable error) {
        if (REPORTED.putIfAbsent(what, Boolean.TRUE) == null) {
            MestJei.LOGGER.warn("MEST JEI panels: JEI internals not as expected ({}); this part is disabled", what,
                    error);
        }
    }
}
