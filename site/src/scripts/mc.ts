// Minecraft-style tooltips for anything with data-tip-items, JEI-like cycling of tag slots, item icons in the site
// search, and the copy buttons ([data-copy]).
const tip = document.createElement('div');
tip.id = 'mc-tooltip';
tip.setAttribute('role', 'tooltip');
document.body.appendChild(tip);
let current: HTMLElement | null = null;

function fill(el: HTMLElement) {
	const entries = (el.dataset.tipItems ?? '').split('|').filter(Boolean);
	if (!entries.length) return false;
	const cyc = el.querySelector('.mc-cycle');
	const idx = cyc ? Math.max(0, [...cyc.querySelectorAll('img')].findIndex((i) => i.classList.contains('on'))) : 0;
	const [name, id] = (entries[idx] ?? entries[0]).split('~');
	tip.replaceChildren();
	const add = (cls: string, text: string) => {
		const d = document.createElement('div');
		d.className = cls;
		d.textContent = text;
		tip.appendChild(d);
	};
	add('t-name', name);
	for (const line of (el.dataset.tipExtra ?? '').split('\n').filter(Boolean)) add('t-blue', line);
	for (const line of (el.dataset.tipBad ?? '').split('\n').filter(Boolean)) add('t-red', line);
	if (el.dataset.tipSub) add('t-gold', el.dataset.tipSub);
	if (id) add('t-id', id);
	return true;
}
function show(el: HTMLElement, x: number, y: number) {
	if (!fill(el)) return;
	place(x, y);
	tip.classList.add('on');
}
/** Shows the tooltip above an element (keyboard focus, taps): no pointer to follow. */
function showAt(el: HTMLElement) {
	const r = el.getBoundingClientRect();
	current = el;
	show(el, r.left + r.width / 2, r.top);
}
function hide() {
	current = null;
	tip.classList.remove('on');
}
function place(x: number, y: number) {
	const r = tip.getBoundingClientRect();
	let left = x + 14;
	let top = y - r.height - 10;
	if (left + r.width > innerWidth - 8) left = x - r.width - 14;
	if (top < 8) top = y + 18;
	tip.style.left = `${Math.max(8, left)}px`;
	tip.style.top = `${top}px`;
}

// Mouse and pen: the tooltip follows the pointer, like in the game
let touch = false;
document.addEventListener('pointerdown', (e) => (touch = e.pointerType === 'touch'), true);
document.addEventListener('pointerover', (e) => {
	if (e.pointerType === 'touch') return;
	const el = (e.target as HTMLElement).closest<HTMLElement>('[data-tip-items]');
	if (el && el !== current) {
		current = el;
		show(el, e.clientX, e.clientY);
	}
	if (!el && current) hide();
});
document.addEventListener('pointermove', (e) => e.pointerType !== 'touch' && current && place(e.clientX, e.clientY));

// Touch: phones have no hover, so the first tap on a slot shows its tooltip and a second tap on it follows its link.
// A tap anywhere else (or scrolling) puts the tooltip away.
document.addEventListener(
	'click',
	(e) => {
		if (!touch) return;
		const el = (e.target as HTMLElement).closest<HTMLElement>('[data-tip-items]');
		if (!el) return hide();
		if (el.closest('[data-copy], input, label')) return;
		if (el.closest('button')) return showAt(el); // a button does its thing and shows what it is
		if (el === current) return; // second tap: follow the link
		if (el.closest('a[href]') && !el.matches('.fd-item')) e.preventDefault(); // inline item links open on the first tap
		showAt(el);
	},
	true,
);
addEventListener('scroll', () => touch && current && hide(), { passive: true });

// Keyboard
document.addEventListener('focusin', (e) => {
	const el = (e.target as HTMLElement).closest<HTMLElement>('[data-tip-items]');
	if (el && !touch) showAt(el);
});
document.addEventListener('focusout', () => !touch && hide());
document.addEventListener('keydown', (e) => e.key === 'Escape' && hide());

// Hanging signs (page titles, the home page sign): pointing at one, or tapping it, sets it swinging again. The
// animation runs on the board and its two chains together; a sign still in its first big swing is left alone.
for (const sign of document.querySelectorAll<HTMLElement>('h1#_top, .vb-hang')) {
	const swing = () => {
		const anims = sign.getAnimations({ subtree: true }).filter((a) => a instanceof CSSAnimation && a.animationName.startsWith('vb-hang'));
		if (anims.some((a) => a.playState === 'running' && +(a.currentTime ?? 0) < 900)) return;
		for (const a of anims) {
			a.currentTime = 0;
			a.play();
		}
	};
	sign.addEventListener('pointerenter', (e) => e.pointerType !== 'touch' && swing());
	sign.addEventListener('click', swing);
}

