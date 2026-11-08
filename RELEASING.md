# Releasing

A release only goes out when every box below is ticked. A build that compiles and a server that boots are **not**
evidence that the mod works.

## 1. Automated (CI must be green on the release commit)

- [ ] `bash scripts/multiloader/verify-all.sh --full` is green on the release commit, on the PC or in the Verify
      workflow's full tier (a `ci/full/...` branch): it runs every box of this section (the quick tier is not enough
      for a release)
- [ ] `./gradlew build`: compiles; Error Prone and the forbidden-pattern checks pass
- [ ] `./gradlew runData` leaves `common/src/generated` unchanged (`src/generated` on the older branches)
- [ ] `./gradlew gameTestsAll`: every test passes on both loaders without Farmer's Delight, with it, and in the compat
      run, **and** the logs have no `ERROR` lines
- [ ] `./gradlew :fabric:smokeAll`: the Fabric smoke tests
      pass in singleplayer, LAN and on a dedicated server, without and with FD Refabricated, and across versions: our
      client on a Fabric server without V&B stays connected, a client without V&B is refused with a reason

## 2. Playtest on a real server with a real client (see CONTRIBUTING.md, Playtesting)

NeoForge, with Farmer's Delight Neoforged:

- [ ] `./gradlew :neoforge:runPlaytest`, then `:neoforge:runPlaytestClient`: the client **joins** without a crash or
      disconnect
- [ ] Brew in the kettle, fill bottles and a bucket by right-click, age in a barrel, tap young and aged drinks
- [ ] Place drinks on a table, break them one by one, nothing duplicates
- [ ] Tipsy up to the top, a blackout, wake up; nothing lost
- [ ] Break a kettle and a barrel with contents: everything drops, nothing duplicates
- [ ] Hoppers into and out of the kettle and barrel
- [ ] JEI categories and Jade tooltips
- [ ] Log out and back in: block contents and Tipsy are still there
- [ ] `logs/latest.log` on server and client: no `ERROR` or exception that mentions `vineandbarrel`

Fabric, a short one: the built jar with Fabric API and Farmer's Delight Refabricated in a normal Fabric install.

- [ ] The game starts and a world loads; brew, age and tap one drink; the FD stove heats the kettle
- [ ] `logs/latest.log`: no `ERROR` or exception that mentions `vineandbarrel`

## 3. Every Minecraft branch

- [ ] Every change on `26.3` since the last release is ported to `26.2`, `26.1.2` and `26.1` with
      `scripts/multiloader/backport.sh` (see CONTRIBUTING.md), and each branch's tests are green. Those branches stay
      NeoForge only

## 4. Publish

The release workflow (`.github/workflows/release.yml`) does the uploading. On each branch:

- [ ] Bump `mod_version` in `gradle.properties` (`<version>+neoforged.<n>`, the same on every branch) and add the entry
      at the top of `changelog.md`: that section becomes the release notes
- [ ] On `26.3` also bump `fabric_mod_version` (`<version>+fabric.<n>`): the same `<version>` before the `+` as
      `mod_version`
- [ ] `release_game_versions` lists exactly the Minecraft versions this jar runs on
- [ ] Tag and push: `git tag <minecraft_version>-<mod_version>` (e.g. `26.3-0.9.0+neoforged.1`), then
      `git push origin <tag>`. The workflow refuses a tag that doesn't match `gradle.properties` (on `26.3` it checks
      both versions), builds and tests again, then publishes to GitHub, Modrinth and CurseForge. On `26.3` that is two
      jars: `neoforge/build/libs/VineAndBarrel-26.3-0.9.0+neoforged.1.jar` and
      `fabric/build/libs/VineAndBarrel-26.3-0.9.0+fabric.1.jar` (full releases on Modrinth and CurseForge, like the NeoForge jar), on the same projects

Names, so loader and version can't be mixed up: every release, version and file title reads loader first, as
`Vine & Barrel <version> · <Loader> · Minecraft <minecraft_version>`, e.g. `Vine & Barrel 0.9.0 · NeoForge · Minecraft 26.3`
and `Vine & Barrel 0.9.0 · Fabric · Minecraft 26.3`. Backport releases from `26.2`, `26.1.2` and `26.1` follow it too
(`Vine & Barrel 0.9.0 · NeoForge · Minecraft 26.2`). The jar names stay as they are
(`VineAndBarrel-26.3-0.9.0+neoforged.1.jar`, `VineAndBarrel-26.3-0.9.0+fabric.1.jar`). The workflow generates the rest:
the GitHub release body starts with a "Which file do I need?" table (Loader | Minecraft | File | Needs), and the first
line of each Modrinth and CurseForge changelog names its loader.

Once, in the repository settings: the variables `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID` and the secrets
`MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`. Without them the workflow only makes the GitHub release. Modrinth uploads also
need the variable `MODRINTH_LIVE` set to `true`: until Modrinth has approved the project, leave it unset and the
workflow skips both Modrinth uploads with a notice, while GitHub and CurseForge still get the release. Set it once the
project is approved.
