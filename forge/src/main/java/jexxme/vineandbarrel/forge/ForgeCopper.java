package jexxme.vineandbarrel.forge;

import jexxme.vineandbarrel.kettle.KettleRegistry;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.level.BlockEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The copper kettles in vanilla's copper rules. NeoForge 21.1 read them from two data maps
 * ({@code data/neoforge/data_maps/block/oxidizables.json} and {@code waxables.json}: the single kettles and the parts
 * of big ones, not the chimneys); Forge 47 has no data maps, and vanilla's own tables ({@code WeatheringCopper.NEXT_BY_BLOCK},
 * {@code HoneycombItem.WAXABLES}) are fixed, immutable maps. So the same pairs are kept here and handed to each vanilla
 * path that asks:
 * <ul>
 * <li>an axe used the vanilla way (sneaking, so the kettle's own click doesn't take it): Forge's
 * {@code BlockToolModificationEvent} for {@code AXE_SCRAPE} and {@code AXE_WAX_OFF};</li>
 * <li>honeycomb used the vanilla way: {@code HoneycombItemMixin} ({@code HoneycombItem.getWaxed});</li>
 * <li>lightning cleaning copper: {@code LightningBoltMixin} ({@code WeatheringCopper.getFirst / getPrevious}).</li>
 * </ul>
 * Weathering over time needs none of this: the kettle blocks name their own next stage.
 */
public final class ForgeCopper
{
	/** Unwaxed block to its next weathering stage, and back; unwaxed block to its waxed twin, and back. */
	private static @Nullable Map<Block, Block> next;
	private static @Nullable Map<Block, Block> previous;
	private static @Nullable Map<Block, Block> waxed;
	private static @Nullable Map<Block, Block> unwaxed;

	private ForgeCopper() {
	}

	static void init() {
		MinecraftForge.EVENT_BUS.addListener(ForgeCopper::onToolUse);
	}

	/** Built on first use: the blocks exist once anything in a world asks. */
	private static synchronized void build() {
		if (next != null) {
			return;
		}
		Map<Block, Block> nextStage = new HashMap<>();
		Map<Block, Block> previousStage = new HashMap<>();
		Map<Block, Block> waxedTwin = new HashMap<>();
		Map<Block, Block> unwaxedTwin = new HashMap<>();
		WeatherState[] stages = WeatherState.values();
		for (WeatherState stage : stages) {
			pair(waxedTwin, unwaxedTwin, KettleRegistry.KETTLES.get(stage).get(), KettleRegistry.WAXED_KETTLES.get(stage).get());
			pair(waxedTwin, unwaxedTwin, KettleRegistry.PARTS.get(stage).get(), KettleRegistry.WAXED_PARTS.get(stage).get());
			if (stage.ordinal() + 1 < stages.length) {
				WeatherState after = stages[stage.ordinal() + 1];
				pair(nextStage, previousStage, KettleRegistry.KETTLES.get(stage).get(), KettleRegistry.KETTLES.get(after).get());
				pair(nextStage, previousStage, KettleRegistry.PARTS.get(stage).get(), KettleRegistry.PARTS.get(after).get());
			}
		}
		previous = previousStage;
		waxed = waxedTwin;
		unwaxed = unwaxedTwin;
		next = nextStage;
	}

	private static void pair(Map<Block, Block> forward, Map<Block, Block> back, Block from, Block to) {
		forward.put(from, to);
		back.put(to, from);
	}

	/** The stage before this kettle block's (one scrape), or null: not ours, or already clean. */
	public static @Nullable Block previous(Block block) {
		build();
		return previous.get(block);
	}

	/** The cleanest stage of this kettle block (what lightning leaves), or null if it is not one of ours. */
	public static @Nullable Block first(Block block) {
		build();
		if (!previous.containsKey(block) && !next.containsKey(block)) {
			return null;
		}
		Block first = block;
		for (Block before = previous.get(first); before != null; before = previous.get(first)) {
			first = before;
		}
		return first;
	}

	/** The waxed twin of this unwaxed kettle block, or null. */
	public static @Nullable Block waxed(Block block) {
		build();
		return waxed.get(block);
	}

	/** The unwaxed twin of this waxed kettle block, or null. */
	public static @Nullable Block unwaxed(Block block) {
		build();
		return unwaxed.get(block);
	}

	/** {@code Platform#unwaxed}: vanilla's table, then ours. */
	static @Nullable Block unwaxedOfAny(Block block) {
		Block vanilla = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(block);
		return vanilla != null ? vanilla : unwaxed(block);
	}

	/** {@code WeatheringCopper.getFirst(BlockState)} with our kettles: the cleanest stage, in the same shape. */
	public static BlockState first(BlockState state) {
		Block first = first(state.getBlock());
		return first != null ? first.withPropertiesOf(state) : WeatheringCopper.getFirst(state);
	}

	/** {@code WeatheringCopper.getPrevious(BlockState)} with our kettles. */
	public static Optional<BlockState> previous(BlockState state) {
		Block before = previous(state.getBlock());
		return before != null ? Optional.of(before.withPropertiesOf(state)) : WeatheringCopper.getPrevious(state);
	}

	/**
	 * What {@code IForgeBlock#getToolModifiedState} answers for vanilla copper, for ours (the common kettle classes
	 * can't override a Forge method): asked by {@code AxeItem#useOn} and by any mod that scrapes or strips with tools.
	 */
	private static void onToolUse(BlockEvent.BlockToolModificationEvent event) {
		BlockState state = event.getState();
		// Another listener has already decided
		if (event.getFinalState() != state || !event.getHeldItemStack().canPerformAction(event.getToolAction())) {
			return;
		}
		Block result = null;
		if (event.getToolAction() == ToolActions.AXE_SCRAPE) {
			result = previous(state.getBlock());
		} else if (event.getToolAction() == ToolActions.AXE_WAX_OFF) {
			result = unwaxed(state.getBlock());
		}
		if (result != null) {
			event.setFinalState(result.withPropertiesOf(state));
		}
	}
}
