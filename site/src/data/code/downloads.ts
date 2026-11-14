// Code facts: which file runs where, each cell a link straight to its jar on the GitHub release. One row per Minecraft
// branch, all from src/data/releases.json (the store pages and README read the same file), so a release only bumps the
// versions there. Citations are for maintainers; pages never show them.
import data from '../releases.json';
import metaJson from '../gen/meta.json';

const RELEASES = 'https://github.com/jexxme/VineAndBarrel/releases/download';
// Each branch builds two jars: NeoForge and Fabric, or Forge and Fabric (1.20.1)
export type Loader = 'neoforge' | 'forge' | 'fabric';
export type Link = { label: string; href: string };
export type Cell = Link[] | string;

// The stores' file lists, every version in one place. Only stores whose page is live get a link: Modrinth is still
// reviewing the project, so set this to true once it is approved.
const MODRINTH_LIVE = false;
export const stores: Link[] = [
	...(MODRINTH_LIVE ? [{ label: 'Modrinth', href: 'https://modrinth.com/mod/vine-and-barrel/versions' }] : []),
	{ label: 'CurseForge', href: 'https://www.curseforge.com/minecraft/mc-mods/vine-and-barrel/files' },
];
type Needs = { neoforge?: string; forge?: string; fabricLoader: string; fabricApi: string };
type Release = {
	minecraft: string[];
	build: string;
	neoforge?: string;
	forge?: string;
	fabric?: string;
	curseforge?: Partial<Record<Loader, number>>;
	needs: Needs;
	without: Partial<Record<Loader, string[]>>;
};
const releases = data.releases as Release[];

// The loaders' names, which no language translates
export const LOADER: Record<Loader, string> = { neoforge: 'NeoForge', forge: 'Forge', fabric: 'Fabric' };
// The loaders some row has a file for, in this order: the download table's columns
export const loaders = (['neoforge', 'forge', 'fabric'] as const).filter((l) => releases.some((r) => r[l]));

// The tag is named after the NeoForge (or Forge) version (26.3-1.0.1+neoforged.1); both jars hang on it. "+" in a URL
// path is %2B.
const enc = (s: string) => s.replaceAll('+', '%2B');
const main = (r: Release) => (r.neoforge ? r.neoforge : r.forge!);
export const jar = (r: Release, loader: Loader) => `VineAndBarrel-${r.build}-${r[loader]}.jar`;
export const fileUrl = (r: Release, loader: Loader) => `${RELEASES}/${enc(`${r.build}-${main(r)}`)}/${enc(jar(r, loader))}`;
// "26.1 / 26.1.1": the Minecraft versions one file runs on
const label = (r: Release) => r.minecraft.join(' / ');
// The version alone: 1.0.1+neoforged.1 is Vine & Barrel 1.0.1
const cell = (r: Release, loader: Loader): Cell => (r[loader] ? [{ label: `Vine & Barrel ${r[loader]!.split('+')[0]}`, href: fileUrl(r, loader) }] : '—');

// The requirements table's columns: each loader some row has, then Fabric API
export const needColumns = (
	[
		{ key: 'neoforge', label: 'NeoForge' },
		{ key: 'forge', label: 'Forge' },
		{ key: 'fabricLoader', label: 'Fabric Loader' },
		{ key: 'fabricApi', label: 'Fabric API' },
	] as const
).filter((c) => releases.some((r) => r.needs[c.key]));

export type Row = { minecraft: string; files: Record<Loader, Cell>; needs: Record<keyof Needs, string> };
export const downloads: Row[] = releases.map((r) => ({
	minecraft: label(r),
	files: { neoforge: cell(r, 'neoforge'), forge: cell(r, 'forge'), fabric: cell(r, 'fabric') },
	needs: Object.fromEntries(needColumns.map((c) => [c.key, r.needs[c.key] ?? '—'])) as Row['needs'],
}));
// "1.20.1–26.3": the oldest to the newest Minecraft version with a file
export const span = `${releases.at(-1)!.minecraft[0]}–${releases[0].minecraft.at(-1)}`;
// The Minecraft versions with a NeoForge file and those with a Forge file ("26.3", "26.1 / 26.1.1"…)
export const neoforgeVersions = releases.filter((r) => r.neoforge).map(label);
export const forgeVersions = releases.filter((r) => r.forge).map(label);
// The Forge files' names, with their Minecraft version: the jar name tells the loader
export const forgeFiles = releases.filter((r) => r.forge).map((r) => ({ minecraft: label(r), jar: jar(r, 'forge') }));
// "NeoForge, Forge and Fabric": every loader with a file, for search titles
export const loaderNames = loaders.map((l) => LOADER[l]).join(', ').replace(/, ([^,]+)$/, ' and $1');

// The newest row is the branch the wiki is built from: say so when its versions lag behind meta.json (bumped, not set here)
const top = releases[0];
if (top.build !== metaJson.minecraft || top.neoforge !== metaJson.modVersion || top.fabric !== metaJson.fabricModVersion) {
	console.warn(`releases.json: the ${top.build} row (${top.neoforge}, ${top.fabric}) differs from meta.json (${metaJson.minecraft}, ${metaJson.modVersion}, ${metaJson.fabricModVersion})`);
}

// The optional mods each loader's file works with (gradle.properties: the compat runs; Nutritional Balance has no
// Fabric build and no 1.20.1 build; Kaleidoscope Tavern is Kaleidoscope Tavern Refabricated on Fabric, VBTags). Farmer's Delight is Farmer's Delight Neoforged on NeoForge for 26.x, the original
// mod on NeoForge for 1.21.1 and on Forge, and Farmer's Delight Refabricated on Fabric (each branch's release.yml);
// releases.json (without) says which version has which. Serene Seasons and Biomes O' Plenty work through tags on all.
export const optional: Record<Loader, string[]> = {
	neoforge: ["Farmer's Delight Neoforged", "Farmer's Delight", 'JEI', 'Jade', 'AppleSkin', 'Tough As Nails', 'Homeostatic', 'Thirst Was Taken 2', 'Nutritional Balance', 'Spice of Life Onion', 'Kaleidoscope Tavern', 'Towns & Towers'],
	forge: ["Farmer's Delight", 'JEI', 'Jade', 'AppleSkin', 'Tough As Nails', 'Homeostatic', 'Thirst Was Taken 2', 'Spice of Life Onion', 'Kaleidoscope Tavern', 'Towns & Towers'],
	fabric: ["Farmer's Delight Refabricated", 'JEI', 'Jade', 'AppleSkin', 'Tough As Nails', 'Homeostatic', 'Thirst Was Taken 2', 'Spice of Life Onion', 'Kaleidoscope Tavern Refabricated', 'Towns & Towers'],
};
// The optional mods that have no build for some Minecraft versions (releases.json: without), with the loaders each
// version's file works with them on: "NeoForge, Fabric", "Forge", "Fabric" or "–". NeoForge and Forge count as the
// same slot, so a mod that works on both files of every version is no gap.
const loadersFor = (mod: string, r: Release) => loaders.filter((l) => r[l] && optional[l].includes(mod) && !(r.without[l] ?? []).includes(mod));
const slots = (mod: string, r: Release) => loadersFor(mod, r).map((l) => (l === 'fabric' ? 'fabric' : 'main')).join();
export const gaps = [...new Set(loaders.flatMap((l) => optional[l]))]
	.filter((mod) => new Set(releases.map((r) => slots(mod, r))).size > 1)
	.map((mod) => ({ mod, cells: releases.map((r) => loadersFor(mod, r).map((l) => LOADER[l]).join(', ') || '–') }));
