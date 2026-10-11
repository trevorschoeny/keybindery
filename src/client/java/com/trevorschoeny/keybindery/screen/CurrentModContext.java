package com.trevorschoeny.keybindery.screen;

import org.jetbrains.annotations.Nullable;

/**
 * Thread-local holding the mod ID of whichever mod is building a YACL screen
 * right now. Set by
 * {@link com.trevorschoeny.keybindery.mixin.ModMenuConfigScreenMixin} at
 * the HEAD of ModMenu's {@code getConfigScreen(modId, parent)} and cleared
 * at RETURN. Read by
 * {@link com.trevorschoeny.keybindery.mixin.YACLBuilderInjectMixin} at
 * the HEAD of YACL Builder's {@code build()} to add the automatic Keybinds
 * category.
 *
 * <p>A thread-local rather than a static field because the YACL build could
 * in theory happen on different threads, and the ModMenu to factory to
 * YACL.build chain is synchronous on whichever thread started it.
 *
 * <p>When it is unset (no ModMenu, or a YACL screen opened some other way,
 * such as a keybind, button or command), the YACL Builder mixin works out the
 * mod ID by walking the stack and matching class paths instead. See that
 * mixin for details.
 */
public final class CurrentModContext {

    private static final ThreadLocal<String> CURRENT_MOD_ID = new ThreadLocal<>();

    private CurrentModContext() {}

    public static void set(String modId) { CURRENT_MOD_ID.set(modId); }
    public static void clear() { CURRENT_MOD_ID.remove(); }
    public static @Nullable String get() { return CURRENT_MOD_ID.get(); }
}
