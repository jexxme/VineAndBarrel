// The link-preview pictures (lib/seo.ts), drawn at build time: /og/<key>.jpg. A shot is cropped to 1200×630; a render,
// a store board or an item's icon sits on the tavern's plank wall (the site's own wall-spruce texture, at the same 4×)
// with a soft shadow; the home page's is the brand sign over the title-screen panorama.
// The build runs in site/, so files are read from there (import.meta.url moves when bundled), and the store boards
// and the sign from the repository's branding/ folder.
import type { APIRoute } from 'astro';
import fs from 'node:fs';
import path from 'node:path';
import sharp from 'sharp';
import { allPictures, pictureKey, pictureSize, type Picture } from '../../lib/seo';

const file = (p: string) => fs.readFileSync(path.join(process.cwd(), p));
const { min, round } = Math;

export function getStaticPaths() {
	return allPictures().map((picture) => ({ params: { key: pictureKey(picture) }, props: { picture } }));
}

/** The plank wall, darker towards the edges. */
async function wall(width: number, height: number) {
	const tile = await sharp(file('src/assets/ui/wall-spruce.png')).resize(64, 64, { kernel: 'nearest' }).png().toBuffer();
	const vignette = Buffer.from(
		`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><defs><radialGradient id="v" cx="50%" cy="45%" r="75%">` +
			`<stop offset="0" stop-color="#000" stop-opacity="0.05"/><stop offset="1" stop-color="#000" stop-opacity="0.6"/></radialGradient></defs>` +
			`<rect width="100%" height="100%" fill="url(#v)"/></svg>`,
	);
	return sharp({ create: { width, height, channels: 4, background: '#2a1f14' } })
		.composite([{ input: tile, tile: true, top: 0, left: 0 }, { input: vignette }])
		.png()
		.toBuffer();
}

/** An image, scaled to fit (nearest-neighbour for whole multiples, so pixel art stays crisp), with a drop shadow. */
async function subject(src: Buffer, maxW: number, maxH: number) {
	const { width = 1, height = 1 } = await sharp(src).metadata();
	const fit = min(maxW / width, maxH / height);
	const whole = Math.floor(fit) >= 1 ? Math.floor(fit) : fit;
	const w = round(width * whole);
	const h = round(height * whole);
	const img = await sharp(src).resize(w, h, { kernel: Number.isInteger(whole) ? ('nearest' as const) : ('lanczos3' as const) }).png().toBuffer();
	// The shadow: the image's outline in black at half strength, blurred (sharp composites last, so blur separately)
	const pad = 24;
	const mask = await sharp(img).ensureAlpha().extractChannel(3).linear(0.55, 0).png().toBuffer();
	const black = await sharp({ create: { width: w, height: h, channels: 3, background: '#000' } }).joinChannel(mask).png().toBuffer();
	const clear = { r: 0, g: 0, b: 0, alpha: 0 };
	const spread = await sharp({ create: { width: w + pad * 2, height: h + pad * 2, channels: 4, background: clear } })
		.composite([{ input: black, top: pad, left: pad }])
		.png()
		.toBuffer();
	const shadow = await sharp(spread).blur(10).png().toBuffer();
	return { img, shadow, w, h, pad };
}

async function onWall(src: Buffer, width: number, height: number, margin: number) {
	const s = await subject(src, width - margin * 2, height - margin * 2);
	const left = round((width - s.w) / 2);
	const top = round((height - s.h) / 2);
	return sharp(await wall(width, height)).composite([
		{ input: s.shadow, left: left - s.pad + 6, top: top - s.pad + 10 },
		{ input: s.img, left, top },
	]);
}

async function draw(p: Picture): Promise<sharp.Sharp> {
	const { width, height } = pictureSize(p);
	if ('shot' in p) return sharp(file(`src/assets/shots/${p.shot}.jpg`)).resize(width, height, { fit: 'cover', position: 'centre' });
	if ('render' in p) return onWall(file(`public/renders/${p.render}.png`), width, height, 40);
	if ('board' in p) return onWall(file(`../branding/store/${p.board}.png`), width, height, 30);
	if ('item' in p) {
		// A block shows its render (as on its page), anything else its inventory icon at a whole multiple
		const [ns, id] = p.item.split(':');
		const render = `public/renders/blocks/${id}.png`;
		if (ns === 'vineandbarrel' && fs.existsSync(path.join(process.cwd(), render))) return onWall(file(render), width, height, 40);
		return onWall(file(`public/icons/${ns}/${id}.png`), width, height, 100);
	}
	// The brand sign over the panorama, dimmed so the sign reads
	const panorama = await sharp(file('src/assets/shots/hero.jpg')).resize(width, height, { fit: 'cover' }).modulate({ brightness: 0.62 }).blur(1.5).png().toBuffer();
	const sign = await sharp(file('../branding/store/hero.png')).resize({ width: width - 200, height: height - 110, fit: 'inside', kernel: 'lanczos3' }).png().toBuffer();
	const { width: sw = 0, height: sh = 0 } = await sharp(sign).metadata();
	return sharp(panorama).composite([{ input: sign, left: round((width - sw) / 2), top: round((height - sh) / 2) }]);
}

export const GET: APIRoute = async ({ props }) => {
	const image = await (await draw((props as { picture: Picture }).picture)).flatten({ background: '#17140f' }).jpeg({ quality: 88, chromaSubsampling: '4:4:4', mozjpeg: true }).toBuffer();
	return new Response(new Uint8Array(image), { headers: { 'Content-Type': 'image/jpeg' } });
};
