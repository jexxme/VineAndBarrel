// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import preact from '@astrojs/preact';
import { visit } from 'unist-util-visit';

// GitHub Pages project site. The repository name isn't decided yet, so the base path is configurable:
// BASE_PATH=/<repo-name> (the Pages workflow passes it), SITE_URL for the host. Locally it defaults to /vine-and-barrel.
// REPO_URL (e.g. https://github.com/<owner>/<repo>) adds the GitHub button and "edit page" links once the repo exists.
const site = process.env.SITE_URL ?? 'https://jexxme.github.io';
const base = (process.env.BASE_PATH ?? '/vine-and-barrel').replace(/\/$/, '');
const repo = process.env.REPO_URL?.replace(/\/$/, '');
const branch = process.env.REPO_BRANCH ?? '26.3';

/** Markdown links written as "/mechanics/..." get the base path, so pages can link with plain site paths. */
function rehypeBaseLinks() {
	return (tree) => {
		visit(tree, 'element', (node) => {
			for (const attr of ['href', 'src']) {
				const v = node.properties?.[attr];
				if (typeof v === 'string' && v.startsWith('/') && !v.startsWith('//') && !v.startsWith(base + '/')) {
					node.properties[attr] = base + v;
				}
			}
		});
	};
}

export default defineConfig({
	site,
	base,
	trailingSlash: 'always',
	// Start loading a page as soon as the pointer rests on (or focus reaches) a link to it, so clicks feel instant
	prefetch: { prefetchAll: true, defaultStrategy: 'hover' },
	markdown: { rehypePlugins: [rehypeBaseLinks] },
	integrations: [
		preact(),
		starlight({
			title: 'Vine & Barrel',
			description: 'The player guide to Vine & Barrel, a cozy brewing mod for Minecraft 26.x: grow grapes, hops and barley, brew in a copper kettle, let it age in oak barrels and share a round. Every number comes from the mod itself.',
			logo: { src: './src/assets/ui/banner.png', alt: 'Vine & Barrel', replacesTitle: true },
			favicon: '/favicon.png',
			social: repo ? [{ icon: 'github', label: 'GitHub', href: repo }] : [],
			editLink: repo ? { baseUrl: `${repo}/edit/${branch}/site/` } : undefined,
			customCss: ['./src/styles/theme.css', './src/styles/mc.css'],
			components: {
				Head: './src/components/overrides/Head.astro',
				Footer: './src/components/overrides/Footer.astro',
				ThemeSelect: './src/components/overrides/ThemeSelect.astro',
			},
			lastUpdated: false,
			tableOfContents: { minHeadingLevel: 2, maxHeadingLevel: 3 },
			sidebar: [
				{ label: 'Start here', items: [{ label: 'Getting started', slug: 'getting-started' }] },
				{
					label: 'Brewing',
					items: [
						{ label: 'Crops', slug: 'crops' },
						{ label: 'Brew Kettle', slug: 'brew-kettle' },
						{ label: 'Aging Barrel', slug: 'aging-barrel' },
						{ label: 'Drinks', slug: 'drinks' },
						{ label: 'Tipsy', slug: 'tipsy' },
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
					],
				},
				{
					label: 'About',
					items: [
						{ label: "Farmer's Delight", slug: 'farmers-delight' },
						{ label: 'Compatibility', slug: 'compatibility' },
						{ label: 'Changelog', slug: 'changelog' },
					],
				},
			],
		}),
	],
});
