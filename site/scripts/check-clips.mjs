// Checks the wiki's clips (src/data/clips.json): every listed clip has its WebM, MP4 and poster, nothing unlisted lies
// in public/clips, and the files stay small. Warns (doesn't fail) when something a clip shows changed since it was
// filmed: each entry names the commit it was recorded at and the repo paths it shows ("watch", git pathspecs).
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const SITE = path.resolve(import.meta.dirname, '..');
const REPO = path.resolve(SITE, '..');
const clips = JSON.parse(fs.readFileSync(path.join(SITE, 'src/data/clips.json'), 'utf8'));
const MAX_CLIP = 700 * 1024;
const MAX_TOTAL = 8 * 1024 * 1024;
const kb = (n) => `${Math.round(n / 1024)} KB`;

let errors = 0;
let total = 0;
const err = (m) => (errors++, console.log('clip error:', m));
for (const [name, c] of Object.entries(clips)) {
	for (const f of [`public/clips/${name}.webm`, `public/clips/${name}.mp4`, `src/assets/clips/${name}.jpg`]) {
		const p = path.join(SITE, f);
		if (!fs.existsSync(p)) {
			err(`${name}: ${f} is missing`);
			continue;
		}
		const size = fs.statSync(p).size;
		if (f.startsWith('public')) {
			total += size;
			if (size > MAX_CLIP) err(`${name}: ${f} is ${kb(size)} (max ${kb(MAX_CLIP)})`);
		}
	}
	if (!c.commit || !c.watch?.length) err(`${name}: needs "commit" and "watch" in clips.json`);
	else {
		let changed = '';
		try {
			changed = execFileSync('git', ['log', '--format=%h %s', `${c.commit}..HEAD`, '--', ...c.watch.map((w) => `:(glob)${w}`)], { cwd: REPO, encoding: 'utf8' }).trim();
		} catch {
			console.log(`clip warning: ${name}: can't compare with ${c.commit} (not in this history?)`);
		}
		if (changed) console.log(`clip warning: ${name} may be stale (filmed at ${c.commit}); since then:\n  ${changed.split('\n').slice(0, 5).join('\n  ')}`);
	}
}
for (const f of fs.readdirSync(path.join(SITE, 'public/clips'))) {
	const name = f.replace(/\.(webm|mp4)$/, '');
	if (f !== '.gitkeep' && !clips[name]) err(`public/clips/${f} isn't listed in src/data/clips.json`);
}
if (total > MAX_TOTAL) err(`clips total ${kb(total)} (max ${kb(MAX_TOTAL)})`);
console.log(`${Object.keys(clips).length} clips, ${kb(total)}, ${errors} errors`);
process.exit(errors ? 1 : 0);
