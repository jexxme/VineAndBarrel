// A small software renderer for Minecraft JSON models. It resolves parents and texture variables, applies
// element, blockstate and display rotations exactly as the game's model format defines them, and rasterises the
// textured quads with a z-buffer and nearest-neighbour sampling (so pixels stay crisp). Used to make the item icons
// (GUI transform) and the isometric block scenes on the docs site.
import sharp from 'sharp';
import { readAsset, readJson, idPath } from './assets.mjs';

const textureCache = new Map();
// Farmer's Delight's own rope art (the vines-on-rope blocks): the wiki doesn't show FD's textures, so a model that needs
// one of these can't be rendered here
const FD_DERIVED = /^vineandbarrel:block\/(rope_top|\w+_coiled_rope|\w+_on_rope_\w+)$/;
async function loadTexture(id) {
	if (FD_DERIVED.test(id)) throw new Error(`${id} is Farmer's Delight art (see NOTICE_farmersdelight.txt); the wiki doesn't show it`);
	if (textureCache.has(id)) return textureCache.get(id);
	const buf = readAsset(idPath(id, 'textures', '.png'));
	let tex = null;
	if (buf) {
		const { data, info } = await sharp(buf).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
		// Animated textures are vertical strips: use the first frame
		const w = info.width;
		const h = Math.min(info.height, info.width);
		tex = { w, h, data };
	}
	textureCache.set(id, tex);
	return tex;
}

const modelCache = new Map();
/** Fully resolved model: { textures, elements, display, generated, gui_light } */
export function resolveModel(id) {
	if (modelCache.has(id)) return modelCache.get(id);
	const chain = [];
	let cur = id.includes(':') ? id : `minecraft:${id}`;
	let generated = false;
	while (cur) {
		if (cur === 'minecraft:builtin/generated' || cur === 'builtin/generated') {
			generated = true;
			break;
		}
		const json = readJson(idPath(cur, 'models', '.json'));
		if (!json) throw new Error(`Missing model ${cur} (from ${id})`);
		chain.push(json);
		cur = json.parent ? (json.parent.includes(':') ? json.parent : `minecraft:${json.parent}`) : null;
	}
	const textures = {};
	const display = {};
	let elements = null;
	let gui_light = 'side';
	for (const m of [...chain].reverse()) {
		Object.assign(textures, m.textures ?? {});
		for (const [k, v] of Object.entries(m.display ?? {})) display[k] = v;
		if (m.gui_light) gui_light = m.gui_light;
	}
	for (const m of chain) {
		if (m.elements) {
			elements = m.elements;
			break;
		}
	}
	const model = { id, textures, elements, display, generated: generated && !elements, gui_light };
	modelCache.set(id, model);
	return model;
}

function resolveTexture(model, ref) {
	// 26.x textures may be objects ({ sprite, force_translucent })
	const sprite = (x) => (x && typeof x === 'object' ? x.sprite : x);
	let r = sprite(ref);
	for (let i = 0; i < 10 && r && r.startsWith('#'); i++) r = sprite(model.textures[r.slice(1)]);
	if (!r || r.startsWith('#')) return null;
	return r.includes(':') ? r : `minecraft:${r}`;
}

// ---------------------------------------------------------------- math
const rad = (d) => (d * Math.PI) / 180;
function rotX(v, a) {
	const c = Math.cos(a), s = Math.sin(a);
	return [v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c];
}
function rotY(v, a) {
	const c = Math.cos(a), s = Math.sin(a);
	return [v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c];
}
function rotZ(v, a) {
	const c = Math.cos(a), s = Math.sin(a);
	return [v[0] * c - v[1] * s, v[0] * s + v[1] * c, v[2]];
}
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = (a) => {
	const l = Math.hypot(a[0], a[1], a[2]) || 1;
	return [a[0] / l, a[1] / l, a[2] / l];
};

