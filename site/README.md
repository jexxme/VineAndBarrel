# Vine & Barrel wiki

The player wiki, built with Astro + Starlight on the same frame as the Farmer's Delight Neoforged docs (mc.css slots and
tooltips, the FD Pixel font, the model renderer, the data pipeline) but with its own look: an evening in the village
tavern. A plank wall (whitewashed plaster by day), a dark oak beam with a brass rail as the header, page titles on gilt
signboards hanging from chains, tables and menus chalked on slate, asides as parchment notes, oak buttons. The textures
are drawn by `scripts/make-ui.mjs`, the logo comes from `branding/`; the palette and surfaces are in
`src/styles/theme.css`.

## Build and preview locally

```bash
cd site
npm ci
npm run build      # static site in site/dist
npm run check      # no unrendered values, no broken internal links
npm run preview    # http://localhost:4321/vine-and-barrel/
```

The base path defaults to `/vine-and-barrel`. Set `BASE_PATH` (and `SITE_URL`) to build for another one; `REPO_URL` adds
the GitHub button and "edit page" links. The Pages workflow (`.github/workflows/docs.yml`) sets all three from the
repository, so the site works whatever the repository ends up being called.

## The one rule: no number from memory

Every number on the wiki comes from the mod.

- **Runtime values** (brewing times, ferment and aging days, servings, buffs, Tipsy levels and config defaults, heat
  sources, tags, village farm crops) are written by the game itself: `./gradlew runDocsExport` runs the dev-only
  `VBDocsExport` GameTest headless and writes `site/src/data/runtime.json`. It runs on its own, so it never shows up in
  the normal GameTest results.
- **Data values** (recipes, loot, trades, biome modifiers, advancements, compost chances, names) are read from the mod's
  resources by `scripts/extract.mjs`.
