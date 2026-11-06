# Vine & Barrel wiki

The player wiki, built with Astro + Starlight on the same frame as the Farmer's Delight Neoforged docs (mc.css slots and
tooltips, the FD Pixel font, the model renderer), with the same look; only the content, logo and name are Vine & Barrel's.

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

The in-game pictures (the home page panorama, the shots on the pages) come from a real game, taken headless:

```bash
./gradlew runScreenshots   # hidden window: builds the showcase world once, takes every shot, quits
cd site
npm run shots              # run/screenshots/wiki/*.png → src/assets/shots/*.jpg
```

The window never appears: the run has no early loading screen and a dev-only mixin keeps the game window hidden
(`src/screenshots`, never in the jar). The showcase world (a homestead with the village tavern, cellar, pergolas,
vineyard, hop garden, barley, well and pond) is built from a fixed seed the first time and kept in
`run/screenshots/saves/showcase`; `-Pshots.fresh` builds it again. Cameras are listed in `ShotDirector.SHOTS`: add one
there for a new picture, then use it with `<Shot src="name.jpg" caption="…" />`.

Farmer's Delight's own textures are never copied here: FD items show as text and link to the Farmer's Delight Neoforged wiki.

## Components

| Component | Use |
|---|---|
| `mc/Item.astro` `<Item id="red_wine" />` | Inline icon + name, linked to the page that explains it. Bare ids try `vineandbarrel:` then `minecraft:`. |
| `mc/Slot.astro` | An inventory slot with a Minecraft tooltip; ingredients with several items cycle like JEI. |
| `recipes/Recipe.astro` `<Recipe id="brewing/grape_juice" />` | One recipe card: crafting, Brew Kettle (the mod's own screen texture), Aging Barrel, FD cooking pot. |
| `brewing/KettleScreen.astro` | The real Brew Kettle screen with items at the menu's slot positions. |
| `mc/Render.astro` `<Render name="scenes/tavern" />` | An isometric render from the real block models (`src/data/gen/renders.json` lists them). |
| `data/Stages.astro` | A strip of renders, one per state (growth, bung, weathering, fill). |
| `data/DrinkTable.astro`, `TipsyLevels.astro`, `WildCrops.astro`, `VillageInfo.astro`, `Advancements.astro` | Generated tables. |

## Voice

A player's guide, not a tech doc: second person, short sentences, gameplay words. Seconds, minutes and in-game days, never
ticks. No class names, file paths or "block entity". Vine & Barrel is a standalone brewing mod with deep Farmer's Delight
support; never call it an add-on.
