// Code facts: which file runs where, each cell a link straight to its jar on the GitHub release. One row per Minecraft
// branch, all from src/data/releases.json (the store pages and README read the same file), so a release only bumps the
// versions there. Citations are for maintainers; pages never show them.
import data from '../releases.json';
import metaJson from '../gen/meta.json';

const RELEASES = 'https://github.com/jexxme/VineAndBarrel/releases/download';
export type Loader = 'neoforge' | 'fabric';
export type Link = { label: string; href: string };
export type Cell = Link[] | string;

// The stores' file lists, every version in one place. Only stores whose page is live get a link: Modrinth is still
// reviewing the project, so set this to true once it is approved.
const MODRINTH_LIVE = false;
export const stores: Link[] = [
	...(MODRINTH_LIVE ? [{ label: 'Modrinth', href: 'https://modrinth.com/mod/vine-and-barrel/versions' }] : []),
	{ label: 'CurseForge', href: 'https://www.curseforge.com/minecraft/mc-mods/vine-and-barrel/files' },
];
type Release = (typeof data.releases)[number];

// The tag is named after the NeoForge version (26.3-1.0.1+neoforged.1); both jars hang on it. "+" in a URL path is %2B.
const enc = (s: string) => s.replaceAll('+', '%2B');
const jar = (r: Release, loader: Loader) => `VineAndBarrel-${r.build}-${r[loader]}.jar`;
export const fileUrl = (r: Release, loader: Loader) => `${RELEASES}/${enc(`${r.build}-${r.neoforge}`)}/${enc(jar(r, loader))}`;
// "26.1 / 26.1.1": the Minecraft versions one file runs on
const label = (r: Release) => r.minecraft.join(' / ');
// The version alone: 1.0.1+neoforged.1 is Vine & Barrel 1.0.1
const cell = (r: Release, loader: Loader): Cell => (r[loader] ? [{ label: `Vine & Barrel ${r[loader].split('+')[0]}`, href: fileUrl(r, loader) }] : '—');

export type Row = { minecraft: string; neoforge: Cell; fabric: Cell; needs: Release['needs'] };
export const downloads: Row[] = data.releases.map((r) => ({ minecraft: label(r), neoforge: cell(r, 'neoforge'), fabric: cell(r, 'fabric'), needs: r.needs }));
// "26.1–26.3": the oldest to the newest Minecraft version with a file
export const span = `${data.releases.at(-1)!.minecraft[0]}–${data.releases[0].minecraft.at(-1)}`;

// The newest row is the branch the wiki is built from: say so when its versions lag behind meta.json (bumped, not set here)
const top = data.releases[0];
if (top.build !== metaJson.minecraft || top.neoforge !== metaJson.modVersion || top.fabric !== metaJson.fabricModVersion) {
	console.warn(`releases.json: the ${top.build} row (${top.neoforge}, ${top.fabric}) differs from meta.json (${metaJson.minecraft}, ${metaJson.modVersion}, ${metaJson.fabricModVersion})`);
}

// The optional mods each loader's file works with (gradle.properties: the compat runs; Nutritional Balance has no
// Fabric build). Serene Seasons and Biomes O' Plenty work through tags on both.
export const optional = {
	neoforge: ["Farmer's Delight Neoforged", 'JEI', 'Jade', 'AppleSkin', 'Tough As Nails', 'Homeostatic', 'Thirst Was Taken 2', 'Nutritional Balance', 'Spice of Life Onion'],
	fabric: ["Farmer's Delight Refabricated", 'JEI', 'Jade', 'AppleSkin', 'Tough As Nails', 'Homeostatic', 'Thirst Was Taken 2', 'Spice of Life Onion'],
};
// The optional mods that have no build for some Minecraft versions (releases.json: without), with the loaders each
// version's file works with them on: "NeoForge, Fabric", "NeoForge", "Fabric" or "–"
const LOADER = { neoforge: 'NeoForge', fabric: 'Fabric' } as const;
const loadersFor = (mod: string, r: Release) =>
	(['neoforge', 'fabric'] as const).filter((l) => optional[l].includes(mod) && !(r.without[l] as string[]).includes(mod));
export const gaps = [...new Set([...optional.neoforge, ...optional.fabric])]
	.filter((mod) => new Set(data.releases.map((r) => loadersFor(mod, r).join())).size > 1)
	.map((mod) => ({ mod, cells: data.releases.map((r) => loadersFor(mod, r).map((l) => LOADER[l]).join(', ') || '–') }));
