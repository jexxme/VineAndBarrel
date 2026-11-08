package jexxme.vineandbarrel.kettle;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Heat for the Brew Kettle: the same rules as FD's cooking pot ({@code HeatableBlockEntity}, MIT). A heat source
 * directly below, or one block further down through a heat conductor such as a hopper. A source with a {@code lit}
 * property only heats while lit.
 */
public final class KettleTags
{
	/** Campfires, fire, lava, magma blocks, lava cauldrons, plus {@code #farmersdelight:heat_sources} when FD is installed. */
	public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK, VineAndBarrel.id("heat_sources"));
	/** Heat sources the kettle stands over on its tray (K1): campfires, fire, lava. */
	public static final TagKey<Block> TRAY_HEAT_SOURCES = TagKey.create(Registries.BLOCK, VineAndBarrel.id("tray_heat_sources"));
	/** Blocks that pass heat up from the block below them: hoppers, plus FD's conductors when FD is installed. */
	public static final TagKey<Block> HEAT_CONDUCTORS = TagKey.create(Registries.BLOCK, VineAndBarrel.id("heat_conductors"));
	/** Every Brew Kettle block (all weathering stages, waxed or not). */
	public static final TagKey<Block> BREW_KETTLES = TagKey.create(Registries.BLOCK, VineAndBarrel.id("brew_kettles"));
	/** Items that fill from the kettle and can wait in its container slot: glass bottles and buckets. */
	public static final TagKey<Item> KETTLE_CONTAINERS = TagKey.create(Registries.ITEM, VineAndBarrel.id("kettle_containers"));

	private KettleTags() {
	}

	public static boolean isHeated(Level level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		if (below.is(HEAT_SOURCES)) {
			return isLit(below);
		}
		if (below.is(HEAT_CONDUCTORS)) {
			BlockState further = level.getBlockState(pos.below(2));
			if (further.is(HEAT_SOURCES)) {
				return isLit(further);
			}
		}
		return false;
	}

	/**
	 * Whether a campfire's smoke would come out through a kettle it heats: a kettle right above it, or one block further
	 * up through a heat conductor (the {@link #isHeated} rule, seen from the fire). A signal fire keeps its tall column.
	 */
	public static boolean hidesCampfireSmoke(BlockGetter level, BlockPos campfire, boolean signalFire) {
		if (signalFire) {
			return false;
		}
		BlockState above = level.getBlockState(campfire.above());
		if (above.getBlock() instanceof BrewKettleBlock) {
			return true;
		}
		return above.is(HEAT_CONDUCTORS) && level.getBlockState(campfire.above(2)).getBlock() instanceof BrewKettleBlock;
	}

	private static boolean isLit(BlockState state) {
		return !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
	}
}
