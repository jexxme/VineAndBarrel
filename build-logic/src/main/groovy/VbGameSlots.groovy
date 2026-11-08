import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * The game slots of a build: every run task that starts a game (GameTest servers, smoke clients, dev clients) takes
 * one, and Gradle runs at most as many of them at once as there are slots (see vb-loader.gradle). The machine's
 * memory is shared, so this stays at two.
 */
abstract class VbGameSlots implements BuildService<BuildServiceParameters.None> {
}
