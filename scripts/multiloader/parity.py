#!/usr/bin/env python3
"""
Builds the loader parity checklist (PARITY.md) from the last test runs: every GameTest of the six GameTest runs
(NeoForge and Fabric, each without and with Farmer's Delight and with the compat mods) and every check of the Fabric
smoke tests, one row each, grouped by feature area, so the gaps show at a glance.

    ./gradlew gameTestsAll :fabric:smokeAll          # or scripts/multiloader/verify-all.sh --full
    python scripts/multiloader/parity.py             # writes PARITY.md
    python scripts/multiloader/parity.py --check     # also exits 1 if anything failed or a run is missing

A test that a run doesn't have (an FD test without FD still runs and checks the FD-less behaviour, so this is rare) shows
as "-"; a run whose report is missing shows its whole column as "?".
"""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

GAMETEST_RUNS = [
    ('NeoForge', ROOT / 'neoforge/build/test-reports/gametest.xml'),
    ('NeoForge + FD', ROOT / 'neoforge/build/test-reports/gametest-fd.xml'),
    ('Fabric', ROOT / 'fabric/build/test-reports/gametest.xml'),
    ('Fabric + FD Refabricated', ROOT / 'fabric/build/test-reports/gametest-fd.xml'),
    ('NeoForge compat mods', ROOT / 'neoforge/build/test-reports/gametest-compat.xml'),
    ('Fabric compat mods', ROOT / 'fabric/build/test-reports/gametest-compat.xml'),
]
SMOKE_RUNS = [
    ('Fabric client', ROOT / 'fabric/run/smoke-client/smoke/results.json'),
    ('Fabric client + FD Refabricated', ROOT / 'fabric/run/smoke-client-fd/smoke/results.json'),
    ('Fabric client over the network', ROOT / 'fabric/run/smoke-join/smoke/results.json'),
    ('Fabric client over the network + FD Refabricated', ROOT / 'fabric/run/smoke-join-fd/smoke/results.json'),
    ('Fabric LAN host', ROOT / 'fabric/run/smoke-host/smoke/results.json'),
    ('Fabric LAN host + FD Refabricated', ROOT / 'fabric/run/smoke-host-fd/smoke/results.json'),
    ('Fabric client on a dedicated server', ROOT / 'fabric/run/smoke-join-server/smoke/results.json'),
    ('Fabric client on a dedicated server + FD Refabricated', ROOT / 'fabric/run/smoke-join-server-fd/smoke/results.json'),
    ('Fabric dedicated server', ROOT / 'fabric/run/server-smoke/smoke/results.json'),
    ('Fabric dedicated server + FD Refabricated', ROOT / 'fabric/run/server-smoke/smoke-fd/results.json'),
    ('Fabric client with V&B on a Fabric server without it', ROOT / 'fabric/run/smoke-visit/smoke/results.json'),
    ('Fabric server without V&B (only Fabric API)', ROOT / 'fabric/run/server-smoke/smoke-bare/results.json'),
    ('Client without V&B or Fabric API (vanilla on the wire)', ROOT / 'fabric/run/bare-refused/smoke/results.json'),
    ('Fabric dedicated server with V&B refusing that client', ROOT / 'fabric/run/server-smoke/smoke-refusal/results.json'),
]

# Feature areas by test name prefix (the GameTests are named <area>_<what>)
AREAS = [
    ('skeleton', 'Setup and harness'), ('polish', 'Names, tab, polish'), ('crops', 'Crops, wild crops, worldgen of crops'),
    ('farm', 'Farming (animals, bees, foxes, composting)'), ('kettle', 'Brew Kettle'), ('base', 'Bases (juices, worts)'),
    ('juice', 'Bases (juices, worts)'), ('barrel', 'Aging Barrel'), ('drinks', 'Drinks, placing, cheers'), ('tipsy', 'Tipsy'),
    ('blackout', 'Blackout endings'), ('compat', 'Other mods (AppleSkin, thirst, nutrition, Spice of Life)'),
    ('fd', "Farmer's Delight hooks"), ('world', 'Villages, taverns, trades, loot'), ('advancement', 'Advancements'),
    ('advancements', 'Advancements'),
]

# What the automated checks don't cover yet, kept by hand (shown under the tables)
KNOWN_GAPS = [
    ('Kettle recipe book clicks', 'Both loaders have the kettle\'s own book type (its settings saved apart, checked by a GameTest) and search tab; clicking through the book itself is not automated on either loader.'),
    ('Protection mods cancelling a placement', 'NeoForge only (drinks_cancelled_place_keeps_no_duplicate); Fabric has no generic place event.'),
    ('Crop growth vetoes', 'NeoForge mods can veto or watch our crops growing (crop grow events); Fabric has no such events, so there our crops just grow.'),
    ('Foxes picking grapes and mob griefing', 'NeoForge mods can veto it per fox with the mob griefing event; Fabric has no such event, so there only the mobGriefing game rule counts.'),
    ('Data maps on Fabric', 'Copper kettle weathering and waxing and the farmer composting barley seeds are data maps on NeoForge (a datapack can change them); Fabric registers the same values in code at start.'),
    ('Nutritional Balance on Fabric', 'It has no Fabric build: our c:nutrients tags ship on both loaders, but only the NeoForge compat run can ask the mod itself.'),
    ('Compat runs', 'They run only the compat_* tests (the other tests\' fake players have no network channels, and the thirst mods sync to every player).'),
    ('Item tooltips by mouse hover', 'The smoke test hovers the effect list (Tipsy\'s tooltip), not items; the item tooltip code is the same common method on both loaders.'),
    ('NeoForge client', 'No automated client smoke test on NeoForge: Jexxme playtests NeoForge with FD by hand; the wiki screenshot tool covers some.'),
]


