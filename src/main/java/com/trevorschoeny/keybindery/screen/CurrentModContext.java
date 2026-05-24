package com.trevorschoeny.keybindery.screen;

import org.jetbrains.annotations.Nullable;

/**
 * Thread-local marker carrying the mod-ID of whichever mod is currently
 * constructing a YACL screen. Set by
 * {@link com.trevorschoeny.keybindery.mixin.ModMenuConfigScreenMixin} at
 * the HEAD of ModMenu's {@code getConfigScreen(modId, parent)} and cleared
 * at RETURN. Read by
 * {@link com.trevorschoeny.keybindery.mixin.YACLBuilderInjectMixin} at
 * YACL Builder {@code build()} HEAD to inject the auto-Keybinds category.
 *
 * <p>Thread-local rather than a static field because the YACL build might
 * (in theory) happen on different threads, and ModMenu→factory→YACL.build
 * is a synchronous call chain on whichever thread invoked it.
 *
 * <p>When the thread-local is unset (ModMenu absent, or a YACL screen
 * opened via a path that bypasses ModMenu — keybind, button, command),
 * the YACL Builder mixin falls back to a stack-walk-and-classpath-match
 * mod-ID inference. See that mixin for details.
 */
public final class CurrentModContext {

    private static final ThreadLocal<String> CURRENT_MOD_ID = new ThreadLocal<>();

    private CurrentModContext() {}

    public static void set(String modId) { CURRENT_MOD_ID.set(modId); }
    public static void clear() { CURRENT_MOD_ID.remove(); }
    public static @Nullable String get() { return CURRENT_MOD_ID.get(); }
}
