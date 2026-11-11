// UI textures for the site: the tavern's signboards, chalkboards, notes, oak buttons and walls (pixel art drawn here,
// in the same language as the logo in branding/: 1 px outlines, light from the top left, an opaque 1 px drop
// shadow), the logo files from branding/, and the vanilla stone buttons for Minecraft GUIs.
// Output: src/assets/ui/*.png (the stylesheets use them, so they get the site's base path at build time)
import fs from 'node:fs';
import path from 'node:path';
import sharp from 'sharp';
import { REPO, readAsset } from './lib/assets.mjs';

const SITE = path.resolve(import.meta.dirname, '..');
const OUT = path.join(SITE, 'src/assets/ui');
fs.mkdirSync(OUT, { recursive: true });

// The logo (branding/*.png): the banner at 1x, trimmed (the home page shows it at whole multiples only),
// the one-line header lockup at 1x (shown at 2x), and the 16 px mark as the favicon at exactly 4x
await sharp(path.join(REPO, 'common/src/main/resources/banner.png')).trim({ threshold: 0 }).png().toFile(path.join(OUT, 'banner.png'));
await sharp(path.join(REPO, 'branding/header.png')).png().toFile(path.join(OUT, 'header.png'));
await sharp(path.join(REPO, 'branding/mark.png')).resize(64, 64, { kernel: 'nearest' }).png().toFile(path.join(SITE, 'public/favicon.png'));

// Vanilla stone buttons (9-slice, 200x20), for controls inside Minecraft GUIs such as the search dialog
const copy = {
	'button.png': 'assets/minecraft/textures/gui/sprites/widget/button.png',
	'button-hover.png': 'assets/minecraft/textures/gui/sprites/widget/button_highlighted.png',
};
for (const [to, from] of Object.entries(copy)) {
	const b = readAsset(from);
	if (b) fs.writeFileSync(path.join(OUT, to), b);
	else console.log('missing', from);
}

