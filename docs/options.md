# Old Faithful's options mod (oldfaithful-options)

Claude Opus 5.5, 2026-11-02. Things the user asked for in Old Faithful that aren't fixes of beta's mistakes
(`docs/fixes.md` has those): options and comforts. Pure Faithful has none of them. Client only. Public id
`oldfaithful-options` (`oldfaithful_edition=fixed`, `docs/release.md` > Names); in Old Faithful's release only. Switches in `config/oldfaithful-options.properties`, one per package under `mixin/`, as
the fix mod has them.

| Switch | What |
|---|---|
| `options.fov` | A FOV slider, 30 to 110 degrees, 70 ("Normal", beta's) by default |
| `gui.normalScale` | A first start (no options.txt) has GUI Scale at Normal instead of Auto; an existing file keeps its choice |
| `options.screen` | Old Faithful... in Options: the settings screen (below) |
| `title.quitGame` | Quit Game on the title screen (user, 2026-11-02: Old Faithful only, not Pure Faithful) |
| (display mod) | The window opens maximised the first time, then where the player left it, fullscreen included |

## The settings screen

The screen and config editing live in `oldfaithful-settings` since 2026-11-04 (same package, so the mods using them didn't change): the library's mods need them on any edition.
Options > "Old Faithful..." with this mod installed, "Mods..." without it; this mod's own switches are a provider like any other's, listed first; `options.screen` is still
read from this mod's file.

Claude Opus 5.5, 2026-11-02. So players needn't edit config files: **Options > Old Faithful...**, a 200-wide button
above Video Settings and Controls on their 24-pixel pitch, in the gap beta leaves under its options (left out if
another mod has put something there). Beta's own look throughout: its buttons, font, background and layout.

- **Shape** (user, 2026-11-05): wide buttons lead somewhere and switches are short, on beta's two widths. The top page
  has a wide (200) button per menu, from a sixth of the way down, Done at `height/6 + 168`: **Fixes...**, **Changes...**
  (the word for what isn't a fix; ours) and **Mods...**, the hint naming a menu's sections. A menu with nothing in it isn't
  there, and with only one the top page is skipped: Options goes straight to it, which is what "Mods..." does on an
  edition without this mod. Done goes back one level.
- **Fixes and Changes**: pages of "Name: Value" switches, two columns of 150-wide buttons as beta's Video Settings, stepping on
  a click (fixes read **Fixed / Beta**, the rest ON / OFF or their choices). A page is a section, titled so
  ("Fixes: Mobs (2/6)"), turned with arrows beside Done; a section past ten switches shares itself evenly over pages.
  Fixes: Blocks and Items, Mobs, Players and Vehicles, Redstone and Lighting (with Lava Light), Looks and Sounds,
  Screens, Multiplayer. Changes: Polish (Still Faithful's), then Window and Mouse (the display settings and Quit Game; "Game" with
  Quit Game alone when the display mod isn't working). Under the buttons, two lines at most: the hint for the button
  pointed at (what beta did, in a sentence), or the section's note.
- **Mods**: a wide button per mod with a page, by name, five to a page with the same arrows, whether the mod is on said
  beside it ("OFF*" when a change on its page waits for a restart). Its page has **Enabled: ON** alone in the top row where
  the mod sits in `mods-library/` (Faithful Library's switch: its README), then the mod's own
  settings and world rules below, eight to a page. With no line of its own the page shows the mod's description. A mod
  that's off isn't running, so its page has only Enabled until it is; a mod in `mods/` with settings has its page without Enabled.
  A page can have one button of its own beside Enabled (`PageButton`): the core's **Lock Rules**, on every page with world rules while
  a world is open, pairs with it in the top row on beta's two columns; alone, either sits in the middle. Whoever knows a mod must stay as it is says so with
  `SettingsRegistry.holdMod`, which greys Enabled on its page and on any page whose mod's going off would take it (the core, inside a world's locked rules).
- **What's there**: only what's installed. Each mod lists its switches through a small API
  (`oldfaithful-settings`: a `SettingsProvider` registered as the `oldfaithful-options:settings` entrypoint in its
  fabric.mod.json, loaded when a screen first asks and never on a server). A switch goes on its mod's own page unless the
  provider names a section it declared with `fixes(...)` or `changes(...)`, as the editions' do: `FixesSettings` (each
  fix's name, section, hint and side), `LightingSettings` (lava), `PolishSettings`, and this mod's own Quit Game and Pure
  Faithful Display's settings when that mod is installed with our LWJGL component (`DisplaySettings`, here, so the display
  mod and Pure Faithful are unchanged). Not shown: the FOV switch (its slider is in Options) and GUI Normal on a first start
  (no first start is left once the screen can be seen); they stay in the file. A fix without a row in `FixesSettings`
  shows in a section "More" by its key, and `FixesSettingsTest` fails until it has one.
- **Saving**: a click writes the file at once, changing that one value in place and keeping every other byte (the
  explanations, other keys, order, line ends: `ConfigFile`), whole or not at all (a temporary file moved over it).
  Values are read as the owning mod reads them (`Boolean.parseBoolean`, "raw" or not), and the files are read again
  each time a screen opens, so hand edits show.
- **When it takes effect**: a fix is in the game only if its switch was on at start (its mixins are skipped
  otherwise), so every fix waits for a restart: its button gets an asterisk ("Repeaters: Beta*") while the file
  differs from what's running, with "* Takes effect after a restart" under the buttons (the top page says "Some
  changes take effect after a restart"). A setting can be both (`applyNow` and `running`: Still Faithful's Dusk, live
  between Stepped and Smooth, after a restart to or from Beta): it's marked while what's running differs from the
  file. At once: VSync, Even Frames, Smooth Look, Mouse and Fullscreen (set where
  the display mod and our LWJGL read them; `Window.refreshSettings` for the mouse and fullscreen), and Lava Light
  (the lighting check reads it per chunk: it changes land as it loads, and light already in the world stays). After
  a restart: Remember Window, Start Maximized, Scale to Display, Quit Game Button.
- **Multiplayer**: a switch decided where the world runs (`serverDecides`: drops, mobs, redstone, saved ticks, lava
  light...) reads "Name: Server" and can't be pressed while playing on a server, with the hint "The server you're
  playing on decides this. Yours is for single player." This game's own switches stay changeable there (what's
  drawn, heard and predicted, and the client-side multiplayer fixes); wooden slabs and tool speeds are this game's
  switch but apply on a server only where it has the fix too (`ServerMarker`), as their hints say. Fixes that run
  only on a server (#5, #16a, #16b, #48) aren't on the screen; the Multiplayer page's note points to the server's
  own file. A mod's world rules show the server's value and can't be pressed.

## FOV

- **Where**: the Options screen's empty place, right of Difficulty (beta lays its five options two to a row); left
  out if another mod has put something there. Drawn as beta's sliders are, on a widget of our own (`FovSlider`):
  beta's `SliderWidget` needs one of beta's `Option`s, an enum. It reads the mouse to the pixel like the fixed sliders
  (#40), so all 81 degrees are reachable at any GUI scale.
- **Stored** in options.txt as `fov:<degrees>` (later versions' key and unit). b1.7.3 ignores lines it doesn't know,
  so the file still loads without the mod, which drops the line at its next save.
- **Applied** in `GameRenderer.getFov`: beta's view (70; 60 under water; narrowing in death) times setting / 70, as
  later versions scale it. Frustum culling reads the projection back from GL, so it follows.
- **The hand and held item** stay at beta's view whatever the setting (as in later versions): beta draws them through
  the world's projection, so before they're drawn the projection is set again at beta's own value (with the anaglyph
  offset, as renderWorld does). Screen overlays (water, fire, the pumpkin) are drawn there too and stay as beta's.

## Quit Game

Beta has the button: beside a half-width Options, shown when the game runs on its own (`Minecraft.isApplet` false).
In a launcher it ran as an applet (Mojang's launcher, and Fabric's start, which sets `isApplet` in
`MinecraftApplet.init`), so players never saw it. The mod makes only the title screen's reading of the flag false:
beta's own layout, label ("Quit Game") and action (`scheduleStop`, what the window's close button does).

## The window

In the display mod (Pure's, `docs/lwjgl.md` > The display mod): `window.startMaximized` (off by default) opens the
window maximised when neither the launcher nor a remembered placement says otherwise. This mod's fabric.mod.json asks
the display mod for `window.remember=true` and `window.startMaximized=true` as defaults (`purefaithful-display:defaults`),
so standalone installs get Old Faithful's window as the pack does; a value already in the player's
`purefaithful-display.properties` wins. A launcher's own setting (Prism's window size or maximised, or `--width`) still
decides when it's given.

## Not added

- Shift (sneak) to leave a vehicle, a later version's addition: beta's right-click on the vehicle stays the only way
  (user, 2026-11-02).
- Antialiasing (multisampling the game's window, 2026-11-03): built and checked, then removed after the user's
  play-test showed light blue (the sky) through the seams between blocks at 4x/8x. Not important to the user, so
  dropped rather than chased (the commit and its revert are in git if it's ever wanted). Since measured
  (2026-11-03, `docs/bugs.md` #70): single-sample rendering shows the same sky through the seams between render
  chunks, only more rarely; multisampling tests several points per pixel, so it finds the cracks more often, and it
  takes a pixel's texture at the pixel's centre even when only an edge of the face covers it, so a face's edge can read
  the transparent texels beside its tile in the atlas and drop out (#72 is the single-sample form of that; reasoned,
  not measured with multisampling). #70's fix closes the first; the second reaches up to half a pixel outside the
  face, beyond what #72's fix would cover, so multisampling would need its texture sampled inside the covered part
  (centroid sampling, a shader) to come back.

## Tests

- `./gw :oldfaithful-options:test`: `Fov` (every degree round-trips the slider and is reachable at Small; labels,
  parsing, proportional scaling).
- `./gw :harness:clientStartupTestFixed` with this mod (`OptionsCheck`): every launch's title screen has Quit Game
  beside Options; in the world, FOV 100 draws the world at 100/70 of the hand's view and the hand at beta's (read
  back from GL's projection), `fov:100` saved and loaded back, the slider in its place in Options. Then a first start
  (`first-start/`: no options.txt, no config, no launcher size) must open maximised with the GUI at Normal, write
  both window defaults into the display config, keep a maximised placement, and stop through Quit Game cleanly.
- `./gw :oldfaithful-options:test` also: `ConfigFileTest` (a value changed in place with every other byte kept: line
  ends, separators, indents, continuations, escaped keys, comments that mention the key, duplicates, appending; the
  file written whole) and `SettingsRegistryTest` (sections and their order, what goes on a mod's page, values read as the
  mods read them, the restart mark, hand edits, settings applied at once).
- `clientStartupTestFixed` with this mod (`SettingsCheck`, after the FOV): Old Faithful... in place with beta's
  buttons where they were; a wide button for each menu; each menu's pages turned by real clicks to the end and back, every fix
  on one; every label fitting its button with any value and the
  restart mark (in the game's own font), every hint in two lines; Repeaters pressed (the file changed in that line
  only, "Beta*") and back (the file byte for byte as before); Lava Light, VSync (the swap interval), Smooth Look and
  Mouse (SDL's hint) taking effect at once; Remember Window marked for a restart; with the world marked remote, the
  world's switches reading "Server" and refusing a press while the game's own stay live. Then Options, the top
  page and two others at GUI scales Small, Normal and Large in 640x480, 854x480 and 1920x1080 windows (resized
  unseen), checked for buttons off the screen, over each other or overflowing, and saved as pictures in
  `local/run/client-startup-fixed/settings-shots/` (drawn with the mouse placed, so a hint shows), with every level and page
  in `levels/` there. Not run on a real server: multiplayer there is the world's `isRemote` flag, set while the screen is
  built.
- `./gw :harness:clientStartupTestLibrary`: the same `SettingsCheck` alone, on Still Faithful with World Rules, Plain F3 and
  No Toasts in `mods-library/`, in about half a minute: also the Mods menu, Enabled against the list, a rule on the mod's
  page, Done back to the menu, a mod with only Enabled, and a hundred made-up mods for the arrows. Its pictures, in
  `local/run/client-startup-library/settings-shots/`, are the ones with every level. What's on a server and Options going
  straight to Mods on an edition without this mod: `libraryRulesTest`.
- The user's to look at: the slider's feel, the hand at wide settings, Quit Game in fullscreen; the settings screen
  (the wording, the sections, the asterisk).
