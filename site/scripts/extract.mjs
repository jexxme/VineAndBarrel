// Turns the mod's real data into the JSON the wiki reads (src/data/gen/*.json).
// Sources: src/data/runtime.json (written by the game itself: ./gradlew runDocsExport), the mod's data files
// (common/src/main/resources, common/src/generated/resources) and the vanilla client jar (names, compost chances).
// No number on the wiki is typed by hand: they all come from here.
import fs from 'node:fs';
import path from 'node:path';
import { REPO, vanillaLang, vanillaData, vanillaLocaleLang } from './lib/assets.mjs';
import { LOCALES } from '../src/i18n/locales.mjs';

const SITE = path.resolve(import.meta.dirname, '..');
const OUT = path.join(SITE, 'src/data/gen');
fs.mkdirSync(OUT, { recursive: true });
const runtime = JSON.parse(fs.readFileSync(path.join(SITE, 'src/data/runtime.json'), 'utf8'));
const NS = 'vineandbarrel';
const GEN = path.join(REPO, 'common/src/generated/resources/data');
const MAIN = path.join(REPO, 'common/src/main/resources/data');
const write = (name, data) => fs.writeFileSync(path.join(OUT, name), JSON.stringify(data, null, '\t'));
const walk = (dir) => (fs.existsSync(dir) ? fs.readdirSync(dir, { recursive: true }).filter((f) => f.endsWith('.json')).map((f) => f.replaceAll('\\', '/')) : []);
const readFile = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));
/** A data file of ours, from the hand-written or the generated resources. */
const dataFiles = (sub) => [GEN, MAIN].flatMap((root) => walk(path.join(root, NS, sub)).map((f) => ({ rel: f, json: readFile(path.join(root, NS, sub, f)) })));
const ourLang = readFile(path.join(REPO, 'common/src/main/resources/assets/vineandbarrel/lang/en_us.json'));
const mcLang = vanillaLang();
const lang = { ...mcLang, ...ourLang };
const props = Object.fromEntries(fs.readFileSync(path.join(REPO, 'gradle.properties'), 'utf8').split(/\r?\n/).filter((l) => /^\w+=/.test(l)).map((l) => l.split(/=(.*)/s).slice(0, 2)));

// ------------------------------------------------------------------ names
const titleCase = (p) => p.replaceAll('_', ' ').replace(/\b\w/g, (c) => c.toUpperCase());
function itemName(id) {
	const key = runtime.items[id]?.components?.['minecraft:item_name']?.translate;
	const [ns, p] = id.split(':');
	return lang[key] ?? lang[`item.${ns}.${p}`] ?? lang[`block.${ns}.${p}`] ?? titleCase(p);
}
const effectName = (id) => {
	const [ns, p] = id.split(':');
	return lang[`effect.${ns}.${p}`] ?? titleCase(p);
};
const isFd = (id) => id?.startsWith('farmersdelight:');

// ------------------------------------------------------------------ ingredients
const itemTags = runtime.tags.item;
const referenced = new Set(); // every item id that needs an icon and a name
const TAG_LABELS = { 'c:crops/onion': 'Any onion', 'c:crops/carrot': 'Any carrot', 'c:foods/raw_beef': 'Any raw beef', 'c:foods/raw_fish': 'Any raw fish', 'c:crops/wheat': 'Any wheat' };
function ingredient(raw) {
	if (raw == null) return null;
	if (typeof raw === 'string') {
		if (raw.startsWith('#')) {
			const tag = raw.slice(1);
			const items = itemTags[tag] ?? [];
			items.forEach((i) => referenced.add(i));
			// A label for every tag: the FD-free export has no items for tags only Farmer's Delight fills (onions...)
			return { tag, items, label: TAG_LABELS[tag] ?? `Any ${tag.split('/').pop().replace(/^safe_/, '').replaceAll('_', ' ')}` };
		}
		referenced.add(raw);
		return { items: [raw] };
	}
	if (Array.isArray(raw)) {
		const parts = raw.map(ingredient);
		return { items: [...new Set(parts.flatMap((p) => p.items))], any: parts };
	}
	if (raw.items) return ingredient(raw.items);
	if (raw.item) return ingredient(raw.item);
	if (raw.tag) return ingredient('#' + raw.tag);
	return { items: [] };
}
function stack(raw) {
	if (!raw) return null;
	if (typeof raw === 'string') return referenced.add(raw), { id: raw, count: 1 };
	const id = raw.id ?? raw.item;
	referenced.add(id);
	return { id, count: raw.count ?? 1 };
}

