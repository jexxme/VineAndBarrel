// The translated wiki. English lives at the root (/crops/), every other language under /<code>/ (/de/crops/), as
// Starlight's locales (src/i18n/locales.mjs). Pages are translated as files (src/content/docs/<code>/…). Components
// print their words through t(), keyed by the English text, from src/i18n/strings/<code>.json; names and other game
// text come from the game's own lang files (lang.<code>.json, see data.ts). Numbers go through Intl.
import { LOCALES, ENGLISH } from '../i18n/locales.mjs';

export type Locale = string; // 'en' or a code from locales.mjs
export const LOCALE_LIST = [ENGLISH, ...LOCALES];
const BY_CODE = Object.fromEntries(LOCALE_LIST.map((l) => [l.code, l]));

/** Astro.currentLocale → our locale code ("en" for the root and anything we don't know). */
export const localeOf = (current: string | undefined): Locale => (current && BY_CODE[current.toLowerCase()] ? current.toLowerCase() : 'en');
/** The BCP 47 tag for Intl and html lang: "pt-br" → "pt-BR". */
export const langTag = (locale: Locale) => BY_CODE[locale]?.lang ?? 'en';

type Entry = string | Partial<Record<Intl.LDMLPluralRule, string>>;
const dicts = import.meta.glob<{ default: Record<string, Entry> }>('../i18n/strings/*.json', { eager: true });
const DICTS: Record<string, Record<string, Entry>> = Object.fromEntries(
	Object.entries(dicts).map(([p, m]) => [p.match(/([\w-]+)\.json$/)![1], m.default]),
);
const plurals = new Map<string, Intl.PluralRules>();
const pluralOf = (locale: Locale, n: number) => {
	const tag = langTag(locale);
	if (!plurals.has(tag)) plurals.set(tag, new Intl.PluralRules(tag));
	return plurals.get(tag)!.select(n);
};

/**
 * A component's words in the page's language, keyed by the English text: t('Servings', locale).
 * {name} placeholders are filled from vars (numbers formatted for the language). A translation may be split by plural
 * form ({ "one": "…", "few": "…", "other": "…" }), picked by vars.n. The English key itself may be written with a
 * plural split: t('{n} day|{n} days', locale, { n }) → English "one|other". Missing translations fall back to English.
 */
export function t(en: string, locale: Locale, vars?: Record<string, string | number>): string {
	const entry: Entry | undefined = locale === 'en' ? undefined : DICTS[locale]?.[en];
	let s: string;
	const n = typeof vars?.n === 'number' ? vars.n : undefined;
	if (entry && typeof entry === 'object') s = entry[n !== undefined ? pluralOf(locale, n) : 'other'] ?? entry.other ?? en;
	else if (typeof entry === 'string' && entry) s = entry;
	else {
		// English (or missing): "singular|plural"
		const [one, other] = en.split('|');
		s = other !== undefined ? (n === 1 ? one : other) : en;
	}
	if (typeof entry === 'string' && entry.includes('|')) {
		// a translation kept the English "a|b" shape: same rule
		const [one, other] = entry.split('|');
		s = n === 1 ? one : other;
	}
	return vars ? s.replace(/\{(\w+)\}/g, (m, k) => (k in vars ? (typeof vars[k] === 'number' ? num(vars[k] as number, locale) : String(vars[k])) : m)) : s;
}

/** A number for the language: 1.5 → "1,5" in German. Up to `digits` decimals, no trailing zeros. */
export const num = (x: number, locale: Locale, digits = 2) => new Intl.NumberFormat(langTag(locale), { maximumFractionDigits: digits, useGrouping: false }).format(x);

/** "a, b and c" in the page's language. */
export const listOf = (xs: string[], locale: Locale, type: 'conjunction' | 'disjunction' = 'conjunction') =>
	new Intl.ListFormat(langTag(locale), { type }).format(xs);

/** A site path ("/crops/") for the page's language ("/de/crops/"); base path not included (see url() in data.ts). */
export const localPath = (p: string, locale: Locale) => (locale === 'en' ? p : `/${locale}${p.startsWith('/') ? p : '/' + p}`);
