package jexxme.vineandbarrel.kettle;

import jexxme.vineandbarrel.inventory.ItemSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Brew Kettles that grow: placing kettles into a square (2x2, 3x3) or a stack (up to 2 tall) joins them into one big
 * kettle, like Create's tanks; sneaking keeps a kettle on its own. Breaking any block splits the kettle back into
 * single kettles, each keeping its share of the brew. A big kettle weathers, takes wax and gets scraped as one vessel.
 * <p>
 * A big kettle is made of {@link BrewKettlePartBlock}s that know their shape and place ({@link KettlePart}); only the
 * controller (its local 0, 0, 0) has the block entity. Single kettles stay the 1.0 block, unchanged.
 */
public final class KettleMultiblock
{
	/**
	 * Set while this class swaps kettle blocks itself (joining, splitting, weathering): removal hooks then leave the
	 * contents alone, as they are being moved. Server thread only.
	 */
	private static boolean converting;
	/** What the next drop of a broken part block carries: its share of the brew (consumed by {@link BrewKettlePartBlock#getDrops}). */
	private static final Map<String, KettleContents> PENDING_DROPS = new HashMap<>();

	private KettleMultiblock() {
	}

	public static boolean isConverting() {
		return converting;
	}

	/** One kettle, single or big: its shape, its controller's position and the side its tap faces. */
	public record Kettle(KettleShape shape, BlockPos controller, Direction facing)
	{
		public List<BlockPos> positions() {
			return KettlePart.positions(shape, controller, facing);
		}

		/** The blocks a fire under can heat. */
		public List<BlockPos> bottom() {
			return positions().subList(0, shape.bottomBlocks());
		}

		/** The kettle block that draws the chimney (the single kettle itself; a big kettle's top centre). */
		public BlockPos chimney() {
			if (!isBig()) {
				return controller;
			}
			KettlePart part = KettlePart.chimney(shape);
			return controller.offset(KettlePart.offset(facing, part.x(), part.y(), part.z()));
		}

		/** The block that draws the sight glass (big kettles; a single kettle shows its brew under the lid). */
		public BlockPos gauge() {
			for (BlockPos pos : positions()) {
				if (KettleMultiblock.partAt(this, pos).isGauge()) {
					return pos;
				}
			}
			return controller;
		}

		/** The kettle's centre line, at the controller's height. */
		public Vec3 centre() {
			return Vec3.atLowerCornerOf(controller).add(Vec3.atLowerCornerOf(KettlePart.offset(facing, shape.size() - 1, 0, shape.size() - 1)).scale(0.5D))
				.add(0.5D, 0.0D, 0.5D);
		}

		/** How many extension blocks stand on the chimney (0 to {@link KettleChimneyBlock#MAX}). */
		public int extension(BlockGetter level) {
			return column(level, this).pieces().size();
		}

		/** The top of the chimney (its cap), where its steam comes out, in world coordinates. */
		public Vec3 chimneyTop(BlockGetter level) {
			Vec3 centre = centre();
			List<BlockPos> pieces = column(level, this).pieces();
			double y = pieces.isEmpty() ? controller.getY() + KettleGeometry.chimneyTop(shape) / 16.0D
				: pieces.getLast().getY() + KettleGeometry.PIECE_TOP / 16.0D;
			return new Vec3(centre.x, y, centre.z);
		}

		public boolean isBig() {
			return shape != KettleShape.SINGLE;
		}
	}

	/** The kettle the block at {@code pos} belongs to, or null if it isn't a kettle block. */
	public static @Nullable Kettle kettleAt(BlockGetter level, BlockPos pos) {
		return kettleOf(level.getBlockState(pos), pos);
	}

