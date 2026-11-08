package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.platform.GameTestRegistrar;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the common GameTests to the test registries while a server loads them from its data (Fabric has no event like
 * NeoForge's {@code RegisterGameTestsEvent}; {@code RegistryLoadTaskMixin} calls this just before each registry
 * freezes). The environments go in first; their tests wait until the test instance registry comes.
 */
public final class FabricGameTestRegistration
{
	private static final List<Pending> PENDING = new ArrayList<>();

	private record Pending(Identifier name, GameTestInstance test)
	{
	}

	private FabricGameTestRegistration() {
	}

	@SuppressWarnings("unchecked")
	public static void beforeFreeze(WritableRegistry<?> registry) {
		if (FabricPlatform.GAME_TESTS.isEmpty()) {
			return;
		}
		if (registry.key().equals(Registries.TEST_ENVIRONMENT)) {
			WritableRegistry<TestEnvironmentDefinition<?>> environments = (WritableRegistry<TestEnvironmentDefinition<?>>) registry;
			synchronized (PENDING) {
				PENDING.clear();
				GameTestRegistrar registrar = new GameTestRegistrar()
				{
					@Override
					public Holder<TestEnvironmentDefinition<?>> registerEnvironment(Identifier name, TestEnvironmentDefinition<?> definition) {
						return environments.register(ResourceKey.create(Registries.TEST_ENVIRONMENT, name), definition, RegistrationInfo.BUILT_IN);
					}

					@Override
					public void registerTest(Identifier name, GameTestInstance test) {
						PENDING.add(new Pending(name, test));
					}
				};
				FabricPlatform.GAME_TESTS.forEach(tests -> tests.accept(registrar));
			}
		} else if (registry.key().equals(Registries.TEST_INSTANCE)) {
			WritableRegistry<GameTestInstance> tests = (WritableRegistry<GameTestInstance>) registry;
			synchronized (PENDING) {
				for (Pending pending : PENDING) {
					tests.register(ResourceKey.create(Registries.TEST_INSTANCE, pending.name()), pending.test(), RegistrationInfo.BUILT_IN);
				}
				PENDING.clear();
			}
		}
	}
}
