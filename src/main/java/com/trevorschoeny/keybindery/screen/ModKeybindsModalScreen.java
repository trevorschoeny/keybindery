package com.trevorschoeny.keybindery.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.chord.ChordCapture;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelPosition;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.ScrollContainer;
import com.trevorschoeny.menukit.screen.MenuKitScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal screen that lists every keybind belonging to a given mod, with
 * vanilla-styled rows + in-place chord capture. Opened from the
 * top-right "Keybinds" button injected on any ModMenu-launched config
 * screen. ESC closes; the previous (mod's own) config screen is restored.
 *
 * <p>Rendered as a {@link MenuKitScreen} subclass — keyboard goes directly
 * to {@link #keyPressed} (no global MK-keyboard suppression, since the
 * modal panel is {@code opaque(true).dimsBehind(true)} but NOT
 * {@code tracksAsModal}; the {@code tracksAsModal} flag causes the
 * {@code MenuKitModalKeyboardHandlerMixin} to eat all keys, which would
 * break chord capture).
 *
 * <p>The background mod-config screen renders behind the dim overlay as a
 * static backdrop — sentinel mouse coords so it doesn't react to hover.
 */
public final class ModKeybindsModalScreen extends MenuKitScreen {

    /** Outer content extent of the modal (before MenuKitScreen padding). */
    private static final int CONTENT_W = 340;
    private static final int CONTENT_H = 220;
    private static final int TITLE_H = 14;
    private static final int VIEWPORT_W = CONTENT_W;
    private static final int VIEWPORT_H = CONTENT_H - TITLE_H;
    private static final int ROW_WIDTH = ScrollContainer.viewportWidthFor(VIEWPORT_W);

    private final String modId;
    private final Screen background;
    private final State state;

    /** Mutable capture + scroll state. Held on the screen, shared with row
     *  callbacks via lambdas captured at construction. */
    static final class State {
        @Nullable KeyMapping captureMapping;
        @Nullable ChordCapture capture;
        double scrollOffset;
    }

    private ModKeybindsModalScreen(String modId, Screen background, State state, List<Panel> panels) {
        super(Component.literal("Keybinds — " + modId), panels);
        this.modId = modId;
        this.background = background;
        this.state = state;
    }

    public static ModKeybindsModalScreen open(String modId, Screen background) {
        State state = new State();
        List<Panel> panels = buildPanels(modId, state);
        return new ModKeybindsModalScreen(modId, background, state, panels);
    }

    private static List<Panel> buildPanels(String modId, State state) {
        List<KeyMapping> mappings = ModConfigKeybindsRegistry.keybindsFor(modId);
        List<PanelElement> rows = new ArrayList<>();
        for (int i = 0; i < mappings.size(); i++) {
            KeyMapping km = mappings.get(i);
            rows.add(new KeybindRow(
                    0, i * KeybindRow.HEIGHT, ROW_WIDTH, km,
                    () -> state.captureMapping,
                    () -> state.capture != null ? state.capture.getPreviewText() : Component.empty(),
                    mapping -> startCapture(state, mapping),
                    () -> resetBinding(km)));
        }
        ScrollContainer scroll = ScrollContainer.builder()
                .at(0, TITLE_H)
                .size(VIEWPORT_W, VIEWPORT_H)
                .content(rows)
                .scrollOffset(() -> state.scrollOffset, v -> state.scrollOffset = v)
                .build();
        // opaque + dimsBehind for the modal visual, but NOT tracksAsModal —
        // tracksAsModal triggers MenuKitModalKeyboardHandlerMixin's global
        // keyboard suppression which would break chord capture.
        Panel modal = new Panel(
                "keybindery-mod-keybinds-modal",
                List.<PanelElement>of(scroll),
                /*visible=*/ true,
                PanelStyle.RAISED,
                PanelPosition.BODY,
                /*toggleKey=*/ -1)
                .opaque(true)
                .dimsBehind(true);
        return List.of(modal);
    }

    private static void startCapture(State state, KeyMapping mapping) {
        state.captureMapping = mapping;
        ChordCapture.activeMapping = mapping;
        state.capture = new ChordCapture(
                chord -> {
                    IChordKeyMapping.updateFromChord(mapping, chord);
                    state.captureMapping = null;
                    state.capture = null;
                },
                () -> {
                    state.captureMapping = null;
                    state.capture = null;
                },
                () -> {},
                () -> {
                    IChordKeyMapping.updateFromChord(mapping, Chord.UNBOUND);
                    state.captureMapping = null;
                    state.capture = null;
                });
        ChordCapture.activeCapture = state.capture;
        state.capture.start();
    }

    private static void resetBinding(KeyMapping km) {
        IChordKeyMapping.updateFromChord(km, Chord.UNBOUND);
        km.setKey(km.getDefaultKey());
        KeyMapping.resetMapping();
    }

    // ── Input — chord-capture takes priority when active ───────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        ChordCapture active = state.capture;
        if (active != null && active.isCapturing()) {
            active.onKeyPressed(InputConstants.getKey(event));
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        ChordCapture active = state.capture;
        if (active != null && active.isCapturing()) {
            active.onKeyReleased(InputConstants.getKey(event));
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ChordCapture active = state.capture;
        if (active != null && active.isCapturing()) {
            active.onMousePressed(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        ChordCapture active = state.capture;
        if (active != null && active.isCapturing()) {
            active.onMouseReleased(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        return super.mouseReleased(event);
    }

    // ── Rendering — paint the previous screen as a static backdrop ─────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (background != null) {
            background.render(graphics, -1, -1, partialTick);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        ChordCapture active = state.capture;
        if (active != null && active.isCapturing()) {
            active.pollReleases(Minecraft.getInstance().getWindow().handle());
        }
    }

    @Override
    public void onClose() {
        if (state.capture != null && state.capture.isCapturing()) {
            ChordCapture.activeCapture = null;
            ChordCapture.activeMapping = null;
            state.capture = null;
            state.captureMapping = null;
        }
        ModConfigKeybindsRegistry.close();
        Minecraft.getInstance().setScreen(background);
    }
}
