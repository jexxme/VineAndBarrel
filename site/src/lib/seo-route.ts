// Starlight route middleware (astro.config.mjs: routeMiddleware): the search and link-preview metadata of lib/seo.ts
// in every page's <head>, on top of what Starlight writes (title, description, canonical, hreflang, og:*).
import { defineRouteMiddleware } from '@astrojs/starlight/route-data';
import { LOCALES, ENGLISH } from '../i18n/locales.mjs';
import { t } from './i18n';
import { PAGES, SITE_NAME, itemTitle, pictureOf, pictureKey, pictureSize, ogLocale, softwareApplication } from './seo';

type Entry = App.Locals['starlightRoute']['head'][number];
const BASE = import.meta.env.BASE_URL.replace(/\/$/, '');
const CODES = LOCALES.map((l) => l.code);
const MC: Record<string, string> = Object.fromEntries([ENGLISH, ...LOCALES].map((l) => [l.code, l.mc]));

/** The <meta> with this name or property: its content set, or a new one added. */
function setMeta(head: Entry[], key: 'name' | 'property', value: string, content: string) {
	const found = head.find((e) => e.tag === 'meta' && e.attrs?.[key] === value);
	if (found) found.attrs = { ...found.attrs, content };
	else head.push({ tag: 'meta', attrs: { [key]: value, content } });
}
const getMeta = (head: Entry[], key: 'name' | 'property', value: string) =>
	head.find((e) => e.tag === 'meta' && e.attrs?.[key] === value)?.attrs?.content as string | undefined;
// JSON in a <script>: "<" escaped, so no text can close the tag
const jsonLd = (data: object): Entry => ({
	tag: 'script',
	attrs: { type: 'application/ld+json' },
	content: JSON.stringify({ '@context': 'https://schema.org', ...data }).replace(/</g, '\\u003c'),
});

export const onRequest = defineRouteMiddleware((context) => {
	const route = context.locals.starlightRoute;
	const head = route.head;
	const site = context.site;
	// "/VineAndBarrel/de/items/red_wine/" → locale "de", slug "items/red_wine"
	const path = context.url.pathname.slice(BASE.length).replace(/^\/|\/$/g, '');
	const first = path.split('/')[0];
	const locale = CODES.includes(first) ? first : 'en';
	const slug = locale === 'en' ? path : path.slice(first.length + 1);
	if (slug === '404' || !site) return;
	const isHome = slug === '';
	const title = route.entry.data.title;

	// Title and description: the English search ones (translated pages keep their own)
	const page = PAGES[slug];
	const isItem = slug.startsWith('items/');
	if (locale === 'en' && (page || isItem)) {
		const search = page?.title ?? itemTitle(title);
		const full = isHome ? search : `${search} | ${SITE_NAME}`;
		const titleTag = head.find((e) => e.tag === 'title');
		if (titleTag) titleTag.content = full;
		setMeta(head, 'property', 'og:title', search);
		// Item pages keep their generated line ("Red Wine: drinks from Vine & Barrel. Gives Regeneration.") and say what's on them
		const description = page?.description ?? (isItem ? `${getMeta(head, 'name', 'description') ?? ''} How to get it in Minecraft and what it's used for.`.trim() : undefined);
		if (description) {
			setMeta(head, 'name', 'description', description);
			setMeta(head, 'property', 'og:description', description);
		}
	}
	if (isHome) setMeta(head, 'property', 'og:type', 'website');

	// og:locale as language_TERRITORY, and the page's other languages (every page is in all of them)
	setMeta(head, 'property', 'og:locale', ogLocale(MC[locale]));
	for (const code of ['en', ...CODES].filter((c) => c !== locale)) head.push({ tag: 'meta', attrs: { property: 'og:locale:alternate', content: ogLocale(MC[code]) } });

	// The link preview's picture
	// (an item's alt is its name in the page's language)
	const picture = pictureOf(slug);
	if ('item' in picture) picture.alt = title;
	const image = new URL(`${BASE}/og/${pictureKey(picture)}.jpg`, site).href;
	const { width, height } = pictureSize(picture);
	setMeta(head, 'property', 'og:image', image);
	setMeta(head, 'property', 'og:image:width', String(width));
	setMeta(head, 'property', 'og:image:height', String(height));
	setMeta(head, 'property', 'og:image:alt', picture.alt);
	setMeta(head, 'name', 'twitter:card', 'item' in picture ? 'summary' : 'summary_large_image');
	setMeta(head, 'name', 'twitter:image', image);
	setMeta(head, 'name', 'twitter:image:alt', picture.alt);

	// Structured data: the mod on the home page, breadcrumbs on the others
	const home = new URL(`${BASE}/${locale === 'en' ? '' : `${locale}/`}`, site).href;
	const here = new URL(context.url.pathname, site).href;
	if (isHome) {
		head.push(
			jsonLd({
				'@graph': [
					{ '@type': 'WebSite', '@id': `${home}#website`, url: home, name: SITE_NAME, alternateName: 'Vine and Barrel', inLanguage: route.lang },
					softwareApplication(home, getMeta(head, 'name', 'description') ?? '', image),
				],
			}),
		);
	} else {
		const crumbs = [{ name: SITE_NAME, item: home }];
		if (isItem) crumbs.push({ name: t('All items & blocks', locale), item: new URL('items/', home).href });
		crumbs.push({ name: title, item: here });
		head.push(
			jsonLd({
				'@type': 'BreadcrumbList',
				itemListElement: crumbs.map((c, i) => ({ '@type': 'ListItem', position: i + 1, ...c })),
			}),
		);
	}
});