// ------------------------------------------------------------------ recipes
// Brewing and aging come from the game (runtime.json); FD-only ones did not load in that run, so they are read from
// their files and marked fdOnly. Crafting and FD cooking come from the data files.
const loaded = new Set(runtime.loadedRecipes);
const recipes = [];
for (const { rel, json } of dataFiles('recipe')) {
	const id = `${NS}:${rel.replace(/\.json$/, '')}`;
	const type = json.type.replace(/^minecraft:/, '');
	const fdOnly = JSON.stringify(json['neoforge:conditions'] ?? []).includes('"farmersdelight"');
	const r = { id, type, loaded: loaded.has(id), fdOnly };
	const live = (list) => list.find((x) => x.id === id);
	switch (type) {
		case 'crafting_shaped': {
			const key = Object.fromEntries(Object.entries(json.key).map(([k, v]) => [k, ingredient(v)]));
			r.grid = json.pattern.map((row) => [...row].map((c) => (c === ' ' ? null : key[c])));
			r.result = stack(json.result);
			break;
		}
		case 'crafting_shapeless':
			r.ingredients = json.ingredients.map(ingredient);
			r.result = stack(json.result);
			break;
		case 'vineandbarrel:brewing': {
			const g = live(runtime.brewing);
			if (!g) throw new Error(`Brewing recipe ${id} is missing from runtime.json; run ./gradlew runDocsExport`);
			r.ingredients = g.ingredients.map(ingredient);
			r.result = stack({ id: g.result, count: g.servings });
			r.bucket = stack(g.bucket);
			r.servings = g.servings;
			r.brewingTime = g.brewingTime;
			r.experience = g.experience;
			break;
		}
		case 'vineandbarrel:aging': {
			// Codec defaults (AgingRecipe): fermenting 1 day, aging 3 more days
			const g = live(runtime.aging) ?? { base: json.base, bucket: json.bucket, young: json.young, aged: json.aged, fermentingTime: json.fermenting_time, agingTime: json.aging_time };
			r.base = stack(g.base);
			r.bucket = g.bucket ? stack(g.bucket) : null;
			r.young = stack(g.young);
			r.aged = stack(g.aged);
			r.fermentingTime = g.fermentingTime;
			r.agingTime = g.agingTime;
			break;
		}
		case 'farmersdelight:cooking':
			r.ingredients = json.ingredients.map(ingredient);
			r.result = stack(json.result);
			r.cookingtime = json.cookingtime ?? 200; // FD's codec default
			r.experience = json.experience ?? 0;
			// FD's codec default: no container given, the result's own remainder (a meal's bowl; a feast has none)
			r.container = json.container ? stack(json.container) : stack(runtime.items[json.result.id]?.components?.['minecraft:use_remainder'] ?? null);
			break;
		case 'farmersdelight:cutting':
			r.ingredients = json.ingredients.map(ingredient);
			r.tool = ingredient(json.tool);
			r.results = json.result.map((x) => ({ ...stack(x.item), chance: x.chance ?? 1 }));
			break;
		default:
			r.special = true;
	}
	recipes.push(r);
}
recipes.sort((a, b) => a.id.localeCompare(b.id));
for (const g of runtime.brewing) if (!recipes.some((r) => r.id === g.id)) throw new Error(`No file for brewing recipe ${g.id}`);

