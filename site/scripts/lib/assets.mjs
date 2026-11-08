// Reads Minecraft resources the way the game resolves them: Vine & Barrel's own resources first
// (hand-written, then generated), then the vanilla client jar from the Gradle cache.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { unzipSync } from 'fflate';

export const REPO = path.resolve(import.meta.dirname, '../../..');
const MOD_ROOTS = [path.join(REPO, 'common/src/main/resources'), path.join(REPO, 'common/src/generated/resources')];

function findClientJar() {
	if (process.env.MC_CLIENT_JAR) return process.env.MC_CLIENT_JAR;
	const props = fs.readFileSync(path.join(REPO, 'gradle.properties'), 'utf8');
	const mc = /^minecraft_version\s*=\s*(.+)$/m.exec(props)?.[1].trim();
	const dir = path.join(os.homedir(), '.gradle/caches/neoformruntime/artifacts');
	const jar = path.join(dir, `minecraft_${mc}_client.jar`);
	if (!fs.existsSync(jar)) {
		throw new Error(`Vanilla client jar not found at ${jar}. Run any Gradle task of the mod once (it downloads the jar) or set MC_CLIENT_JAR.`);
	}
	return jar;
}

let vanilla;
function vanillaFiles() {
	if (!vanilla) {
		const wanted = /^assets\/minecraft\/(models|textures\/(block|item|gui|mob_effect|entity\/signs)|items|blockstates|lang\/en_us\.json)|^data\/minecraft\/context_(int|float)_provider\//;
		vanilla = unzipSync(fs.readFileSync(findClientJar()), { filter: (f) => wanted.test(f.name) });
	}
	return vanilla;
}

/** Returns the raw bytes of an asset path such as "assets/minecraft/models/block/block.json", or null. */
export function readAsset(rel) {
	for (const root of MOD_ROOTS) {
		const p = path.join(root, rel);
		if (fs.existsSync(p)) return fs.readFileSync(p);
	}
	const v = vanillaFiles()[rel];
	return v ? Buffer.from(v) : null;
}

export function readJson(rel) {
	const b = readAsset(rel);
	return b ? JSON.parse(b.toString('utf8')) : null;
}

/** "farmersdelight:block/stove" + kind "models" -> "assets/farmersdelight/models/block/stove.json" */
export function idPath(id, kind, ext) {
	const [ns, p] = id.includes(':') ? id.split(':') : ['minecraft', id];
	return `assets/${ns}/${kind}/${p}${ext}`;
}

export function vanillaLang() {
	return JSON.parse(Buffer.from(vanillaFiles()['assets/minecraft/lang/en_us.json']).toString('utf8'));
}

/** Vanilla data file (e.g. "data/minecraft/context_int_provider/compostable/low.json") as JSON, or null. */
export function vanillaData(rel) {
	const v = vanillaFiles()[rel];
	return v ? JSON.parse(Buffer.from(v).toString('utf8')) : null;
}

/**
 * Vanilla's lang file for another locale ("de_de"), or null. Only en_us is in the client jar; the others are asset
 * objects, which Gradle keeps in its asset cache. The asset index is the one the mod's Minecraft version names
 * (assetIndex.id in its version JSON), never just the newest. MC_ASSETS overrides the asset dir.
 */
export function vanillaLocaleLang(locale) {
	const mc = /^minecraft_version\s*=\s*(.+)$/m.exec(fs.readFileSync(path.join(REPO, 'gradle.properties'), 'utf8'))?.[1].trim();
	const caches = path.join(os.homedir(), '.gradle/caches');
	const versionJson = path.join(caches, `neoformruntime/artifacts/minecraft_${mc}_version_manifest.json`);
	const indexId = fs.existsSync(versionJson) ? JSON.parse(fs.readFileSync(versionJson, 'utf8')).assetIndex?.id : null;
	if (!indexId) {
		console.warn(`No version JSON for Minecraft ${mc} at ${versionJson}: no vanilla ${locale} names (run any Gradle task of the mod once)`);
		return null;
	}
	const dirs = [process.env.MC_ASSETS, path.join(caches, 'neoformruntime/assets'), path.join(caches, 'fabric-loom/assets')].filter(Boolean);
	for (const dir of dirs) {
		// NeoForm names the index "<id>.json", Loom "<version>-<id>.json"
		for (const name of [`${indexId}.json`, `${mc}-${indexId}.json`]) {
			const f = path.join(dir, 'indexes', name);
			if (!fs.existsSync(f)) continue;
			const hash = JSON.parse(fs.readFileSync(f, 'utf8')).objects?.[`minecraft/lang/${locale}.json`]?.hash;
			const obj = hash && path.join(dir, 'objects', hash.slice(0, 2), hash);
			if (obj && fs.existsSync(obj)) return JSON.parse(fs.readFileSync(obj, 'utf8'));
		}
	}
	console.warn(`Asset index ${indexId} (Minecraft ${mc}) or its ${locale} lang file isn't in the Gradle asset cache: no vanilla ${locale} names`);
	return null;
}
