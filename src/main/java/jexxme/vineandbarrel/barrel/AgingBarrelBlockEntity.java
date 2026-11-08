package jexxme.vineandbarrel.barrel;

import com.mojang.logging.LogUtils;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.SafeItemStacksResourceHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Unit;
import net.minecraft.world.Containers;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Aging Barrel (DESIGN §4.2): holds one batch of up to {@link #CAPACITY} servings. Fill it empty or top it up with
 * the same base while it is still fermenting; after one day it is the young drink, after 3 more the aged drink; it is
 * tapped one serving at a time into the drink's vessel, and resets when it is empty.
 * <p>
 * Time: the batch remembers both the game time and the day time (the dimension's clock) of its first fill, and its age
 * is whichever has moved further. So it keeps aging while the chunk is unloaded (game time), sleeping through the night
 * and {@code /time add} move it along (day time), {@code /time set} backwards never un-ages it, and with the daylight
 * cycle off it still ages on game time. Each tick both stamps are re-based to the age, so it only ever moves forward.
 * As an item (B12) the batch carries its age instead, so aging pauses until the barrel is placed again.
 * <p>
 * Automation (B14), like the Brew Kettle: a hopper above feeds bases into the input slot; hoppers at the sides feed
 * empty vessels into the vessel slot; drinks and the empty buckets and bottles left from pouring come out of the two
 * output slots, from below or the sides.
 */
public class AgingBarrelBlockEntity extends BlockEntity implements Nameable
{
	private static final Logger LOGGER = LogUtils.getLogger();

	/** 2 buckets or 8 bottles (DESIGN §4.2). */
	public static final int CAPACITY = 8;
	/** One bucket of a base is a whole kettle batch, as in the kettle. */
	public static final int SERVINGS_PER_BUCKET = BrewKettleBlockEntity.SERVINGS_PER_BUCKET;

	public static final int INPUT_SLOT = 0;
	public static final int VESSEL_SLOT = 1;
	public static final int DRINK_SLOT = 2;
	public static final int CONTAINER_SLOT = 3;
	public static final int INVENTORY_SIZE = 4;

	/** The aged "pop" (B7) only plays if the batch turned aged at most this long ago, not when a chunk loads days later. */
	static final int AGED_POP_WINDOW = 100;
	/** How long the tap handle stays turned after a serving is tapped (B9). */
	static final int TAP_TICKS = 8;

	private BarrelContents batch = BarrelContents.EMPTY;
	/** Game time of the batch's first fill (re-based so that now - fillTime is the age). */
	private long fillTime;
	/** Day time (the dimension's clock) of the batch's first fill, re-based the same way; {@link #NO_DAY} if unknown. */
	private long fillDayTime = NO_DAY;
	private static final long NO_DAY = Long.MIN_VALUE;
	/** Re-basing by more than this (a night slept, /time) is sent to clients, so they see the same age. */
	private static final long RESYNC_JUMP = 20L;
	/** The batch's age while it is not in a level yet (loaded from an item before the level is known); -1 otherwise. */
	private long pausedAge = -1L;
	/**
	 * The batch was poured in during play. A tavern's batch comes from the structure file without the flag, so only
	 * the player's own aging earns Patience Pays, wherever the barrel stands.
	 */
	private boolean homeBrewed;
	private @Nullable Component customName;

	private final ItemStacksResourceHandler inventory;
	private final ResourceHandler<ItemResource> topHandler;
	private final ResourceHandler<ItemResource> sideHandler;

	public AgingBarrelBlockEntity(BlockPos pos, BlockState state) {
		super(BarrelRegistry.AGING_BARREL_BE.get(), pos, state);
		this.inventory = createHandler();
		this.topHandler = new BarrelItemHandler(inventory, Direction.UP);
		this.sideHandler = new BarrelItemHandler(inventory, Direction.DOWN);
	}

	static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.Item.BLOCK, BarrelRegistry.AGING_BARREL_BE.get(),
			(be, side) -> side == Direction.UP ? be.topHandler : be.sideHandler);
	}

	// ------------------------------------------------------------------ time and stage

	private long now() {
		return level != null ? level.getGameTime() : 0L;
	}

	/** The dimension's day clock (0 where there is none, e.g. the Nether: game time alone counts there). */
	private long today() {
		return level != null ? level.getDefaultClockTime() : 0L;
	}

	private void stamp(long age) {
		fillTime = now() - age;
		fillDayTime = today() - age;
	}

	/** Ticks since the batch's first fill (0 when empty). */
	public long age() {
		if (batch.isEmpty()) {
			return 0L;
		}
		if (pausedAge >= 0L) {
			return pausedAge;
		}
		long byGameTime = now() - fillTime;
		long byDayTime = fillDayTime == NO_DAY ? byGameTime : today() - fillDayTime;
		return Math.max(0L, Math.max(byGameTime, byDayTime));
	}

	/**
	 * Re-bases both stamps on the current age, so the clock that moved further (a night slept, game time with the
	 * daylight cycle off) carries the other along, and a clock set back later can't take the age back.
	 */
	private void rebase() {
		if (batch.isEmpty() || pausedAge >= 0L || level == null) {
			return;
		}
		long age = age();
		long gameJump = (now() - age) - fillTime;
		long dayJump = fillDayTime == NO_DAY ? Long.MAX_VALUE : Math.abs((today() - age) - fillDayTime);
		stamp(age);
		if (Math.abs(gameJump) > RESYNC_JUMP || dayJump > RESYNC_JUMP) {
			changed();
		}
	}

	public BarrelStage stage() {
		return batch.stageAt(age());
	}

	/** Servings left. */
	public int servings() {
		return batch.servings();
	}

	/** The batch, with its current age. */
	/** {@return whether the batch was poured in during play, rather than a tavern's stock from world generation} */
	public boolean isHomeBrewed() {
		return homeBrewed;
	}

	public BarrelContents contents() {
		return batch.withElapsed(age());
	}

	/** What a serving is right now: the base while fermenting, then the young drink, then the aged drink (count 1). */
	public ItemStack content() {
		return batch.contentAt(age());
	}

	/** The drink this batch is, or will be once fermented. */
	public ItemStack drink() {
		return batch.drinkAt(age());
	}

	public long ticksToNextStage() {
		return batch.ticksToNextStage(age());
	}

	/** Sets the batch's age (as if it had been filled {@code age} ticks ago). For commands and tests. */
	public void setAge(long age) {
		if (!batch.isEmpty()) {
			pausedAge = -1L;
			stamp(Math.max(0L, age));
			changed();
		}
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		resolvePausedAge();
	}

	private void resolvePausedAge() {
		if (pausedAge >= 0L && level != null) {
			long age = pausedAge;
			pausedAge = -1L;
			stamp(age);
		}
	}

	// ------------------------------------------------------------------ recipes

	/** The aging recipe this stack is the base (or bucket of base) of, on server and client alike (see {@link AgingRecipes}). */
	public static @Nullable AgingRecipe recipeFor(Level level, ItemStack stack) {
		return AgingRecipes.forBase(level, stack);
	}

	/** Servings one of these pours into the current batch: 1 for its base, 4 for its bucket, 0 for anything else. */
	private int servingsInBatch(ItemStack stack) {
		if (batch.isEmpty() || stack.isEmpty()) return 0;
		if (ItemStack.isSameItemSameComponents(stack, batch.base())) return 1;
		ItemStack bucket = batch.bucket();
		if (!bucket.isEmpty() && ItemStack.isSameItemSameComponents(stack, bucket)) return SERVINGS_PER_BUCKET;
		return 0;
	}

	// ------------------------------------------------------------------ pouring in (B8, B13, B14)

	/** Why a stack can't be poured in, or {@link Refusal#NONE} if it can. */
	public Refusal pourRefusal(ItemStack stack) {
		if (batch.isEmpty()) {
			return level != null && recipeFor(level, stack) != null ? Refusal.NONE : Refusal.NOT_A_BASE;
		}
		int servings = servingsInBatch(stack);
		if (servings == 0) {
			return level != null && recipeFor(level, stack) != null ? Refusal.OTHER_BASE : Refusal.NOT_A_BASE;
		}
		if (stage() != BarrelStage.FERMENTING) {
			return Refusal.ALREADY_A_DRINK;
		}
		return batch.servings() + servings > CAPACITY ? Refusal.FULL : Refusal.NONE;
	}

	/** Servings this stack pours in right now, 0 if the barrel refuses it (the fill rule, capacity). */
	public int pourableServings(ItemStack stack) {
		if (pourRefusal(stack) != Refusal.NONE) {
			return 0;
		}
		if (batch.isEmpty()) {
			AgingRecipe recipe = level == null ? null : recipeFor(level, stack);
			return recipe == null ? 0 : recipe.servingsOf(stack);
		}
		return servingsInBatch(stack);
	}

	/** The empty container a poured-in base leaves behind (its crafting remainder: a bucket or a glass bottle). */
	public static ItemStack containerOf(ItemStack filled) {
		ItemStackTemplate remainder = filled.getCraftingRemainder();
		return remainder != null ? remainder.create() : ItemStack.EMPTY;
	}

	/**
	 * Pours one of {@code stack} in: a bottle of base is 1 serving, a bucket 4. The first fill starts the timer, a
	 * top-up doesn't. Takes the item from the stack (not for players with infinite materials) and returns the empty
	 * container it leaves, or null if the barrel refuses it (and nothing changed).
	 */
	public @Nullable ItemStack pour(ItemStack stack, @Nullable Player player) {
		int servings = pourableServings(stack);
		if (servings == 0) {
			return null;
		}
		if (batch.isEmpty()) {
			AgingRecipe recipe = recipeFor(level, stack);
			if (recipe == null) {
				return null;
			}
			batch = recipe.newBatch();
			pausedAge = -1L;
			homeBrewed = true;
			stamp(0L);
		}
		batch = batch.withServings(batch.servings() + servings);
		ItemStack container = containerOf(stack);
		if (player == null || !player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		if (level != null) {
			// The slosh rises in pitch as the barrel fills (the cask hides its level, the sound shows it)
			level.playSound(null, worldPosition, servings == SERVINGS_PER_BUCKET ? BarrelRegistry.BARREL_FILL_BUCKET.get() : BarrelRegistry.BARREL_FILL.get(),
				SoundSource.BLOCKS, 1.0F, fillPitch());
		}
		changed();
		return container;
	}

	private float fillPitch() {
		return 0.8F + 0.4F * batch.servings() / CAPACITY;
	}

	// ------------------------------------------------------------------ taking the base back out (B8: undo)

	/** Servings this empty container takes back out (1 for the base's bottle, 4 for its bucket), 0 if it can't. */
	public int undoServings(ItemStack container) {
		if (container.isEmpty() || stage() != BarrelStage.FERMENTING) {
			return 0;
		}
		ItemStack bottle = containerOf(batch.base());
		if (!bottle.isEmpty() && container.is(bottle.getItem())) {
			return 1;
		}
		ItemStack bucket = batch.bucket();
		if (!bucket.isEmpty()) {
			ItemStack emptyBucket = containerOf(bucket);
			if (!emptyBucket.isEmpty() && container.is(emptyBucket.getItem())) {
				return SERVINGS_PER_BUCKET;
			}
		}
		return 0;
	}

	/**
	 * Until it turns into a drink, an empty bucket or bottle takes the base back out (like a cauldron). Takes one
	 * container and returns the filled one, or empty if it can't (it is a drink already, or a bucket needs 4 servings).
	 */
	public ItemStack takeBack(ItemStack container, @Nullable Player player) {
		int servings = undoServings(container);
		if (servings == 0 || batch.servings() < servings) {
			return ItemStack.EMPTY;
		}
		ItemStack filled = servings == SERVINGS_PER_BUCKET ? batch.bucket() : batch.base();
		if (player == null || !player.hasInfiniteMaterials()) {
			container.shrink(1);
		}
		float pitch = fillPitch();
		setServings(batch.servings() - servings);
		if (level != null) {
			level.playSound(null, worldPosition, servings == SERVINGS_PER_BUCKET ? BarrelRegistry.BARREL_DRAIN_BUCKET.get() : BarrelRegistry.BARREL_DRAIN.get(),
				SoundSource.BLOCKS, 1.0F, pitch);
		}
		return filled;
	}

	// ------------------------------------------------------------------ tapping (B9, B13, B14)

	/** The vessel a drink is served in: its use remainder (a mug or a wine glass), or null if it has none. */
	public static @Nullable Item vesselOf(ItemStack drink) {
		UseRemainder remainder = drink.get(DataComponents.USE_REMAINDER);
		return remainder == null ? null : remainder.convertInto().item().value();
	}

	/** The vessel the current drink needs, or null while it is not a drink yet. */
	public @Nullable Item vesselNeeded() {
		return stage().isDrink() ? vesselOf(content()) : null;
	}

	public boolean canTap(ItemStack vessel) {
		Item needed = vesselNeeded();
		return needed != null && !vessel.isEmpty() && vessel.is(needed);
	}

	/**
	 * Taps one serving into an empty vessel: takes the vessel (not for players with infinite materials) and returns the
	 * drink, or empty if this is not the right vessel or there is no drink yet (nothing is used up then).
	 */
	public ItemStack tap(ItemStack vessel, @Nullable Player player) {
		if (!canTap(vessel)) {
			return ItemStack.EMPTY;
		}
		ItemStack drink = content();
		if (player == null || !player.hasInfiniteMaterials()) {
			vessel.shrink(1);
		}
		setServings(batch.servings() - 1);
		if (level != null) {
			level.playSound(null, worldPosition, BarrelRegistry.BARREL_TAP.get(), SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			BlockState state = getBlockState();
			if (state.hasProperty(AgingBarrelBlock.TAPPING)) {
				level.setBlock(worldPosition, state.setValue(AgingBarrelBlock.TAPPING, true), Block.UPDATE_ALL);
				level.scheduleTick(worldPosition, state.getBlock(), TAP_TICKS);
			}
		}
		return drink;
	}

	/** An empty barrel forgets its batch and timer, ready for the next one. */
	private void setServings(int servings) {
		batch = batch.withServings(servings);
		if (batch.isEmpty()) {
			fillTime = 0L;
			fillDayTime = NO_DAY;
			pausedAge = -1L;
			homeBrewed = false;
		}
		changed();
	}

	// ------------------------------------------------------------------ ticking

	public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, AgingBarrelBlockEntity barrel) {
		barrel.resolvePausedAge();
		barrel.rebase();
		// The fill gauge follows the servings (set right away by every change; this catches loading and placing)
		if (state.getValue(AgingBarrelBlock.FILL) != barrel.servings()) {
			state = state.setValue(AgingBarrelBlock.FILL, barrel.servings());
			level.setBlock(pos, state, Block.UPDATE_ALL);
		}
		// B4/B7: the bung follows the stage; the aged moment pops the cork and seals it with wax
		BungState bung = barrel.stage().bung();
		if (state.getValue(AgingBarrelBlock.BUNG) != bung) {
			level.setBlock(pos, state.setValue(AgingBarrelBlock.BUNG, bung), Block.UPDATE_ALL);
			long sinceAged = barrel.age() - barrel.batch.fermentingTime() - barrel.batch.agingTime();
			if (bung == BungState.WAX && sinceAged < AGED_POP_WINDOW) {
				level.playSound(null, pos, BarrelRegistry.BARREL_AGED.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
				level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.12, 0.05, 0.12, 0.02);
			}
		}
		barrel.automate();
	}

	/** B14: pour in what waits in the input slot, tap into what waits in the vessel slot. One of each per tick. */
	private void automate() {
		ItemStack input = stackIn(INPUT_SLOT);
		if (!input.isEmpty() && pourableServings(input) > 0) {
			ItemStack leftover = containerOf(input);
			ItemStack containers = stackIn(CONTAINER_SLOT);
			if (fitsInto(containers, leftover)) {
				ItemStack container = pour(input, null);
				setStack(INPUT_SLOT, input);
				if (container != null && !container.isEmpty()) {
					setStack(CONTAINER_SLOT, containers.isEmpty() ? container : containers.copyWithCount(containers.getCount() + container.getCount()));
				}
			}
		}
		ItemStack vessels = stackIn(VESSEL_SLOT);
		if (canTap(vessels)) {
			ItemStack drinks = stackIn(DRINK_SLOT);
			if (fitsInto(drinks, content())) {
				ItemStack drink = tap(vessels, null);
				setStack(VESSEL_SLOT, vessels);
				setStack(DRINK_SLOT, drinks.isEmpty() ? drink : drinks.copyWithCount(drinks.getCount() + 1));
			}
		}
	}

	private static boolean fitsInto(ItemStack slot, ItemStack add) {
		if (add.isEmpty() || slot.isEmpty()) return true;
		return ItemStack.isSameItemSameComponents(slot, add) && slot.getCount() + add.getCount() <= slot.getMaxStackSize();
	}

	// ------------------------------------------------------------------ comparator (B11)

	/** Servings left, 0-8 mapped onto 0-15 (8 servings = 15, any serving at least 1). */
	public int comparatorSignal() {
		return servings() * 15 / CAPACITY;
	}

	// ------------------------------------------------------------------ inventory (automation slots)

	public ItemStacksResourceHandler getInventory() {
		return inventory;
	}

	/** The stack in a slot with its real amount. A copy: write changes back with {@link #setStack}. */
	public ItemStack stackIn(int slot) {
		return inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
	}

	public void setStack(int slot, ItemStack stack) {
		inventory.set(slot, stack.isEmpty() ? ItemResource.EMPTY : ItemResource.of(stack), stack.isEmpty() ? 0 : stack.getCount());
	}

	/** The automation slots' items; the batch itself stays in the barrel item when it is broken (B12). */
	public NonNullList<ItemStack> getDroppableInventory() {
		NonNullList<ItemStack> drops = NonNullList.create();
		for (int i = 0; i < INVENTORY_SIZE; ++i) {
			drops.add(stackIn(i));
		}
		return drops;
	}

	/** 26.x: the block entity is already gone in Block#affectNeighborsAfterRemoval, so slot items are dropped here. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel) {
			Containers.dropContents(serverLevel, pos, getDroppableInventory());
		}
	}

	public ItemStack getAsItem() {
		ItemStack stack = new ItemStack(getBlockState().getBlock().asItem());
		stack.applyComponents(collectComponents());
		return stack; // 26.2: AgingBarrelItem#getBurnTime keeps a full barrel out of the furnace
	}

	private ItemStacksResourceHandler createHandler() {
		return new SafeItemStacksResourceHandler(INVENTORY_SIZE)
		{
			@Override
			public boolean isValid(int index, ItemResource resource) {
				if (resource.isEmpty()) {
					return true;
				}
				return switch (index) {
					// Only what this barrel can take now (the fill rule), so a hopper never jams it with another base
					case INPUT_SLOT -> pourableServings(resource.toStack(1)) > 0;
					case VESSEL_SLOT -> resource.is(BarrelRegistry.DRINK_VESSELS);
					default -> true;
				};
			}

			@Override
			protected void onContentsChanged(int index, ItemStack previousContents) {
				changed();
			}
		};
	}

	// ------------------------------------------------------------------ name (B15)

	@Override
	public Component getName() {
		return customName != null ? customName : Component.translatable("block.vineandbarrel.aging_barrel");
	}

	@Override
	public @Nullable Component getCustomName() {
		return customName;
	}

	// ------------------------------------------------------------------ save, sync, item components

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		inventory.deserialize(input.childOrEmpty("Inventory"));
		readBatch(input);
		customName = input.read("CustomName", ComponentSerialization.CODEC).orElse(null);
	}

	private void readBatch(ValueInput input) {
		batch = input.read("Batch", BarrelContents.CODEC).orElse(BarrelContents.EMPTY);
		fillTime = input.getLongOr("FillTime", 0L);
		fillDayTime = input.getLongOr("FillDayTime", NO_DAY);
		pausedAge = input.getLongOr("PausedAge", -1L);
		homeBrewed = !batch.isEmpty() && input.getBooleanOr("HomeBrewed", false);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		inventory.serialize(output.child("Inventory"));
		writeBatch(output);
		output.storeNullable("CustomName", ComponentSerialization.CODEC, customName);
	}

	/** The batch with the absolute fill time: a saved world keeps aging while it is unloaded, like a placed barrel. */
	private void writeBatch(ValueOutput output) {
		if (!batch.isEmpty()) {
			output.store("Batch", BarrelContents.CODEC, batch.withElapsed(0L));
			output.putLong("FillTime", fillTime);
			if (fillDayTime != NO_DAY) {
				output.putLong("FillDayTime", fillDayTime);
			}
			if (pausedAge >= 0L) {
				output.putLong("PausedAge", pausedAge);
			}
			if (homeBrewed) {
				output.putBoolean("HomeBrewed", true);
			}
		}
	}

	@Override
	public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** What the client needs: the batch and its fill time (bubbles, drip colour), and the name. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			writeBatch(output);
			output.storeNullable("CustomName", ComponentSerialization.CODEC, customName);
			return output.buildResult();
		}
	}

	@Override
	public void onDataPacket(Connection connection, ValueInput input) {
		readBatch(input);
		customName = input.read("CustomName", ComponentSerialization.CODEC).orElse(null);
	}

	private void changed() {
		super.setChanged();
		if (level != null) {
			BlockState state = getBlockState();
			if (!level.isClientSide() && state.hasProperty(AgingBarrelBlock.FILL) && state.getValue(AgingBarrelBlock.FILL) != servings()
				&& level.getBlockState(worldPosition) == state) {
				level.setBlock(worldPosition, state.setValue(AgingBarrelBlock.FILL, servings()), Block.UPDATE_ALL);
			}
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
		}
	}


	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		customName = components.get(DataComponents.CUSTOM_NAME);
		BarrelContents contents = components.getOrDefault(BarrelRegistry.BARREL_CONTENTS.get(), BarrelContents.EMPTY);
		batch = contents.withElapsed(0L);
		// B12: the batch carries on aging from the age it had when the barrel was broken
		pausedAge = batch.isEmpty() ? -1L : contents.elapsed();
		fillTime = 0L;
		fillDayTime = NO_DAY;
		homeBrewed = !batch.isEmpty() && components.get(BarrelRegistry.HOME_BREWED.get()) != null;
		resolvePausedAge();
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(DataComponents.CUSTOM_NAME, customName);
		if (!batch.isEmpty()) {
			components.set(BarrelRegistry.BARREL_CONTENTS.get(), contents());
			if (homeBrewed) {
				components.set(BarrelRegistry.HOME_BREWED.get(), Unit.INSTANCE);
			}
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void removeComponentsFromTag(ValueOutput output) {
		output.discard("CustomName");
		output.discard("Batch");
		output.discard("FillTime");
		output.discard("FillDayTime");
		output.discard("PausedAge");
		output.discard("HomeBrewed");
	}

	/** Why something can't be poured into the barrel. */
	public enum Refusal
	{
		NONE,
		/** Not the base of any aging recipe. */
		NOT_A_BASE,
		/** A base, but not the one this barrel holds. */
		OTHER_BASE,
		/** It has turned into a drink already: only an empty barrel takes a new batch. */
		ALREADY_A_DRINK,
		/** It would hold more than 8 servings. */
		FULL
	}
}
