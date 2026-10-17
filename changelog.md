Nothing changes in the Key Binds menu in this release. The additions are for other mods, and players see them through those mods, like the key on each sandbox in Sandboxes 2.1.0.

### Added

- Keybindery has its own icon in Mod Menu.
- For mod developers, runtime key bindings. `KeybinderyAPI.getInstance().bindings()` registers key bindings while the game runs, one for each thing the player makes, like a sandbox. `register(id, label, chord, onPress)` adds or replaces one, and `unregister(id)` removes it. A binding fires on the same presses as vanilla's key mappings, follows the player's chord mode, and runs on the client thread. Runtime bindings don't show in the Key Binds menu and aren't saved, so the mod stores the chord with `Chord.serialize()` and registers it again at startup. `conflicts(chord, id)` lists every key mapping and other runtime binding that shares a key with it.
- For mod developers, a chord button for any screen. `captureWidget(x, y, w, h, chord, onChange)` returns a vanilla button that captures a chord and passes it to `onChange`. Escape, Delete, or a right-click clears it.
- Without Keybindery installed, runtime bindings do nothing, `conflicts` returns an empty list, and `captureWidget` returns `null`.

### Changed

- Requires MenuKit 7.2.0 or newer, below 8.0.0.
