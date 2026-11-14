package jexxme.vineandbarrel.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import jexxme.vineandbarrel.platform.ConfigValue;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * A placement filter that passes while one of our on/off server settings is on:
 * {@code {"type": "vineandbarrel:setting", "setting": "worldgen.wildGrapes"}} (the setting's {@code section.name}, as in
 * the Fabric file). The setting is read each time a chunk places the feature, so turning it off stops it in the next
 * chunks without touching the data packs.
 */
public final class SettingFilter extends PlacementFilter
{
	public static final String NAME = "setting";

	public static final Codec<SettingFilter> CODEC = Codec.STRING.comapFlatMap(SettingFilter::find, SettingFilter::key)
		.fieldOf("setting").xmap(SettingFilter::new, SettingFilter::setting).codec();

	/** The filter's type, as registered ({@link WorldgenConfig}). */
	public static final PlacementModifierType<SettingFilter> TYPE = () -> CODEC;

	private final ConfigValue<Boolean> setting;

	public SettingFilter(ConfigValue<Boolean> setting) {
		this.setting = setting;
	}

	public ConfigValue<Boolean> setting() {
		return setting;
	}

	/** A setting's name in a filter: {@code section.name}. */
	public static String key(ConfigValue<?> value) {
		return value.section() + "." + value.name();
	}

	@SuppressWarnings("unchecked")
	private static DataResult<ConfigValue<Boolean>> find(String key) {
		WorldgenConfig.load();
		for (ConfigValue<?> value : ConfigValue.all()) {
			if (value.side() == ConfigValue.Side.SERVER && key(value).equals(key) && value.getDefault() instanceof Boolean) {
				return DataResult.success((ConfigValue<Boolean>) value);
			}
		}
		return DataResult.error(() -> "No on/off server setting " + key);
	}

	@Override
	protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
		return setting.get();
	}

	@Override
	public PlacementModifierType<?> type() {
		return TYPE;
	}
}
