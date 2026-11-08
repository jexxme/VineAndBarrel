// Copies the GUI textures the recipe cards, the kettle screen and the effect badges use: Vine & Barrel's own kettle
// screen and Tipsy icon, plus a few vanilla GUI sprites (furnace arrow, hunger icons, effect icons).
// Output: public/gui/**.png
import fs from 'node:fs';
import path from 'node:path';
import { readAsset } from './lib/assets.mjs';

const SITE = path.resolve(import.meta.dirname, '..');
const OUT = path.join(SITE, 'public/gui');
const files = {
	'brew_kettle.png': 'assets/vineandbarrel/textures/gui/brew_kettle.png',
	'effect/tipsy.png': 'assets/vineandbarrel/textures/mob_effect/tipsy.png',
	'mc/burn_progress.png': 'assets/minecraft/textures/gui/sprites/container/furnace/burn_progress.png',
	'mc/lit_progress.png': 'assets/minecraft/textures/gui/sprites/container/furnace/lit_progress.png',
	'mc/food_full.png': 'assets/minecraft/textures/gui/sprites/hud/food_full.png',
	'mc/food_half.png': 'assets/minecraft/textures/gui/sprites/hud/food_half.png',
	'mc/food_empty.png': 'assets/minecraft/textures/gui/sprites/hud/food_empty.png',
	'mc/trade_arrow.png': 'assets/minecraft/textures/gui/sprites/container/villager/trade_arrow.png',
};
for (const e of ['regeneration', 'speed', 'haste', 'saturation', 'absorption', 'fire_resistance', 'slowness', 'mining_fatigue', 'nausea']) {
	files[`effect/${e}.png`] = `assets/minecraft/textures/mob_effect/${e}.png`;
}
let n = 0;
for (const [to, from] of Object.entries(files)) {
	const buf = readAsset(from);
	if (!buf) {
		console.log('missing', from);
		continue;
	}
	fs.mkdirSync(path.dirname(path.join(OUT, to)), { recursive: true });
	fs.writeFileSync(path.join(OUT, to), buf);
	n++;
}
console.log(`gui: ${n} files`);
