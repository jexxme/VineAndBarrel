// Code facts: which file runs where, with links straight to the filtered downloads. One row per Minecraft branch
// (CONTRIBUTING.md, Minecraft versions); the NeoForge ranges are each branch's neo_version_range in gradle.properties
// (26.3 comes from meta.json). Only 26.3 has a Fabric build. Citations are for maintainers; pages never show them.
const MODRINTH = 'https://modrinth.com/mod/vine-and-barrel/versions';
const CURSEFORGE = 'https://www.curseforge.com/minecraft/mc-mods/vine-and-barrel/files/all';
// CurseForge's mod loader type ids: 6 NeoForge, 4 Fabric
const cfLoader = { neoforge: 6, fabric: 4 } as const;
export type Loader = keyof typeof cfLoader;
export type Link = { label: string; href: string };

// Only stores whose page is live get a link. Modrinth is still reviewing the project: set this to true once it is approved.
const MODRINTH_LIVE = false;

const links = (loader: Loader, mc: string[]): Link[] => [
	...(MODRINTH_LIVE ? [{ label: 'Modrinth', href: `${MODRINTH}?l=${loader}&${mc.map((v) => `g=${v}`).join('&')}` }] : []),
	{ label: 'CurseForge', href: `${CURSEFORGE}?version=${mc[0]}&gameVersionTypeId=${cfLoader[loader]}` },
];

// Which cells have a file today, so no link leads to an empty download list: 'live' links to the downloads, 'next' says
// "next release" (coming with the next release, nothing up yet), 'planned' says "planned" (a later release), 'none' shows
// "—" (no build for that loader). Flip a cell to 'live' once its file is uploaded; README.md's table is kept the same by
// hand. Translated pages name the two words in their own language (their cells).
type Status = 'live' | 'next' | 'planned' | 'none';
const status = (s: Status, loader: Loader, mc: string[]): Cell =>
	s === 'live' ? links(loader, mc) : s === 'next' ? 'next release' : s === 'planned' ? 'planned' : '—';
export type Cell = Link[] | string;

export type Row = { minecraft: string; neoforge: Cell; neoforgeNeeds: string | null; fabric: Cell };
export const downloads: Row[] = [
	// neoforgeNeeds null: the current branch, the page shows meta.neoforge
	{ minecraft: '26.3', neoforge: status('live', 'neoforge', ['26.3']), neoforgeNeeds: null, fabric: status('next', 'fabric', ['26.3']) },
	{ minecraft: '26.2', neoforge: status('planned', 'neoforge', ['26.2']), neoforgeNeeds: '26.2.0.93 or newer', fabric: '—' },
	{ minecraft: '26.1.2', neoforge: status('planned', 'neoforge', ['26.1.2']), neoforgeNeeds: '26.1.2.126 or newer', fabric: '—' },
	{ minecraft: '26.1 / 26.1.1', neoforge: status('planned', 'neoforge', ['26.1', '26.1.1']), neoforgeNeeds: '26.1.0.19-beta to 26.1.1', fabric: '—' },
];

// The optional mods each loader's file works with (gradle.properties: the compat runs; Nutritional Balance has no
// Fabric build). Serene Seasons and Biomes O' Plenty work through tags on both.
export const optional = {
	neoforge: ["Farmer's Delight Neoforged", 'JEI', 'Jade', 'AppleSkin', 'Tough As Nails', 'Homeostatic', 'Thirst Was Taken 2', 'Nutritional Balance', 'Spice of Life Onion'],
	fabric: ["Farmer's Delight Refabricated", 'JEI', 'Jade', 'AppleSkin', 'Tough As Nails', 'Homeostatic', 'Thirst Was Taken 2', 'Spice of Life Onion'],
};
