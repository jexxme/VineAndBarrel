// One place to read the generated data (src/data/gen, written by scripts/extract.mjs from the mod's real data and
// from runtime.json, which the game itself writes: ./gradlew runDocsExport).
import itemsJson from '../data/gen/items.json';
import othersJson from '../data/gen/others.json';
import blocksJson from '../data/gen/blocks.json';
import recipesJson from '../data/gen/recipes.json';
import iconsJson from '../data/gen/icons.json';
import rendersJson from '../data/gen/renders.json';
import drinksJson from '../data/gen/drinks.json';
import worldgenJson from '../data/gen/worldgen.json';
import seasonsJson from '../data/gen/seasons.json';
import animalsJson from '../data/gen/animals.json';
import tradesJson from '../data/gen/trades.json';
import lootJson from '../data/gen/loot.json';
import advancementsJson from '../data/gen/advancements.json';
import rulesJson from '../data/gen/rules.json';
import metaJson from '../data/gen/meta.json';
import namesJson from '../data/gen/names.json';
const vanillaNames = namesJson as Record<string, string>;

export const NS = 'vineandbarrel';
/** The Farmer's Delight Neoforged wiki: FD's own items link there (we don't show FD's textures here). */
export const FDN_WIKI = 'https://jexxme.github.io/FarmersDelight-Neoforged';

export type Ingredient = { items: string[]; tag?: string | null; label?: string | null; any?: Ingredient[] };
export type Stack = { id: string; count: number };
export type Recipe = {
	id: string;
	type: string;
	loaded: boolean;
	fdOnly: boolean;
	grid?: (Ingredient | null)[][];
	ingredients?: Ingredient[];
	result?: Stack;
	bucket?: Stack | null;
	servings?: number;
	brewingTime?: number;
	experience?: number;
	cookingtime?: number;
	container?: Stack | null;
	base?: Stack;
	young?: Stack;
	aged?: Stack;
	fermentingTime?: number;
	agingTime?: number;
};
export type Item = {
	id: string;
	block: string | null;
	name: string;
	slug: string;
	category: string;
	stack: number;
	food: { nutrition: number; saturation: number; alwaysEdible: boolean } | null;
	consumable: { seconds: number; animation: string; effects: { id: string; name: string; duration: number; amplifier: number; probability: number }[] } | null;
	remainder: string | null;
	compost: number | null;
	glint: boolean;
	tags: string[];
};
export type Drink = {
	id: string;
	name: string;
	stage: 'young' | 'aged';
	base: string;
	bucket: string | null;
	vessel: string;
	color: string;
	effect: { id: string; name: string; duration: number; amplifier: number };
	fdOnly: boolean;
	aging: string;
};

export const items = itemsJson as unknown as Record<string, Item>;
export const others = othersJson as Record<string, { id: string; name: string }>;
export const blocks = blocksJson as Record<string, any>;
export const recipes = recipesJson as unknown as Recipe[];
export const icons = iconsJson as Record<string, 'flat' | '3d' | null>;
export const renders = rendersJson as Record<string, boolean>;
export const drinks = drinksJson as Drink[];
export const worldgen = worldgenJson as any[];
export const seasons = seasonsJson as Record<string, string[]>;
export const animals = animalsJson as Record<string, string[]>;
export const trades = tradesJson as any[];
export const loot = lootJson as Record<string, { rolls: number[]; entries: { id: string; weight: number; chance: number; count: number[] }[] }[]>;
export const advancements = advancementsJson as any[];
export const rules = rulesJson as any;
export const meta = metaJson;

const BASE = import.meta.env.BASE_URL.replace(/\/$/, '');
export const url = (p: string) => BASE + (p.startsWith('/') ? p : '/' + p);

export const itemList = Object.values(items);
export const CATEGORIES: Record<string, { label: string; blurb: string }> = {
	workstations: { label: 'Workstations', blurb: 'Brew Kettle and Aging Barrel' },
	crops: { label: 'Crops & seeds', blurb: 'Grapes, hops, barley and their wild plants' },
	storage: { label: 'Storage', blurb: 'Crates and the Barley Bale' },
	bases: { label: 'Juice & wort', blurb: 'What the kettle brews' },
	drinks: { label: 'Drinks', blurb: 'Wines, beers, ciders and their vessels' },
	meals: { label: "Farmer's Delight meals", blurb: "Cooking pot meals, with Farmer's Delight" },
};

