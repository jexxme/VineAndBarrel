// Search and link-preview metadata. lib/seo-route.ts writes it into every page's <head>; pages/og/[key].jpg.ts draws
// the pictures at build time.
//
// - Titles and descriptions: the English pages get the ones search results show, written for what players look for
//   ("minecraft beer mod", "wine", "brewing", "tavern", NeoForge, Fabric, the Minecraft version). The page's own title
//   (its signboard) and the text stay as they are. Translated pages keep the title and description of their file;
//   their home pages set a search title in index.mdx.
// - Pictures: what a link preview shows (Discord, Reddit, X, WhatsApp…), the same in every language: a shot from the
//   page, a render or a store board on the tavern's plank wall, the brand sign for the home page and everything else.
// - Structured data (JSON-LD): the mod as a free SoftwareApplication on the home page, breadcrumbs everywhere else.
//
// The same rule as the rest of the wiki: no number from memory. Versions come from meta.json and releases.json.
import { meta, itemList, iconOf, nameOf } from './data';
import { span } from '../data/code/downloads';

export const SITE_NAME = 'Vine & Barrel';
export const CURSEFORGE = 'https://www.curseforge.com/minecraft/mc-mods/vine-and-barrel';
export const GITHUB = 'https://github.com/jexxme/VineAndBarrel';

/** A link preview's picture. Files are read at build time from site/ (and the repository's branding/ folder). */
export type Picture =
	| { banner: true; alt: string } // the brand sign (branding/store/hero.png) over the title-screen panorama
	| { shot: string; alt: string } // src/assets/shots/<shot>.jpg, cropped to the preview's shape
	| { render: string; alt: string } // public/renders/<render>.png on the plank wall
	| { board: string; alt: string } // branding/store/<board>.png (the store page's boards) on the plank wall
	| { item: string; alt: string }; // a block's render or an item's icon, big, on the plank wall (square, a small preview)

export const SITE_PICTURE: Picture = { banner: true, alt: 'The Vine & Barrel sign: beer, wine, mead, cider. Cheers!' };

type Page = { title: string; description?: string; picture: Picture };

/** English pages by slug ('' is the home page). A title is shown as "<title> | Vine & Barrel", the home page's alone. */
export const PAGES: Record<string, Page> = {
	'': {
		title: 'Vine & Barrel — Minecraft beer, wine & brewing mod',
		description: `Brew beer, wine, cider and mead in Minecraft ${span} on NeoForge and Fabric: grow hops and grapes, brew in a copper kettle, age in oak barrels, open a tavern.`,
		picture: SITE_PICTURE,
	},
	'getting-started': {
		title: 'Getting started: install the Minecraft brewing mod',
		description: `Install Vine & Barrel for Minecraft ${span} on NeoForge or Fabric, see what the brewing mod adds and how the loop goes, and pick where to start.`,
		picture: { shot: 'hero', alt: 'A Vine & Barrel tavern at golden hour, with vineyard terraces on the hill behind it.' },
	},
	'guides/first-drink': {
		title: 'How to make wine in Minecraft: your first drink',
		description: 'A step-by-step start with Vine & Barrel, from the first wild grapes to a glass of aged red wine, with only vanilla materials to begin.',
		picture: { shot: 'vineyard', alt: 'Rows of grapevines on trellises in a Vine & Barrel vineyard.' },
	},
	'guides/cellar': {
		title: 'Build a brewery and wine cellar in Minecraft',
		description: 'From one barrel to a cellar: plan a batch, a brewhouse that runs on hoppers, barrel racks and walls, automatic tapping, and serving a round.',
		picture: { shot: 'cellar', alt: 'A brick cellar lined with Aging Barrels.' },
	},
	crops: {
		title: 'Grapes, hops and barley: Minecraft brewing crops',
		description: 'Red and white grape bushes, hop bushes, trellises, barley and wild crops in Vine & Barrel: where to find them, how they grow, and how to farm them.',
		picture: { shot: 'fields', alt: 'A barley field at sunset beside a village.' },
	},
	'brew-kettle': {
		title: 'Brew Kettle: how to brew beer and wine in Minecraft',
		description: 'How the Brew Kettle turns grapes, berries, apples, honey, barley and wheat into juice, must and wort, and how to heat, fill and automate it.',
		picture: { render: 'scenes/kettle-on-campfire', alt: 'A copper Brew Kettle on a campfire.' },
	},
	'aging-barrel': {
		title: 'Aging Barrel: ferment and age beer and wine',
		description: 'Fill, ferment, age and tap. How the Aging Barrel works, how long it takes, and how to build a cellar of them.',
		picture: { shot: 'taverns/cellar', alt: "A village tavern's cellar with barrels ready to tap." },
	},
	drinks: {
		title: 'All drinks: Minecraft beer, wine, cider and mead',
		description: "Every beer, wine, cider and mead in Vine & Barrel, what it's made from, what it's served in and the buff it gives.",
		picture: { shot: 'tables', alt: 'Mugs and wine glasses on a tavern table at night.' },
	},
	tipsy: {
		title: 'Tipsy: what getting drunk does in Minecraft',
		description: 'What Tipsy does at each level, what happens after one drink too many, how to sober up, and how to turn it down or off.',
		picture: { board: 'tipsy', alt: 'The Tipsy levels, from a gentle sway to lights out.' },
	},
	blackouts: {
		title: 'Blackouts: one drink too many, and where you wake up',
		description: 'One drink too many: what happens when you black out, every place you can wake up, how likely each is, and the souvenirs you bring back.',
		picture: { shot: 'terrace', alt: 'Friends toasting at a tavern table, seen through Tipsy eyes.' },
	},
	interactions: {
		title: 'Interactions: what brewing blocks and drinks do',
		description: 'All the small things blocks and drinks do in Vine & Barrel, each one working the way something in vanilla or Farmer\'s Delight already does.',
		picture: { render: 'scenes/placed-drinks', alt: 'Two mugs and a wine glass placed on a table.' },
	},
	villages: {
		title: 'Village taverns in Minecraft: barrels, farms and trades',
		description: 'Village taverns with barrels ready to tap, village farms that grow grapes, hops and barley, seed trades and chest loot.',
		picture: { shot: 'taverns/plains', alt: 'A tavern in a plains village.' },
	},
	'reference/recipes': {
		title: 'All recipes: brewing beer, wine, cider and mead',
		description: 'Every Vine & Barrel recipe as it appears in game, filterable by station and searchable by result or ingredient.',
		picture: { board: 'craft', alt: 'The recipes to craft first: the Brew Kettle, the Aging Barrel, the mug and the wine glass.' },
	},
	'reference/advancements': {
		title: 'Advancements: the brewing advancement tab',
		description: "The Vine & Barrel advancement tab and how to earn each one, laid out as a tree like the game's own screen.",
		picture: SITE_PICTURE,
	},
	'reference/config': {
		title: 'Configuration: every option for servers and players',
		description: 'Every Vine & Barrel option, for server owners and for each player, with its default and what it does, on NeoForge and Fabric.',
		picture: SITE_PICTURE,
	},
	'farmers-delight': {
		title: "Farmer's Delight support: drinks, meals and straw",
		description: "What changes in Vine & Barrel when Farmer's Delight is installed too: drinks from its bottles, cooking pot meals like Coq au Vin, beer bread and straw.",
		picture: SITE_PICTURE,
	},
	compatibility: {
		title: 'Mod compatibility on NeoForge and Fabric',
		description: "Which mods Vine & Barrel works with on NeoForge and Fabric and what each one adds: Farmer's Delight, JEI, Jade, AppleSkin, Serene Seasons and more.",
		picture: SITE_PICTURE,
	},
	changelog: {
		title: 'Changelog: every version for NeoForge and Fabric',
		description: 'What changed in each version of Vine & Barrel, the Minecraft brewing mod, on NeoForge and Fabric.',
		picture: SITE_PICTURE,
	},
	items: {
		title: 'All items and blocks of the brewing mod',
		picture: { render: 'scenes/storage', alt: 'A barley bale and crates of hops and grapes.' },
	},
};