// Corners (top-left, top-right, bottom-left of the texture) of each face, as seen from outside the element
function faceCorners(dir, f, t) {
	const [x1, y1, z1] = f, [x2, y2, z2] = t;
	switch (dir) {
		case 'north': return [[x2, y2, z1], [x1, y2, z1], [x2, y1, z1]];
		case 'south': return [[x1, y2, z2], [x2, y2, z2], [x1, y1, z2]];
		case 'west': return [[x1, y2, z1], [x1, y2, z2], [x1, y1, z1]];
		case 'east': return [[x2, y2, z2], [x2, y2, z1], [x2, y1, z2]];
		case 'up': return [[x1, y2, z1], [x2, y2, z1], [x1, y2, z2]];
		case 'down': return [[x1, y1, z2], [x2, y1, z2], [x1, y1, z1]];
	}
}
function defaultUv(dir, f, t) {
	const [x1, y1, z1] = f, [x2, y2, z2] = t;
	switch (dir) {
		case 'down': return [x1, 16 - z2, x2, 16 - z1];
		case 'up': return [x1, z1, x2, z2];
		case 'north': return [16 - x2, 16 - y2, 16 - x1, 16 - y1];
		case 'south': return [x1, 16 - y2, x2, 16 - y1];
		case 'west': return [z1, 16 - y2, z2, 16 - y1];
		case 'east': return [16 - z2, 16 - y2, 16 - z1, 16 - y1];
	}
}

/**
 * Turns a model into world-space quads (block units, origin at the block's corner) with an optional blockstate
 * rotation (x, y in degrees) and offset (in blocks).
 */
async function modelQuads(model, { x = 0, y = 0, offset = [0, 0, 0], tint = null, uvlock = false } = {}) {
	const quads = [];
	if (!model.elements) return quads;
	for (const el of model.elements) {
		const r = el.rotation;
		for (const [dir, face] of Object.entries(el.faces ?? {})) {
			const texId = resolveTexture(model, face.texture);
			if (!texId) continue;
			const tex = await loadTexture(texId);
			if (!tex) continue;
			let pts = faceCorners(dir, el.from, el.to);
			if (r && r.angle) {
				const o = r.origin ?? [8, 8, 8];
				const a = rad(r.angle);
				const sc = r.rescale ? 1 / Math.cos(a) : 1;
				pts = pts.map((p) => {
					let v = sub(p, o);
					if (r.axis === 'x') v = rotX(v, a), (v = [v[0], v[1] * sc, v[2] * sc]);
					if (r.axis === 'y') v = rotY(v, a), (v = [v[0] * sc, v[1], v[2] * sc]);
					if (r.axis === 'z') v = rotZ(v, a), (v = [v[0] * sc, v[1] * sc, v[2]]);
					return add(v, o);
				});
			}
			// Blockstate rotation: clockwise when viewed from the positive axis, around the block centre
			if (x || y) {
				pts = pts.map((p) => {
					let v = sub(p, [8, 8, 8]);
					if (x) v = rotX(v, -rad(x));
					if (y) v = rotY(v, -rad(y));
					return add(v, [8, 8, 8]);
				});
			}
			pts = pts.map((p) => [p[0] / 16 + offset[0], p[1] / 16 + offset[1], p[2] / 16 + offset[2]]);
			const uv = face.uv ?? defaultUv(dir, el.from, el.to);
			const tintColor = face.tintindex !== undefined && face.tintindex >= 0 ? tint?.[face.tintindex] ?? tint?.[0] ?? null : null;
			quads.push({ pts, uv, rot: face.rotation ?? 0, tex, shade: el.shade !== false, emission: el.light_emission ?? 0, tint: tintColor });
		}
	}
	return quads;
}

function applyView(quads, { rotation, translation = [0, 0, 0], scale = [1, 1, 1], center }) {
	const [rx, ry, rz] = rotation.map(rad);
	return quads.map((q) => {
		const pts = q.pts.map((p) => {
			let v = sub(p, center);
			v = [v[0] * scale[0], v[1] * scale[1], v[2] * scale[2]];
			v = rotZ(v, rz);
			v = rotY(v, ry);
			v = rotX(v, rx);
			return add(v, translation);
		});
		const n = norm(cross(sub(pts[2], pts[0]), sub(pts[1], pts[0])));
		return { ...q, pts, n };
	});
}