FOREIGN = "Farmer's Delight's own GameTests (FD Neoforged ships them; not ours)"


def area_of(test):
    if ':' in test and not test.startswith('vineandbarrel:'):
        return FOREIGN
    name = test.split(':', 1)[-1]
    for prefix, title in AREAS:
        if name.startswith(prefix + '_') or name == prefix:
            return title
    return 'Other'


def read_gametests(path):
    if not path.is_file():
        return None
    results = {}
    for case in ET.parse(path).getroot().iter('testcase'):
        name = case.get('name')
        failure = case.find('failure')
        skipped = case.find('skipped')
        results[name] = ('fail', failure.get('message')) if failure is not None else ('skip', skipped.get('message')) if skipped is not None else ('ok', None)
    return results


def read_smoke(path):
    if not path.is_file():
        return None
    report = json.loads(path.read_text(encoding='utf-8'))
    results = {c['check']: ('ok', None) if c['ok'] else ('fail', c.get('problem')) for c in report.get('checks', [])}
    if not report.get('completed', False):
        results['(run completed)'] = ('fail', 'the smoke run did not complete')
    return results


MARK = {'ok': '✅', 'fail': '❌', 'skip': '⚠️'}


def table(runs, title):
    lines = []
    names = sorted({name for _, results in runs if results for name in results})
    header = '| ' + title + ' | ' + ' | '.join(label for label, _ in runs) + ' |'
    lines.append(header)
    lines.append('|' + '---|' * (len(runs) + 1))
    problems = []
    for name in names:
        cells = []
        for label, results in runs:
            if results is None:
                cells.append('?')
            elif name not in results:
                cells.append('-')
            else:
                state, message = results[name]
                cells.append(MARK[state])
                if state == 'fail':
                    problems.append(f'{label}: {name}: {message}')
        lines.append(f'| `{name.split(":", 1)[-1]}` | ' + ' | '.join(cells) + ' |')
    return lines, problems


def main(argv):
    check = '--check' in argv
    out = ROOT / 'PARITY.md'
    gametests = [(label, read_gametests(path)) for label, path in GAMETEST_RUNS]
    smoke = [(label, read_smoke(path)) for label, path in SMOKE_RUNS]

    lines = ['# Loader parity', '',
             'Generated by `scripts/multiloader/parity.py` from the last runs of `./gradlew gameTestsAll :fabric:smokeAll` '
             '(see `scripts/multiloader/verify-all.sh --full`). ✅ passed, ❌ failed, ⚠️ skipped, - not in that run, ? run missing.', '']
    lines.append('## Summary')
    lines.append('')
    lines.append('| Run | Passed | Failed | Total |')
    lines.append('|---|---|---|---|')
    missing = []
    for label, results in gametests + smoke:
        if results is None:
            lines.append(f'| {label} | ? | ? | not run |')
            missing.append(label)
        else:
            passed = sum(1 for s, _ in results.values() if s == 'ok')
            failed = sum(1 for s, _ in results.values() if s == 'fail')
            lines.append(f'| {label} | {passed} | {failed} | {len(results)} |')
    lines.append('')

    all_problems = []
    lines.append('## GameTests by feature area')
    lines.append('')
    by_area = {}
    for label, results in gametests:
        for name in (results or {}):
            by_area.setdefault(area_of(name), set()).add(name)
    order = list(dict.fromkeys(title for _, title in AREAS)) + ['Other', FOREIGN]
    for area in [a for a in order if a in by_area]:
        runs = [(label, {k: v for k, v in results.items() if k in by_area[area]} if results is not None else None) for label, results in gametests]
        rows, problems = table(runs, area + f' ({len(by_area[area])})')
        lines.append('### ' + area)
        lines.append('')
        lines.extend(rows)
        lines.append('')
        all_problems.extend(problems)

    lines.append('## Smoke tests (Fabric, clients in hidden windows)')
    lines.append('')
    rows, problems = table(smoke, 'Check')
    lines.extend(rows)
    lines.append('')
    all_problems.extend(problems)

    lines.append('## Not covered by the automated checks')
    lines.append('')
    for what, why in KNOWN_GAPS:
        lines.append(f'- **{what}:** {why}')
    lines.append('')
    if all_problems:
        lines.append('## Failures')
        lines.append('')
        lines.extend('- ' + p for p in all_problems)
        lines.append('')

    out.write_text('\n'.join(lines), encoding='utf-8', newline='\n')
    print(f'wrote {out}: {len(all_problems)} failures, {len(missing)} runs missing')
    for p in all_problems:
        print('  FAILED ' + p)
    for m in missing:
        print('  MISSING ' + m)
    return 1 if check and (all_problems or missing) else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
