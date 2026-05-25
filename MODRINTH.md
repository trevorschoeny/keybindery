# Keybindery — Modrinth project page

This file holds the content for the Modrinth project page. Copy-paste sections into the corresponding fields when uploading.

---

## Project metadata

- **Project name:** Keybindery
- **Project type:** Mod
- **Project category:** Utility (primary), Library (secondary)
- **Client/server side:** Client required, Server unsupported
- **License:** MIT
- **Source:** https://github.com/trevorschoeny/keybindery *(update once repo is published)*
- **Issue tracker:** Same as source repo
- **Discord/community:** *(leave empty unless you set one up)*

## Summary (one-liner shown in search results)

> Bind multi-key chords to any keybind, find a mod's bindings inside its own config, search/sort/filter the controls screen.

(Modrinth limits summaries to ~150 chars — this is 137.)

## Featured tags

`utility`, `library`, `gui`, `equipment`

---

## Long description (paste into Modrinth's Description field, markdown)

# Keybindery

Bind **multi-key chord shortcuts** to any keybind in the game, jump straight to a mod's keybinds **from inside that mod's config screen**, and **search/sort/filter** the controls screen.

Three features in one client-side mod.

## Multi-key chord keybinds

Hold any combination of keys — `Ctrl + Shift + K`, `X + Z`, even mouse buttons — and release to bind. Works on every keybind in the game: vanilla, modded, anything.

Single-key bindings still work exactly as before. Chords are opt-in per binding.

## Auto-listed mod keybinds

No more hunting through the Key Binds menu to find a specific mod's keybindings.

- **Mods using YACL** get a native "Keybinds" tab added to their config screen automatically.
- **Other mods' configs** get a small "Keybinds" button in the top-right corner — one click opens the controls screen pre-filtered to that mod's bindings.

Mod Menu is recommended (it's how Keybindery discovers which keybinds belong to which mod), but the YACL tab works without it.

## Controls screen overhaul

The Key Binds menu gets a toolbar:

- **Search keybinds** — type a name to narrow the list
- **Search Keybind** — click and press any chord; the list shows everything that shares a key with that chord (instant conflict-finder)
- **Sort** — by category (default), alphabetical, or by bound key
- **Filter** — only conflicting binds, only unbound binds, or no filter

Every row also gets two icons:

- **⚠ Conflicts** — opens the controls screen pre-filtered to every keybind this one conflicts with
- **↻ Reset** — restores the keybind's default

Long keybind names auto-scroll instead of getting cut off.

The "MISC" category that vanilla dumps every uncategorized mod keybind into is automatically broken up by mod, so you see "Inventory Plus / Shulker Palette / [other mod]" instead of one giant unsorted MISC.

## Requirements

- **Fabric Loader** 0.15 or newer
- **Fabric API**
- **YACL** (Yet Another Config Lib) — bundled in many mods, install standalone if needed
- **Mod Menu** — strongly recommended for auto-listed mod keybinds

## Installing

Drop the jar in your `mods/` folder along with the requirements above. Keybindery is client-side only — installing it on a server has no effect.

## Compatibility

- Vanilla single-key bindings are fully respected. Mods that don't opt into chords behave unchanged.
- Compatible with the popular Fabric input mods (Controlify, MidnightControls, etc.) — Keybindery uses precise mixin injection at narrow hook points, no `@Overwrite`.
- Chord bindings serialize alongside vanilla's `options.txt`. Uninstall later and everything reverts to single-key behavior cleanly.

## For mod developers

Keybindery ships a small API jar you can depend on at compile time. Your mod's UI degrades gracefully (no-op stubs) if a player runs without Keybindery installed.

See the [README on GitHub](https://github.com/trevorschoeny/keybindery#using-it-as-a-mod-developer) for the API surface.

## Credits

Inspired by the design choices in [Amecs](https://modrinth.com/mod/amecs), [Controlling](https://modrinth.com/mod/controlling), and the broader Fabric input-mod ecosystem.

---

## Gallery (screenshots to upload)

Screenshots to take + upload (in order — first one is the social-card preview):

1. **F4 toolbar in action** — the Key Binds menu with the toolbar visible, search box populated, a conflicting row visible (yellow brackets + ⚠ icon active)
2. **YACL config tab** — an Inventory Plus or Shulker Palette YACL screen showing the auto-injected "Keybinds" tab
3. **Cross-mod overlay button** — a non-YACL config screen with the small "Keybinds" overlay button visible in the top-right
4. **Chord capture in progress** — a row with `> Ctrl + Shift + K <` showing live capture preview
5. *(optional)* **Conflicts filter** — the Key Binds menu pre-filtered to show only the conflict set of a specific binding

Modrinth supports up to 16 images. 5 is enough for v1.0.0; add more later if useful.

---

## Version-tag conventions

For each release, use Modrinth's release-channel tags:

- **Release** — stable, recommended for normal users
- **Beta** — testing builds, breaking changes possible
- **Alpha** — early/experimental

v1.0.0 is the first **Release** tag. Subsequent feature work that breaks API compatibility goes out as Beta first.

---

## Pre-submission checklist

Before clicking "Submit for review" on Modrinth:

- [ ] README.md reads well on GitHub
- [ ] LICENSE file present
- [ ] `fabric.mod.json` version/name/description match the Modrinth page
- [ ] At least one screenshot uploaded (the first one becomes the social-card preview)
- [ ] All five required Modrinth fields filled (Title, Summary, Description, License, Project type)
- [ ] Source-code URL set (GitHub repo public + URL pasted into Modrinth's "Links" section)
- [ ] Issue-tracker URL set (same GitHub repo's `/issues`)
- [ ] Compatibility versions checked off — Minecraft 1.21.11, Fabric loader
- [ ] Tested in a clean Minecraft + Fabric + Fabric API + YACL install (no other mods loaded) to confirm no missing-dependency crashes

Once submitted, Modrinth staff review takes a few days. The mod stays in "Under review" status until approved. Approval criteria are listed at https://docs.modrinth.com/rules.
