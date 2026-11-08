# Contributing

## Building and testing

JDK 21+ for the shell; Gradle runs on a JDK 25 it finds or downloads (`gradle/gradle-daemon-jvm.properties`).

One mod, two loaders, laid out like the MultiLoader template: `common/` holds everything that only needs vanilla
Minecraft (compiled against vanilla, so nothing loader-specific creeps in), `neoforge/` and `fabric/` the loader glue.
Both jars contain the common code.

```bash
./gradlew build                          # both jars (neoforge/build/libs, fabric/build/libs), plus the static checks
./gradlew runData                        # regenerate common/src/generated/resources (commit the result)
./gradlew :neoforge:runGameTestServer    # in-world tests on NeoForge, headless
./gradlew :fabric:runGameTest            # the same tests on Fabric
./gradlew gameTestsAll                   # NeoForge without and with Farmer's Delight and the compat run, Fabric without and with FD Refabricated
bash scripts/multiloader/verify-all.sh          # quick: after each round of changes
bash scripts/multiloader/verify-all.sh --full   # everything: before merging and before tagging
```

`verify-all.sh` has two tiers:

- **Quick** (the default, about 4 minutes): `build`, the datagen check (only when the data generators or
  `common/src/generated` changed since its last green run), the GameTests on NeoForge and Fabric without Farmer's
  Delight, and the singleplayer Fabric smoke test. A GameTest or smoke run whose inputs are unchanged since its last
  green run (`build/verify-all`) is not run again. Run it as often as you like.