// ------------------------------------------------------------------ items
const compostChance = (layers) => {
	if (!layers) return null;
	const p = vanillaData(`data/minecraft/context_int_provider/${layers.replace('minecraft:', '')}.json`);
	if (p === 1 || p?.value === 1) return 1;
	const d = p?.default?.distribution;
	if (!d) return null;
	const total = d.reduce((s, x) => s + x.weight, 0);
	return d.filter((x) => x.data > 0).reduce((s, x) => s + x.weight, 0) / total;
};
const CATEGORY = (id, it) => {
	const p = id.split(':')[1];
	if (runtime.drinks[id] || p === 'mug' || p === 'wine_glass') return 'drinks';
	if (/juice|wort|must/.test(p)) return 'bases';
	if (/kettle|aging_barrel/.test(p)) return 'workstations';
	if (/crate|bale/.test(p)) return 'storage';
	if (runtime.rules?.fdMealNourishment?.[id] != null || /beer_bread|coq_au_vin/.test(p)) return 'meals';
	return 'crops';
};
const items = {};
for (const [id, it] of Object.entries(runtime.items)) {
	const c = it.components;
	const food = c['minecraft:food'];
	const cons = c['minecraft:consumable'];
	const effects = (cons?.on_consume_effects ?? [])
		.filter((e) => e.type === 'minecraft:apply_effects')
		.flatMap((e) => e.effects.map((x) => ({ id: x.id, name: effectName(x.id), duration: x.duration ?? 0, amplifier: x.amplifier ?? 0, probability: e.probability ?? 1 })));
	items[id] = {
		id,
		name: itemName(id),
		// the item's id, as on the FD Neoforged docs (/items/red_wine/): one URL scheme across the family
		slug: id.split(':')[1],
		category: CATEGORY(id, it),
		stack: c['minecraft:max_stack_size'] ?? 64,
		food: food ? { nutrition: food.nutrition ?? 0, saturation: food.saturation ?? 0, alwaysEdible: !!food.can_always_eat } : null,
		consumable: cons ? { seconds: cons.consume_seconds ?? 1.6, animation: cons.animation ?? 'eat', effects } : null,
		remainder: c['minecraft:use_remainder']?.id ?? null,
		compost: compostChance(c['minecraft:compostable']?.layers),
		glint: !!c['minecraft:enchantment_glint_override'],
		block: Object.entries(runtime.blocks).find(([, b]) => b.item === id)?.[0] ?? null,
		tags: it.tags,
	};
	referenced.add(id);
}

// ------------------------------------------------------------------ drinks (the generated table)
const drinks = [];
for (const r of recipes.filter((x) => x.type === 'vineandbarrel:aging')) {
	for (const [stage, d] of [['young', r.young.id], ['aged', r.aged.id]]) {
		const info = runtime.drinks[d];
		referenced.add(info.vessel);
		drinks.push({
			id: d,
			name: items[d].name,
			stage,
			base: r.base.id,
			bucket: r.bucket?.id ?? null,
			vessel: info.vessel,
			color: '#' + info.color.toString(16).padStart(6, '0'),
			effect: { id: info.effect, name: effectName(info.effect), duration: info.duration, amplifier: info.amplifier },
			fdOnly: r.fdOnly,
			aging: r.id,
		});
	}
}

