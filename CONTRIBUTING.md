# Contributing

## Building and testing

JDK 21+ for Gradle; a JDK 25 toolchain is downloaded automatically.

```bash
./gradlew build             # jar in build/libs/, plus the static checks
./gradlew runData           # regenerate src/generated/resources (commit the result)
./gradlew runGameTestServer # in-world tests, headless
./gradlew gameTestsAll      # the same tests without and with Farmer's Delight
```

CI runs `build`, `runData` (it fails if `src/generated` changes) and `runGameTestServer` on every push and pull
request, and also fails on any `ERROR` in the test log.

The Farmer's Delight run needs a Farmer's Delight Neoforged jar **for the same Minecraft version**: build the matching
branch of [Farmer's Delight Neoforged](https://github.com/jexxme/FarmersDelight-Neoforged) (`./gradlew jar`) and pass
it with `-PfdJar=<path>` (or `FD_JAR`). Without it the build looks in `../Season4/farmersdelight-port/build/libs`,
which only fits if that checkout is on the same Minecraft version.

## Minecraft versions

Minecraft and NeoForge change too many APIs within 26.x for one jar, so each supported version has its own branch,
the same way as Farmer's Delight Neoforged:

| Branch | Minecraft | NeoForge | Farmer's Delight Neoforged |
|---|---|---|---|
| `26.3` (default) | 26.3 | 26.3.0.101+ | branch `26.3` |
| `26.2` | 26.2 | 26.2.0.93+ | branch `26.2` |
| `26.1.2` | 26.1.2 | 26.1.2.126+ | branch `26.1.2` |
| `26.1` | 26.1, 26.1.1 | 26.1.0.19-beta to 26.1.1 | branch `26.1` |

- Open pull requests against `26.3`. Fixes and features are then ported down with `git cherry-pick -x <commit>` to
  each older branch (`26.2`, then `26.1.2`, then `26.1`), adapted where the API differs, and only merged there when
  that branch's tests are green.
- Each older branch changes only what its Minecraft version needs. Keep those changes small and leave the code around
  them as on `26.3`, so the next cherry-pick applies cleanly.
- After a cherry-pick that touches data generation, run `./gradlew runData` on that branch and commit the result:
  `src/generated` is always written in that branch's own data formats.
- Structure templates (`.nbt`) stay exactly as `26.3` saves them; the older branches convert them while building.
- Content that needs a newer Minecraft (a block or API that doesn't exist in an older version) stays on the branches
  that have it.
- `README.md` and `CONTRIBUTING.md` are the same on every branch: change them on `26.3`, then copy them.

## Versions and releases

The mod version (`0.9.0+neoforged.1`: content version plus build number, like FD Neoforged) is the same on every
branch. A release is tagged per branch as `<minecraft_version>-<mod_version>`
(for example `26.3-0.9.0+neoforged.1`), which is also the version in the jar name, and `release_game_versions` in
`gradle.properties` lists the Minecraft versions that jar is published for (`26.1` branch: `26.1,26.1.1`). Pushing the
tag runs the release workflow, which builds and tests again and publishes the jar.
