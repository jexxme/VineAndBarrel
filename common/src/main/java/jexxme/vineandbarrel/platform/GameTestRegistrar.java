package jexxme.vineandbarrel.platform;

import net.minecraft.gametest.framework.TestFunction;

/**
 * Where the GameTest classes add their tests. On this Minecraft a test is a {@link TestFunction} (its name, structure,
 * time limit and code) that each loader adds to vanilla's {@code GameTestRegistry} (26.x: the test registries).
 */
public interface GameTestRegistrar
{
	void registerTest(TestFunction test);
}