// ------------------------------------------------------------------ world: wild crops, seasons, animals, trades, loot
const biomeTags = runtime.tags['worldgen/biome'];
const biomeName = (b) => lang[`biome.${b.replace(':', '.')}`] ?? titleCase(b.split(':')[1]);
function biomeList(ref) {
	if (!ref) return [];
	const list = Array.isArray(ref) ? ref : [ref];
	return [...new Set(list.flatMap((x) => (x.startsWith('#') ? biomeTags[x.slice(1)] ?? [] : [x])))].sort();
}
/** A biome tag file of ours, as written (so optional BoP entries that didn't load still show). */
const tagFile = (tag) => {
	const [ns, p] = tag.replace('#', '').split(':');
	for (const root of [GEN, MAIN]) {
		const f = path.join(root, ns, 'tags/worldgen/biome', p + '.json');
		if (fs.existsSync(f)) return readFile(f).values.map((v) => (typeof v === 'string' ? { id: v, required: true } : { id: v.id, required: v.required !== false }));
	}
	return [];
};
const worldgen = [];
for (const { rel, json } of dataFiles('neoforge/biome_modifier')) {
	const name = rel.replace(/\.json$/, '');
	const crop = name.replace('_extra', '');
	const allowed = json.allowed_biomes;
	const e = {
		name,
		crop: `${NS}:${crop}`,
		extra: name.endsWith('_extra'),
		minTemperature: json.min_temperature ?? null,
		maxTemperature: json.max_temperature ?? null,
		allowedTags: tagFile(allowed).map((v) => v.id),
		deniedTags: json.denied_biomes ? tagFile(json.denied_biomes).map((v) => v.id) : [],
		optional: tagFile(allowed).filter((v) => !v.required).map((v) => ({ id: v.id, name: biomeName(v.id) })),
	};
	referenced.add(e.crop);
	worldgen.push(e);
}
const seasons = {};
for (const s of ['spring', 'summer', 'autumn', 'winter']) {
	const f = path.join(GEN, 'sereneseasons/tags/item', `${s}_crops.json`);
	if (fs.existsSync(f)) seasons[s] = readFile(f).values.filter((v) => typeof v === 'string' && v.startsWith(NS));
}
// Vanilla animal and villager tags our items joined
const ANIMAL_TAGS = ['minecraft:chicken_food', 'minecraft:cow_food', 'minecraft:sheep_food', 'minecraft:goat_food', 'minecraft:horse_food', 'minecraft:llama_food', 'minecraft:parrot_food', 'minecraft:fox_food', 'minecraft:villager_plantable_seeds', 'minecraft:bee_growables'];
const animals = Object.fromEntries(ANIMAL_TAGS.map((t) => [t, [...(itemTags[t] ?? []), ...(runtime.tags.block[t] ?? [])].filter((i) => i.startsWith(NS))]));

const trades = dataFiles('villager_trade').map(({ rel, json }) => {
	const [profession, level] = rel.split('/');
	referenced.add(json.gives.id);
	referenced.add(json.wants.id);
	return { profession, level: /^\d+$/.test(level) ? +level : null, gives: stack(json.gives), wants: stack(json.wants), maxUses: json.max_uses };
});

const countRange = (fns) => {
	const c = (Array.isArray(fns) ? fns : [fns]).find((f) => f?.function === 'minecraft:set_count' || f?.type === 'minecraft:set_count') ?? (fns?.type === 'minecraft:set_count' ? fns : null);
	const n = c?.count;
	if (n == null) return [1, 1];
	return typeof n === 'number' ? [n, n] : [n.min, n.max];
};
const loot = {};
for (const { rel, json } of dataFiles('loot_table/chests')) {
	const name = rel.replace(/\.json$/, '');
	loot[name] = json.pools.map((p) => {
		const total = p.entries.reduce((s, e) => s + (e.weight ?? 1), 0);
		const rolls = typeof p.rolls === 'number' ? [p.rolls, p.rolls] : [p.rolls.min, p.rolls.max];
		return {
			rolls,
			entries: p.entries.map((e) => (referenced.add(e.name), { id: e.name, weight: e.weight ?? 1, chance: (e.weight ?? 1) / total, count: countRange(e.modifier ?? e.functions) })),
		};
	});
}

