// Isometric renders for the wiki, from the mod's real block models: every Vine & Barrel block, growth stages, the
// kettle's supports and weathering, the barrel's bungs, racks and stacks, and the small scenes on the pages. Output: public/renders/**.png and
// src/data/gen/renders.json (which renders exist).
import fs from 'node:fs';
import path from 'node:path';
import { renderScene, blockstateModel } from './lib/render.mjs';

const SITE = path.resolve(import.meta.dirname, '..');
const runtime = JSON.parse(fs.readFileSync(path.join(SITE, 'src/data/runtime.json'), 'utf8'));
const OUT = path.join(SITE, 'public/renders');
const GRASS = [[0x91, 0xbd, 0x59]];
const manifest = {};
const failed = [];

/** A piece of the trellis frame (end posts, top rail, caps; the game adds them from the neighbours) for an east-west row. */
const frame = (part, offset) => ({ model: `vineandbarrel:block/trellis_${part}`, offset });

/** place(blockId, state, [x, y, z]) -> scene parts */
function place(block, state = {}, offset = [0, 0, 0], tint) {
	// Fire is a multipart model with OR conditions; its floor model is what you see on a block
	if (block === 'minecraft:fire') return [{ model: 'minecraft:block/fire_floor0', offset }];
	const parts = blockstateModel(block, state);
	if (!parts) throw new Error('no blockstate for ' + block);
	return parts.map((p) => ({ ...p, offset, tint }));
}
async function save(name, parts, opts = {}) {
	try {
		const png = await renderScene(parts, { size: 256, ...opts });
		if (!png) throw new Error('empty');
		const file = path.join(OUT, name + '.png');
		fs.mkdirSync(path.dirname(file), { recursive: true });
		fs.writeFileSync(file, png);
		manifest[name] = true;
	} catch (e) {
		console.log(`! ${name}: ${e.message}`);
		failed.push(name);
	}
}
const ageValues = (id, prop = 'age') => runtime.blocks[id]?.properties[prop] ?? [];
const facingNorth = (id) => (runtime.blocks[id]?.properties.facing ? { facing: 'north' } : {});

// Liquid colours as the mod tints them (the drinks' colours from runtime.json)
const rgb = (drink) => {
	const c = runtime.drinks[`vineandbarrel:${drink}`].color;
	return [(c >> 16) & 255, (c >> 8) & 255, c & 255];
};

// The kettle's brews in the colours its gauge and open lid show
const liquid = (id) => {
	const c = runtime.rules.kettleLiquidColors[id];
	return [(c >> 16) & 255, (c >> 8) & 255, c & 255];
};

// Every block in its default look (waxed kettles look like their unwaxed stage)
const KETTLE = { facing: 'north', open: 'false', fill: '0', support: 'none', waterlogged: 'false' };
const BARREL = { facing: 'north', bung: 'cork', fill: '0', left: 'false', right: 'false', stacked: 'false', tapping: 'false' };
for (const [id, b] of Object.entries(runtime.blocks)) {
	const slug = id.split(':')[1];
	// vines on a rope or a fence, wall, chain or bars hold a block the wiki can't show on its own (FD's rope, the block kept inside)
	if (slug.startsWith('waxed_') || slug === 'placed_drinks' || slug.endsWith('_on_rope') || slug.endsWith('_on_support')) continue;
	const state = { ...b.defaultState, ...facingNorth(id) };
	if ('age' in state) state.age = String(Math.max(...ageValues(id).map(Number)));
	await save(`blocks/${slug}`, place(id, state));
}

// Growth stages, standing on the block they grow on
const crops = [
	['vineandbarrel:grape_bush', 'minecraft:grass_block', {}, GRASS],
	['vineandbarrel:white_grape_bush', 'minecraft:grass_block', {}, GRASS],
	['vineandbarrel:hop_bush', 'minecraft:grass_block', {}, GRASS],
	['vineandbarrel:barley', 'minecraft:farmland', { moisture: '7' }],
];
for (const [id, ground, gstate, tint] of crops) {
	for (const v of ageValues(id)) {
		// a bush on its own: the round bush (not the base of a vine, which its other states draw)
		const alone = id.endsWith('_bush') ? { support: 'none', trellis: 'none' } : {};
		await save(`stages/${id.split(':')[1]}-${v}`, [...place(ground, gstate, [0, -1, 0], tint), ...place(id, { age: v, ...alone })], { size: 192 });
	}
}

