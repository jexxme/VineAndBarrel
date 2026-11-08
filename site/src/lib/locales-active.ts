// The languages that are switched on: those with a src/content/docs/<code>/ folder (as in astro.config.mjs).
// The build runs in site/, so the folder is looked up from there (import.meta.url moves when bundled).
import fs from 'node:fs';
import path from 'node:path';
import { LOCALES } from '../i18n/locales.mjs';
export const activeLocales = () => LOCALES.map((l) => l.code).filter((code) => fs.existsSync(path.join(process.cwd(), 'src/content/docs', code)));