// ------------------------------------------------------------------ advancements
const advancements = dataFiles('advancement/main').map(({ rel, json }) => {
	const d = json.display;
	if (d) referenced.add(d.icon.id);
	return d && {
		id: `${NS}:main/${rel.replace(/\.json$/, '')}`,
		parent: json.parent ?? null,
		title: lang[d.title.translate] ?? d.title.translate,
		description: lang[d.description.translate] ?? d.description.translate,
		titleKey: d.title.translate,
		descriptionKey: d.description.translate,
		icon: d.icon.id,
		frame: d.frame ?? 'task',
		hidden: !!d.hidden,
	};
}).filter(Boolean);

// ------------------------------------------------------------------ blocks
const blocks = Object.fromEntries(Object.entries(runtime.blocks).map(([id, b]) => [id, { id, name: lang[`block.${id.replace(':', '.')}`] ?? itemName(b.item), item: b.item === 'minecraft:air' ? null : b.item, properties: b.properties, defaultState: b.defaultState, tags: b.tags, hardness: b.hardness }]));

// ------------------------------------------------------------------ rules: servings, heat, Tipsy, Cheers
const heat = {
	sources: runtime.tags.block[`${NS}:heat_sources`],
	tray: runtime.tags.block[`${NS}:tray_heat_sources`],
	conductors: runtime.tags.block[`${NS}:heat_conductors`],
	containers: runtime.tags.item[`${NS}:kettle_containers`],
	// The optional FD entries, read from the tag files (they only load with FD)
	fdSources: JSON.stringify(readFile(path.join(MAIN, NS, 'tags/block/heat_sources.json'))).includes('#farmersdelight:heat_sources'),
};
[...heat.sources, ...heat.conductors, ...heat.containers].forEach((i) => referenced.add(i));
// The blackout endings come from the game (weights, poses, souvenirs); their wake-up lines from the lang file
const tipsy = structuredClone(runtime.tipsy);
for (const e of tipsy.blackout?.endings ?? []) {
	e.lineKeys = Array.from({ length: e.lines }, (_, n) => `blackout.${NS}.${e.id}.${n}`).filter((k) => lang[k]);
	e.lines = e.lineKeys.map((k) => lang[k]);
	e.souvenirs.forEach((i) => referenced.add(i));
}
const sobering = runtime.tags.item[`${NS}:sobering_drinks`];
sobering.forEach((i) => referenced.add(i));
['minecraft:milk_bucket', 'minecraft:glass_bottle', 'minecraft:bucket', 'minecraft:hopper', 'minecraft:dispenser', 'minecraft:comparator', 'minecraft:honeycomb', 'minecraft:iron_axe', 'minecraft:sweet_berries', 'minecraft:wheat', 'minecraft:sugar', 'minecraft:red_bed', 'minecraft:composter', 'minecraft:crafting_table', 'minecraft:anvil', 'minecraft:iron_chain', 'minecraft:copper_ingot', 'minecraft:melon_slice'].forEach((i) => referenced.add(i));

// Picking a bush (block_interact loot tables, built like vanilla's sweet berry one: every pool adds up, a pool or entry
// with a match_block age only at that age) and what wild crops drop without shears
const atAge = (age, cond) => !cond || JSON.stringify(cond).match(/"age":"?(\d)"?/)?.[1] == age;
const harvest = {};
for (const { rel, json } of dataFiles('loot_table/harvest')) {
	const at = (age) => json.pools.flatMap((p) => (atAge(age, p.condition ?? p.conditions) ? p.entries : []))
		.filter((e) => atAge(age, e.condition ?? e.conditions))
		.map((e) => countRange(e.modifier ?? e.functions))
		.reduce((sum, r) => [sum[0] + r[0], sum[1] + r[1]], [0, 0]);
	harvest[rel.replace(/\.json$/, '')] = { id: json.pools[0].entries[0].name, count: at(3), halfRipe: at(2) };
}
const wildDrops = {};
for (const { rel, json } of dataFiles('loot_table/blocks').filter(({ rel }) => rel.startsWith('wild_'))) {
	wildDrops[`${NS}:${rel.replace(/\.json$/, '')}`] = json.pools.map((p) => {
		const chance = JSON.stringify(p.condition ?? '').match(/"chance":([\d.]+)/);
		const entry = p.entries[0].type === 'minecraft:alternatives' ? p.entries[0].children.find((c) => !c.condition) : p.entries[0];
		const fortune = JSON.stringify(entry).includes('minecraft:fortune');
		referenced.add(entry.name);
		return { id: entry.name, chance: chance ? +chance[1] : 1, fortune };
	});
}

