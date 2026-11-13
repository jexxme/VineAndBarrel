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

/**
 * The leaves of a vine on a fence, wall, bars or chain, as the game adds them from the block it holds (SupportLeaves):
 * on each arm that reaches a neighbour and on the post's faces; crossed planes through a chain. kind: fence, wall, bars
 * or chain; sides: the held block's north/east/south/west (and up for a wall) as in its block state.
 */
function supportLeaves(crop, kind, sides, base, age, offset) {
	const model = (piece) => `vineandbarrel:block/${crop}_vine_on_support_${piece}${base ? '_base' : ''}_stage${age}`;
	const out = [];
	const add = (piece, turn) => out.push({ model: model(piece), y: turn ? turn * 90 : undefined, offset });
	const arms = ['west', 'north', 'east', 'south'];
	if (kind === 'chain') {
		add('cross', 0);
	} else if (kind === 'wall') {
		let any = false;
		arms.forEach((side, turn) => {
			const v = sides[side] ?? 'none';
			if (v !== 'none') { add(v === 'tall' ? 'wall_arm_tall' : 'wall_arm_low', turn); any = true; }
		});
		if (sides.up === 'true' || !any) { add('wall_post', 0); add('wall_post', 1); }
	} else {
		let alongX = false, alongZ = false;
		arms.forEach((side, turn) => {
			if (sides[side] === 'true') {
				add(kind === 'fence' ? 'fence_arm' : 'bars_arm', turn);
				if (side === 'west' || side === 'east') alongX = true; else alongZ = true;
			}
		});
		const lone = !alongX && !alongZ;
		if (kind === 'fence') {
			if (alongX || lone) add('fence_post', 0);
			if (alongZ || lone) add('fence_post', 1);
		} else if (lone) { add('bars_post', 0); add('bars_post', 1); }
	}
	return out;
}

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

// A cauldron of a base, full, in the base's colour (barley_wort_cauldron: the barley_wort_bottle's colour)
const cauldronLiquid = (slug) => {
	const base = `vineandbarrel:${slug.replace(/_cauldron$/, '')}`;
	return liquid(base in runtime.rules.kettleLiquidColors ? base : `${base}_bottle`);
};

