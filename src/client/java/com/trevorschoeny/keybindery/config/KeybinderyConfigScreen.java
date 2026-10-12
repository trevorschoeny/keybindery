package com.trevorschoeny.keybindery.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * YACL config screen for Keybindery's settings. The chord settings:
 *
 * <ul>
 *   <li><b>Simultaneous Mode</b> (boolean, default off). When off, a chord
 *       fires only while all its keys are held at once (the default). When
 *       on, all keys' first presses must land within the configured time
 *       window.</li>
 *   <li><b>Simultaneous Window (ms)</b> (int 50 to 500, default 100). Shown
 *       only when Simultaneous Mode is on. Larger is more forgiving about the
 *       timing between keys; smaller is stricter.</li>
 * </ul>
 *
 * <p>Settings are saved to {@code config/keybindery.json}. Simultaneous Mode
 * is off by default; players turn it on if they prefer that way of detecting
 * chords.
 */
public final class KeybinderyConfigScreen {

    private KeybinderyConfigScreen() {}

    public static Screen build(Screen parent) {
        KeybinderyConfig cfg = KeybinderyConfig.get();

        Option<Boolean> simultaneousOpt = Option.<Boolean>createBuilder()
                .name(Component.literal("Simultaneous Mode"))
                .description(OptionDescription.of(
                        Component.literal(
                                "When OFF (default): chord keys must be held " +
                                "concurrently for the chord to fire. " +
                                "Like Ctrl+Shift+P in a text editor.\n\n" +
                                "When ON: chord keys just need their initial " +
                                "presses to fall within the time window below. " +
                                "Holds and releases don't matter after the press.\n\n" +
                                "Tip: bind 'Simultaneous Mode' in the Controls " +
                                "screen to toggle this at runtime.")))
                .binding(false, () -> cfg.simultaneousMode, v -> cfg.simultaneousMode = v)
                .controller(BooleanControllerBuilder::create)
                .build();

        Option<Integer> windowOpt = Option.<Integer>createBuilder()
                .name(Component.literal("Simultaneous Window (ms)"))
                .description(OptionDescription.of(
                        Component.literal(
                                "Maximum time between the first and last chord-key " +
                                "press for the chord to count as 'simultaneous.' " +
                                "Only applies when Simultaneous Mode is ON.\n\n" +
                                "Smaller values are stricter; larger values are " +
                                "more forgiving. 100ms is a balanced default.")))
                .binding(100, () -> cfg.simultaneousWindowMs, v -> cfg.simultaneousWindowMs = v)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                        .range(50, 500)
                        .step(10))
                .available(cfg.simultaneousMode)
                .build();

        // Grey out the window slider while Simultaneous Mode is off: when the
        // mode flips, a YACL listener updates the window option's availability.
        simultaneousOpt.addListener((opt, newValue) -> windowOpt.setAvailable(newValue));

        Option<Boolean> disableControlsReplacement = Option.<Boolean>createBuilder()
                .name(Component.literal("Disable controls-screen replacement"))
                .description(OptionDescription.of(Component.literal(
                        "When OFF (default): Keybindery replaces vanilla's controls " +
                        "screen with its search/sort/filter version.\n\n" +
                        "When ON: vanilla's plain controls screen is used. Useful if " +
                        "you have Controlling installed and prefer its UX, or just " +
                        "want vanilla back. Requires re-opening the screen to take " +
                        "effect.")))
                .binding(false,
                        () -> cfg.disableControlsScreenReplacement,
                        v -> cfg.disableControlsScreenReplacement = v)
                .controller(BooleanControllerBuilder::create)
                .build();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Keybindery"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Chord Detection"))
                        .option(simultaneousOpt)
                        .option(windowOpt)
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Controls Screen"))
                        .option(disableControlsReplacement)
                        .build())
                .save(KeybinderyConfig::save)
                .build()
                .generateScreen(parent);
    }
}