// Kettle: weathering stages and supports
const kettles = ['brew_kettle', 'exposed_brew_kettle', 'weathered_brew_kettle', 'oxidized_brew_kettle'];
for (const [i, k] of kettles.entries()) await save(`stages/kettle-weathering-${i}`, place(`vineandbarrel:${k}`, KETTLE), { size: 192 });
// Barrel: bung states and the sight glass from empty to full
for (const [i, bung] of ['cork', 'airlock', 'wax'].entries()) await save(`stages/barrel-bung-${i}`, place('vineandbarrel:aging_barrel', { ...BARREL, bung, fill: '8' }), { size: 192 });
for (const f of ageValues('vineandbarrel:aging_barrel', 'fill')) await save(`stages/barrel-fill-${f}`, place('vineandbarrel:aging_barrel', { ...BARREL, fill: f }, [0, 0, 0], [rgb('red_wine')]), { size: 192 });

const RACK_DRINKS = ['aged_red_wine', 'barley_ale', 'sweet_berry_wine'];
const rack = (y, stacked, z = 0, x0 = 0) => [0, 1, 2].flatMap((x) =>
	place('vineandbarrel:aging_barrel', { ...BARREL, fill: '6', bung: ['wax', 'airlock', 'cork'][x], stacked: String(stacked), left: String(!stacked && x > 0), right: String(!stacked && x < 2) }, [x0 + x, y, z], [rgb(RACK_DRINKS[x])]));
const drinks = (spots) => Object.fromEntries(spots.map((v, i) => [`spot_${i}`, v]));
const table = (spots, colours, offset) => place('vineandbarrel:placed_drinks', drinks(spots), offset, colours.map((d) => (d ? rgb(d) : [255, 255, 255])));

