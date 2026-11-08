// Scans the built site for rendering leftovers: undefined/NaN, "[object Object]", unrendered {expressions}, TODO.
// Run after `npm run build`; exits 1 if any page has one.
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '../dist');
const files = [];
const walk = (d) => fs.readdirSync(d, { withFileTypes: true }).forEach((e) => (e.isDirectory() ? walk(path.join(d, e.name)) : e.name.endsWith('.html') && files.push(path.join(d, e.name))));
walk(root);
let bad = 0;
for (const f of files) {
	const text = fs
		.readFileSync(f, 'utf8')
		.replace(/<script[\s\S]*?<\/script>/g, '')
		.replace(/<style[\s\S]*?<\/style>/g, '')
		.replace(/<pre[\s\S]*?<\/pre>/g, '')
		.replace(/<[^>]+>/g, ' ');
	const hits = [];
	for (const re of [/\bundefined\b/g, /\bNaN\b/g, /\[object Object\]/g, /\{[a-zA-Z_][\w.]*\}/g, /\bTODO\b/g]) {
		const m = text.match(re);
		if (m) hits.push(`${m[0]} ×${m.length}`);
	}
	if (hits.length) {
		bad++;
		console.log(path.relative(root, f).replaceAll('\\', '/'), '→', hits.join(', '));
	}
}
console.log(`${files.length} pages, ${bad} with leftovers`);

// Internal links and images: every href/src under the base path must exist in dist
const BASE = (process.env.BASE_PATH ?? '/vine-and-barrel').replace(/\/$/, '');
const broken = new Map();
for (const f of files) {
	const html = fs.readFileSync(f, 'utf8');
	for (const m of html.matchAll(/(?:href|src)="([^"#?]+)(?:[#?][^"]*)?"/g)) {
		const u = m[1];
		if (!u.startsWith('/') || u.startsWith('//')) continue;
		let target;
		if (!u.startsWith(BASE + '/') && u !== BASE) target = null;
		else {
			const rel = decodeURIComponent(u.slice(BASE.length)) || '/';
			const p = path.join(root, rel);
			target = fs.existsSync(p) && fs.statSync(p).isFile() ? p : fs.existsSync(path.join(p, 'index.html')) ? p : null;
		}
		if (!target) {
			const k = `${u}`;
			if (!broken.has(k)) broken.set(k, new Set());
			broken.get(k).add(path.relative(root, f).replaceAll('\\', '/'));
		}
	}
}
for (const [u, pages] of broken) console.log('broken link', u, '←', [...pages].slice(0, 3).join(', '), pages.size > 3 ? `(+${pages.size - 3})` : '');
console.log(`${broken.size} broken internal links`);
process.exit(bad || broken.size ? 1 : 0);