// Copy buttons: anything with data-copy copies that text and says so in a tooltip
document.addEventListener('click', async (e) => {
	const el = (e.target as HTMLElement).closest<HTMLElement>('[data-copy]');
	if (!el) return;
	e.preventDefault();
	let ok = true;
	try {
		await navigator.clipboard.writeText(el.dataset.copy!);
	} catch {
		ok = false;
	}
	el.classList.add('copied');
	// say so in the tooltip for a moment, then put the element's own tooltip back
	const keys = ['tipItems', 'tipExtra', 'tipSub', 'tipBad'] as const;
	const prev = Object.fromEntries(keys.map((k) => [k, el.dataset[k]]));
	for (const k of keys) delete el.dataset[k];
	el.dataset.tipItems = ok ? `${words().copied.replace('{text}', el.dataset.copy!)}~` : `${words().copyByHand}~`;
	showAt(el);
	setTimeout(() => {
		el.classList.remove('copied');
		for (const k of keys) prev[k] === undefined ? delete el.dataset[k] : (el.dataset[k] = prev[k]);
		if (current === el) hide();
	}, 1400);
});

// Site search: put the item's icon on item results and drop the sub-result that only repeats the page title. Pagefind
// re-renders as you type, so this just re-syncs after every change. Item pages are /items/<id>/, icons <id>.png.
const search = document.getElementById('starlight__search');
const iconBase = `${import.meta.env.BASE_URL.replace(/\/$/, '')}/icons/vineandbarrel/`;
const syncSearch = () => {
	for (const r of search!.querySelectorAll<HTMLElement>('.pagefind-ui__result')) {
		const title = r.querySelector<HTMLElement>('.pagefind-ui__result-title');
		const link = title?.querySelector<HTMLAnchorElement>('.pagefind-ui__result-link');
		if (!title || !link) continue;
		const slug = link.pathname.match(/\/items\/([^/]+)\/$/)?.[1];
		const want = slug ? iconBase + slug + '.png' : null;
		let img = title.querySelector<HTMLImageElement>('img.fd-search-icon');
		if (want && img?.getAttribute('src') !== want) {
			img?.remove();
			img = Object.assign(new Image(32, 32), { className: 'fd-search-icon', alt: '', src: want, onerror: () => img?.remove() });
			title.prepend(img);
		} else if (!want) img?.remove();
		const name = link.textContent?.trim();
		for (const n of r.querySelectorAll<HTMLElement>('.pagefind-ui__result-nested')) {
			n.classList.toggle('fd-dup', n.querySelector('.pagefind-ui__result-link')?.textContent?.trim() === name);
		}
	}
};
if (search) new MutationObserver(syncSearch).observe(search, { childList: true, subtree: true });

// Cycle tag slots once a second, all in step (like JEI). A hovered slot holds still so its tooltip stays right.
// Only slots on screen are cycled: long pages have hundreds, and touching them all every second costs real time.
const visible = new Set<HTMLElement>();
const io = new IntersectionObserver(
	(entries) => {
		for (const e of entries) e.isIntersecting ? visible.add(e.target as HTMLElement) : visible.delete(e.target as HTMLElement);
	},
	{ rootMargin: '100px' },
);
for (const c of document.querySelectorAll<HTMLElement>('.mc-cycle')) io.observe(c);
setInterval(() => {
	if (document.hidden) return;
	for (const c of visible) {
		if (current && current.contains(c)) continue;
		const on = c.querySelector('img.on') ?? c.firstElementChild;
		const next = on?.nextElementSibling?.tagName === 'IMG' ? on.nextElementSibling : c.querySelector('img');
		on?.classList.remove('on');
		next?.classList.add('on');
	}
}, 1000);

/** The page's words for this script, from Head.astro (English if missing). */
function words(): { copied: string; copyByHand: string } {
	try {
		return JSON.parse(document.getElementById('vb-words')!.textContent!);
	} catch {
		return { copied: 'Copied {text}', copyByHand: 'Press Ctrl+C to copy' };
	}
}
