package com.geziasi.mestjei.client.panel;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;

import mezz.jei.gui.overlay.elements.IElement;

import com.lhy.mest.api.client.MestModuleContext;
import com.lhy.mest.client.dock.model.ModuleDefaults;

import com.geziasi.mestjei.client.jei.JeiAccess;

/**
 * JEI's ingredient list as a terminal panel: JEI's filtered, sorted list, filtered by the search
 * module (or by JEI's search bar on any other screen; the filter is shared).
 */
public final class JeiIngredientListPanel extends JeiGridPanel {
    public static final String ID = "mestjei:ingredient_list";

    private List<IElement<?>> lastElements = List.of();
    private String lastFilter = JeiAccess.filterText();

    public JeiIngredientListPanel(MestModuleContext context) {
        super(context, true);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("gui.mestjei.ingredient_list");
    }

    @Override
    public int defaultWidth() {
        return widthForColumns(9);
    }

    @Override
    public int defaultHeight() {
        return heightForRows(8);
    }

    @Override
    public ModuleDefaults defaults() {
        return new ModuleDefaults(true, true, false, true);
    }

    @Override
    public Icon icon() {
        return Icon.SEARCH_JEI;
    }

    /** A new search starts at the top of the list, as in JEI. */
    @Override
    protected List<IElement<?>> elements() {
        String filter = JeiAccess.filterText();
        if (!filter.equals(lastFilter)) {
            lastFilter = filter;
            resetScroll();
        }
        List<IElement<?>> elements = JeiAccess.ingredientElements();
        if (elements != lastElements) {
            lastElements = elements;
            invalidateGrid();
        }
        return elements;
    }

    @Override
    public boolean listEnabled() {
        return JeiAccess.isReady() && JeiAccess.toggleState().isOverlayEnabled();
    }

    @Nullable
    @Override
    protected Component placeholder() {
        if (!JeiAccess.isReady()) {
            return Component.translatable("gui.mestjei.jei_loading");
        }
        if (!JeiAccess.toggleState().isOverlayEnabled()) {
            return Component.translatable("gui.mestjei.ingredient_list.disabled",
                    JeiAccess.keyMappings().getToggleOverlay().getTranslatedKeyMessage());
        }
        return null;
    }

    /** No title-bar widgets of its own: the title may use everything left of the dock's buttons. */
    @Override
    protected int titleRightInset() {
        return chromeButtonsReserve() + 2;
    }
}
