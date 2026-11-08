// Renders an inventory icon for every item the site shows (src/data/gen/referenced.json, written by extract.mjs)
// into public/icons/<namespace>/<path>.png, from the item's real model and textures. Flat items keep their native
// pixel size (the site scales them up without smoothing); 3D block items are rendered at 128 px.
import fs from 'node:fs';
import path from 'node:path';
import { readJson, idPath } from './lib/assets.mjs';
import { renderIcon, itemDefinitionModel, resolveModel } from './lib/render.mjs';

const SITE = path.resolve(import.meta.dirname, '..');
const ids = JSON.parse(fs.readFileSync(path.join(SITE, 'src/data/gen/referenced.json'), 'utf8'));
const outDir = path.join(SITE, 'public/icons');
const manifest = {};
let failed = [];
for (const id of ids) {
	const [ns, p] = id.split(':');
	try {
		const def = readJson(idPath(id, 'items', '.json'));
		if (!def) throw new Error('no item definition');
		const pick = itemDefinitionModel(def);
		if (!pick?.model) throw new Error('no plain model in ' + JSON.stringify(def.model).slice(0, 80));
		const model = resolveModel(pick.model);
		const flat = model.generated;
		const png = await renderIcon(pick.model, flat ? 16 : 128, pick.tints);
		if (!png) throw new Error('unrenderable model ' + pick.model);
		fs.mkdirSync(path.join(outDir, ns), { recursive: true });
		fs.writeFileSync(path.join(outDir, ns, p + '.png'), png);
		manifest[id] = flat ? 'flat' : '3d';
	} catch (e) {
		failed.push(`${id}: ${e.message}`);
		manifest[id] = null;
	}
}
fs.writeFileSync(path.join(SITE, 'src/data/gen/icons.json'), JSON.stringify(manifest, null, '\t'));
console.log(`icons: ${Object.values(manifest).filter(Boolean).length} rendered, ${failed.length} failed`);
if (failed.length) console.log(failed.join('\n'));