const others = {};
for (const id of referenced) if (id && !items[id]) others[id] = { id, name: itemName(id) };

write('items.json', items);
// ------------------------------------------------------------------ other mods (the data compat/CompatData generates)
// Thirst values per item from each mod's own format, grouped into rows of items that share all of them
const tagValues = (ns, dir, re) =>
	Object.fromEntries(walk(path.join(GEN, ns, 'tags/item', dir)).flatMap((f) => readFile(path.join(GEN, ns, 'tags/item', dir, f)).values.map((id) => [id, +f.match(re)[1]])));
const tanThirst = tagValues('toughasnails', 'thirst', /^(\d+)_/);
const tanHydration = tagValues('toughasnails', 'hydration', /^(\d+)_/);
const twtValues = readFile(path.join(GEN, NS, 'thirstwastaken2/drinks', `${NS}.json`)).values;
const thirstRows = [];
for (const f of walk(path.join(GEN, NS, 'environment/drinkable'))) {
	const h = readFile(path.join(GEN, NS, 'environment/drinkable', f));
	const id = h.type;
	const twt = twtValues[id];
	const row = {
		tan: tanThirst[id] ? { thirst: tanThirst[id], hydration: tanHydration[id] } : null,
		homeostatic: { amount: h.amount, saturation: h.saturation },
		twt: twt ? { thirst: twt.thirst, quenched: twt.quenched } : null,
	};
	const key = JSON.stringify(row);
	const same = thirstRows.find((r) => r.key === key);
	if (same) same.items.push(id);
	else thirstRows.push({ key, items: [id], ...row });
}
// In the order of the mod's own registry (items.json), most quenching first
const order = Object.keys(items);
thirstRows.forEach((r) => r.items.sort((a, b) => order.indexOf(a) - order.indexOf(b)));
thirstRows.sort((a, b) => (b.tan?.thirst ?? 0) - (a.tan?.thirst ?? 0) || b.homeostatic.amount - a.homeostatic.amount || order.indexOf(a.items[0]) - order.indexOf(b.items[0]));
const nutrients = Object.fromEntries(walk(path.join(GEN, 'c/tags/item/nutrients')).map((f) => [f.replace('.json', ''), readFile(path.join(GEN, 'c/tags/item/nutrients', f)).values.sort((a, b) => order.indexOf(a) - order.indexOf(b))]));
write('compat.json', {
	thirst: thirstRows.map(({ key, ...r }) => r),
	nutrients,
	versions: {
		appleskin: props.appleskin_version.replace(/^mc[\d.]+-/, ''),
		toughasnails: props.tough_as_nails_version,
		homeostatic: props.homeostatic_version,
		thirstwastaken2: props.thirst_was_taken_version.replace(/\+.*$/, ''),
		nutritionalbalance: props.nutritional_balance_version.replace(/^[\d.]+-/, ''),
		solonion: props.spice_of_life_version,
	},
});

write('names.json',Object.fromEntries(Object.entries(mcLang).filter(([k]) => /^(item|block)\.minecraft\.[a-z0-9_]+$/.test(k)).map(([k, v]) => ['minecraft:' + k.split('.')[2], v])));
write('others.json', others);