// View-space brightness: top faces brightest, the left face lighter than the right (the look of the inventory)
function brightness(n) {
	return Math.max(0.35, Math.min(1, 0.75 + 0.25 * n[1] - 0.18 * n[0]));
}

// light: the scene's light level as a 0..1 brightness (default 1, full daylight). A face of an element with
// light_emission is lit at least that much, like the game's lightmap: 15 renders fullbright in the dark.
function rasterize(quads, size, project, light = 1) {
	const color = new Float32Array(size * size * 4);
	const depth = new Float32Array(size * size).fill(-Infinity);
	for (const q of quads) {
		const P = q.pts.map(project); // screen [sx, sy, z]
		const e1 = sub(P[1], P[0]);
		const e2 = sub(P[2], P[0]);
		const det = e1[0] * e2[1] - e1[1] * e2[0];
		if (Math.abs(det) < 1e-9) continue;
		// Back faces are not drawn (front faces point to the viewer, +z)
		if (q.n[2] <= 1e-6) continue;
		const P3 = add(P[1], e2);
		const xs = [P[0][0], P[1][0], P[2][0], P3[0]];
		const ys = [P[0][1], P[1][1], P[2][1], P3[1]];
		const minX = Math.max(0, Math.floor(Math.min(...xs)));
		const maxX = Math.min(size - 1, Math.ceil(Math.max(...xs)));
		const minY = Math.max(0, Math.floor(Math.min(...ys)));
		const maxY = Math.min(size - 1, Math.ceil(Math.max(...ys)));
		const b = (q.shade ? brightness(q.n) : 1) * Math.max(light, (q.emission ?? 0) / 15);
		const { tex, uv, rot } = q;
		const [u0, v0, u1, v1] = uv;
		for (let py = minY; py <= maxY; py++) {
			for (let px = minX; px <= maxX; px++) {
				const dx = px + 0.5 - P[0][0];
				const dy = py + 0.5 - P[0][1];
				let s = (dx * e2[1] - dy * e2[0]) / det;
				let t = (e1[0] * dy - e1[1] * dx) / det;
				if (s < 0 || s > 1 || t < 0 || t > 1) continue;
				const z = P[0][2] + s * e1[2] + t * e2[2];
				const di = py * size + px;
				if (z <= depth[di]) continue;
				let ss = s, tt = t;
				if (rot === 90) [ss, tt] = [t, 1 - s];
				else if (rot === 180) [ss, tt] = [1 - s, 1 - t];
				else if (rot === 270) [ss, tt] = [1 - t, s];
				const u = u0 + ss * (u1 - u0);
				const v = v0 + tt * (v1 - v0);
				let tx = Math.floor((u / 16) * tex.w - (u1 < u0 ? 1e-4 : -1e-4));
				let ty = Math.floor((v / 16) * tex.h - (v1 < v0 ? 1e-4 : -1e-4));
				tx = Math.min(tex.w - 1, Math.max(0, tx));
				ty = Math.min(tex.h - 1, Math.max(0, ty));
				const ti = (ty * tex.w + tx) * 4;
				const a = tex.data[ti + 3];
				if (a < 128) continue;
				let r = tex.data[ti], g = tex.data[ti + 1], bl = tex.data[ti + 2];
				if (q.tint) {
					r = (r * q.tint[0]) / 255;
					g = (g * q.tint[1]) / 255;
					bl = (bl * q.tint[2]) / 255;
				}
				depth[di] = z;
				color[di * 4] = r * b;
				color[di * 4 + 1] = g * b;
				color[di * 4 + 2] = bl * b;
				color[di * 4 + 3] = 255;
			}
		}
	}
	const out = Buffer.alloc(size * size * 4);
	for (let i = 0; i < out.length; i++) out[i] = Math.round(Math.min(255, color[i]));
	return out;
}