// Every block in its default look (waxed kettles look like their unwaxed stage; a cauldron of a base full and tinted)
const KETTLE = { facing: 'north', open: 'false', fill: '0', support: 'none', waterlogged: 'false', extended: 'false' };
const BARREL = { facing: 'north', bung: 'cork', fill: '0', left: 'false', right: 'false', stacked: 'false', tapping: 'false' };
for (const [id, b] of Object.entries(runtime.blocks)) {
	const slug = id.split(':')[1];
	// vines on a rope or a fence, wall, chain or bars hold a block the wiki can't show on its own (FD's rope, the block kept
	// inside); big kettles' blocks and chimney pieces aren't blocks you hold: they're shown in the kettle scenes
	if (slug.startsWith('waxed_') || slug === 'placed_drinks' || slug.endsWith('_on_rope') || slug.endsWith('_on_support')
		|| slug.endsWith('_part') || slug.endsWith('_chimney')) continue;
	const state = { ...b.defaultState, ...facingNorth(id) };
	if ('age' in state) state.age = String(Math.max(...ageValues(id).map(Number)));
	const cauldron = slug.endsWith('_cauldron');
	if (cauldron) state.level = String(Math.max(...ageValues(id, 'level').map(Number)));
	await save(`blocks/${slug}`, place(id, state, [0, 0, 0], cauldron ? [cauldronLiquid(slug)] : undefined));
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

// A big kettle (size 2 or 3) on lit campfires, its sight glass at a level, its chimney grown by some pieces. Its blocks
// are the kettle's parts as the mod numbers them (KettlePart: the Big Kettle's 8, then the Great Kettle's 27, x, y, z order)
function bigKettle(size, [ox, oy, oz], { level = 0, chimney = 0, tint } = {}) {
	const parts = [];
	const first = size === 3 ? 8 : 0;
	const c = size === 3 ? 1 : 0;
	for (let x = 0; x < size; x++) for (let y = 0; y < size; y++) for (let z = 0; z < size; z++) {
		const id = first + x * size * size + y * size + z;
		const state = { facing: 'north', part: String(id), tray: String(y === 0), level: String(level), extended: String(chimney > 0 && x === c && z === c && y === size - 1) };
		parts.push(...place('vineandbarrel:brew_kettle_part', state, [ox + x, oy + y + 1, oz + z], tint ? [tint] : undefined));
	}
	for (let x = 0; x < size; x++) for (let z = 0; z < size; z++) parts.push(...place('minecraft:campfire', { lit: 'true', facing: 'north' }, [ox + x, oy, oz + z]));
	for (let i = 1; i <= chimney; i++) parts.push(...place('vineandbarrel:brew_kettle_chimney', { facing: 'north', size: String(size), top: String(i === chimney), flashing: 'false', through: '0', rising: 'false' }, [ox + c, oy + size + i, oz + c]));
	return parts;
}

// Scenes for the pages
const scenes = {
	'kettle-on-magma': [...place('minecraft:magma_block'), ...place('vineandbarrel:brew_kettle', KETTLE, [0, 1, 0])],
	'kettle-on-hopper': [...place('minecraft:magma_block'), ...place('minecraft:hopper', { facing: 'down' }, [0, 1, 0]), ...place('vineandbarrel:brew_kettle', KETTLE, [0, 2, 0])],
	'kettle-on-campfire': [...place('minecraft:campfire', { lit: 'true', facing: 'north' }), ...place('vineandbarrel:brew_kettle', { ...KETTLE, support: 'tray' }, [0, 1, 0])],
	'kettle-hanging': [...place('minecraft:oak_planks', {}, [0, 2, 0]), ...place('minecraft:iron_chain', { axis: 'y' }, [0, 1, 0]), ...place('vineandbarrel:brew_kettle', { ...KETTLE, support: 'handle' }), ...place('minecraft:campfire', { lit: 'true', facing: 'north' }, [0, -1, 0])],
	// The lid lifted (screen open): the brew inside in its colour, the sight glass beside the tap
	'kettle-open': [...place('minecraft:campfire', { lit: 'true', facing: 'north' }), ...place('vineandbarrel:brew_kettle', { ...KETTLE, support: 'tray', open: 'true', fill: '3' }, [0, 1, 0], [liquid('vineandbarrel:grape_juice')])],
	// WP4: the Big Kettle, beside a plain one, with a taller chimney, a chimney grown through a roof
	'kettle-big': bigKettle(2, [0, 0, 0], { level: 2, tint: liquid('vineandbarrel:barley_wort_bottle') }),
	'kettle-big-chimney': bigKettle(2, [0, 0, 0], { level: 3, chimney: 2, tint: liquid('vineandbarrel:barley_wort_bottle') }),
	'kettle-family': [...bigKettle(2, [0, 0, 0], { level: 3, tint: liquid('vineandbarrel:barley_wort_bottle') }),
		...place('minecraft:campfire', { lit: 'true', facing: 'north' }, [2.75, 0, 0]), ...place('vineandbarrel:brew_kettle', { ...KETTLE, support: 'tray', fill: '2' }, [2.75, 1, 0], [liquid('vineandbarrel:barley_wort_bottle')])],
	'kettle-chimney': [...place('minecraft:campfire', { lit: 'true', facing: 'north' }), ...place('vineandbarrel:brew_kettle', { ...KETTLE, support: 'tray', extended: 'true' }, [0, 1, 0]),
		// the roof stays whole: the piece on top draws its pipe through the plank under it
		...[2, 4].flatMap((y) => place('vineandbarrel:brew_kettle_chimney', { facing: 'north', size: '1', top: String(y === 4), flashing: 'false', through: String(y === 4 ? 1 : 0), rising: 'false' }, [0, y, 0])),
		...[-1, 0, 1].flatMap((x) => [-1, 0, 1].flatMap((z) => place('minecraft:spruce_planks', {}, [x, 3, z])))],
	'barrel-rack': rack(0, false),
	'barrel-cellar': [...rack(0, false), ...rack(1, true)],
	'placed-drinks': [...place('minecraft:oak_planks'), ...table(['mug_full', 'glass_full', 'glass', 'mug_full'], ['barley_ale', 'red_wine', null, 'wheat_beer'], [0, 1, 0])],
	// Our bases in cauldrons: honey must and grape juice full, barley wort three servings (the front one is grape juice,
	// full, so the rim doesn't hide it)
	'cauldrons': [['honey_must', '4'], ['barley_wort', '3'], ['grape_juice', '4']].flatMap(([base, level], x) =>
		place(`vineandbarrel:${base}_cauldron`, { level }, [x, 0, 0], [cauldronLiquid(`${base}_cauldron`)])),
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
	// Grapes and hops grown into an oak fence row and a mossy wall two high, the leaves following the block they hold:
	// ripe red grapes, ripe hops, a bare post; ripe white grapes, half-ripe red, a bare wall
	'supports': (() => {
		const fence = (x) => ({ east: String(x < 2), west: String(x > 0), north: 'false', south: 'false' });
		const wall = (x, y) => ({ east: x < 5 ? (y === 1 ? 'tall' : 'low') : 'none', west: x > 3 ? (y === 1 ? 'tall' : 'low') : 'none', north: 'none', south: 'none', up: String(x === 3 || x === 5) });
		return [
			...[0, 1, 2, 3, 4, 5].flatMap((x) => place('minecraft:grass_block', {}, [x, 0, 0], GRASS)),
			...[0, 1, 2].flatMap((x) => place('minecraft:oak_fence', fence(x), [x, 1, 0])),
			...supportLeaves('grape', 'fence', fence(0), true, 3, [0, 1, 0]),
			...supportLeaves('hop', 'fence', fence(1), true, 3, [1, 1, 0]),
			...[3, 4, 5].flatMap((x) => [1, 2].flatMap((y) => place('minecraft:mossy_cobblestone_wall', wall(x, y), [x, y, 0]))),
			...[['white_grape', 3, 3], ['grape', 2, 4]].flatMap(([crop, age, x]) => [
				...supportLeaves(crop, 'wall', wall(x, 1), true, age, [x, 1, 0]),
				...supportLeaves(crop, 'wall', wall(x, 2), false, age, [x, 2, 0]),
			]),
		];
	})(),
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