	public static @Nullable Kettle kettleOf(BlockState state, BlockPos pos) {
		if (state.getBlock() instanceof BrewKettleBlock) {
			return new Kettle(KettleShape.SINGLE, pos, state.getValue(BrewKettleBlock.FACING));
		}
		if (state.getBlock() instanceof BrewKettlePartBlock) {
			Direction facing = state.getValue(BrewKettlePartBlock.FACING);
			KettlePart part = BrewKettlePartBlock.part(state);
			return new Kettle(part.shape(), part.controllerFrom(pos, facing), facing);
		}
		return null;
	}

	/** The block entity holding the brew of the kettle the block at {@code pos} belongs to. */
	public static @Nullable BrewKettleBlockEntity controller(BlockGetter level, BlockPos pos) {
		Kettle kettle = kettleAt(level, pos);
		return kettle != null && level.getBlockEntity(kettle.controller()) instanceof BrewKettleBlockEntity be ? be : null;
	}

	/** The size of the kettle whose chimney this block draws (1, 2, 3), or 0 if it draws none (or hangs). */
	public static int chimneyOf(BlockState state) {
		if (state.getBlock() instanceof BrewKettleBlock) {
			return state.getValue(BrewKettleBlock.SUPPORT) == KettleSupport.HANDLE ? 0 : 1;
		}
		if (state.getBlock() instanceof BrewKettlePartBlock) {
			KettlePart part = BrewKettlePartBlock.part(state);
			return part.isChimney() ? part.shape().size() : 0;
		}
		return 0;
	}

	/** Whether this kettle block's chimney carries extension blocks. */
	public static boolean isExtended(BlockState state) {
		return state.getOptionalValue(BrewKettleBlock.EXTENDED).orElse(false);
	}

	public static BlockState withExtended(BlockState state, boolean extended) {
		return state.hasProperty(BrewKettleBlock.EXTENDED) ? state.setValue(BrewKettleBlock.EXTENDED, extended) : state;
	}

	/**
	 * A kettle's chimney as it stands: its pieces from the bottom up (each on the one before, right on it or through up to
	 * {@link KettleChimneyBlock#MAX_THROUGH} roof blocks), and where the next piece goes with how many roof blocks it
	 * passes on the way ({@code next} is null when it can't grow: eight pieces, a third roof block, a block that holds
	 * things, another chimney or the top of the world).
	 */
	public record Column(List<BlockPos> pieces, @Nullable BlockPos next, int through)
	{
	}

	public static Column column(BlockGetter level, Kettle kettle) {
		List<BlockPos> pieces = new ArrayList<>();
		BlockPos foot = kettle.chimney();
		for (BlockPos on; pieces.size() < KettleChimneyBlock.MAX && (on = KettleChimneyBlock.pieceOn(level, foot)) != null; foot = on) {
			pieces.add(on);
		}
		if (pieces.size() < KettleChimneyBlock.MAX) {
			for (int through = 0; through <= KettleChimneyBlock.MAX_THROUGH; through++) {
				BlockPos at = foot.above(through + 1);
				if (level.isOutsideBuildHeight(at)) {
					break;
				}
				BlockState there = level.getBlockState(at);
				if (there.canBeReplaced()) {
					return new Column(pieces, at, through);
				}
				if (!isRoof(there)) {
					break;
				}
			}
		}
		return new Column(pieces, null, 0);
	}

	/** A block the pipe goes through and leaves as it is: anything solid that holds nothing and isn't a kettle or chimney. */
	private static boolean isRoof(BlockState state) {
		return !state.canBeReplaced() && !state.hasBlockEntity() && !(state.getBlock() instanceof KettleChimneyBlock)
			&& !(state.getBlock() instanceof BrewKettleBlock) && !(state.getBlock() instanceof BrewKettlePartBlock);
	}