/** A flat item sprite (item/generated) composited from its layers, scaled up with nearest neighbour. */
async function renderGenerated(model, size, tints) {
	const layers = [];
	for (let i = 0; i < 8; i++) {
		const id = resolveTexture(model, `#layer${i}`);
		if (!id) break;
		const tex = await loadTexture(id);
		if (tex) layers.push({ tex, tint: tints?.[i] ?? null });
	}
	if (!layers.length) return null;
	const base = layers[0].tex.w;
	const out = Buffer.alloc(size * size * 4);
	for (let py = 0; py < size; py++) {
		for (let px = 0; px < size; px++) {
			let r = 0, g = 0, b = 0, a = 0;
			for (const { tex, tint } of layers) {
				const tx = Math.floor((px / size) * tex.w);
				const ty = Math.floor((py / size) * tex.h);
				const ti = (ty * tex.w + tx) * 4;
				const ta = tex.data[ti + 3] / 255;
				if (!ta) continue;
				let tr = tex.data[ti], tg = tex.data[ti + 1], tb = tex.data[ti + 2];
				if (tint) (tr = (tr * tint[0]) / 255), (tg = (tg * tint[1]) / 255), (tb = (tb * tint[2]) / 255);
				r = tr * ta + r * (1 - ta);
				g = tg * ta + g * (1 - ta);
				b = tb * ta + b * (1 - ta);
				a = ta + a * (1 - ta);
			}
			const i = (py * size + px) * 4;
			out[i] = r, out[i + 1] = g, out[i + 2] = b, out[i + 3] = Math.round(a * 255);
		}
	}
	return { data: out, pixel: base };
}

const DEFAULT_GUI = { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625] };

/**
 * Renders an inventory icon of a model the way the GUI shows it. Returns PNG bytes, or null if unrenderable.
 * `size` is the output size; flat sprites are scaled by whole pixels.
 */
export async function renderIcon(modelId, size, tints) {
	const model = resolveModel(modelId);
	if (model.generated) {
		const g = await renderGenerated(model, size, tints);
		return g ? sharp(g.data, { raw: { width: size, height: size, channels: 4 } }).png().toBuffer() : null;
	}
	if (!model.elements) return null;
	const gui = { ...DEFAULT_GUI, ...(model.display.gui ?? {}) };
	const quads = applyView(await modelQuads(model, { tint: tints }), {
		rotation: gui.rotation,
		translation: (gui.translation ?? [0, 0, 0]).map((v) => v / 16),
		scale: gui.scale ?? [1, 1, 1],
		center: [0.5, 0.5, 0.5],
	});
	// The GUI slot is one block wide; y points down on screen
	const project = (p) => [(p[0] + 0.5) * size, (0.5 - p[1]) * size, p[2]];
	const data = rasterize(quads, size, project);
	return sharp(data, { raw: { width: size, height: size, channels: 4 } }).png().toBuffer();
}

/**
 * Renders a scene of models placed in the world.
 * parts: [{ model, x?, y?, offset: [bx, by, bz], tint? }]; view rotation defaults to the classic isometric angle.
 */