// ---------------------------------------------------------------- the tavern
// Vine & Barrel's own look: an evening in the village tavern. Hanging signboards with gilt rims, chalkboard menus in
// oak frames, pinned parchment notes, oak buttons, the plank wall behind it all. Same pixel rules as the signs above:
// 1 px outline, light from the top left, an opaque 1 px drop shadow baked in.
const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
/** Paints a w x h tile (plus the shadow) pixel by pixel: fn(x, y, d) returns a colour or null; d = distance to the edge. */
async function paint(name, w, h, fn, { shadow = true, cut = true } = {}) {
	const W = w + (shadow ? 1 : 0);
	const H = h + (shadow ? 1 : 0);
	const buf = Buffer.alloc(W * H * 4);
	const put = (x, y, c, a = 255) => {
		const i = (y * W + x) * 4;
		buf[i] = c[0], buf[i + 1] = c[1], buf[i + 2] = c[2], buf[i + 3] = a;
	};
	const corner = (x, y) => cut && (x === 0 || x === w - 1) && (y === 0 || y === h - 1);
	if (shadow) for (let x = 1; x <= w; x++) for (let y = 1; y <= h; y++) if (!corner(x - 1, y - 1)) put(x, y, [0, 0, 0]);
	for (let y = 0; y < h; y++) {
		for (let x = 0; x < w; x++) {
			if (corner(x, y)) continue;
			const c = fn(x, y, Math.min(x, y, w - 1 - x, h - 1 - y));
			if (c) put(x, y, typeof c === 'string' ? hex(c) : c);
		}
	}
	await sharp(buf, { raw: { width: W, height: H, channels: 4 } }).png().toFile(path.join(OUT, `${name}.png`));
}
const T = {
	line: '#1a0f07',
	gilt: { hl: '#fff0a8', light: '#f6d36a', mid: '#e2b240', dark: '#a77a22' },
	oak: { hl: '#e3bd84', light: '#c69a5e', mid: '#a67b45', dark: '#835d31', seam: '#5a3e1f' },
	board: ['#4a3018', '#553820', '#4f341b'],
	seam: '#36230f',
	slate: ['#2c2a27', '#302d2a'],
	slateEdge: '#171513',
	paper: ['#efe3c8', '#ebdec1'],
	paperEdge: '#d9c6a0',
	paperRim: '#a8875a',
};
// Hanging signboard: dark oak planks, a gilt rim lit from the top left (14 x 14, 9-slice 4 / 5)
await paint('board', 14, 14, (x, y, d) => {
	if (d === 0) return T.line;
	if (d === 1) return x === 1 || y === 1 ? ((x + y) % 2 ? T.gilt.light : T.gilt.hl) : T.gilt.dark;
	if (d === 2) return T.line;
	if ((y - 3) % 4 === 3) return T.seam;
	return (x * 5 + y * 3) % 7 === 0 ? T.board[2] : T.board[Math.floor((y - 3) / 4) % 2];
});
// The same board without the gilt (pagination, small plaques)
await paint('board-plain', 12, 12, (x, y, d) => {
	if (d === 0) return T.line;
	if (d === 1) return x === 1 || y === 1 ? T.oak.dark : T.seam;
	if ((y - 2) % 4 === 3) return T.seam;
	return T.board[(x * 5 + y * 3) % 7 === 0 ? 2 : (Math.floor((y - 2) / 4) % 2)];
});
// Chalkboard: warm slate in a 2 px oak frame (14 x 14, 9-slice 4 / 5)
await paint('chalkboard', 14, 14, (x, y, d) => {
	if (d === 0) return T.line;
	if (d === 1) return x === 1 || y === 1 ? T.oak.light : T.oak.dark;
	if (d === 2) return x === 2 || y === 2 ? T.oak.mid : T.oak.dark;
	if (d === 3) return T.slateEdge;
	return T.slate[(x + y) % 2];
});
// Parchment note, pinned to the wall (the nail is drawn in CSS) and a wine-red one for warnings (12 x 12, 9-slice 3 / 4)
await paint('note', 12, 12, (x, y, d) => (d === 0 ? T.paperRim : d === 1 ? T.paperEdge : T.paper[(x + y) % 2]), { cut: true });
await paint('note-wine', 12, 12, (x, y, d) => (d === 0 ? '#5a0f1e' : d === 1 ? '#c98b8b' : ['#f3e1d6', '#efdbcf'][(x + y) % 2]));
// Oak buttons (vanilla widget layout: 1 px outline, light top-left edge, 2 px dark bottom), normal / hover / pressed
async function oakButton(name, { hl, a, b, dk, outline }) {
	await paint(name, 20, 20, (x, y, d) => {
		if (d === 0) return outline ?? T.line;
		if (y >= 17) return dk;
		if (x === 18) return dk;
		if (x === 1 || y === 1) return hl;
		return (x + y) % 2 ? a : b;
	}, { shadow: false, cut: false });
}
await oakButton('button-oak', { hl: T.oak.light, a: '#6b4a2b', b: '#664628', dk: '#3a2612' });
await oakButton('button-oak-hover', { hl: T.oak.hl, a: '#835d31', b: '#7d592f', dk: '#4a3018', outline: T.gilt.light });
await oakButton('button-oak-on', { hl: T.gilt.light, a: '#8e1a2e', b: '#871828', dk: '#5a0f1e', outline: T.line });
// Walls: the spruce plank wall at night (boards stood upright), whitewashed plaster by day
async function wall(name, from, { rotate = 0, mul, add = [0, 0, 0] }) {
	const { data, info } = await sharp(readAsset(from)).extract({ left: 0, top: 0, width: 16, height: 16 }).rotate(rotate).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
	for (let i = 0; i < data.length; i += 4) for (let k = 0; k < 3; k++) data[i + k] = Math.min(255, Math.round(data[i + k] * mul[k] + add[k]));
	await sharp(data, { raw: { width: info.width, height: info.height, channels: 4 } }).png().toFile(path.join(OUT, `${name}.png`));
}
await wall('wall-spruce', 'assets/minecraft/textures/block/spruce_planks.png', { rotate: 90, mul: [0.4, 0.31, 0.27] });
await wall('wall-plaster', 'assets/minecraft/textures/block/calcite.png', { mul: [1.0, 0.96, 0.87], add: [6, 4, 0] });
await wall('beam', 'assets/minecraft/textures/block/dark_oak_planks.png', { mul: [0.95, 0.9, 0.88] });

console.log('ui:', fs.readdirSync(OUT).join(' '));
