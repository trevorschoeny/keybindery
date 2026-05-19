package com.trevorschoeny.keybindery.config;

import com.trevorschoeny.keybindery.KeybinderyClient;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * YACL-backed config screen for Keybindery's two Section 1 settings:
 *
 * <ul>
 *   <li><b>Simultaneous Mode</b> (boolean, default OFF). When OFF, chord
 *       detection requires all keys held concurrently (default mode). When ON,
 *       chord detection requires all keys' initial presses within the
 *       configured time window.</li>
 *   <li><b>Simultaneous Window (ms)</b> (int 50–500, default 100). Visible
 *       only when Simultaneous Mode is ON. Larger = more forgiving on the
 *       inter-key timing; smaller = stricter.</li>
 * </ul>
 *
 * <p>Settings persist to {@code config/keybindery.json} on save.
 * Per Trev's spec (2026-05-18), default mode = OFF — players opt INTO
 * simultaneous mode if they prefer that detection model.
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

        // Hide the window slider when simultaneous mode is OFF. YACL's listener
        // pattern: when Simultaneous Mode flips, refresh the window option's
        // availability so the slider greys out / lights up accordingly.
        simultaneousOpt.addListener((opt, newValue) -> windowOpt.setAvailable(newValue));

        // F1 demo entry — uses the F1 API to render a chord-bindable row.
        // Lives in its own category so it's clearly a developer-facing demo,
        // separated from end-user Chord Detection settings.
        Option<?> f1DemoChord = KeybinderyAPI.getInstance().createYACLChordOption(
                KeybinderyClient.F1_DEMO_CHORD,
                Component.literal("F1 Demo Chord"),
                OptionDescription.of(Component.literal(
                        "Click to bind a multi-key chord using Keybindery's F1 " +
                        "API. The same widget consumer mods get by calling " +
                        "KeybinderyAPI.getInstance().createYACLChordOption(...).\n\n" +
                        "When the chord fires in-game, Keybindery logs " +
                        "\"F1 demo chord fired!\" — watch the launcher log " +
                        "to see it in action.")));

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Keybindery"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Chord Detection"))
                        .option(simultaneousOpt)
                        .option(windowOpt)
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("F1 Demo"))
                        .option(f1DemoChord)
                        .build())
                .save(KeybinderyConfig::save)
                .build()
                .generateScreen(parent);
    }
}
