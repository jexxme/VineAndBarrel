package jexxme.vineandbarrel.platform;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Where the GameTest classes add their tests: NeoForge's {@code RegisterGameTestsEvent}, or on Fabric the test
 * registries while the server loads them. Same methods as the NeoForge event.
 */
public interface GameTestRegistrar
{
	/** A test environment that sets nothing up (NeoForge's {@code registerEnvironment(name)}). */
	default Holder<TestEnvironmentDefinition<?>> registerEnvironment(Identifier name) {
		return registerEnvironment(name, new TestEnvironmentDefinition.AllOf(List.of()));
	}

	Holder<TestEnvironmentDefinition<?>> registerEnvironment(Identifier name, TestEnvironmentDefinition<?> definition);

	void registerTest(Identifier name, GameTestInstance test);
}
