package com.trevorschoeny.keybindery.screen;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.InsideRegion;
import com.trevlar.menukit.api.panel.VanillaScreenPanelAdapter;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A top-right MenuKit "Keybinds" button added to every config screen opened
 * from ModMenu that is not a {@link YACLScreen}. YACL screens get a native
 * Keybinds tab from {@link com.trevorschoeny.keybindery.mixin.YACLBuilderInjectMixin}
 * instead, so a screen never shows both.
 *
 * <p>One adapter is registered at client init with {@code .onAny()}; the
 * panel's {@code visibleWhen} supplier decides per screen, based on the
 * current screen ({@code Minecraft.getInstance().gui.screen()}). Hidden when:
 * <ul>
 *   <li>No active screen.</li>
 *   <li>Active screen is a {@code YACLScreen} (YACL tab covers it).</li>
 *   <li>Active screen has no recorded mod-ID (not opened via ModMenu).</li>
 *   <li>The mod has no registered key bindings.</li>
 * </ul>
 */
public final class ModKeybindsOverlayPanel {

    private ModKeybindsOverlayPanel() {}

    /** Builds the panel and adapter and registers them with MenuKit. Call once at client init. */
    public static void install() {
        Button keybindsBtn = Button.builder()
                .size(80, 20)
                .label(Component.literal("Keybinds"))
                .onClick(() -> {
                    Screen current = Minecraft.getInstance().gui.screen();
                    if (current == null) return;
                    String modId = ModConfigKeybindsRegistry.modIdFor(current);
                    if (modId == null) return;
                    KeybinderyKeyBindsScreen.openWithModFilterFor(modId, current);
                })
                .build();

        // Since MenuKit 6.0.0, placement is set on the panel; the adapter only
        // says which screens and how much padding.
        Panel panel = Panel.builder("keybindery-mod-config-overlay")
                .add(keybindsBtn)
                .style(PanelStyle.NONE)
                .position(PanelPosition.screenAnchor(InsideRegion.TOP_RIGHT))
                .visibleWhen(ModKeybindsOverlayPanel::shouldShow)
                .build();

        new VanillaScreenPanelAdapter(panel, /*padding=*/ 0).onAny();
    }

    /** Read every frame by the panel to decide visibility. */
    private static boolean shouldShow() {
        Screen current = Minecraft.getInstance().gui.screen();
        if (current == null) return false;
        if (current instanceof YACLScreen) return false;
        String modId = ModConfigKeybindsRegistry.modIdFor(current);
        if (modId == null) return false;
        return !ModConfigKeybindsRegistry.keybindsFor(modId).isEmpty();
    }
}
