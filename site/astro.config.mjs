// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import preact from '@astrojs/preact';
import { visit } from 'unist-util-visit';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sitemap from '@astrojs/sitemap';
import { translateSidebar } from './src/i18n/sidebar.mjs';
import { LOCALES } from './src/i18n/locales.mjs';

// GitHub Pages project site. The repository name isn't decided yet, so the base path is configurable:
// BASE_PATH=/<repo-name> (the Pages workflow passes it), SITE_URL for the host. Locally it defaults to /vine-and-barrel.
// REPO_URL (e.g. https://github.com/<owner>/<repo>) adds the GitHub button and "edit page" links once the repo exists.
const site = process.env.SITE_URL ?? 'https://jexxme.github.io';
const base = (process.env.BASE_PATH ?? '/vine-and-barrel').replace(/\/$/, '');
const repo = process.env.REPO_URL?.replace(/\/$/, '');
const branch = process.env.REPO_BRANCH ?? '26.3';

// The translated wiki: each language of src/i18n/locales.mjs switches on once src/content/docs/<code>/ exists.
const locales = LOCALES.filter((l) => fs.existsSync(new URL(`./src/content/docs/${l.code}/`, import.meta.url)));
const codes = locales.map((l) => l.code);
const codeGroup = `(${codes.join('|') || '-'})`;
const localeDir = new RegExp(String.raw`[\\/]content[\\/]docs[\\/]` + codeGroup + String.raw`[\\/]`);
const localePath = new RegExp(`^/${codeGroup}/`);

/**
 * Markdown links written as "/mechanics/..." get the base path, so pages can link with plain site paths. On a
 * translated page, links to other pages also get its language ("/tipsy/" → "/de/tipsy/"); files (/clips/x.webm) don't.
 */
