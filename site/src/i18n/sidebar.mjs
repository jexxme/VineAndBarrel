// Sidebar labels in every language, from the same string files as the components (src/i18n/strings/<code>.json,
// keyed by the English label). A label a language doesn't have stays English.
import fs from 'node:fs';
import { LOCALES } from './locales.mjs';

// Starlight keys sidebar translations by language tag ("pt-BR", "zh-CN"), not by the folder name
const dicts = LOCALES.map(({ code, lang }) => {
	const f = new URL(`./strings/${code}.json`, import.meta.url);
	return [lang, fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : {}];
});

/** Adds `translations: { <lang>: label }` to every sidebar group and entry. */
export function translateSidebar(entries) {
	return entries.map((e) => {
		const tr = Object.fromEntries(dicts.map(([code, d]) => [code, d[e.label]]).filter(([, v]) => typeof v === 'string' && v));
		return {
			...e,
			...(Object.keys(tr).length ? { translations: { ...e.translations, ...tr } } : {}),
			...(e.items ? { items: translateSidebar(e.items) } : {}),
		};
	});
}
