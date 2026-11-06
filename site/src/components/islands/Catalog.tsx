/** @jsxImportSource preact */
import { useEffect, useMemo, useState } from 'preact/hooks';

type Row = {
	id: string;
	slug: string;
	name: string;
	category: string;
	icon: string;
	flat: boolean;
	nutrition: number | null;
	saturation: number | null;
	effects: string[];
	block: boolean;
	stack: number;
	tags: string[];
};
type Cat = { key: string; label: string; blurb: string; count: number };

export default function Catalog({ rows, cats, base }: { rows: Row[]; cats: Cat[]; base: string }) {
	const [q, setQ] = useState('');
	const [active, setActive] = useState<string[]>([]);
	const [food, setFood] = useState(false);
	const [blocks, setBlocks] = useState(false);
	const [sort, setSort] = useState<'tab' | 'name' | 'nutrition' | 'saturation'>('tab');

	useEffect(() => {
		const h = location.hash.slice(1);
		if (h && cats.some((c) => c.key === h)) setActive([h]);
	}, []);

	// Every tile is rendered once and stays in the page; filtering only toggles `hidden` and sorting only reorders.
	// Rebuilding the tiles on each keystroke took ~0.3 s on a mid-range phone, so fast typing queued up and lagged.
	const search = useMemo(() => rows.map((r) => [r.name.toLowerCase(), r.id, ...r.effects.map((e) => e.toLowerCase()), ...r.tags].join('\n')), [rows]);
	const shown = useMemo(() => {
		const words = q.toLowerCase().split(/\s+/).filter(Boolean);
		const set = new Set<string>();
		rows.forEach((r, i) => {
			if ((!active.length || active.includes(r.category)) && (!food || r.nutrition !== null) && (!blocks || r.block) && words.every((w) => search[i].includes(w))) set.add(r.id);
		});
		return set;
	}, [q, active, food, blocks]);
	const byCat = useMemo(() => new Map(cats.map((c) => [c.key, rows.filter((r) => r.category === c.key)])), [rows, cats]);
	const sorted = useMemo(() => {
		if (sort === 'name') return [...rows].sort((a, b) => a.name.localeCompare(b.name));
		if (sort === 'nutrition') return [...rows].sort((a, b) => (b.nutrition ?? -1) - (a.nutrition ?? -1));
		if (sort === 'saturation') return [...rows].sort((a, b) => (b.saturation ?? -1) - (a.saturation ?? -1));
		return rows;
	}, [sort]);

	const grouped = sort === 'tab';
	const toggle = (k: string) => setActive((a) => (a.includes(k) ? a.filter((x) => x !== k) : [...a, k]));

	const tile = (r: Row) => (
		<a class="cat-tile" href={`${base}${r.slug}/`} key={r.id} hidden={!shown.has(r.id)} data-tip-items={`${r.name}~${r.id}`}>
			<span class="cat-icon">
				<img src={r.icon} alt="" width={48} height={48} loading="lazy" style={{ imageRendering: 'pixelated' }} />
			</span>
			<span class="cat-name">{r.name}</span>
			{r.nutrition !== null && (
				<span class="cat-food">
					{r.nutrition} <small>/ {+r.saturation!.toFixed(1)}</small>
				</span>
			)}
		</a>
	);

	return (
		<div class="catalog not-content">
			<div class="cat-bar">
				<input class="cat-search" type="search" placeholder="Filter by name, id, effect or tag…" value={q} onInput={(e) => setQ((e.target as HTMLInputElement).value)} aria-label="Filter items" />
				<select value={sort} onChange={(e) => setSort((e.target as HTMLSelectElement).value as any)} aria-label="Sort">
					<option value="tab">Creative tab order</option>
					<option value="name">Name A–Z</option>
					<option value="nutrition">Most hunger restored</option>
					<option value="saturation">Most saturation</option>
				</select>
				<label class="cat-check">
					<input type="checkbox" checked={food} onChange={() => setFood(!food)} /> Food only
				</label>
				<label class="cat-check">
					<input type="checkbox" checked={blocks} onChange={() => setBlocks(!blocks)} /> Placeable blocks
				</label>
			</div>
			<div class="cat-chips" role="group" aria-label="Categories">
				{cats.map((c) => (
					<button type="button" class={active.includes(c.key) ? 'on' : ''} onClick={() => toggle(c.key)} aria-pressed={active.includes(c.key)} title={c.blurb}>
						{c.label} <span>{c.count}</span>
					</button>
				))}
				{active.length > 0 && (
					<button type="button" class="clear" onClick={() => setActive([])}>
						Clear
					</button>
				)}
			</div>
			<p class="cat-count">
				{shown.size} of {rows.length} items
			</p>
			{grouped ? (
				cats.map((c) => (
					<section key={c.key} id={c.key} hidden={!byCat.get(c.key)!.some((r) => shown.has(r.id))}>
						<h2 class="cat-h">
							{c.label} <span>{c.blurb}</span>
						</h2>
						<div class="cat-grid">{byCat.get(c.key)!.map(tile)}</div>
					</section>
				))
			) : (
				<div class="cat-grid">{sorted.map(tile)}</div>
			)}
			{shown.size === 0 && <p>No items match. Try fewer words, or clear the filters.</p>}
			<style>{`
.catalog { margin-top: 24px; }
.cat-bar { display: flex; flex-wrap: wrap; gap: 10px 16px; align-items: center; position: sticky; top: var(--sl-nav-height); z-index: 5; background: var(--sl-color-black); padding: 12px 0 10px; }
.cat-search { flex: 1 1 18rem; padding: 6px 10px; border: 2px solid #a0a0a0; border-radius: 0; background: #000; color: #e0e0e0; font: inherit; font-size: 20px; }
.cat-search::placeholder { color: #707070; }
.cat-search:focus { outline: none; border-color: #fff; }
.catalog select { padding: 4px 8px; border: 2px solid #a0a0a0; border-radius: 0; background: #000; color: #e0e0e0; font: inherit; font-size: 20px; }
.cat-check { display: inline-flex; gap: 8px; align-items: center; color: var(--sl-color-gray-2); }
.cat-check input { accent-color: #8f3a12; width: 18px; height: 18px; }
.cat-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.cat-chips button { font: inherit; font-size: 20px; color: #fff; text-shadow: 2px 2px 0 #3f3f3f; padding: 2px 12px 4px; border-style: solid; border-width: 6px; border-image: url('/FarmersDelight-Neoforged/ui/button.png') 3 fill / 6px stretch; image-rendering: pixelated; background: none; cursor: pointer; border-radius: 0; }
.cat-chips button:hover { border-image-source: url('/FarmersDelight-Neoforged/ui/button-hover.png'); color: #ffffa0; }
.cat-chips button span { color: #c0c0c0; margin-left: 6px; }
.cat-chips button.on { border-image-source: url('/FarmersDelight-Neoforged/ui/button-hover.png'); color: #ffff55; }
.cat-chips .clear { color: #ffaaaa; }
.cat-count { color: var(--sl-color-gray-3); margin: 12px 0 0; }
.cat-h { font-family: var(--sl-font); font-weight: 400; font-size: 30px; margin: 40px 0 0; display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; scroll-margin-top: 9rem; color: var(--fd-heading-ink, var(--sl-color-white)); }
.cat-h span { font-size: 20px; color: var(--sl-color-gray-3); }
.cat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 4px; margin-top: 12px; padding: 10px; background: #c6c6c6; box-shadow: inset 2px 2px 0 #fff, inset -2px -2px 0 #555, 0 0 0 2px #000, 6px 6px 0 2px #000; }
.cat-tile[hidden], .catalog section[hidden] { display: none; }
.cat-tile { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 10px 4px 8px; text-decoration: none; color: #404040; text-align: center; }
.cat-tile:hover { background: #d8d8d8; }
.cat-tile:hover .cat-name { color: #000; text-decoration: underline; text-decoration-thickness: 2px; text-underline-offset: 4px; }
.cat-icon { width: 64px; height: 64px; display: grid; place-items: center; background: #8b8b8b; box-shadow: inset 4px 4px 0 #373737, inset -4px -4px 0 #fff; }
.cat-icon img { width: 48px; height: 48px; }
.cat-name { font-size: 20px; line-height: 24px; }
.cat-food { font-size: 20px; color: #a06000; }
.cat-food small { color: #707070; font-size: 20px; }
@media (max-width: 30rem) { .cat-grid { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); } }
`}</style>
		</div>
	);
}
