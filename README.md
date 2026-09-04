# Keybindery

A chord-keybind library + controls-screen overhaul for Fabric. Bind multi-key combinations to any keymapping (vanilla or modded), discover and rebind mod keybinds from inside the mod's own config screen, and search/sort/filter the controls screen.

## What it is

Three features in one client-side mod:

1. **Multi-key chord keybinds.** Bind sequences like `Ctrl + Shift + K` or `X + Z` to any keybind in the game — vanilla *and* modded. Existing single-key bindings keep working unchanged.

2. **Auto-listed mod keybinds.** Every mod's keybinds show up on a dedicated "Keybinds" tab in that mod's config screen (for YACL configs), or behind a small "Keybinds" overlay button (for other config screens). No more hunting through the Key Binds menu to find a specific mod's bindings.

3. **Controls screen overhaul.** A search box, search-by-chord button, sort dropdown, and filter dropdown sit at the top of the vanilla Key Binds menu. Each row has a Conflicts icon (jumps to the conflict set) and a Reset icon. Long keybind names auto-scroll.

Keybindery is client-side only — installing it adds zero weight on servers.

## Install

Drop the jar in your `mods/` folder along with Fabric API. YACL is bundled inside the jar.

**Requires:**
- Fabric Loader ≥ 0.15
- Fabric API
- Minecraft 26.2 (this build); [YACL](https://modrinth.com/mod/yacl) 3.9.6 comes bundled, no separate install needed

**Recommended (not required):**
- [Mod Menu](https://modrinth.com/mod/modmenu) — needed for the auto-listed-mod-keybinds feature to discover which keybinds belong to which mod

## Using it as a player

### Bind a chord to any keybind
Open the Key Binds menu (Options → Controls → Key Binds). Click any row's chord button and press the chord you want — hold all the keys together, then release. The chord saves.

To clear a binding, right-click the chord button (during normal browsing) or press Delete/Backspace mid-capture. Press Escape mid-capture to cancel without changing anything.

### Find a mod's keybinds quickly
Open any mod's config screen via Mod Menu. If the mod uses YACL, there's a "Keybinds" tab at the top. Otherwise, look for a small "Keybinds" button in the top-right corner of the config screen — clicking it opens the controls screen pre-filtered to that mod's keybinds.

### Search and filter the controls screen
At the top of the Key Binds menu:
- **Search keybinds** — type a name to narrow the list
- **Search Keybind** — click and press any chord; the list shows every keybind that shares a key with that chord (use this to find conflicts)
- **Sort** — by category (default), alphabetical, or by bound key
- **Filter** — show only conflicting binds, only unbound binds, or no filter

Press **Escape** while capturing in the Search Keybind field to clear the filter.

### Conflict + Reset icons (per row)
- **⚠** — opens the controls screen pre-filtered to every keybind this one conflicts with. Grayed out if the row has no conflicts.
- **↻** — reset this keybind to its default. Grayed out if it's already at default.

## Using it as a mod developer

Keybindery ships a small API jar (`keybindery-api`) you depend on at compile time. Players still install the regular Keybindery mod.

### Read or write a chord

```java
import com.trevorschoeny.keybindery.api.Chord;
import com.trevorschoeny.keybindery.api.KeybinderyAPI;

KeyMapping myKey = ...; // your existing KeyMapping
KeybinderyAPI api = KeybinderyAPI.getInstance();

Chord chord = api.getChord(myKey);     // current chord, or Chord.UNBOUND
api.setChord(myKey, newChord);          // overwrite
```

### Add chord-binding rows to your YACL config

```java
ConfigCategory keybindsCategory = ConfigCategory.createBuilder()
        .name(Component.literal("Controls"))
        .options(KeybinderyAPI.getInstance().createYACLChordOption(
                myKey, Component.literal("My action"), OptionDescription.EMPTY))
        .build();
```

That single call adds the label + chord button + Conflicts icon + Reset icon to the category. The mapping is automatically marked as "claimed" so Keybindery's auto-list doesn't duplicate it on a fallback Keybinds tab.

### Mark a keybind as surfaced in your own UI

If you render keybind rows yourself (not via `createYACLChordOption`), tell Keybindery to skip auto-listing them:

```java
KeybinderyAPI.getInstance().markClaimed(myKey);
```

Idempotent — safe to call repeatedly.

### Stub behavior

`keybindery-api` is a separate jar with no runtime dependency on Keybindery. If a player runs without Keybindery installed, every API call falls through to a safe no-op stub (chords read as `UNBOUND`, write is silent, `createYACLChordOption` returns a single disabled "Keybindery not installed" label). Your mod's UI degrades gracefully.

## Compatibility

- **Vanilla single-key bindings** — fully respected. Mods that don't opt into chords behave exactly as before.
- **Input mods (Controlify, MidnightControls, etc.)** — Keybindery uses precise mixin injection at narrow hook points (no `@Overwrite`), so it composes cleanly with other mods that touch `KeyMapping`. Compatibility-tested against the top Fabric input mods.
- **Cross-mod conflicts** — the conflict-detection rule is "any shared key counts." A mod that defaults a keybind to `S` is correctly flagged as conflicting with vanilla's walk-back `S` (vanilla-vs-vanilla default overlap is the only case suppressed, since Mojang explicitly designed those).
- **Persistence** — chords serialize alongside vanilla's `options.txt`. Removing Keybindery later restores single-key behavior for all binds.

## License

MIT — see [LICENSE](LICENSE).

## Credits

Inspired by the design choices in [Amecs](https://modrinth.com/mod/amecs) (multi-key bindings), [Controlling](https://modrinth.com/mod/controlling) (controls-screen search), and the broader Fabric input-mod ecosystem.
