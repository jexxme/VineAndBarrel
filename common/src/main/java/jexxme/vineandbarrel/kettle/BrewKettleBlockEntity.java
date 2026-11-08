package jexxme.vineandbarrel.kettle;

import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.hooks.DataPacketReceiver;
import com.google.common.collect.Lists;
import jexxme.vineandbarrel.advancement.VBAdvancements;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.inventory.ItemSlots;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * The Brew Kettle's brewing logic, adapted from Farmer's Delight's {@code CookingPotBlockEntity} (MIT, vectorwing).
 * <p>
 * Slots: 0-5 ingredients (the screen's grid), 6 the finished brew (in its bottle form, count = servings), 7 and 8
 * the hopper buffers: empty bottles or buckets waiting, and the filled ones waiting below. Only the grid is on the
 * screen; the brew is served by hand like a cauldron (right-click with a container) or by hoppers. One brew makes {@link BrewingRecipe#servings()} servings (4 for every base).
 * A glass bottle takes 1 serving, a bucket takes {@link #SERVINGS_PER_BUCKET}.
 */
public class BrewKettleBlockEntity extends BlockEntity implements MenuProvider, Nameable, RecipeCraftingHolder, Clearable, DataPacketReceiver
{
	private static final Logger LOGGER = LogUtils.getLogger();

	public static final int INPUT_SLOTS = 6;
	public static final int BREW_SLOT = 6;
	public static final int CONTAINER_SLOT = 7;
	public static final int OUTPUT_SLOT = 8;
	public static final int INVENTORY_SIZE = OUTPUT_SLOT + 1;
	/** One bucket holds a whole batch: 4 servings (DESIGN §4.1). One bottle is one serving. */
	public static final int SERVINGS_PER_BUCKET = 4;
	/** Like FD's meal slot: a kettle holds up to 64 servings of one brew. */
	public static final int MAX_SERVINGS = 64;

	private static final Codec<Map<ResourceKey<Recipe<?>>, Integer>> RECIPES_USED_CODEC = Codec.unboundedMap(Recipe.KEY_CODEC, Codec.INT);

	/** FD's ingredient remainder overrides: items that leave a container behind when used as an ingredient. */
	public static final Map<Item, Item> INGREDIENT_REMAINDER_OVERRIDES = Map.ofEntries(
		entry(Items.POWDER_SNOW_BUCKET, Items.BUCKET),
		entry(Items.AXOLOTL_BUCKET, Items.BUCKET),
		entry(Items.COD_BUCKET, Items.BUCKET),
		entry(Items.PUFFERFISH_BUCKET, Items.BUCKET),
		entry(Items.SALMON_BUCKET, Items.BUCKET),
		entry(Items.TROPICAL_FISH_BUCKET, Items.BUCKET),
		entry(Items.SUSPICIOUS_STEW, Items.BOWL),
		entry(Items.MUSHROOM_STEW, Items.BOWL),
		entry(Items.RABBIT_STEW, Items.BOWL),
		entry(Items.BEETROOT_SOUP, Items.BOWL),
		entry(Items.POTION, Items.GLASS_BOTTLE),
		entry(Items.SPLASH_POTION, Items.GLASS_BOTTLE),
		entry(Items.LINGERING_POTION, Items.GLASS_BOTTLE),
		entry(Items.EXPERIENCE_BOTTLE, Items.GLASS_BOTTLE)
	);

	private final ItemSlots inventory;

	private int brewTime;
	private int brewTimeTotal;
	/** The current brew in its bucket form (e.g. a Bucket of Sweet Berry Juice), empty when the kettle holds no brew. */
	private ItemStack bucketForm = ItemStack.EMPTY;
	private @Nullable Component customName;
	private int openCount;
	/**
	 * Whether the ingredients make a brew, as the server last matched them on the fire. Synced, not saved: the client
	 * has no recipes, and this is what tells it a kettle of odds and ends stays quiet (K3).
	 */
	private boolean ingredientsMatch;
	/** Set while the dropped slots are cleared on removal, so that clearing doesn't notify a block that is going away. */
	private boolean clearingDrops;

	protected final ContainerData kettleData;
	private final Object2IntOpenHashMap<ResourceKey<Recipe<?>>> usedRecipeTracker = new Object2IntOpenHashMap<>();
	private final RecipeManager.CachedCheck<RecipeInput, BrewingRecipe> quickCheck;
	/**
	 * The last recipe match, kept while the ingredient slots and the recipes stay the same: a heated kettle asks every
	 * tick, and matching is the kettle's main cost. The slots are compared with a copy of what they held (any change
	 * counts, however it was made: hopper, menu, command), the recipes by identity (a reload makes a new map).
	 */
	private final ItemStack[] matchedInputs = new ItemStack[INPUT_SLOTS];
	private @Nullable RecipeMap matchedRecipes;
	private @Nullable RecipeHolder<BrewingRecipe> matched;
	/** What {@link #matched} makes (its result never depends on the input), for {@link #canBrew}. */
	private ItemStack matchedResult = ItemStack.EMPTY;

	public BrewKettleBlockEntity(BlockPos pos, BlockState state) {
		super(KettleRegistry.BREW_KETTLE_BE.get(), pos, state);
		this.inventory = createHandler();
		this.kettleData = createData();
		this.quickCheck = RecipeManager.createCheck(KettleRegistry.BREWING.get());
	}

	/**
	 * K-automation: hoppers above feed ingredients and containers, at the sides containers; a hopper below pulls the
	 * filled ones. Which side's rules apply when pipes ask from {@code side} (no side counts as the top).
	 */
	public static Direction automationSide(@Nullable Direction side) {
		return side == null || side == Direction.UP ? Direction.UP : side == Direction.DOWN ? Direction.DOWN : Direction.NORTH;
	}

	/**
	 * Hopper access, like a cauldron that works itself: the brew lives in the kettle and only ever leaves in a container.
	 * <ul>
	 * <li>top: ingredients go into the grid, empty bottles and buckets into the container buffer;</li>
	 * <li>sides: empty bottles and buckets into the container buffer;</li>
	 * <li>bottom: the filled ones come out of the output buffer.</li>
	 * </ul>
	 * Nothing pulls ingredients, the brew or the empty containers back out. {@code side} is an {@link #automationSide}.
	 */
	public static boolean mayAutomationInsert(Direction side, int index, ItemStack stack) {
		if (side == Direction.DOWN) {
			return false;
		}
		boolean container = stack.is(KettleTags.KETTLE_CONTAINERS);
		if (index == CONTAINER_SLOT) {
			return container;
		}
		return side == Direction.UP && index < INPUT_SLOTS && !container;
	}

	public static boolean mayAutomationExtract(Direction side, int index) {
		return side == Direction.DOWN && index == OUTPUT_SLOT;
	}

	// ------------------------------------------------------------------ ticking

	public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, BrewKettleBlockEntity kettle) {
		boolean heated = KettleTags.isHeated(level, pos);
		boolean changed = false;
		boolean brewing = false;

		boolean hasInput = kettle.hasInput();
		boolean matches = kettle.ingredientsMatch && hasInput;
		if (heated && hasInput) {
			RecipeHolder<BrewingRecipe> recipe = kettle.matchRecipe(level);
			matches = recipe != null;
			if (recipe != null && kettle.canBrew(kettle.matchedResult, recipe.value())) {
				brewing = true;
				changed = kettle.processBrewing(level, recipe);
			} else {
				kettle.brewTime = Mth.clamp(kettle.brewTime - 2, 0, kettle.brewTimeTotal);
			}
		} else {
			// Cold or empty: let go of the last match, so a reload's old recipes aren't kept alive by idle kettles
			kettle.forgetMatch();
			if (kettle.brewTime > 0) {
				kettle.brewTime = Mth.clamp(kettle.brewTime - 2, 0, kettle.brewTimeTotal);
			}
		}

		if (kettle.getServings() > 0 && !kettle.stackIn(CONTAINER_SLOT).isEmpty()) {
			changed |= kettle.fillStoredContainers();
		}

		if (matches != kettle.ingredientsMatch) {
			kettle.ingredientsMatch = matches;
			if (!changed) {
				level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
			}
		}
		if (changed) {
			kettle.inventoryChanged();
		}

		// The waiting brew shows under the raised lid; while a batch brews the lid stays down
		int fill = brewing ? 0 : fillLevel(kettle.getServings());
		if (state.getValue(BrewKettleBlock.FILL) != fill) {
			state = state.setValue(BrewKettleBlock.FILL, fill);
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}

		// K2: re-count viewers now and then, so the lid never stays open after someone left without closing the screen
		if (state.getValue(BrewKettleBlock.OPEN) && level.getGameTime() % 20L == 0L) {
			kettle.updateLid(null, null);
		}
	}

	/**
	 * K3: whether a kettle on the fire stays quiet, with no steam, bubbles or boiling: nothing brewed in it and nothing in
	 * the grid that makes a brew. Empty bottles and buckets waiting in the container slot don't count.
	 */
	public static boolean staysQuiet(boolean hasIngredients, boolean ingredientsMatch, int servings) {
		return servings <= 0 && !(hasIngredients && ingredientsMatch);
	}

	/** {@link #staysQuiet(boolean, boolean, int)} for this kettle as it is now; on the client, as the server last synced it. */
	public boolean staysQuiet() {
		return staysQuiet(hasInput(), ingredientsMatch, getServings());
	}

	/**
	 * K3: while heated with a brew or a batch in it, steam curls out under the lid and bubbles pop, the lid rattles now
	 * and then while brewing. A kettle with nothing to brew stays quiet, so it never looks like it's cooking.
	 */
	public static void clientTick(Level level, BlockPos pos, BlockState state, BrewKettleBlockEntity kettle) {
		boolean hasInput = kettle.hasInput();
		int servings = kettle.getServings();
		if (staysQuiet(hasInput, kettle.ingredientsMatch, servings) || !KettleTags.isHeated(level, pos)) {
			return;
		}
		boolean brewing = hasInput && kettle.ingredientsMatch;
		RandomSource random = level.getRandom();
		boolean open = state.getValue(BrewKettleBlock.OPEN) || state.getValue(BrewKettleBlock.FILL) > 0;
		double cx = pos.getX() + 0.5D;
		double cz = pos.getZ() + 0.5D;
		double seam = pos.getY() + 10.0D / 16.0D;
		if (random.nextFloat() < 0.2F) {
			double spread = open ? 0.3D : 0.38D;
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = open ? random.nextDouble() * spread : spread;
			level.addParticle(ParticleTypes.BUBBLE_POP, cx + Math.cos(angle) * r, seam + 0.02D, cz + Math.sin(angle) * r, 0.0D, 0.0D, 0.0D);
		}
		// a thin wisp now and then: from the open pot, or curling out under the lid's rim
		if (random.nextFloat() < (open ? 0.1F : 0.075F)) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = open ? random.nextDouble() * 0.22D : 0.34D;
			double out = open ? 0.0D : 0.004D;
			level.addParticle(KettleRegistry.STEAM.get(), cx + Math.cos(angle) * r, seam + 0.04D, cz + Math.sin(angle) * r,
				Math.cos(angle) * out, 0.004D + random.nextDouble() * 0.006D, Math.sin(angle) * out);
		}
		if (random.nextInt(10) == 0) {
			// FD's boil sound; the bubbling one once there is a brew inside
			level.playLocalSound(cx, pos.getY(), cz, servings <= 0 ? KettleRegistry.BOIL.get() : KettleRegistry.BOIL_BREW.get(),
				SoundSource.BLOCKS, 0.5F, random.nextFloat() * 0.2F + 0.9F, false);
		}
		if (!open && brewing && random.nextInt(40) == 0) {
			level.playLocalSound(cx, seam, cz, KettleRegistry.LID_RATTLE.get(), SoundSource.BLOCKS, 0.35F, random.nextFloat() * 0.3F + 1.1F, false);
		}
	}

	/** The recipe the ingredient slots make, matched again only when they or the recipes changed since the last match. */
	private @Nullable RecipeHolder<BrewingRecipe> matchRecipe(ServerLevel level) {
		RecipeMap recipes = level.recipeAccess().recipes;
		boolean same = recipes == matchedRecipes;
		for (int i = 0; i < INPUT_SLOTS && same; ++i) {
			ItemStack now = inventory.getStack(i);
			ItemStack before = matchedInputs[i];
			same = before != null && (now.isEmpty() ? before.isEmpty() : ItemStack.matches(now, before));
		}
		if (!same) {
			matched = quickCheck.getRecipeFor(new KettleRecipeInput(inventory), level).orElse(null);
			matchedResult = matched == null ? ItemStack.EMPTY : matched.value().assemble(new KettleRecipeInput(inventory));
			matchedRecipes = recipes;
			for (int i = 0; i < INPUT_SLOTS; ++i) {
				matchedInputs[i] = inventory.copyStack(i);
			}
		}
		return matched;
	}

	private void forgetMatch() {
		if (matchedRecipes != null) {
			matchedRecipes = null;
			matched = null;
			matchedResult = ItemStack.EMPTY;
			java.util.Arrays.fill(matchedInputs, null);
		}
	}

	private boolean hasInput() {
		for (int i = 0; i < INPUT_SLOTS; ++i) {
			if (!inventory.getStack(i).isEmpty()) return true;
		}
		return false;
	}

	public boolean hasIngredients() {
		return hasInput();
	}

	/**
	 * Whether the ingredient grid holds something that makes no recipe (a leftover after a batch, a wrong mix): the screen
	 * greys its arrow. Cold or hot alike; the match is the cached one, so asking every tick costs nothing new. Server only.
	 */
	public boolean ingredientsMakeNothing() {
		return hasInput() && level instanceof ServerLevel server && matchRecipe(server) == null;
	}

	/** Whether a batch making {@code result} (only read) fits with the brew already stored. */
	private boolean canBrew(ItemStack result, BrewingRecipe recipe) {
		if (result.isEmpty()) {
			return false;
		}
		ItemStack stored = inventory.getStack(BREW_SLOT);
		if (stored.isEmpty()) {
			return true;
		}
		if (!ItemStack.isSameItemSameComponents(stored, result) || !ItemStack.isSameItemSameComponents(bucketForm, recipe.bucket())) {
			return false;
		}
		return stored.getCount() + result.getCount() <= MAX_SERVINGS;
	}

	private boolean processBrewing(ServerLevel level, RecipeHolder<BrewingRecipe> holder) {
		BrewingRecipe recipe = holder.value();
		++brewTime;
		brewTimeTotal = recipe.brewingTime();
		if (brewTime < brewTimeTotal) {
			return false;
		}

		// The pooled match decides which slots this batch takes from (DESIGN: hoppers stack repeated ingredients into one slot)
		int[] taken = recipe.consumption(new KettleRecipeInput(inventory));
		if (taken == null) {
			return false;
		}
		brewTime = 0;
		ItemStack result = recipe.assemble(new KettleRecipeInput(inventory));
		ItemStack stored = getBrew();
		if (stored.isEmpty()) {
			setStack(BREW_SLOT, result.copy());
			bucketForm = recipe.bucket();
		} else {
			stored.grow(result.getCount());
			setStack(BREW_SLOT, stored);
		}
		setRecipeUsed(holder);

		for (int i = 0; i < taken.length; ++i) {
			ItemStack slotStack = stackIn(i);
			if (taken[i] == 0 || slotStack.isEmpty()) {
				continue;
			}
			for (int n = 0; n < taken[i]; ++n) {
				ItemStackTemplate remainder = Services.PLATFORM.craftingRemainder(slotStack);
				if (remainder != null) {
					ejectIngredientRemainder(remainder.create());
				} else if (INGREDIENT_REMAINDER_OVERRIDES.containsKey(slotStack.getItem())) {
					ejectIngredientRemainder(INGREDIENT_REMAINDER_OVERRIDES.get(slotStack.getItem()).getDefaultInstance());
				}
			}
			slotStack.shrink(taken[i]);
			setStack(i, slotStack);
		}

		// K4: the brew is done, a puff of steam and a short hiss (like a furnace finishing)
		double x = worldPosition.getX() + 0.5D;
		double y = worldPosition.getY() + 0.7D;
		double z = worldPosition.getZ() + 0.5D;
		// a ring of wisps lifting out under the lid's rim, a little quicker than the simmering ones (count 0: the
		// "spread" is the wisp's direction, times the speed)
		RandomSource random = level.getRandom();
		double start = random.nextDouble() * Math.PI * 2.0D;
		for (int i = 0; i < 7; i++) {
			double angle = start + i * Math.PI * 2.0D / 7.0D + (random.nextDouble() - 0.5D) * 0.5D;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			level.sendParticles(KettleRegistry.STEAM.get(), x + dx * 0.33D, y, z + dz * 0.33D, 0, dx * 0.25D, 1.0D, dz * 0.25D, 0.02D + random.nextDouble() * 0.012D);
		}
		level.playSound(null, worldPosition, KettleRegistry.BREW_FINISHED.get(), SoundSource.BLOCKS, 0.6F, 1.0F);
		return true;
	}

	protected void ejectIngredientRemainder(ItemStack remainder) {
		if (level == null) return;
		Direction direction = getBlockState().getValue(BrewKettleBlock.FACING).getCounterClockWise();
		double x = worldPosition.getX() + 0.5D + direction.getStepX() * 0.25D;
		double y = worldPosition.getY() + 0.7D;
		double z = worldPosition.getZ() + 0.5D + direction.getStepZ() * 0.25D;
		ItemEntity entity = new ItemEntity(level, x, y, z, remainder);
		entity.setDeltaMovement(direction.getStepX() * 0.08F, 0.25F, direction.getStepZ() * 0.08F);
		level.addFreshEntity(entity);
	}

	// ------------------------------------------------------------------ serving (K5 and the container slot)

	/** How many servings one of these containers takes, or 0 if it can't take the current brew. */
	public int servingsFor(ItemStack container) {
		ItemStack brew = getBrew();
		if (container.isEmpty() || brew.isEmpty()) return 0;
		ItemStackTemplate bottle = Services.PLATFORM.craftingRemainder(brew);
		if (bottle != null && container.is(bottle.item())) return 1;
		if (!bucketForm.isEmpty()) {
			ItemStackTemplate bucket = Services.PLATFORM.craftingRemainder(bucketForm);
			if (bucket != null && container.is(bucket.item())) return SERVINGS_PER_BUCKET;
		}
		return 0;
	}

	/** The brew one container of this kind turns into: a bottle of it, or a bucket of it. */
	private ItemStack filledFormFor(ItemStack container) {
		return servingsFor(container) == SERVINGS_PER_BUCKET ? bucketForm.copyWithCount(1) : getBrew().copyWithCount(1);
	}

	/** Whether one container like this can be filled right now: the right kind, and enough brew (a bucket needs 4 servings). */
	public boolean canServe(ItemStack container) {
		int servings = servingsFor(container);
		return !isRemoved() && servings > 0 && getServings() >= servings;
	}

	/**
	 * The one fill rule, for the container slot and for right-clicks: takes the brew for up to {@code max} containers
	 * like {@code container} and returns them filled, or empty (and changes nothing) if not even one fits.
	 */
	private ItemStack drawFilled(ItemStack container, int max) {
		int servingsEach = servingsFor(container);
		if (isRemoved() || servingsEach == 0) {
			return ItemStack.EMPTY;
		}
		ItemStack brew = getBrew();
		int count = Math.min(max, brew.getCount() / servingsEach);
		if (count <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack filled = filledFormFor(container).copyWithCount(count);
		brew.shrink(count * servingsEach);
		setStack(BREW_SLOT, brew);
		if (brew.isEmpty()) {
			bucketForm = ItemStack.EMPTY;
		}
		return filled;
	}

	/**
	 * K5: right-click with a bottle or bucket, like a cauldron. Takes the brew for one container and returns it filled
	 * (with the fill sound), or returns empty and changes nothing if there isn't enough brew. The empty container is
	 * the caller's to consume.
	 */
	public ItemStack drawServing(ItemStack container) {
		boolean bucket = servingsFor(container) == SERVINGS_PER_BUCKET;
		ItemStack filled = drawFilled(container, 1);
		if (!filled.isEmpty() && level != null) {
			level.playSound(null, worldPosition, bucket ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		return filled;
	}

	/** {@link #drawServing} that also takes one container from {@code container} (not in creative). */
	public ItemStack serveInto(ItemStack container, @Nullable Player player) {
		ItemStack filled = drawServing(container);
		if (!filled.isEmpty() && (player == null || !player.hasInfiniteMaterials())) {
			container.shrink(1);
		}
		return filled;
	}

	/** What taking brew out gives, for the output slot and for right-clicks alike: the recipes, their experience, the advancement. */
	public void awardServing(Player player, ItemStack served, int count) {
		served.onCraftedBy(player, count);
		if (!player.level().isClientSide()) {
			awardUsedRecipes(player, getDroppableInventory());
			if (!served.isEmpty()) {
				VBAdvancements.award(VBAdvancements.BREWED, player);
			}
		}
	}

	/** FD's {@code useStoredContainersOnMeal}: fill the containers waiting in the container slot into the output slot. */
	private boolean fillStoredContainers() {
		ItemStack containers = stackIn(CONTAINER_SLOT);
		ItemStack output = stackIn(OUTPUT_SLOT);
		if (servingsFor(containers) == 0) {
			return false;
		}
		ItemStack form = filledFormFor(containers);
		if (!output.isEmpty() && !ItemStack.isSameItemSameComponents(output, form)) {
			return false;
		}
		ItemStack filled = drawFilled(containers, Math.min(containers.getCount(), form.getMaxStackSize() - output.getCount()));
		if (filled.isEmpty()) {
			return false;
		}
		containers.shrink(filled.getCount());
		setStack(CONTAINER_SLOT, containers);
		setStack(OUTPUT_SLOT, filled.copyWithCount(output.getCount() + filled.getCount()));
		return true;
	}

	// ------------------------------------------------------------------ lid (K2): open while someone has the screen open

	public void startOpen(Player player) {
		updateLid(player, null);
	}

	public void stopOpen(Player player) {
		updateLid(null, player);
	}

	/** Counts the players looking at this kettle's screen, counting {@code opening} and not counting {@code closing}. */
	void updateLid(@Nullable Player opening, @Nullable Player closing) {
		if (level == null || level.isClientSide() || isRemoved()) return;
		int count = 0;
		boolean openingCounted = false;
		for (Player player : level.players()) {
			if (player == closing) continue;
			if (player.containerMenu instanceof BrewKettleMenu menu && menu.blockEntity == this) {
				count++;
				openingCounted |= player == opening;
			}
		}
		if (opening != null && !openingCounted) {
			count++;
		}
		openCount = count;
		BlockState state = getBlockState();
		boolean open = count > 0;
		if (state.getValue(BrewKettleBlock.OPEN) != open) {
			level.setBlock(worldPosition, state.setValue(BrewKettleBlock.OPEN, open), Block.UPDATE_ALL);
			level.playSound(null, worldPosition, open ? KettleRegistry.LID_OPEN.get() : KettleRegistry.LID_CLOSE.get(), SoundSource.BLOCKS,
				0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		}
	}

	public int getOpenCount() {
		return openCount;
	}

	// ------------------------------------------------------------------ recipe book and experience (FD)

	@Override
	public void setRecipeUsed(@Nullable RecipeHolder<?> recipe) {
		if (recipe != null) {
			usedRecipeTracker.addTo(recipe.id(), 1);
		}
	}

	@Override
	public @Nullable RecipeHolder<?> getRecipeUsed() {
		return null;
	}

	@Override
	public void awardUsedRecipes(Player player, List<ItemStack> items) {
		if (player.level() instanceof ServerLevel serverLevel) {
			player.awardRecipes(getUsedRecipesAndPopExperience(serverLevel, player.position()));
			usedRecipeTracker.clear();
		}
	}

	public List<RecipeHolder<?>> getUsedRecipesAndPopExperience(ServerLevel level, Vec3 pos) {
		List<RecipeHolder<?>> list = Lists.newArrayList();
		for (Object2IntMap.Entry<ResourceKey<Recipe<?>>> entry : usedRecipeTracker.object2IntEntrySet()) {
			level.recipeAccess().byKey(entry.getKey()).ifPresent(recipe -> {
				list.add(recipe);
				if (recipe.value() instanceof BrewingRecipe brewing) {
					splitAndSpawnExperience(level, pos, entry.getIntValue(), brewing.experience());
				}
			});
		}
		return list;
	}

	private static void splitAndSpawnExperience(ServerLevel level, Vec3 pos, int craftedAmount, float experience) {
		int total = Mth.floor(craftedAmount * experience);
		float fraction = Mth.frac(craftedAmount * experience);
		if (fraction != 0.0F && Math.random() < fraction) {
			++total;
		}
		ExperienceOrb.award(level, pos, total);
	}

	// ------------------------------------------------------------------ inventory access

	public ItemSlots getInventory() {
		return inventory;
	}

	/** The stack in a slot with its real amount. A copy: write changes back with {@link #setStack}. */
	public ItemStack stackIn(int slot) {
		return inventory.copyStack(slot);
	}

	public void setStack(int slot, ItemStack stack) {
		inventory.set(slot, stack);
	}

	/** The finished brew in its bottle form; the count is the number of servings left. */
	public ItemStack getBrew() {
		return stackIn(BREW_SLOT);
	}

	public ItemStack getBucketForm() {
		return bucketForm.copy();
	}

	public int getServings() {
		return inventory.getAmount(BREW_SLOT);
	}

	/**
	 * The fill level a brew of this many servings shows: its share of the kettle's {@link #MAX_SERVINGS}, in
	 * {@link BrewKettleBlock#MAX_FILL} steps, and never 0 while any serving is left.
	 */
	public static int fillLevel(int servings) {
		if (servings <= 0) {
			return 0;
		}
		return Mth.clamp(Mth.ceil(servings * (float) BrewKettleBlock.MAX_FILL / MAX_SERVINGS), 1, BrewKettleBlock.MAX_FILL);
	}

	/** How far the current batch is (0 when nothing brews), out of {@link #getBrewTimeTotal()}. */
	public int getBrewTime() {
		return brewTime;
	}

	public int getBrewTimeTotal() {
		return brewTimeTotal;
	}

	public boolean isHeated() {
		return level != null && KettleTags.isHeated(level, worldPosition);
	}

	/** K7: FD's comparator rule over every slot, each slot measured against what it can really hold (the brew: 64 servings). */
	public int comparatorSignal() {
		int filledSlots = 0;
		float fullness = 0.0F;
		for (int i = 0; i < inventory.size(); ++i) {
			int amount = inventory.getAmount(i);
			if (amount > 0) {
				int capacity = Math.max(1, inventory.getCapacityAsInt(i, inventory.getStack(i)));
				fullness += Math.min(1.0F, (float) amount / (float) capacity);
				++filledSlots;
			}
		}
		fullness /= inventory.size();
		return Mth.clamp(Mth.floor(fullness * 14.0F) + (filledSlots > 0 ? 1 : 0), 0, 15);
	}

	/** Everything but the brew, which stays in the kettle item when it is broken (K6). */
	public NonNullList<ItemStack> getDroppableInventory() {
		NonNullList<ItemStack> drops = NonNullList.create();
		for (int i = 0; i < INVENTORY_SIZE; ++i) {
			if (i != BREW_SLOT) {
				drops.add(stackIn(i));
			}
		}
		return drops;
	}

	public ItemStack getAsItem() {
		ItemStack stack = new ItemStack(getBlockState().getBlock().asItem());
		stack.applyComponents(collectComponents());
		return stack;
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < inventory.size(); i++) {
			inventory.set(i, ItemStack.EMPTY);
		}
		bucketForm = ItemStack.EMPTY;
	}

	/** 26.x: the block entity is already gone in Block#affectNeighborsAfterRemoval, so contents are dropped here. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel) {
			Containers.dropContents(serverLevel, pos, getDroppableInventory());
			getUsedRecipesAndPopExperience(serverLevel, Vec3.atCenterOf(pos));
			// What just dropped is gone from the kettle: nothing (a screen still open on it, a stale handler) can hand
			// it out a second time. The brew stays: it is in the kettle item, whose loot can be rolled after removal.
			clearingDrops = true;
			try {
				for (int i = 0; i < INVENTORY_SIZE; i++) {
					if (i != BREW_SLOT) {
						inventory.set(i, ItemStack.EMPTY);
					}
				}
			} finally {
				clearingDrops = false;
			}
		}
	}

	// ------------------------------------------------------------------ menu, name

	@Override
	public Component getName() {
		return customName != null ? customName : Component.translatable("container.vineandbarrel.brew_kettle");
	}

	@Override
	public Component getDisplayName() {
		return getName();
	}

	@Override
	public @Nullable Component getCustomName() {
		return customName;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new BrewKettleMenu(id, playerInventory, this, kettleData);
	}

	// ------------------------------------------------------------------ save, sync, item components

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		inventory.deserialize(input.childOrEmpty("Inventory"));
		brewTime = input.getIntOr("BrewTime", 0);
		brewTimeTotal = input.getIntOr("BrewTimeTotal", 0);
		bucketForm = input.read("BucketForm", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		customName = input.read("CustomName", ComponentSerialization.CODEC).orElse(null);
		// only in the sync data; a loaded world matches again on the first heated tick
		ingredientsMatch = input.getBooleanOr("IngredientsMatch", false);
		usedRecipeTracker.clear();
		usedRecipeTracker.putAll(input.read("RecipesUsed", RECIPES_USED_CODEC).orElse(Collections.emptyMap()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("BrewTime", brewTime);
		output.putInt("BrewTimeTotal", brewTimeTotal);
		output.storeNullable("BucketForm", ItemStack.CODEC, bucketForm.isEmpty() ? null : bucketForm);
		output.storeNullable("CustomName", ComponentSerialization.CODEC, customName);
		inventory.serialize(output.child("Inventory"));
		output.store("RecipesUsed", RECIPES_USED_CODEC, usedRecipeTracker);
	}

	/** What the client needs: the inventory (brew colour, tooltips, bubbling sound), the bucket form, the name and whether the batch brews. */
	private void writeSyncData(ValueOutput output) {
		output.putBoolean("IngredientsMatch", ingredientsMatch);
		output.storeNullable("BucketForm", ItemStack.CODEC, bucketForm.isEmpty() ? null : bucketForm);
		output.storeNullable("CustomName", ComponentSerialization.CODEC, customName);
		inventory.serialize(output.child("Inventory"));
	}

	@Override
	public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			writeSyncData(output);
			return output.buildResult();
		}
	}

	@Override
	public void onDataPacket(Connection connection, ValueInput input) {
		loadWithComponents(input);
		// The open lid shows the brew's colour (K2): re-render when the brew changes while the lid is up
		if (level != null && level.isClientSide() && (getBlockState().getValue(BrewKettleBlock.OPEN) || getBlockState().getValue(BrewKettleBlock.FILL) > 0)) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	protected void inventoryChanged() {
		super.setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		customName = components.get(DataComponents.CUSTOM_NAME);
		KettleContents contents = components.getOrDefault(KettleRegistry.KETTLE_CONTENTS.get(), KettleContents.EMPTY);
		setStack(BREW_SLOT, contents.brew());
		bucketForm = contents.isEmpty() ? ItemStack.EMPTY : contents.bucket();
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(DataComponents.CUSTOM_NAME, customName);
		if (!getBrew().isEmpty()) {
			components.set(KettleRegistry.KETTLE_CONTENTS.get(), new KettleContents(getBrew(), bucketForm));
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void removeComponentsFromTag(ValueOutput output) {
		output.discard("CustomName");
	}

	// ------------------------------------------------------------------ helpers

	private ItemSlots createHandler() {
		return new ItemSlots(INVENTORY_SIZE)
		{
			@Override
			public int getCapacity(int index, ItemStack stack) {
				if (index == BREW_SLOT) {
					return MAX_SERVINGS;
				}
				return super.getCapacity(index, stack);
			}

			@Override
			public boolean isValid(int index, ItemStack stack) {
				if (index == CONTAINER_SLOT) {
					return stack.isEmpty() || stack.is(KettleTags.KETTLE_CONTAINERS);
				}
				return super.isValid(index, stack);
			}

			@Override
			protected void onContentsChanged(int index, ItemStack previousContents) {
				if (clearingDrops) {
					return;
				}
				if (index == BREW_SLOT && inventory != null && getAmount(BREW_SLOT) == 0) {
					bucketForm = ItemStack.EMPTY;
				}
				inventoryChanged();
			}
		};
	}

	/** The screen's synced numbers: 0 brew time, 1 its total, 2 the servings (the gauge), 3 whether the grid makes nothing. */
	public static final int DATA_SERVINGS = 2;
	public static final int DATA_MAKES_NOTHING = 3;
	public static final int DATA_COUNT = 4;

	private ContainerData createData() {
		return new ContainerData()
		{
			@Override
			public int get(int index) {
				return switch (index) {
					case 0 -> brewTime;
					case 1 -> brewTimeTotal;
					case DATA_SERVINGS -> getServings();
					case DATA_MAKES_NOTHING -> ingredientsMakeNothing() ? 1 : 0;
					default -> 0;
				};
			}

			@Override
			public void set(int index, int value) {
				switch (index) {
					case 0 -> brewTime = value;
					case 1 -> brewTimeTotal = value;
					default -> {
					}
				}
			}

			@Override
			public int getCount() {
				return DATA_COUNT;
			}
		};
	}
}
