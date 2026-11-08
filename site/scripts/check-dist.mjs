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

// Internal links and images: every href/src (and <option value> link) under the base path must exist in dist
const BASE = (process.env.BASE_PATH ?? '/vine-and-barrel').replace(/\/$/, '');
const broken = new Map();
for (const f of files) {
	const html = fs.readFileSync(f, 'utf8');
	// href/src, and the language picker's <option value="/…">
	for (const m of html.matchAll(/(?:href|src|<option[^>]*?\svalue)="([^"#?]+)(?:[#?][^"]*)?"/g)) {
		const u = m[1];
		if (!u.startsWith('/') || u.startsWith('//')) continue;
		let target;
		if (!u.startsWith(BASE + '/') && u !== BASE) target = null;
		else {
			const rel = decodeURIComponent(u.slice(BASE.length)) || '/';
			const p = path.join(root, rel);
			target = fs.existsSync(p) && fs.statSync(p).isFile() ? p : fs.existsSync(path.join(p, 'index.html')) ? p : null;
			// The 404 page's language picker links to /404/ and /de/404/: Pages answers any missing path with 404.html
			if (!target && /^\/([\w-]+\/)?404\/$/.test(rel) && fs.existsSync(path.join(root, '404.html'))) target = p;
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

// Anchors: a link to "/page/#section" needs an element with id="section" on that page (translated pages map the
// English anchors to their own headings; this catches a heading that drifted)
const ids = new Map();
const idsOf = (file) => {
	if (!ids.has(file)) ids.set(file, new Set([...fs.readFileSync(file, 'utf8').matchAll(/\sid="([^"]+)"/g)].map((m) => m[1])));
	return ids.get(file);
};
const missing = new Map();
for (const f of files) {
	const html = fs.readFileSync(f, 'utf8');
	for (const m of html.matchAll(/href="([^"#?]*)#([^"]+)"/g)) {
		const [, u, frag] = m;
		let target;
		if (u === '') target = f;
		else if (u.startsWith(BASE + '/')) {
			const p = path.join(root, decodeURIComponent(u.slice(BASE.length)));
			target = fs.existsSync(path.join(p, 'index.html')) ? path.join(p, 'index.html') : null;
		} else continue;
		if (!target || /^_?top$/.test(frag)) continue;
		const id = decodeURIComponent(frag.replace(/&amp;/g, '&'));
		if (!idsOf(target).has(id)) {
			const k = `${u}#${id}`;
			if (!missing.has(k)) missing.set(k, new Set());
			missing.get(k).add(path.relative(root, f).replaceAll('\\', '/'));
		}
	}
}
for (const [u, pages] of missing) console.log('missing anchor', u, '←', [...pages].slice(0, 3).join(', '), pages.size > 3 ? `(+${pages.size - 3})` : '');
console.log(`${missing.size} missing anchors`);
// Search and link previews (src/lib/seo.ts): every page has a title, a description, a canonical URL and a preview
// picture that was built, and its structured data parses. Redirect stubs and the 404 page don't count.
const seo = [];
for (const f of files) {
	const html = fs.readFileSync(f, 'utf8');
	const rel = path.relative(root, f).replaceAll('\\', '/');
	if (/http-equiv="refresh"/.test(html) || /(^|\/)404(\/index)?\.html$/.test(rel)) continue;
	const head = html.slice(0, html.indexOf('</head>'));
	const problems = [];
	if (!/<title>[^<]+<\/title>/.test(head)) problems.push('no title');
	if (!/<meta name="description" content="[^"]+"/.test(head)) problems.push('no description');
	if (!/<link rel="canonical" href="https:\/\/[^"]+"/.test(head)) problems.push('no canonical');
	const og = head.match(/<meta property="og:image" content="([^"]+)"/)?.[1];
	if (!og) problems.push('no og:image');
	else if (!fs.existsSync(path.join(root, decodeURIComponent(new URL(og).pathname.slice(BASE.length))))) problems.push(`og:image not built: ${og}`);
	for (const m of head.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)) {
		try {
			JSON.parse(m[1]);
		} catch {
			problems.push('JSON-LD does not parse');
		}
	}
	if (problems.length) seo.push(`${rel} → ${problems.join(', ')}`);
}
for (const s of seo.slice(0, 20)) console.log('seo', s);
console.log(`${seo.length} pages with missing search metadata`);
process.exit(bad || broken.size || missing.size || seo.length ? 1 : 0);