	/**
	 * A copper ingot on a kettle or its chimney: the chimney grows one block, up to {@link KettleChimneyBlock#MAX}. A roof
	 * in the way stays whole: the piece goes on top of it and its pipe is drawn through (up to two roof blocks); blocks
	 * that hold things (chests, kettles) and thicker roofs stop it. Hanging kettles have no room for a chimney. Returns
	 * whether it grew; the new piece shows a glint and makes the copper sound.
	 */
	public static boolean extendChimney(Level level, Kettle kettle, net.minecraft.world.entity.player.@Nullable Player player) {
		BlockPos base = kettle.chimney();
		BlockState baseState = level.getBlockState(base);
		int size = chimneyOf(baseState);
		if (size == 0) {
			return false;
		}
		Column column = column(level, kettle);
		BlockPos at = column.next();
		if (at == null || !level.isInWorldBounds(at) || player != null && (!player.mayBuild() || !level.mayInteract(player, at))) {
			return false;
		}
		if (!level.isClientSide()) {
			Block piece = KettleRegistry.chimneyOf(KettleRegistry.singleOf(baseState.getBlock()));
			// (the flashing only for a piece in a hole in a flat roof: one going through a roof leaves the roof as it is)
			BlockState state = piece.defaultBlockState().setValue(KettleChimneyBlock.FACING, kettle.facing())
				.setValue(KettleChimneyBlock.SIZE, size).setValue(KettleChimneyBlock.TOP, true)
				.setValue(KettleChimneyBlock.THROUGH, column.through())
				.setValue(KettleChimneyBlock.FLASHING, column.through() == 0 && KettleChimneyBlock.inRoof(level, at));
			level.setBlock(at, state, Block.UPDATE_ALL);
			if (column.pieces().isEmpty()) {
				level.setBlock(base, withExtended(baseState, true), Block.UPDATE_ALL);
			} else {
				// the piece under it gives up its cap (and draws its pipe on up into a two-block roof)
				BlockPos below = column.pieces().getLast();
				BlockState belowState = level.getBlockState(below);
				level.setBlock(below, KettleChimneyBlock.linked(level, below, belowState), Block.UPDATE_ALL);
			}
			if (level instanceof ServerLevel server) {
				// a copper glint where the new piece stands
				server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(Vec3.fromRGB24(0xE3826C).toVector3f(), 0.8F), at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, 10, 0.25D, 0.4D, 0.25D, 0.0D);
			}
			level.playSound(null, at, net.minecraft.sounds.SoundEvents.COPPER_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.9F);
		}
		return true;
	}

	/** Whether every block of {@code kettle} is still the right part (false once it was split or broken). */
	public static boolean isIntact(BlockGetter level, Kettle kettle) {
		if (!kettle.isBig()) {
			return level.getBlockState(kettle.controller()).getBlock() instanceof BrewKettleBlock;
		}
		List<BlockPos> positions = kettle.positions();
		for (BlockPos pos : positions) {
			Kettle there = kettleAt(level, pos);
			if (there == null || !there.equals(kettle)) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ joining

	/**
	 * Called when a single kettle was placed (not sneaking) at {@code placed}: joins it with the kettles around into the
	 * biggest shape it completes. Every block of the shape must be a kettle (single, or a whole big kettle inside the
	 * shape); none may hang; their brews must be the same (or empty) and their ingredients must fit one grid.
	 */
	public static boolean tryJoin(ServerLevel level, BlockPos placed) {
		BlockState placedState = level.getBlockState(placed);
		if (!(placedState.getBlock() instanceof BrewKettleBlock) || placedState.getValue(BrewKettleBlock.SUPPORT) == KettleSupport.HANDLE) {
			return false;
		}
		Direction facing = placedState.getValue(BrewKettleBlock.FACING);
		for (KettleShape shape : KettleShape.BIGGEST_FIRST) {
			int s = shape.size();
			int h = shape.height();
			for (int dy = 0; dy < h; dy++) {
				for (int dx = 0; dx < s; dx++) {
					for (int dz = 0; dz < s; dz++) {
						BlockPos min = placed.offset(-dx, -dy, -dz);
						Map<BlockPos, Kettle> members = members(level, min, shape);
						if (members != null && members.size() > 1 && join(level, placed, min, shape, facing, members)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/** The kettles that exactly fill the box at {@code min}, by controller, or null if the box isn't made of whole kettles. */
	private static @Nullable Map<BlockPos, Kettle> members(Level level, BlockPos min, KettleShape shape) {
		Map<BlockPos, Kettle> kettles = new LinkedHashMap<>();
		int s = shape.size();
		int h = shape.height();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < s; x++) {
				for (int z = 0; z < s; z++) {
					BlockPos pos = min.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					Kettle kettle = kettleOf(state, pos);
					if (kettle == null || state.getBlock() instanceof BrewKettleBlock && state.getValue(BrewKettleBlock.SUPPORT) == KettleSupport.HANDLE) {
						return null;
					}
					kettles.putIfAbsent(kettle.controller(), kettle);
				}
			}
		}
		// every kettle must lie wholly inside the box (a big kettle half in it can't join)
		int count = 0;
		for (Kettle kettle : kettles.values()) {
			for (BlockPos pos : kettle.positions()) {
				if (pos.getX() < min.getX() || pos.getY() < min.getY() || pos.getZ() < min.getZ()
					|| pos.getX() >= min.getX() + s || pos.getY() >= min.getY() + h || pos.getZ() >= min.getZ() + s) {
					return null;
				}
			}
			count += kettle.shape().blocks();
		}
		return count == shape.blocks() ? kettles : null;
	}

	private static boolean join(ServerLevel level, BlockPos placed, BlockPos min, KettleShape shape, Direction facing, Map<BlockPos, Kettle> members) {
		List<BrewKettleBlockEntity> entities = new ArrayList<>();
		for (Kettle kettle : members.values()) {
			if (!(level.getBlockEntity(kettle.controller()) instanceof BrewKettleBlockEntity be)) {
				return false;
			}
			entities.add(be);
		}
		BrewKettleBlockEntity.Merged merged = BrewKettleBlockEntity.merge(entities);
		if (merged == null) {
			return false;
		}
		// One vessel of one copper: only kettles of the same stage and wax join (else joining and breaking would wax or
		// clean kettles for free)
		Block single = KettleRegistry.singleOf(level.getBlockState(placed).getBlock());
		for (Kettle kettle : members.values()) {
			if (KettleRegistry.singleOf(level.getBlockState(kettle.controller()).getBlock()) != single) {
				return false;
			}
		}
		Block partBlock = KettleRegistry.partOf(single);
		BlockPos controller = controllerFor(min, shape, facing);
		Kettle joined = new Kettle(shape, controller, facing);

		converting = true;
		try {
			for (BrewKettleBlockEntity be : entities) {
				be.clearContent();
			}
			for (BlockPos pos : joined.positions()) {
				KettlePart part = partAt(joined, pos);
				BlockState state = partBlock.defaultBlockState()
					.setValue(BrewKettlePartBlock.FACING, facing)
					.setValue(BrewKettlePartBlock.PART, part.id())
					.setValue(BrewKettlePartBlock.TRAY, part.isBottom() && BrewKettleBlock.trayState(level, pos) == KettleSupport.TRAY);
				level.setBlock(pos, state, Block.UPDATE_ALL);
				if (!part.isController()) {
					// a part that stays the same block (a Big Kettle growing) keeps its old block entity: only the
					// controller may have one
					level.removeBlockEntity(pos);
				}
			}
		} finally {
			converting = false;
		}
		if (level.getBlockEntity(controller) instanceof BrewKettleBlockEntity be) {
			be.load(merged);
			for (ItemStack spill : merged.spills()) {
				Containers.dropItemStack(level, placed.getX() + 0.5D, placed.getY() + 1.0D, placed.getZ() + 0.5D, spill);
			}
		}
		level.playSound(null, placed, net.minecraft.sounds.SoundEvents.COPPER_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.6F);
		return true;
	}

	/** The world position of local (0, 0, 0) for a kettle filling the box at {@code min}. */
	static BlockPos controllerFor(BlockPos min, KettleShape shape, Direction facing) {
		BlockPos far = KettlePart.offset(facing, shape.size() - 1, 0, shape.size() - 1);
		return min.offset(Math.max(0, -far.getX()), 0, Math.max(0, -far.getZ()));
	}

	/** The part of {@code kettle} at world {@code pos}. */
	static KettlePart partAt(Kettle kettle, BlockPos pos) {
		BlockPos rel = pos.subtract(kettle.controller()).rotate(inverse(KettlePart.rotation(kettle.facing())));
		return new KettlePart(kettle.shape(), rel.getX(), rel.getY(), rel.getZ());
	}

	private static net.minecraft.world.level.block.Rotation inverse(net.minecraft.world.level.block.Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
			case COUNTERCLOCKWISE_90 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
			default -> rotation;
		};
	}

	// ------------------------------------------------------------------ splitting

	/**
	 * Splits a big kettle into single kettles: the brew is shared out evenly (a broken block's share goes into its drop,
	 * or to the others if nothing drops it), ingredients and buffered containers drop at {@code broken}.
	 *
	 * @param broken     the block that is being removed, or null to split a whole kettle in place
	 * @param dropBroken whether the broken block drops its share (a survival player broke it)
	 */
	public static void split(ServerLevel level, Kettle kettle, BrewKettleBlockEntity controller, Block look, @Nullable BlockPos broken, boolean dropBroken) {
		if (!kettle.isBig()) {
			return;
		}
		List<BlockPos> positions = new ArrayList<>(kettle.positions());
		BlockPos dropAt = broken != null ? broken : kettle.controller();
		// what can't stay in single kettles: the grid and the buffers
		Containers.dropContents(level, dropAt, controller.getDroppableInventory());
		controller.getUsedRecipesAndPopExperience(level, Vec3.atCenterOf(dropAt));
		controller.clearUsedRecipes();
		ItemStack brew = controller.getBrew();
		ItemStack bucket = controller.getBucketForm();
		int servings = controller.getServings();
		Block single = KettleRegistry.singleOf(look);
		boolean brokenTakesShare = broken != null && dropBroken;
		List<BlockPos> keepers = new ArrayList<>(positions);
		if (broken != null) {
			keepers.remove(broken);
		}
		int sharers = keepers.size() + (brokenTakesShare ? 1 : 0);
		int each = sharers == 0 ? 0 : servings / sharers;
		int extra = sharers == 0 ? 0 : servings % sharers;
		if (brokenTakesShare && each > 0) {
			PENDING_DROPS.put(dropKey(level, broken), new KettleContents(brew.copyWithCount(each), bucket));
		}
		int overflow = 0;
		converting = true;
		try {
			controller.clearContent();
			int i = 0;
			for (BlockPos pos : keepers) {
				int share = each + (i++ < extra ? 1 : 0);
				if (share > BrewKettleBlockEntity.MAX_SERVINGS) {
					overflow += share - BrewKettleBlockEntity.MAX_SERVINGS;
					share = BrewKettleBlockEntity.MAX_SERVINGS;
				}
				BlockState state = single.defaultBlockState()
					.setValue(BrewKettleBlock.FACING, kettle.facing())
					.setValue(BrewKettleBlock.SUPPORT, BrewKettleBlock.trayState(level, pos))
					.setValue(BrewKettleBlock.FILL, BrewKettleBlockEntity.fillLevel(share));
				level.setBlock(pos, state, Block.UPDATE_ALL);
				if (share > 0 && level.getBlockEntity(pos) instanceof BrewKettleBlockEntity be) {
					be.setBrew(brew.copyWithCount(share), bucket);
				}
			}
		} finally {
			converting = false;
		}
		// (a block removed without dropping leaves fewer kettles than the brew needs)
		if (overflow > 0) {
			int buckets = bucket.isEmpty() ? 0 : overflow / BrewKettleBlockEntity.SERVINGS_PER_BUCKET;
			for (int b = 0; b < buckets; b++) {
				Block.popResource(level, dropAt, bucket.copyWithCount(1));
			}
			int bottles = overflow - buckets * BrewKettleBlockEntity.SERVINGS_PER_BUCKET;
			while (bottles > 0) {
				int count = Math.min(bottles, brew.getMaxStackSize());
				Block.popResource(level, dropAt, brew.copyWithCount(count));
				bottles -= count;
			}
		}
	}

	/**
	 * The kettle behind a part block, a chimney piece (walking down the chimney) or a kettle: its controller, or null.
	 */
	public static @Nullable BrewKettleBlockEntity controllerUnder(Level level, BlockPos pos) {
		return controller(level, KettleChimneyBlock.kettleUnder(level, pos));
	}

	/** The share of the brew a broken part block drops with, once (see {@link #split}). */
	static @Nullable KettleContents takePendingDrop(Level level, BlockPos pos) {
		return PENDING_DROPS.remove(dropKey(level, pos));
	}

	/** A pending drop is for one block in one dimension, this tick only (a drop that never happened can't come back later). */
	private static String dropKey(Level level, BlockPos pos) {
		return level.dimension().location() + "@" + pos.asLong() + "@" + level.getGameTime();
	}

	/**
	 * Whether the rest of {@code kettle} (every block but {@code removed}) is still its parts: a removal that finds it so
	 * splits it; one that finds it already split (a player broke it: playerWillDestroy did) leaves it alone.
	 */
	public static boolean restIntact(BlockGetter level, Kettle kettle, BlockPos removed) {
		if (!kettle.isBig()) {
			return false;
		}
		for (BlockPos pos : kettle.positions()) {
			if (pos.equals(removed)) {
				continue;
			}
			Kettle there = kettleAt(level, pos);
			if (there == null || !there.equals(kettle)) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ one vessel: weathering, wax, scraping

	/** Turns every block of {@code kettle} into the kettle look of {@code single} (a weathering stage, waxed or not). */
	public static void restage(Level level, Kettle kettle, Block single) {
		Block part = KettleRegistry.partOf(single);
		Block chimney = KettleRegistry.chimneyOf(single);
		// (found before the kettle changes: the pieces stand on its chimney block)
		List<BlockPos> pieces = column(level, kettle).pieces();
		converting = true;
		try {
			for (BlockPos pos : kettle.positions()) {
				BlockState state = level.getBlockState(pos);
				Block target = state.getBlock() instanceof BrewKettlePartBlock ? part : single;
				if (state.getBlock() != target) {
					level.setBlock(pos, target.withPropertiesOf(state), Block.UPDATE_ALL_IMMEDIATE);
				}
			}
			// the chimney's extension weathers with the kettle, above a roof too
			for (BlockPos pos : pieces) {
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() != chimney) {
					level.setBlock(pos, chimney.withPropertiesOf(state), Block.UPDATE_ALL_IMMEDIATE);
				}
			}
		} finally {
			converting = false;
		}
	}

	/** Whether {@code items} pack into the {@link BrewKettleBlockEntity#INPUT_SLOTS} grid slots; fills {@code grid} if so. */
	static boolean pack(List<ItemStack> items, List<ItemStack> grid, ItemSlots capacityOf) {
		for (ItemStack stack : items) {
			ItemStack left = stack.copy();
			for (int i = 0; i < grid.size() && !left.isEmpty(); i++) {
				ItemStack slot = grid.get(i);
				if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, left)) {
					int room = capacityOf.getCapacity(i, slot) - slot.getCount();
					int moved = Math.min(room, left.getCount());
					slot.grow(moved);
					left.shrink(moved);
				}
			}
			for (int i = 0; i < grid.size() && !left.isEmpty(); i++) {
				if (grid.get(i).isEmpty()) {
					grid.set(i, left.copy());
					left = ItemStack.EMPTY;
				}
			}
			if (!left.isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
