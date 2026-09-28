package com.trevorschoeny.keybindery;

import net.fabricmc.api.ModInitializer;

/**
 * Keybindery's common entrypoint. Keybindery is universal ("environment": "*")
 * only so that universal mods depending on it (Inventory Plus, Inventory Max)
 * can load on a dedicated server; everything Keybindery does is client input
 * and screens, all of it in src/client. A server loads this class and nothing
 * else, so it does nothing.
 */
public final class Keybindery implements ModInitializer {
    @Override
    public void onInitialize() {
        // ponytail: deliberately empty; the server half of Keybindery is its absence.
    }
}
