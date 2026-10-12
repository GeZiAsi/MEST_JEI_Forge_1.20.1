package com.geziasi.mestjei.client.panel;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;

import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.Internal;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.elements.IElement;

import com.lhy.mest.api.client.MestModuleContext;
import com.lhy.mest.client.dock.model.ModuleDefaults;

import com.geziasi.mestjei.client.jei.JeiAccess;

/**
 * JEI's bookmarks as a terminal panel. It shows JEI's own bookmark list, so bookmarks added or
 * removed here (A over an ingredient, as in JEI) are JEI's bookmarks, saved to JEI's file.
 */
public final class JeiBookmarkPanel extends JeiGridPanel {
    public static final String ID = "mestjei:bookmarks";

    private List<IElement<?>> elements = List.of();
    @Nullable
    private BookmarkList source;
    private int revision = -1;
    private int sizedCount = -1;

    public JeiBookmarkPanel(MestModuleContext context) {
        super(context, false);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("gui.mestjei.bookmarks");
    }

    @Override
    public int defaultWidth() {
        return widthForColumns(3);
    }

    @Override
    public int defaultHeight() {
        return heightForRows(5);
    }

    @Override
    public boolean expandsVertically() {
        return false;
    }

    public int bookmarkCount() {
        return elements().size();
    }

    /** True once per change of the bookmark count (and on the first call). */
    public boolean takeCountChange() {
        int count = bookmarkCount();
        if (count == sizedCount) {
            return false;
        }
        sizedCount = count;
        return true;
    }

    @Override
    public ModuleDefaults defaults() {
        return new ModuleDefaults(true, true, false, true);
    }

    @Override
    public Icon icon() {
        return Icon.SEARCH_JEI;
    }

    /** JEI's own bookmark-button star, in the terminal's module buttons and the layout editor. */
    @Override
    public void renderIcon(GuiGraphics g, int x, int y, int w, int h, float opacity) {
        IDrawableStatic star;
        try {
            star = Internal.getTextures().getBookmarkButtonEnabledIcon();
        } catch (RuntimeException e) {
            star = null;
        }
        if (star == null) {
            super.renderIcon(g, x, y, w, h, opacity);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, opacity);
        star.draw(g, x + (w - star.getWidth()) / 2, y + (h - star.getHeight()) / 2);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The bookmark list rebuilds its element list per call, so it is re-read only after edits. */
    @Override
    protected List<IElement<?>> elements() {
        BookmarkList list = JeiAccess.bookmarkList();
        int current = JeiAccess.bookmarkRevision();
        if (list != source || current != revision) {
            source = list;
            revision = current;
            elements = list == null ? List.of() : list.getElements();
            invalidateGrid();
        }
        return elements;
    }

    @Nullable
    public BookmarkList bookmarkList() {
        elements();
        return source;
    }

    @Override
    public boolean listEnabled() {
        return JeiAccess.isReady() && JeiAccess.toggleState().isBookmarkOverlayEnabled();
    }

    @Nullable
    @Override
    protected Component placeholder() {
        if (!JeiAccess.isReady()) {
            return Component.translatable("gui.mestjei.jei_loading");
        }
        // JEI itself switches the bookmark list off while it is empty and back on with the first
        // bookmark, so an empty list is explained as empty, not as hidden.
        if (elements().isEmpty()) {
            return Component.translatable("gui.mestjei.bookmarks.empty",
                    JeiAccess.keyMappings().getBookmark().getTranslatedKeyMessage());
        }
        if (!JeiAccess.toggleState().isBookmarkOverlayEnabled()) {
            var key = JeiAccess.keyMappings().getToggleBookmarkOverlay();
            return key.isUnbound()
                    ? Component.translatable("gui.mestjei.bookmarks.disabled.unbound")
                    : Component.translatable("gui.mestjei.bookmarks.disabled", key.getTranslatedKeyMessage());
        }
        return null;
    }

    /** No title-bar widgets of its own: the title may use everything left of the dock's buttons. */
    @Override
    protected int titleRightInset() {
        return chromeButtonsReserve() + 2;
    }
}
