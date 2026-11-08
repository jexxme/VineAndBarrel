// Reads Minecraft resources the way the game resolves them: Vine & Barrel's own resources first
// (hand-written, then generated), then the vanilla client jar from the Gradle cache.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { unzipSync } from 'fflate';

export const REPO = path.resolve(import.meta.dirname, '../../..');
const MOD_ROOTS = [path.join(REPO, 'src/main/resources'), path.join(REPO, 'src/generated/resources')];

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