// Scenes for the pages
const scenes = {
	'kettle-on-magma': [...place('minecraft:magma_block'), ...place('vineandbarrel:brew_kettle', KETTLE, [0, 1, 0])],
	'kettle-on-hopper': [...place('minecraft:magma_block'), ...place('minecraft:hopper', { facing: 'down' }, [0, 1, 0]), ...place('vineandbarrel:brew_kettle', KETTLE, [0, 2, 0])],
	'kettle-on-campfire': [...place('minecraft:campfire', { lit: 'true', facing: 'north' }), ...place('vineandbarrel:brew_kettle', { ...KETTLE, support: 'tray' }, [0, 1, 0])],
	'kettle-hanging': [...place('minecraft:oak_planks', {}, [0, 2, 0]), ...place('minecraft:iron_chain', { axis: 'y' }, [0, 1, 0]), ...place('vineandbarrel:brew_kettle', { ...KETTLE, support: 'handle' }), ...place('minecraft:campfire', { lit: 'true', facing: 'north' }, [0, -1, 0])],
	// A finished brew waits under the raised lid, in its colour
	'kettle-open': [...place('minecraft:campfire', { lit: 'true', facing: 'north' }), ...place('vineandbarrel:brew_kettle', { ...KETTLE, support: 'tray', fill: '3' }, [0, 1, 0], [liquid('vineandbarrel:grape_juice')])],
	'barrel-rack': rack(0, false),
	'barrel-cellar': [...rack(0, false), ...rack(1, true)],
	'placed-drinks': [...place('minecraft:oak_planks'), ...table(['mug_full', 'glass_full', 'glass', 'mug_full'], ['barley_ale', 'red_wine', null, 'wheat_beer'], [0, 1, 0])],
	// The camera looks from the north-west: low x and low z are in front
	'tavern': [
		...place('vineandbarrel:grape_crate', {}, [0, 0, 1]), ...place('vineandbarrel:hops_crate', {}, [0, 1, 1]), ...place('vineandbarrel:barley_bale', { axis: 'y' }, [0, 0, 2]),
		...rack(0, false, 2, 1), ...rack(1, true, 2, 1),
		...place('minecraft:campfire', { lit: 'true', facing: 'north' }, [4, 0, 2]),
		...place('vineandbarrel:brew_kettle', { ...KETTLE, support: 'tray' }, [4, 1, 2]),
		...place('minecraft:oak_planks', {}, [2, 0, 0]), ...table(['mug_full', 'mug_full', 'glass_full', 'none'], ['barley_ale', 'wheat_beer', 'red_wine', null], [2, 1, 0]),
		...place('minecraft:oak_planks', {}, [3, 0, 0]), ...table(['glass_full', 'none', 'mug', 'glass_full'], ['sweet_berry_wine', null, null, 'aged_red_wine'], [3, 1, 0]),
	],
	'storage': [...place('vineandbarrel:grape_crate'), ...place('vineandbarrel:white_grape_crate', {}, [1, 0, 0]), ...place('vineandbarrel:hops_crate', {}, [2, 0, 0]),
		...place('vineandbarrel:barley_bale', { axis: 'y' }, [3, 0, 0])],
	'wild-crops': [
		...[0, 1, 2, 3].flatMap((x) => place('minecraft:grass_block', {}, [x, 0, 0], GRASS)),
		...place('vineandbarrel:wild_grapes', {}, [0, 1, 0]), ...place('vineandbarrel:wild_white_grapes', {}, [1, 1, 0]), ...place('vineandbarrel:wild_hops', {}, [2, 1, 0]),
		...place('vineandbarrel:wild_barley', {}, [3, 1, 0]),
	],
	// A vineyard row of trellises three high, the bushes planted into their feet: ripe red grapes, half-ripe red grapes,
	// ripe hops, and a bare one waiting for a bush
	'trellis': [
		...[0, 1, 2, 3].flatMap((x) => place('minecraft:grass_block', {}, [x, 0, 0], GRASS)),
		...[['grape', '3', 0], ['grape', '2', 1], ['hop', '3', 2]].flatMap(([crop, age, x]) => [
			...place(`vineandbarrel:${crop}_bush`, { age, support: 'trellis_x', trellis: 'x' }, [x, 1, 0]),
			...place(`vineandbarrel:${crop}_vine_on_trellis`, { age, axis: 'x' }, [x, 2, 0]),
			...place(`vineandbarrel:${crop}_vine_on_trellis`, { age, axis: 'x' }, [x, 3, 0]),
		]),
		...[1, 2, 3].flatMap((y) => place('vineandbarrel:trellis', { axis: 'x' }, [3, y, 0])),
		// the frame the row draws by itself: end posts, the top rail and its caps
		...[1, 2, 3].flatMap((y) => [frame('post_left', [0, y, 0]), frame('post_right', [3, y, 0])]),
		...[0, 1, 2, 3].map((x) => frame('rail', [x, 3, 0])), frame('cap_left', [0, 3, 0]), frame('cap_right', [3, 3, 0]),
	],
	// Grapes and hops grown into an oak fence row and a mossy wall two high (the wiki shows the block they hold under the
	// leaves, as the game draws it): ripe red grapes, ripe hops, a bare post; ripe white grapes, half-ripe red, a bare wall
	'supports': [
		...[0, 1, 2, 3, 4, 5].flatMap((x) => place('minecraft:grass_block', {}, [x, 0, 0], GRASS)),
		...[0, 1, 2].flatMap((x) => place('minecraft:oak_fence', { east: String(x < 2), west: String(x > 0), north: 'false', south: 'false' }, [x, 1, 0])),
		...place('vineandbarrel:grape_vine_on_support', { age: '3', base: 'true', look: 'thin_x' }, [0, 1, 0]),
		...place('vineandbarrel:hop_vine_on_support', { age: '3', base: 'true', look: 'thin_x' }, [1, 1, 0]),
		...[3, 4, 5].flatMap((x) => [1, 2].flatMap((y) => place('minecraft:mossy_cobblestone_wall',
			{ east: x < 5 ? (y === 1 ? 'tall' : 'low') : 'none', west: x > 3 ? (y === 1 ? 'tall' : 'low') : 'none', north: 'none', south: 'none', up: String(x === 3 || x === 5) },
			[x, y, 0]))),
		...[['white_grape', '3', 3], ['grape', '2', 4]].flatMap(([crop, age, x]) => [
			...place(`vineandbarrel:${crop}_vine_on_support`, { age, base: 'true', look: 'wide_x' }, [x, 1, 0]),
			...place(`vineandbarrel:${crop}_vine_on_support`, { age, base: 'false', look: 'wide_x' }, [x, 2, 0]),
		]),
	],
	'garden': [
		...[0, 1, 2].flatMap((x) => place('minecraft:farmland', { moisture: '7' }, [x, 0, 1])),
		...[0, 1, 2].flatMap((x) => place('minecraft:grass_block', {}, [x, 0, 0], GRASS)),
		...['grape', 'hop', 'white_grape'].flatMap((crop, x) => place(`vineandbarrel:${crop}_bush`, { age: '3', support: 'none', trellis: 'none' }, [x, 1, 0])),
		...place('vineandbarrel:barley', { age: '7' }, [0, 1, 1]), ...place('vineandbarrel:barley', { age: '7' }, [1, 1, 1]), ...place('vineandbarrel:barley', { age: '5' }, [2, 1, 1]),
	],
};
for (const [name, parts] of Object.entries(scenes)) await save(`scenes/${name}`, parts, { size: 512 });

fs.writeFileSync(path.join(SITE, 'src/data/gen/renders.json'), JSON.stringify(manifest, null, '\t'));
console.log(`renders: ${Object.keys(manifest).length}`);
// Every named scene and stage must render (a skipped block default is reported above but doesn't fail)
const missing = failed.filter((n) => !n.startsWith('blocks/'));
if (missing.length) throw new Error(`scenes not rendered: ${missing.join(', ')}`);