// The game's own text for the translated wiki (lang.<code>.json): item and block names by id, plus our lang keys and
// vanilla's effect, biome and entity names by key. Vanilla's from its lang file in the Gradle asset cache, ours from
// the mod's lang file. Anything missing falls back to English on the page.
for (const { code, mc } of LOCALES) {
	const modFile = path.join(REPO, `common/src/main/resources/assets/vineandbarrel/lang/${mc}.json`);
	const ourFile = fs.existsSync(modFile) ? modFile : null;
	const ours = ourFile ? readFile(ourFile) : {};
	const locLang = { ...(vanillaLocaleLang(mc) ?? {}), ...ours };
	const nameIn = (id) => {
		const key = runtime.items[id]?.components?.['minecraft:item_name']?.translate;
		const [ns, p] = id.split(':');
		return locLang[key] ?? locLang[`item.${ns}.${p}`] ?? locLang[`block.${ns}.${p}`];
	};
	const ids = new Set([...Object.keys(items), ...Object.keys(others), ...Object.keys(blocks), ...Object.keys(locLang).filter((k) => /^(item|block)\.minecraft\.[a-z0-9_]+$/.test(k)).map((k) => 'minecraft:' + k.split('.')[2])]);
	const out = Object.fromEntries([...ids].sort().map((id) => [id, nameIn(id)]).filter(([, n]) => n));
	for (const [k, v] of Object.entries(locLang).sort()) if (k in ours || /^(effect|biome|entity)\.minecraft\.[a-z0-9_]+$/.test(k)) out[k] = v;
	const ourNames = Object.keys(out).filter((id) => id.startsWith(NS + ':')).length;
	console.log(`lang.${code}.json: ${Object.keys(out).length} entries, ${ourNames} of our names${ourFile ? '' : ` (no ${mc}.json yet)`}`);
	write(`lang.${code}.json`, out);
}
write('blocks.json', blocks);
write('recipes.json', recipes);
write('drinks.json', drinks);
write('worldgen.json', worldgen);
write('seasons.json', seasons);
write('animals.json', animals);
write('trades.json', trades);
write('loot.json', loot);
write('advancements.json', advancements);
write('rules.json', { ...runtime.rules, crops: runtime.crops, villages: runtime.villages, harvest, wildDrops, tipsy, heat, sobering, tipsyEffect: runtime.effects[`${NS}:tipsy`] ?? null });
write('meta.json', {
	modVersion: props.mod_version,
	fabricModVersion: props.fabric_mod_version,
	// The version players see, without the loader build ("0.9.0"); names read "Vine & Barrel 0.9.0 · NeoForge · Minecraft 26.3"
	version: props.mod_version.split('+')[0],
	minecraft: props.minecraft_version,
	neoforge: props.neo_version,
	fabricLoader: props.fabric_loader_version,
	fabricApi: props.fabric_api_version.replace(/\+.*$/, ''),
	modId: props.mod_id,
	license: props.mod_license,
	jei: props.jei_version,
	jade: props.jade_version,
	counts: {
		items: Object.keys(items).length,
		// unique ids: a drink with two aging recipes (hard cider from FD's or our apple juice) has two rows
		drinks: new Set(drinks.map((d) => d.id)).size,
		brewing: recipes.filter((r) => r.type === 'vineandbarrel:brewing').length,
		aging: recipes.filter((r) => r.type === 'vineandbarrel:aging').length,
		advancements: advancements.length,
	},
});
// The repo's changelog, rendered as the Changelog page
fs.copyFileSync(path.join(REPO, 'changelog.md'), path.join(OUT, 'changelog.md'));
// FD's own items get no icon (we don't redistribute FD's textures); they show as text and link to the FDN wiki
write('referenced.json', [...referenced].filter((i) => i && !isFd(i)).sort());
console.log(`items ${Object.keys(items).length}, recipes ${recipes.length}, drinks ${drinks.length}, worldgen ${worldgen.length}, trades ${trades.length}, advancements ${advancements.length}, referenced ${referenced.size}`);