- **Code facts** that neither covers (the kettle screen's slot positions) live in `src/data/code/*.ts` with a citation.
- Pages read everything through `src/lib/data.ts`. Don't type a value into a page; if something can't be confirmed in the
  code, leave it out.

## Regenerating the data

```bash
./gradlew runDocsExport   # 1. runtime.json from a headless game
cd site
npm run data              # 2. extract + icons + block renders + GUI textures + UI textures + font
```

`npm run data` needs the vanilla client jar from the Gradle cache (any Gradle task of the mod downloads it). The generated
files (`src/data`, `public/icons`, `public/renders`, `public/gui`, `src/assets/ui`) are committed, so CI only runs
`npm run build`.

## Screenshots

The in-game pictures (the home page panorama, the shots on the pages) come from a real game: a showcase world built
from code and photographed in a hidden window by the maintainer's dev tooling, which isn't part of this repository. It
leaves PNGs in `neoforge/run/screenshots/wiki`, and

```bash
cd site
npm run shots              # neoforge/run/screenshots/wiki/*.png → src/assets/shots/*.jpg
```

turns them into the JPGs the pages use with `<Shot src="name.jpg" caption="…" />`. The JPGs are committed.

Farmer's Delight's own textures are never copied here: FD items show as text and link to the Farmer's Delight Neoforged wiki.
The one exception is the "Also by Jexxme" panel on the home page: FDN's own logo and one of its screenshots
(`src/assets/fdn/`, copied from the FDN docs; FD's art is © vectorwing, MIT).

## Clips

Short muted loops of the mechanics, filmed in game with the maintainer's dev tooling, embedded with
`<Clip src="kettle-fill" caption="…" />`. Each clip is `public/clips/<name>.webm` + `.mp4` (960×540, no audio, at most
700 KB, 8 MB for all) and a poster `src/assets/clips/<name>.jpg`, listed in `src/data/clips.json`:

```json
"kettle-fill": { "width": 960, "height": 540, "scene": "wiki_kettle_fill", "commit": "<filmed at>", "watch": ["common/src/main/resources/assets/vineandbarrel/textures/block/brew_kettle*"] }
```

`npm run check` fails on a missing or oversized file and warns when something under `watch` changed after `commit`:
that clip shows an old model or texture, so film it again.

## Languages

The wiki is in English and the game's 12 other languages (`src/i18n/locales.mjs`): English at `/`, the others under
`/<code>/`. A language switches on once `src/content/docs/<code>/` exists.

- **Pages** are copies of the English ones in `src/content/docs/<code>/` (same file names, same headings in the same
  order). Write them with the English imports and links; `node scripts/i18n.mjs` (part of `npm run data` and
  `npm run build`) points the imports one folder deeper, swaps `lib/data` for `lib/l10n/<code>` (whose `days()`,
  `nameOf()`… print that language), quotes titles that need it and maps `#english-anchors` to the translated headings.
- **Component words** go through `t('English text', locale)` or `<Tr k="… {slot} …">` and live in
  `src/i18n/strings/<code>.json`, keyed by the English. `src/i18n/strings.en.json` lists every key (generated). Sidebar
  labels come from the same files.
- **Game text** (item, block, effect and advancement names, blackout lines, barrel status) comes from the game's lang
  files: `lang.<code>.json` in `src/data/gen`, built from vanilla's (Gradle asset cache) and the mod's own lang files.
  Never type a name the game has.
- **When English changes**, `npm run check` lists the translated pages whose English changed since they were translated
  and any missing UI strings. Update the translation, then `node scripts/i18n.mjs stamp <code> <page.mdx>`.

## Components

| Component | Use |
|---|---|
| `mc/Item.astro` `<Item id="red_wine" />` | Inline icon + name, linked to the page that explains it. Bare ids try `vineandbarrel:` then `minecraft:`. |
| `mc/Slot.astro` | An inventory slot with a Minecraft tooltip; ingredients with several items cycle like JEI. |
| `recipes/Recipe.astro` `<Recipe id="brewing/grape_juice" />` | One recipe card: crafting, Brew Kettle (the mod's own screen texture), Aging Barrel, FD cooking pot. |
| `brewing/KettleScreen.astro` | The real Brew Kettle screen with items at the menu's slot positions. |
| `mc/Clip.astro` `<Clip src="kettle-fill" caption="…" />` | A short muted loop, played while on screen; a still poster with reduced motion. |
| `mc/Render.astro` `<Render name="scenes/tavern" />` | An isometric render from the real block models (`src/data/gen/renders.json` lists them). |
| `data/Stages.astro` | A strip of renders, one per state (growth, bung, weathering, fill). |
| `data/DrinkTable.astro`, `WildCrops.astro`, `VillageInfo.astro` | Generated tables. |
| `data/Advancements.astro` | The advancement tab as a tree like the game's screen, plus the list. |
| `data/TipsyLevels.astro` | The Tipsy levels; hover (tap) a level to look through the player's eyes at it. |
| `data/DrinkSwatches.astro` | Every drink's colour in its own vessel on a shelf; click copies the colour. |
| `brewing/BrewFlow.astro` | A brew played out on the kettle screen: fill, heat, brew, gauge, bottle. |
| `brewing/AgingTimeline.astro` | The Aging Barrel over the days: slider or play; bung, sky, status line, what a tap gives. |
| `brewing/CellarPlanner.astro` | Servings of a drink to crops, batches, buckets and bottles, barrels, waiting and vessels. |

Small touches that work everywhere: Minecraft tooltips on anything with `data-tip-items` (on phones the first tap shows
it, the second follows the link), `data-copy="text"` makes a copy button, `.vb-chalk` and `.vb-board` give a chalkboard or
a gilt signboard. Everything that moves respects "reduce motion".

## Voice

A player's guide, not a tech doc: second person, short sentences, gameplay words. Seconds, minutes and in-game days, never
ticks. No class names, file paths or "block entity". Vine & Barrel is a standalone brewing mod with deep Farmer's Delight
support; never call it an add-on.
