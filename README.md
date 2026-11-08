# Vine & Barrel

A cozy wine and beer mod for NeoForge (Minecraft 26.1 to 26.3), in the spirit of Farmer's Delight.

Grow grapes, hops and barley. Brew in a copper kettle, let it ferment and age in barrels, and tap mugs and glasses for your friends. Vine & Barrel works on its own. When Farmer's Delight is installed it plugs into it: the stove heats the kettle, vines climb FD ropes, and the cooking pot uses your wine and beer.

> Status: early development. The crops are in (grapes, hops, barley); brewing comes next.

## Minecraft versions

One jar per Minecraft version, each built from its own branch:

| Minecraft | NeoForge | Branch |
|---|---|---|
| 26.3 | 26.3.0.101 or newer | `26.3` |
| 26.2 | 26.2.0.93 or newer | `26.2` |
| 26.1.2 | 26.1.2.126 or newer | `26.1.2` |
| 26.1, 26.1.1 | 26.1.0.19-beta to 26.1.1 | `26.1` |

How the branches are kept in step: [CONTRIBUTING.md](CONTRIBUTING.md).

## Building

Requires Java 25 for the game (Gradle downloads it) and a JDK 21+ to run Gradle.

```
./gradlew build              # jar in build/libs, plus static checks
./gradlew runGameTestServer  # headless: runs every GameTest and exits, no window
./gradlew runClient          # play in a dev client
```

Farmer's Delight is an optional dependency and never on the compile classpath. Every FD hook is tested twice: `runGameTestServer` runs without FD, `runGameTestServerFd` runs the same tests with FD installed (it copies an FD jar into `run-fd/mods`). By default it uses the build output of a [Farmer's Delight Neoforged](https://jexxme.github.io/FarmersDelight-Neoforged/) checkout next to this one (`../Season4/farmersdelight-port/build/libs`); point it elsewhere with `-PfdJar=<path>` or `FD_JAR`. The FD jar must be built for the same Minecraft version (FD Neoforged has the same branches).

```
./gradlew runGameTestServerFd                 # the GameTests with Farmer's Delight installed
./gradlew gameTestsAll                        # both runs
./gradlew runGameTestServerFd -PfdJar=FD.jar  # with a specific FD jar
```

## Art

A few pieces that Farmer's Delight already has (the Brew Kettle's heating tray, its handle and its screen, and the rope that vines climb) use Farmer's Delight's own art under the MIT License, so they look exactly like FD. Some logic (wild crops, rope climbing, the biome filter) is adapted from FD too. See [NOTICE_farmersdelight.txt](NOTICE_farmersdelight.txt).

## License

[MIT](LICENSE). The Farmer's Delight art we use is © vectorwing, MIT (see [NOTICE_farmersdelight.txt](NOTICE_farmersdelight.txt)).
