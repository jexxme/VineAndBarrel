// Turns in-game screenshots (neoforge/run/screenshots/wiki/*.png, taken by the maintainer's dev tooling, which isn't part of
// this repository) into the wiki's JPGs in src/assets/shots (Astro serves them as AVIF/WebP in several widths).
import fs from 'node:fs';
import path from 'node:path';
import sharp from 'sharp';
import { REPO } from './lib/assets.mjs';

const from = path.join(REPO, 'neoforge/run/screenshots/wiki');
const to = path.resolve(import.meta.dirname, '../src/assets/shots');
fs.mkdirSync(to, { recursive: true });
const pngs = fs.existsSync(from) ? fs.readdirSync(from).filter((f) => f.endsWith('.png')) : [];
if (!pngs.length) throw new Error(`No screenshots in ${from}; take the screenshots first`);
for (const f of pngs) {
	const out = path.join(to, f.replace(/\.png$/, '.jpg'));
	await sharp(path.join(from, f)).jpeg({ quality: 86, mozjpeg: true }).toFile(out);
	console.log(`${f} → ${path.relative(path.resolve(import.meta.dirname, '..'), out)} (${Math.round(fs.statSync(out).size / 1024)} KB)`);
}
