package com.trevorschoeny.keybindery.screen;

import com.trevorschoeny.menukit.core.AbstractPanelElement;
import com.trevorschoeny.menukit.core.MKFocus;
import com.trevorschoeny.menukit.core.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * F4 toolbar search field — wraps vanilla {@link EditBox} as an MK
 * {@link com.trevorschoeny.menukit.core.PanelElement}. Pattern parallels
 * MK's own {@code TextField}, but explicitly calls {@link Screen#setFocused}
 * in {@code mouseClicked} — required because {@code VanillaScreenPanelAdapter}
 * eats in-panel clicks before vanilla's Screen.mouseClicked can route them
 * to widgets and set focus naturally.
 *
 * <p>If MK's TextField grows that focus-on-click contract (likely fold-on-
 * evidence once another consumer hits this), this class can fold into
 * TextField and disappear.
 */
public class SearchBox extends AbstractPanelElement<SearchBox> {

    /** MK 2.0.0 self-typed-generic contract — chainable base setters
     *  return the concrete subtype. */
    @Override protected SearchBox self() { return this; }

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final Component hint;
    private final Consumer<String> responder;
    /** Optional lens for the EditBox's value at each attach — lets the
     *  search box reflect a filter set externally (e.g. by
     *  {@code openWithModFilterFor} populating the underlying list's
     *  search query before the screen opens). Null = no auto-populate. */
    private final @Nullable Supplier<String> initialValueSupplier;

    // Lazily constructed in onAttach — see the note there for why we can't
    // construct it eagerly in the SearchBox constructor.
    private @Nullable EditBox editBox;
    private @Nullable Screen attachedScreen;

    public SearchBox(int childX, int childY, int width, int height,
                      Component hint, Consumer<String> responder) {
        this(childX, childY, width, height, hint, responder, null);
    }

    public SearchBox(int childX, int childY, int width, int height,
                      Component hint, Consumer<String> responder,
                      @Nullable Supplier<String> initialValueSupplier) {
        this.childX = childX;
        this.childY = childY;
        this.width = width;
        this.height = height;
        this.hint = hint;
        this.responder = responder;
        this.initialValueSupplier = initialValueSupplier;
    }

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }

    @Override
    public void render(RenderContext ctx) {
        if (editBox == null) return;  // not yet attached
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        editBox.setX(sx);
        editBox.setY(sy);
        if (ctx.hasMouseInput()) {
            editBox.render(ctx.graphics(), ctx.mouseX(), ctx.mouseY(), 0f);
        } else {
            editBox.render(ctx.graphics(), -1, -1, 0f);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // The MK adapter eats in-panel clicks before vanilla's
        // Screen.mouseClicked can route them to children — so EditBox would
        // never get focus through the normal flow. Wire setFocused directly.
        if (attachedScreen != null && editBox != null && button == 0) {
            attachedScreen.setFocused(editBox);
        }
        return true;
    }

    @Override
    public void onAttach(Screen screen) {
        if (attachedScreen == screen) return;
        attachedScreen = screen;
        // Lazy-construct the EditBox here, not in the SearchBox constructor.
        // Reason: ControlsToolbarPanel.install() runs at Fabric onInitializeClient
        // time, but Minecraft.getInstance().font is null at that point (font
        // loads later in the client lifecycle). Caching that null font into
        // a final EditBox.font field crashes on first render.
        // onAttach fires only when a screen is open — font is guaranteed
        // initialized by then.
        if (editBox == null) {
            var font = Minecraft.getInstance().font;
            editBox = new EditBox(font, 0, 0, width, height,
                    Component.literal("Search"));
            editBox.setHint(hint);
            editBox.setResponder(responder);
        }
        // Register for input dispatch (children + narratables) AND opt into
        // MK-managed focus semantics (the focus-janitor mixin clears focus
        // when the user clicks outside the EditBox's bounds anywhere on
        // screen). MKFocus.addWidget wraps the underlying registration —
        // no separate addRenderableWidget call needed (we render manually
        // in render() so the EditBox draws AFTER the panel background).
        MKFocus.addWidget(screen, editBox);

        // Populate the EditBox from the lens on every attach — the screen
        // (e.g. via openWithModFilterFor / openWithConflictsFilterFor)
        // pre-set the underlying list's search state before opening, so
        // the visible search box now reflects that. Triggers the
        // responder, which is a no-op when the list's query already
        // equals the supplier's value (setSearchQuery dedups).
        if (initialValueSupplier != null) {
            String value = initialValueSupplier.get();
            editBox.setValue(value == null ? "" : value);
        }
    }

    @Override
    public void onDetach(Screen screen) {
        if (attachedScreen == screen) {
            if (editBox != null) {
                MKFocus.removeWidget(screen, editBox);
            }
            attachedScreen = null;
        }
    }
}