const itemsBySlug = new Map(itemList.map((i) => [i.slug, i]));

/** The English search title of an item page: what players type ("minecraft red wine"). */
export const itemTitle = (name: string) => `${name} in Minecraft: how to get & use it`;

/** The page's picture: its own, an item's, or the brand sign. `slug` is the English one ('', 'crops', 'items/red_wine'). */
export function pictureOf(slug: string): Picture {
	if (PAGES[slug]) return PAGES[slug].picture;
	const item = slug.startsWith('items/') && itemsBySlug.get(slug.slice('items/'.length));
	if (item && iconOf(item.id)) return { item: item.id, alt: nameOf(item.id) }; // a fresh object: the route sets alt per language
	return SITE_PICTURE;
}

/** The file name a picture is published under (og/<key>.jpg); pictures shared by pages are drawn once. */
export function pictureKey(p: Picture): string {
	const name = (s: string) => s.replace(/[^\w-]+/g, '-');
	if ('banner' in p) return 'vine-and-barrel';
	if ('shot' in p) return `shot-${name(p.shot)}`;
	if ('render' in p) return `render-${name(p.render)}`;
	if ('board' in p) return `board-${name(p.board)}`;
	return `item-${name(p.item.split(':')[1])}`;
}

/** Large previews are 1200×630 (what every site expects); an item's is a square thumbnail. */
export const pictureSize = (p: Picture) => ('item' in p ? { width: 600, height: 600 } : { width: 1200, height: 630 });

/** Every picture the site uses, once each (pages/og/[key].jpg.ts draws them). */
export function allPictures(): Picture[] {
	const all = [SITE_PICTURE, ...Object.values(PAGES).map((p) => p.picture), ...itemList.map((i) => pictureOf(`items/${i.slug}`))];
	return [...new Map(all.map((p) => [pictureKey(p), p])).values()];
}

/** og:locale wants language_TERRITORY: the game's own lang file names (de_de → de_DE). */
export const ogLocale = (mc: string) => mc.replace(/_(\w+)$/, (_, r: string) => `_${r.toUpperCase()}`);

/** The mod itself, for the home page's structured data. */
export function softwareApplication(home: string, description: string, image: string) {
	return {
		'@type': 'SoftwareApplication',
		'@id': `${home}#mod`,
		name: SITE_NAME,
		alternateName: 'Vine and Barrel',
		description,
		url: home,
		image,
		applicationCategory: 'GameApplication',
		applicationSubCategory: 'Minecraft mod',
		operatingSystem: 'Windows, macOS, Linux',
		softwareVersion: meta.version,
		softwareRequirements: `Minecraft: Java Edition ${span} with NeoForge or Fabric`,
		downloadUrl: `${CURSEFORGE}/files`,
		license: 'https://opensource.org/licenses/MIT',
		isAccessibleForFree: true,
		offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
		author: { '@type': 'Person', name: 'Jexxme', url: 'https://github.com/jexxme' },
		sameAs: [CURSEFORGE, GITHUB],
	};
}