- **Full** (`--full`, about 15 minutes): everything, nothing skipped: `build`, datagen, the six GameTest runs (both
  loaders, without and with Farmer's Delight, and the compat run), the six Fabric smoke tests in hidden windows
  (singleplayer, LAN, dedicated server, each without and with FD Refabricated), the cross-version smoke tests and the
  parity checklist (PARITY.md). Run it before merging into `26.3` and before tagging a release.

Both run at most two games at once (the machine's memory is shared), and both print the time of each step. `--fd`
adds the Farmer's Delight GameTests and smoke test to the quick tier; `--no-smoke` leaves out every smoke test (no
game client), which a release can't use.

The same script runs on GitHub's runners, smoke clients included: the Verify workflow (`.github/workflows/verify.yml`).
Push a branch named `ci/quick/<name>` or `ci/full/<name>` and it runs that tier on it, or start it by hand (Actions,
Verify, Run workflow) with a ref and the tier. Its summary, logs, smoke screenshots and PARITY.md are the run's
artifact. That is the merge gate: a green `ci/full/...` run on the commit being merged.

CI runs `build`, `runData` (it fails if `common/src/generated` changes) and the GameTests of both loaders on every
push and pull request, and also fails on any `ERROR` in the test log.

On Fabric the Farmer's Delight run uses Farmer's Delight Refabricated from Modrinth's Maven, nothing to build.

The Farmer's Delight run needs a Farmer's Delight Neoforged jar **for the same Minecraft version**: build the matching
branch of [Farmer's Delight Neoforged](https://github.com/jexxme/FarmersDelight-Neoforged) (`./gradlew jar`) and pass
it with `-PfdJar=<path>` (or `FD_JAR`). Without it the build looks in `../Season4/farmersdelight-port/build/libs`,
which only fits if that checkout is on the same Minecraft version.

## Minecraft versions

Minecraft and NeoForge change too many APIs within 26.x for one jar, so each supported version has its own branch,
the same way as Farmer's Delight Neoforged. Only `26.3` has the multiloader layout and a Fabric jar; the older branches
are NeoForge only, with the single-loader layout (everything in `src/`).

| Branch | Minecraft | NeoForge | Farmer's Delight Neoforged |
|---|---|---|---|
| `26.3` (default) | 26.3 | 26.3.0.101+ | branch `26.3` |
| `26.2` | 26.2 | 26.2.0.93+ | branch `26.2` |
| `26.1.2` | 26.1.2 | 26.1.2.126+ | branch `26.1.2` |
| `26.1` | 26.1, 26.1.1 | 26.1.0.19-beta to 26.1.1 | branch `26.1` |

- Open pull requests against `26.3`. Fixes and features are then backported to each older branch (`26.2`, then
  `26.1.2`, then `26.1`), adapted where the API differs, and only merged there when that branch's tests are green.
- Each older branch changes only what its Minecraft version needs. Keep those changes small and leave the code around
  them as on `26.3`, so the next backport applies cleanly.
- After a backport that touches data generation, run `./gradlew runData` on that branch and commit the result:
  `src/generated` is always written in that branch's own data formats.
- Structure templates (`.nbt`) stay exactly as `26.3` saves them; the older branches convert them while building.
- Content that needs a newer Minecraft (a block or API that doesn't exist in an older version) stays on the branches
  that have it.
- `README.md` and `CONTRIBUTING.md` are the same on every branch: change them on `26.3`, then copy them.

### Backports

Port changes to `26.2`, `26.1.2` and `26.1` **only** with the backport script:

```bash
bash scripts/multiloader/backport.sh <target-branch> <commit-or-range>
```

It maps `common/src/*` and `neoforge/src/*` to `src/*`, refuses changes under `fabric/`, and applies the commits with
`git format-patch` and `git am -3`. If a path doesn't exist on the target, it stops and lists those paths instead of
letting git guess a file. Never cherry-pick across the two layouts by hand: git's rename detection can put an edit in
the wrong file (an edit to `apple_juice.json` once landed in `wheat_wort.json`).

## Versions and releases

The mod version (`0.9.0+neoforged.1`: content version plus build number, like FD Neoforged) is the same on every
branch. On `26.3` the Fabric jar has its own `fabric_mod_version` (`0.9.0+fabric.1`), with the same version before the
`+`. A release is tagged per branch as `<minecraft_version>-<mod_version>`
(for example `26.3-0.9.0+neoforged.1`), which is also the version in the jar name, and `release_game_versions` in
`gradle.properties` lists the Minecraft versions that jar is published for (`26.1` branch: `26.1,26.1.1`). Pushing the
tag runs the release workflow, which builds and tests again and publishes the jars. Release titles name the loader
first: `Vine & Barrel 0.9.0 · NeoForge · Minecraft 26.3`, `Vine & Barrel 0.9.0 · Fabric · Minecraft 26.3`. See
`RELEASING.md`.

## Playtesting

```bash
./gradlew :neoforge:runPlaytest        # a local server (neoforge/run/playtest): FD, JEI and Jade, 127.0.0.1 only, offline mode
./gradlew :neoforge:runPlaytestClient  # a dev client (neoforge/run/playtest-client) that joins it
```

Stop the server with `stop` in its console, so the world is saved. Accepting the Minecraft EULA for the local server
(`neoforge/run/playtest/eula.txt`) is up to you.

Fabric has no playtest task: `./gradlew :fabric:smokeAll` plays through the main interactions in hidden windows, and a
short check in a normal Fabric install with Farmer's Delight Refabricated covers the rest (see `RELEASING.md`).

## The wiki

The wiki lives in `site/` (Astro + Starlight, the same setup as the Farmer's Delight Neoforged docs). Its numbers come
from the mod, never typed by hand: `./gradlew :neoforge:runDocsExport` writes `site/src/data/runtime.json`.
`cd site && npm ci && npm run build` builds it; pushes to `26.3` that touch `site/` only build and check it. It is
published to GitHub Pages only by running the Docs workflow by hand (Actions, Docs, Run workflow) with "deploy" ticked.

## Maintainer tooling

The maintainer's dev tooling (the in-game screenshot and video tools, the branding generator) isn't part of this
repository. Everything above builds, tests and runs without it.
