package jexxme.vineandbarrel.world;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.platform.ConfigValue;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.List;

import static jexxme.vineandbarrel.platform.ConfigValue.Side.SERVER;

/**
 * World generation settings (server, next to the Tipsy ones in the same file, section {@code [worldgen]}): one switch
 * per wild crop, for a modpack whose other mod already has grapes, hops or barley growing wild. Each wild crop's placed
 * feature starts with a {@link SettingFilter} on its switch, so the switch is read as a chunk is decorated, on both
 * loaders: turned off, new chunks get none of that crop, and the ones already out there stay. Village farms, chests and
 * the traders' seeds are not wild growth and stay as they are.
 */
public final class WorldgenConfig
{
	public static final String SECTION = "worldgen";
	public static final String COMMENT = "World generation: what new chunks get (chunks already generated stay as they are)";

	static {
		ConfigValue.describeSection(SERVER, SECTION, COMMENT);
	}

	public static final ConfigValue<Boolean> WILD_GRAPES = ConfigValue.define(SERVER, SECTION, "wildGrapes",
			"Wild red grapes grow in warm biomes. false: new chunks get none (village farms and seeds from traders stay)", true);
	public static final ConfigValue<Boolean> WILD_WHITE_GRAPES = ConfigValue.define(SERVER, SECTION, "wildWhiteGrapes",
			"Wild white grapes grow in milder biomes. false: new chunks get none (village farms and seeds from traders stay)", true);
	public static final ConfigValue<Boolean> WILD_HOPS = ConfigValue.define(SERVER, SECTION, "wildHops",
			"Wild hops grow in temperate and cool biomes. false: new chunks get none (village farms and seeds from traders stay)", true);
	public static final ConfigValue<Boolean> WILD_BARLEY = ConfigValue.define(SERVER, SECTION, "wildBarley",
			"Wild barley grows nearly everywhere. false: new chunks get none (village farms and seeds from traders stay)", true);

	/** Every wild crop switch, in the order of the file. */
	public static final List<ConfigValue<Boolean>> WILD_CROPS = List.of(WILD_GRAPES, WILD_WHITE_GRAPES, WILD_HOPS, WILD_BARLEY);

	private static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE,
		VineAndBarrel.MODID);

	static {
		PLACEMENT_MODIFIERS.register(SettingFilter.NAME, () -> SettingFilter.TYPE);
	}

	private WorldgenConfig() {
	}

	/** Makes sure every setting above is declared (the loaders build their config files from {@link ConfigValue#all()}). */
	public static void load() {
	}

	public static void register() {
		PLACEMENT_MODIFIERS.register();
	}
}
