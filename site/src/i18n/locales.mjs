// The wiki's languages: the same set as the game's own translations. English is the root (/crops/), the others live
// under /<code>/ (/de/crops/). `mc` is the game's lang file, `lang` the BCP 47 tag (html lang, Intl formatting).
// A language switches on once src/content/docs/<code>/ exists.
export const LOCALES = [
	{ code: 'de', mc: 'de_de', lang: 'de', label: 'Deutsch' },
	{ code: 'es', mc: 'es_es', lang: 'es', label: 'Español' },
	{ code: 'fr', mc: 'fr_fr', lang: 'fr', label: 'Français' },
	{ code: 'it', mc: 'it_it', lang: 'it', label: 'Italiano' },
	{ code: 'pl', mc: 'pl_pl', lang: 'pl', label: 'Polski' },
	{ code: 'pt-br', mc: 'pt_br', lang: 'pt-BR', label: 'Português (Brasil)' },
	{ code: 'tr', mc: 'tr_tr', lang: 'tr', label: 'Türkçe' },
	{ code: 'ru', mc: 'ru_ru', lang: 'ru', label: 'Русский' },
	{ code: 'uk', mc: 'uk_ua', lang: 'uk', label: 'Українська' },
	{ code: 'zh-cn', mc: 'zh_cn', lang: 'zh-CN', label: '简体中文' },
	{ code: 'ja', mc: 'ja_jp', lang: 'ja', label: '日本語' },
	{ code: 'ko', mc: 'ko_kr', lang: 'ko', label: '한국어' },
];
export const ENGLISH = { code: 'en', mc: 'en_us', lang: 'en', label: 'English' };