// ------------------------------------------------------------ recipe queries
export const recipeTypeName: Record<string, string> = {
	crafting_shaped: 'Crafting',
	crafting_shapeless: 'Crafting (shapeless)',
	'vineandbarrel:brewing': 'Brew Kettle',
	'vineandbarrel:aging': 'Aging Barrel',
	'farmersdelight:cooking': "Farmer's Delight Cooking Pot",
};
/** Every recipe a player can use; the Farmer's Delight ones (fdOnly) need FD installed. */
export const playableRecipes = recipes.filter((r) => (r.loaded || r.fdOnly) && !r.special);
const outputsOf = (r: Recipe) => [r.result?.id, r.bucket?.id, r.young?.id, r.aged?.id].filter(Boolean) as string[];
const inputsOf = (r: Recipe) => [...(r.ingredients ?? []), ...((r.grid ?? []).flat().filter(Boolean) as Ingredient[])].flatMap((i) => i.items).concat(r.type === 'vineandbarrel:aging' ? [r.base!.id, ...(r.bucket ? [r.bucket.id] : [])] : []);
export function recipesFor(id: string) {
	return playableRecipes.filter((r) => outputsOf(r).includes(id));
}
export function recipesUsing(id: string) {
	return playableRecipes.filter((r) => inputsOf(r).includes(id) || r.container?.id === id);
}
export const recipeOutputs = outputsOf;
export const recipeInputs = inputsOf;

export const recipeById = new Map(recipes.map((r) => [r.id, r]));
export const recipe = (id: string) => {
	const r = recipeById.get(id.includes(':') ? id : `${NS}:${id}`);
	if (!r) throw new Error(`Unknown recipe ${id}`);
	return r;
};
export const byType = (type: string) => recipes.filter((r) => r.type === type);

/** Bare ids try vineandbarrel: first, then minecraft:. */
export const fullId = (raw: string) => (raw.includes(':') ? raw : items[`${NS}:${raw}`] ? `${NS}:${raw}` : `minecraft:${raw}`);
export const isFd = (id: string) => id.startsWith('farmersdelight:');

/** Name of any item id, from the game's language files (FD items from their id). */
export function nameOf(id: string): string {
	return items[id]?.name ?? others[id]?.name ?? vanillaNames[id] ?? id.split(':')[1].replaceAll('_', ' ').replace(/\b\w/g, (c) => c.toUpperCase());
}
export function iconOf(id: string): { src: string; flat: boolean } | null {
	const kind = icons[id];
	if (!kind) return null;
	const [ns, p] = id.split(':');
	return { src: url(`/icons/${ns}/${p}.png`), flat: kind === 'flat' };
}
/** The page that explains an item (there are no per-item pages: each item lives on its mechanic's page). */
/** The mechanic page that explains an item (its item page links there). */
const CATEGORY_PAGE: Record<string, [string, string]> = {
	crops: ['/crops/', 'crops'], storage: ['/crops/#storage-blocks', 'storage blocks'], bases: ['/brew-kettle/', 'the Brew Kettle'],
	drinks: ['/drinks/', 'drinks'], meals: ['/farmers-delight/', "Farmer's Delight meals"],
};
export function mechanicFor(it: Item): { href: string; label: string } | null {
	if (/kettle/.test(it.id)) return { href: '/brew-kettle/', label: 'the Brew Kettle' };
	if (/aging_barrel/.test(it.id)) return { href: '/aging-barrel/', label: 'the Aging Barrel' };
	const p = CATEGORY_PAGE[it.category];
	return p ? { href: p[0], label: p[1] } : null;
}
/** Our items have their own page (/items/<id path>/); Farmer's Delight items link to the FD Neoforged wiki. */
export function pageOf(id: string): string | null {
	if (isFd(id)) return `${FDN_WIKI}/items/${id.split(':')[1].replaceAll('_', '-')}/`;
	return items[id] ? url(`/items/${items[id].slug}/`) : null;
}
export function renderOf(name: string): string | null {
	return renders[name] ? url(`/renders/${name}.png`) : null;
}

// ------------------------------------------------------------ formatting
export const seconds = (ticks: number) => {
	const s = ticks / 20;
	return Number.isInteger(s) ? `${s} s` : `${+s.toFixed(2)} s`;
};
/** "15 s", "1 min", "1 min 30 s" */
export function duration(ticks: number) {
	const s = Math.round(ticks / 20);
	if (s < 60) return `${s} s`;
	const m = Math.floor(s / 60);
	return s % 60 ? `${m} min ${s % 60} s` : `${m} min`;
}
/** In-game days ("1 day", "3 days") from ticks */
export const days = (ticks: number) => {
	const d = ticks / rules.dayTicks;
	return `${+d.toFixed(2)} ${d === 1 ? 'day' : 'days'}`;
};
export const pct = (x: number, digits = 0) => `${+(x * 100).toFixed(digits)}%`;
export const roman = (n: number) => ['I', 'II', 'III', 'IV', 'V', 'VI'][n] ?? String(n + 1);
export const short = (id: string) => id.replace(/^minecraft:/, '');
/** "a, b and c" */
export const list = (xs: string[]) => (xs.length < 2 ? xs.join('') : `${xs.slice(0, -1).join(', ')} and ${xs[xs.length - 1]}`);

/** All items an ingredient can be, for cycling slot displays (de-duplicated, stable order). */
export const ingredientItems = (i: Ingredient | null | undefined) => (i ? [...new Set(i.items)] : []);