function rehypeBaseLinks() {
	return (tree, file) => {
		const loc = localeDir.exec(file?.path ?? '')?.[1];
		visit(tree, 'element', (node) => {
			for (const attr of ['href', 'src']) {
				let v = node.properties?.[attr];
				if (typeof v === 'string' && v.startsWith('/') && !v.startsWith('//') && !v.startsWith(base + '/')) {
					if (loc && attr === 'href' && !localePath.test(v) && !/\.\w+([?#]|$)/.test(v)) v = `/${loc}${v}`;
					node.properties[attr] = base + v;
				}
			}
		});
	};
}

// Item pages used to be /items/red-wine/ (dashes); they're /items/red_wine/ now, as on the FD Neoforged docs. The old
// addresses (live and in search engines) redirect to the new ones.
const items = JSON.parse(fs.readFileSync(new URL('./src/data/gen/items.json', import.meta.url), 'utf8'));
const redirects = Object.fromEntries(
	Object.values(items)
		.filter((it) => it.slug.includes('_'))
		.map((it) => [`/items/${it.slug.replaceAll('_', '-')}/`, `${base}/items/${it.slug}/`]), // Astro doesn't add the base to the target
);
// Translated pages that exist as files ("de/tipsy", as in the URL). Every other page under a language is Starlight's
// English fallback: it stays out of the sitemap and gets noindex (Head.astro), so search engines don't see English
// text as another language. The item pages are generated in every language.
const translated = new Set(
	locales.flatMap(({ code }) =>
		fs
			.readdirSync(new URL(`./src/content/docs/${code}/`, import.meta.url), { recursive: true })
			.map((f) => String(f).replaceAll('\\', '/'))
			.filter((f) => /\.mdx?$/.test(f))
			.map((f) => `${code}/${f.replace(/\.mdx?$/, '').replace(/(^|\/)index$/, '')}`.replace(/\/$/, '')),
	),
);
/** Sitemap filter: English pages, and translated ones that really are. */
const translatedOrEnglish = (page) => {
	const p = new URL(page).pathname.slice(base.length).replace(/^\/|\/$/g, '');
	const loc = p.split('/')[0];
	return !codes.includes(loc) || p.startsWith(`${loc}/items`) || translated.has(p);
};

/**
 * After the build: sitemap.xml next to @astrojs/sitemap's sitemap-index.xml (the address crawlers and people try
 * first), and robots.txt pointing at it. A project site's robots.txt isn't the host's (crawlers read
 * https://<owner>.github.io/robots.txt), so the sitemap also gets submitted in Search Console.
 */
const seoFiles = {
	name: 'vb-seo-files',
	hooks: {
		'astro:build:done': ({ dir }) => {
			const out = fileURLToPath(dir);
			if (fs.existsSync(path.join(out, 'sitemap-index.xml'))) fs.copyFileSync(path.join(out, 'sitemap-index.xml'), path.join(out, 'sitemap.xml'));
			fs.writeFileSync(path.join(out, 'robots.txt'), `User-agent: *
Allow: /

Sitemap: ${new URL(`${base}/sitemap.xml`, site).href}
`);
		},
	},
};

export default defineConfig({
	site,
	base,
	trailingSlash: 'always',
	redirects,
	// Start loading a page as soon as the pointer rests on (or focus reaches) a link to it, so clicks feel instant
	prefetch: { prefetchAll: true, defaultStrategy: 'hover' },
	markdown: { rehypePlugins: [rehypeBaseLinks] },
	integrations: [
		preact(),
		// Starlight adds its own sitemap; with translations on, ours (same i18n settings) leaves the fallbacks out
		...(locales.length
			? [sitemap({ i18n: { defaultLocale: 'root', locales: { root: 'en', ...Object.fromEntries(locales.map((l) => [l.code, l.lang])) } }, filter: translatedOrEnglish })]
			: []),
		starlight({
			title: 'Vine & Barrel',
			description: 'The player guide to Vine & Barrel, a cozy brewing mod for Minecraft 26.x: grow grapes, hops and barley, brew in a copper kettle, let it age in oak barrels and share a round. Every number comes from the mod itself.',
			logo: { src: './src/assets/ui/header.png', alt: 'Vine & Barrel', replacesTitle: true },
			favicon: '/favicon.png',
			// Google Search Console's "HTML tag" check: the repository variable GOOGLE_SITE_VERIFICATION (the docs workflow
			// passes it) is the token from the tag it shows
			head: process.env.GOOGLE_SITE_VERIFICATION
				? [{ tag: 'meta', attrs: { name: 'google-site-verification', content: process.env.GOOGLE_SITE_VERIFICATION } }]
				: [],
			social: repo ? [{ icon: 'github', label: 'GitHub', href: repo }] : [],
			editLink: repo ? { baseUrl: `${repo}/edit/${branch}/site/` } : undefined,
			customCss: ['./src/styles/theme.css', './src/styles/mc.css'],
			components: {
				Head: './src/components/overrides/Head.astro',
				Footer: './src/components/overrides/Footer.astro',
				ThemeSelect: './src/components/overrides/ThemeSelect.astro',
				LanguageSelect: './src/components/overrides/LanguageSelect.astro',
			},
			lastUpdated: false,
			// Search titles and descriptions, link-preview pictures, structured data (src/lib/seo.ts)
			routeMiddleware: './src/lib/seo-route.ts',
			...(locales.length
				? { defaultLocale: 'root', locales: { root: { label: 'English', lang: 'en' }, ...Object.fromEntries(locales.map((l) => [l.code, { label: l.label, lang: l.lang }])) } }
				: {}),
			tableOfContents: { minHeadingLevel: 2, maxHeadingLevel: 3 },
			sidebar: translateSidebar([
				{
					label: 'Start here',
					items: [
						{ label: 'Getting started', slug: 'getting-started' },
						{ label: 'Your first drink', slug: 'guides/first-drink' },
						{ label: 'Building a cellar', slug: 'guides/cellar' },
					],
				},
				{
					label: 'Brewing',
					items: [
						{ label: 'Crops', slug: 'crops' },
						{ label: 'Brew Kettle', slug: 'brew-kettle' },
						{ label: 'Aging Barrel', slug: 'aging-barrel' },
						{ label: 'Drinks', slug: 'drinks' },
						{ label: 'Tipsy', slug: 'tipsy' },
						{ label: 'Blackouts', slug: 'blackouts' },
					],
				},
				{
					label: 'The world',
					items: [
						{ label: 'Interactions', slug: 'interactions' },
						{ label: 'Villages & taverns', slug: 'villages' },
					],
				},
				{
					label: 'Reference',
					items: [
						{ label: 'All items & blocks', link: '/items/' },
						{ label: 'All recipes', slug: 'reference/recipes' },
						{ label: 'Advancements', slug: 'reference/advancements' },
						{ label: 'Configuration', slug: 'reference/config' },
					],
				},
				{
					label: 'About',
					items: [
						{ label: "Farmer's Delight", slug: 'farmers-delight' },
						{ label: 'Compatibility', slug: 'compatibility' },
						{ label: 'Changelog', slug: 'changelog' },
						{ label: "Farmer's Delight Neoforged", link: 'https://jexxme.github.io/FarmersDelight-Neoforged/' },
					],
				},
			]),
		}),
		seoFiles,
	],
});
