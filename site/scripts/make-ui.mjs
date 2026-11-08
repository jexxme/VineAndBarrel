// UI textures for the site, drawn the same way branding/make_branding.py draws the logo's signs:
// checkered sign paper, a 1 px frame with a light inner edge, cut corners and an opaque 1 px drop shadow.
// Also copies the trimmed banner and icon (branding/) and a few vanilla textures (buttons, planks).
// Output: src/assets/ui/*.png (the stylesheets use them, so they get the site's base path at build time)
import fs from 'node:fs';
import path from 'node:path';
import sharp from 'sharp';
import { REPO, readAsset } from './lib/assets.mjs';

const SITE = path.resolve(import.meta.dirname, '..');
const OUT = path.join(SITE, 'src/assets/ui');
fs.mkdirSync(OUT, { recursive: true });

// The FD Neoforged docs' sign palette (make_branding.py comments there); the wiki shares that look. Its orange "Neoforged"
// sign is wine red here (the Vine & Barrel wordmark's colour); the file keeps FDN's name so the stylesheets stay the same.
const PALETTES = {
	sign: { a: [206, 181, 151], b: [212, 190, 164], edge: [195, 168, 136], light: [177, 142, 101], frame: [72, 48, 21] },
	orange: { a: [176, 48, 64], b: [168, 40, 58], edge: [142, 26, 46], light: [207, 66, 89], frame: [46, 7, 16] },
	dark: { a: [52, 36, 22], b: [58, 41, 26], edge: [70, 50, 31], light: [96, 68, 40], frame: [20, 13, 7] },
};

/** A 9-slice sign: w x h including the 1 px shadow on the right and bottom. */
async function sign(name, pal, { w = 10, h = 10, shadow = true } = {}) {
	const W = w + (shadow ? 1 : 0);
	const H = h + (shadow ? 1 : 0);
	const buf = Buffer.alloc(W * H * 4);
	const set = (x, y, c, a = 255) => {
		const i = (y * W + x) * 4;
		buf[i] = c[0], buf[i + 1] = c[1], buf[i + 2] = c[2], buf[i + 3] = a;
	};
	if (shadow) {
		for (let x = 1; x <= w; x++) for (let y = 1; y <= h; y++) if (!((x === 1 || x === w) && (y === 1 || y === h))) set(x, y, [0, 0, 0]);
	}
	for (let x = 0; x < w; x++) {
		for (let y = 0; y < h; y++) {
			const corner = (x === 0 || x === w - 1) && (y === 0 || y === h - 1);
			if (corner) continue;
			if (x === 0 || x === w - 1 || y === 0 || y === h - 1) set(x, y, pal.frame);
			else if (x === 1 || x === w - 2 || y === 1 || y === h - 2) set(x, y, (x + y) % 2 ? pal.light : pal.edge);
			else set(x, y, (x + y) % 2 ? pal.a : pal.b);
		}
	}
	await sharp(buf, { raw: { width: W, height: H, channels: 4 } }).png().toFile(path.join(OUT, `${name}.png`));
}
/** A 2x2 checker tile of the sign paper, for large paper backgrounds. */
async function checker(name, pal) {
	const c = [pal.a, pal.b, pal.b, pal.a].flatMap((x) => [...x, 255]);
	await sharp(Buffer.from(c), { raw: { width: 2, height: 2, channels: 4 } }).png().toFile(path.join(OUT, `${name}.png`));
}

for (const [k, pal] of Object.entries(PALETTES)) {
	await sign(k === 'sign' ? 'sign' : `sign-${k}`, pal);
	await sign(k === 'sign' ? 'sign-flat' : `sign-${k}-flat`, pal, { shadow: false });
	await checker(k === 'sign' ? 'paper' : `paper-${k}`, pal);
}

// The banner and icon (branding/), trimmed to their content; the icon is also the favicon
await sharp(path.join(REPO, 'branding/banner.png')).trim({ threshold: 0 }).png().toFile(path.join(OUT, 'banner.png'));
await sharp(path.join(REPO, 'branding/icon.png')).trim({ threshold: 0 }).png().toFile(path.join(OUT, 'icon.png'));
await sharp(path.join(REPO, 'branding/icon.png')).resize(64, 64, { kernel: 'nearest' }).png().toFile(path.join(SITE, 'public/favicon.png'));

// Vanilla textures: buttons (9-slice, 200x20) and planks for wooden surfaces
const copy = {
	'button.png': 'assets/minecraft/textures/gui/sprites/widget/button.png',
	'button-hover.png': 'assets/minecraft/textures/gui/sprites/widget/button_highlighted.png',
	'oak-planks.png': 'assets/minecraft/textures/block/oak_planks.png',
	'spruce-planks.png': 'assets/minecraft/textures/block/spruce_planks.png',
	'dark-oak-planks.png': 'assets/minecraft/textures/block/dark_oak_planks.png',
	'stripped-oak.png': 'assets/minecraft/textures/block/stripped_oak_log.png',
};
for (const [to, from] of Object.entries(copy)) {
	const b = readAsset(from);
	if (b) fs.writeFileSync(path.join(OUT, to), b);
	else console.log('missing', from);
}
// The header: the Aging Barrel's own staves and iron hoops (the mod's art), a little darker so the logo stands out
await sharp(path.join(REPO, 'src/main/resources/assets/vineandbarrel/textures/block/aging_barrel_side_rotated.png'))
	.modulate({ brightness: 0.82 }).png().toFile(path.join(OUT, 'barrel-staves.png'));

console.log('ui:', fs.readdirSync(OUT).join(' '));
