package com.trevorschoeny.keybindery.screen;

import com.trevorschoeny.menukit.core.Button;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelPosition;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.VanillaScreenRegion;
import com.trevorschoeny.menukit.inject.VanillaScreenPanelAdapter;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Top-right MenuKit-styled "Keybinds" button injected onto every
 * ModMenu-launched config screen that ISN'T a {@link YACLScreen}. For
 * YACL screens, {@link com.trevorschoeny.keybindery.mixin.YACLBuilderInjectMixin}
 * adds a native Keybinds tab instead — these two paths are mutually
 * exclusive at the visibility level.
 *
 * <p>Single adapter registered at client init with {@code .onAny()}; the
 * panel's {@code showWhen} supplier gates per-screen visibility based on
 * the current active screen (read from {@code Minecraft.getInstance().screen}).
 * Hides when:
 * <ul>
 *   <li>No active screen.</li>
 *   <li>Active screen is a {@code YACLScreen} (YACL tab covers it).</li>
 *   <li>Active screen has no recorded mod-ID (not opened via ModMenu).</li>
 *   <li>The mod has no registered key bindings.</li>
 * </ul>
 */
public final class ModKeybindsOverlayPanel {

    private ModKeybindsOverlayPanel() {}

    /** Constructs the panel + adapter and registers with MK. Call once
     *  at client init. */
    public static void install() {
        Button keybindsBtn = new Button(
                /*childX=*/ 0, /*childY=*/ 0,
                /*width=*/ 80, /*height=*/ 20,
                Component.literal("Keybinds"),
                btn -> {
                    Screen current = Minecraft.getInstance().screen;
                    if (current == null) return;
                    String modId = ModConfigKeybindsRegistry.modIdFor(current);
                    if (modId == null) return;
                    KeybinderyKeyBindsScreen.openWithModFilterFor(modId, current);
                });

        Panel panel = new Panel(
                "keybindery-mod-config-overlay",
                List.<PanelElement>of(keybindsBtn),
                /*visible=*/ true,
                PanelStyle.NONE,
                PanelPosition.BODY,
                /*toggleKey=*/ -1);
        panel.showWhen(ModKeybindsOverlayPanel::shouldShow);

        new VanillaScreenPanelAdapter(panel, VanillaScreenRegion.TOP_RIGHT, /*padding=*/ 0)
                .onAny();
    }

    /** Supplier read every frame by the panel to decide visibility. */
    private static boolean shouldShow() {
        Screen current = Minecraft.getInstance().screen;
        if (current == null) return false;
        if (current instanceof YACLScreen) return false;
        String modId = ModConfigKeybindsRegistry.modIdFor(current);
        if (modId == null) return false;
        return !ModConfigKeybindsRegistry.keybindsFor(modId).isEmpty();
    }
}
