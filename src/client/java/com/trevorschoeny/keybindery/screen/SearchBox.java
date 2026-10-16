package com.trevorschoeny.keybindery.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.panel.Focus;
import com.trevlar.menukit.api.element.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The toolbar's search field: wraps vanilla's {@link EditBox} as a MenuKit
 * {@link com.trevlar.menukit.api.element.PanelElement}. Works like MenuKit's
 * own {@code TextField}, but calls {@link Screen#setFocused} itself in
 * {@code mouseClicked}, because {@code VanillaScreenPanelAdapter} takes
 * in-panel clicks before vanilla's Screen.mouseClicked can pass them to
 * widgets and set focus the usual way.
 *
 * <p>If MenuKit's TextField gains focus-on-click (likely once another mod
 * needs it), this class can be replaced by TextField.
 */
public class SearchBox extends AbstractPanelElement {

    private final Component hint;
    private final Consumer<String> responder;
    /**
     * Optional source for the EditBox's value on each attach, so the search box
     * shows a filter set from outside (for example by {@code openWithModFilterFor}
     * setting the list's search query before the screen opens). Null means the
     * box starts empty.
     */
    private final @Nullable Supplier<String> initialValueSupplier;

    // Built in onAttach; the note there explains why it can't be built in the
    // SearchBox constructor.
    private @Nullable EditBox editBox;
    private @Nullable Screen attachedScreen;

    public SearchBox(int childX, int childY, int width, int height,
                      Component hint, Consumer<String> responder) {
        this(childX, childY, width, height, hint, responder, null);
    }

    public SearchBox(int childX, int childY, int width, int height,
                      Component hint, Consumer<String> responder,
                      @Nullable Supplier<String> initialValueSupplier) {
        // A MenuKit 6 subclass without a builder: set the base's geometry directly.
        this.childX = childX;
        this.childY = childY;
        this.width = this.authoredWidth = width;
        this.height = height;
        this.hint = hint;
        this.responder = responder;
        this.initialValueSupplier = initialValueSupplier;
    }

    @Override
    public void render(RenderContext ctx) {
        if (editBox == null) return;  // not yet attached
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        editBox.setX(sx);
        editBox.setY(sy);
        if (ctx.hasMouseInput()) {
            editBox.extractRenderState(ctx.graphics(), ctx.mouseX(), ctx.mouseY(), 0f);
        } else {
            editBox.extractRenderState(ctx.graphics(), -1, -1, 0f);
        }
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        // The MenuKit adapter takes in-panel clicks before vanilla's
        // Screen.mouseClicked can pass them to children, so the EditBox would
        // never get focus the normal way. Set focus directly.
        if (attachedScreen != null && editBox != null && button == InputConstants.MOUSE_BUTTON_LEFT) {
            attachedScreen.setFocused(editBox);
        }
        return true;
    }

    @Override
    public void onAttach(Screen screen) {
        if (attachedScreen == screen) return;
        attachedScreen = screen;
        // Build the EditBox here, not in the SearchBox constructor.
        // ControlsToolbarPanel.install() runs during Fabric's onInitializeClient,
        // when Minecraft.getInstance().font is still null (the font loads later).
        // Storing that null in EditBox's final font field crashes on first
        // render. onAttach only runs once a screen is open, by which time the
        // font is loaded.
        if (editBox == null) {
            var font = Minecraft.getInstance().font;
            editBox = new EditBox(font, 0, 0, width, height,
                    Component.literal("Search"));
            editBox.setHint(hint);
            editBox.setResponder(responder);
        }
        // Register for input (children and narratables) and for MenuKit's
        // focus handling, which clears focus when the player clicks anywhere
        // outside the EditBox. Focus.addWidget does the registration, so no
        // addRenderableWidget call is needed (render() draws the EditBox
        // itself, so it lands after the panel background).
        Focus.addWidget(screen, editBox);

        // Fill the EditBox from the supplier on every attach. The screen (for
        // example through openWithModFilterFor or openWithConflictsFilterFor)
        // set the list's search state before opening, and the search box
        // should show it. This triggers the responder, which does nothing when
        // the list's query already equals the value (setSearchQuery skips
        // duplicates).
        if (initialValueSupplier != null) {
            String value = initialValueSupplier.get();
            editBox.setValue(value == null ? "" : value);
        }
    }

    @Override
    public void onDetach(Screen screen) {
        if (attachedScreen == screen) {
            if (editBox != null) {
                Focus.removeWidget(screen, editBox);
            }
            attachedScreen = null;
        }
    }
}