export async function renderScene(parts, { size = 512, rotation = [30, 225, 0], padding = 0.06, width, height, light = 1 } = {}) {
	let quads = [];
	for (const p of parts) {
		quads.push(...(await modelQuads(resolveModel(p.model), { x: p.x, y: p.y, offset: p.offset ?? [0, 0, 0], tint: p.tint })));
	}
	if (!quads.length) return null;
	const all = quads.flatMap((q) => q.pts);
	const lo = [0, 1, 2].map((i) => Math.min(...all.map((p) => p[i])));
	const hi = [0, 1, 2].map((i) => Math.max(...all.map((p) => p[i])));
	const center = lo.map((v, i) => (v + hi[i]) / 2);
	const viewed = applyView(quads, { rotation, center });
	const vp = viewed.flatMap((q) => q.pts);
	const minX = Math.min(...vp.map((p) => p[0])), maxX = Math.max(...vp.map((p) => p[0]));
	const minY = Math.min(...vp.map((p) => p[1])), maxY = Math.max(...vp.map((p) => p[1]));
	const W = width ?? size, H = height ?? size;
	const scale = Math.min((W * (1 - 2 * padding)) / (maxX - minX), (H * (1 - 2 * padding)) / (maxY - minY));
	const cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
	const S = Math.max(W, H);
	const project = (p) => [(p[0] - cx) * scale + S / 2, (cy - p[1]) * scale + S / 2, p[2]];
	const data = rasterize(viewed, S, project, light);
	let img = sharp(data, { raw: { width: S, height: S, channels: 4 } });
	if (W !== S || H !== S) img = sharp(await img.png().toBuffer()).extract({ left: Math.round((S - W) / 2), top: Math.round((S - H) / 2), width: W, height: H });
	return img.png().toBuffer();
}

/** Picks a single model id out of an item definition's model tree (the "plain" look, like the inventory). */
export function itemDefinitionModel(def) {
	const walk = (m) => {
		if (!m) return null;
		switch (m.type) {
			case 'minecraft:model':
			case 'model':
				return { model: m.model, tints: (m.tints ?? []).map(tintColor) };
			case 'minecraft:condition':
			case 'condition':
				return walk(m.on_false) ?? walk(m.on_true);
			case 'minecraft:select':
			case 'select':
				return walk(m.fallback) ?? walk(m.cases?.[0]?.model);
			case 'minecraft:range_dispatch':
			case 'range_dispatch':
				return walk(m.fallback) ?? walk(m.entries?.[0]?.model);
			case 'minecraft:composite':
			case 'composite':
				return walk(m.models?.[0]);
			default:
				return m.model && typeof m.model === 'string' ? { model: m.model, tints: [] } : walk(m.model);
		}
	};
	return walk(def.model);
}

function tintColor(t) {
	const fromInt = (v) => [(v >> 16) & 255, (v >> 8) & 255, v & 255];
	if (!t) return null;
	if (t.type?.endsWith('constant')) return Array.isArray(t.value) ? t.value.map((x) => x * 255) : fromInt(t.value);
	if (t.type?.endsWith('grass')) return [0x91, 0xbd, 0x59];
	if (t.type?.endsWith('dye') || t.type?.endsWith('firework') || t.type?.endsWith('potion')) return t.default !== undefined ? fromInt(t.default) : null;
	return t.default !== undefined ? (Array.isArray(t.default) ? t.default.map((x) => x * 255) : fromInt(t.default)) : null;
}

export function blockstateModel(blockId, wanted = {}) {
	const bs = readJson(idPath(blockId, 'blockstates', '.json'));
	if (!bs) return null;
	if (bs.variants) {
		const entries = Object.entries(bs.variants);
		const score = (key) => {
			if (key === '') return 0;
			const kv = Object.fromEntries(key.split(',').map((s) => s.split('=')));
			let sc = 0;
			for (const [k, v] of Object.entries(wanted)) if (kv[k] === String(v)) sc += 10;
			for (const [k, v] of Object.entries(kv)) if (wanted[k] !== undefined && wanted[k] !== v) sc -= 100;
			return sc;
		};
		entries.sort((a, b) => score(b[0]) - score(a[0]));
		const v = entries[0][1];
		const pick = Array.isArray(v) ? v[0] : v;
		return [{ model: pick.model, x: pick.x, y: pick.y }];
	}
	if (bs.multipart) {
		const parts = [];
		for (const part of bs.multipart) {
			const when = part.when;
			const ok = !when || Object.entries(when).every(([k, v]) => k === 'OR' || k === 'AND' ? false : String(v).split('|').includes(String(wanted[k] ?? '')));
			if (!when || ok) {
				const a = Array.isArray(part.apply) ? part.apply[0] : part.apply;
				parts.push({ model: a.model, x: a.x, y: a.y });
			}
		}
		return parts;
	}
	return null;
}
