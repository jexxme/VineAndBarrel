package jexxme.vineandbarrel.kettle;

import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.hooks.DataPacketReceiver;
import com.google.common.collect.Lists;
import jexxme.vineandbarrel.advancement.VBAdvancements;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.inventory.ItemSlots;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
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
	 * never matches recipes itself, and this is what tells it a kettle of odds and ends stays quiet (K3).
	 */
	private boolean ingredientsMatch;
	/** Set while the dropped slots are cleared on removal, so that clearing doesn't notify a block that is going away. */
	private boolean clearingDrops;

	protected final ContainerData kettleData;
	private final Object2IntOpenHashMap<ResourceLocation> usedRecipeTracker = new Object2IntOpenHashMap<>();
	private final RecipeManager.CachedCheck<RecipeInput, BrewingRecipe> quickCheck;
	/**
	 * The last recipe match, kept while the ingredient slots and the recipes stay the same: a heated kettle asks every
	 * tick, and matching is the kettle's main cost. The slots are compared with a copy of what they held (any change
	 * counts, however it was made: hopper, menu, command), the recipes by identity (a reload makes a new collection).
	 */
	private final ItemStack[] matchedInputs = new ItemStack[INPUT_SLOTS];
	private @Nullable Collection<RecipeHolder<?>> matchedRecipes;
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
		int fires = kettle.heatedBlocks();
		boolean heated = fires > 0;
		boolean changed = false;
		boolean brewing = false;

		boolean hasInput = kettle.hasInput();
		boolean matches = kettle.ingredientsMatch && hasInput;
		if (heated && hasInput) {
			RecipeHolder<BrewingRecipe> recipe = kettle.matchRecipe(level);
			matches = recipe != null;
			int sets = recipe == null ? 0 : kettle.batchSets(recipe.value());
			if (sets > 0) {
				brewing = true;
				changed = kettle.processBrewing(level, recipe, sets, fires);
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

		if (state.getBlock() instanceof BrewKettleBlock) {
			// The sight glass beside the tap shows how full it is (and the brew under the lid while the screen is open)
			int fill = fillLevel(kettle.getServings());
			if (state.getValue(BrewKettleBlock.FILL) != fill) {
				state = state.setValue(BrewKettleBlock.FILL, fill);
				level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}
		} else {
			// A big kettle shows its brew in the sight glass beside its tap
			BlockPos gauge = kettle.kettle().gauge();
			BlockState glass = level.getBlockState(gauge);
			int glassLevel = gaugeLevel(kettle.getServings(), kettle.capacity());
			if (glass.hasProperty(BrewKettlePartBlock.LEVEL) && glass.getValue(BrewKettlePartBlock.LEVEL) != glassLevel) {
				level.setBlock(gauge, glass.setValue(BrewKettlePartBlock.LEVEL, glassLevel), Block.UPDATE_CLIENTS);
			}
		}

		// K2: re-count viewers now and then, so the lid never stays open after someone left without closing the screen
		// (or open from a save: the count isn't saved, the lid is)
		if ((kettle.openCount > 0 || state.getBlock() instanceof BrewKettleBlock && state.getValue(BrewKettleBlock.OPEN)) && level.getGameTime() % 20L == 0L) {
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
	 * What the kettle shows (WP4 playtest): brewing = steam out of the chimney, the boil and bubbles; a finished brew
	 * waiting = the tap drips; a lit fire and nothing brewing = the fire's smoke out of the chimney; otherwise quiet.
	 */
	public static void clientTick(Level level, BlockPos pos, BlockState state, BrewKettleBlockEntity be) {
		int fires = be.heatedBlocks();
		int servings = be.getServings();
		boolean brewing = fires > 0 && be.hasInput() && be.ingredientsMatch && servings < be.capacity();
		boolean hanging = state.getBlock() instanceof BrewKettleBlock && state.getValue(BrewKettleBlock.SUPPORT) == KettleSupport.HANDLE;
		KettleMultiblock.Kettle kettle = be.kettle();
		RandomSource random = level.getRandom();
		int size = kettle.shape().size();

		// What comes out of the chimney, more of it the taller the chimney is built: steam while it brews, otherwise the
		// smoke of a lit fire under it (never both: one clear sign of what the kettle is doing)
		int extension = hanging ? 0 : kettle.extension(level);
		if (brewing) {
			// Brewing: steam out of the chimney, the boil and bubbles at the rim
			if (random.nextDouble() < 0.2D + 0.08D * size + 0.04D * extension) {
				chimneySteam(level, kettle, random, 1.0D + 0.25D * (size - 1) + 0.1D * extension);
			}
			if (random.nextInt(10) == 0) {
				Vec3 centre = kettle.centre();
				float pitch = (random.nextFloat() * 0.2F + 0.9F) * (size == 1 ? 1.0F : size == 2 ? 0.85F : 0.75F);
				level.playLocalSound(centre.x, kettle.controller().getY() + 0.5D, centre.z, servings <= 0 ? KettleRegistry.BOIL.get() : KettleRegistry.BOIL_BREW.get(),
					SoundSource.BLOCKS, 0.5F + 0.1F * (size - 1), pitch, false);
			}
			if (size == 1 && random.nextFloat() < 0.2F) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5D + Math.cos(angle) * 0.38D, pos.getY() + KettleGeometry.bodyTop(KettleShape.SINGLE) / 16.0D + 0.02D,
					pos.getZ() + 0.5D + Math.sin(angle) * 0.38D, 0.0D, 0.0D, 0.0D);
			}
		} else if (fires > 0 && !hanging && random.nextDouble() < 0.015D + 0.035D * extension) {
			chimneySmoke(level, kettle, random, extension);
		}
		if (!brewing && servings > 0 && random.nextInt(30) == 0) {
			// Done: the brew waits, and the tap drips now and then
			Vec3 tap = localPoint(kettle, 8.0D * size, 2.3D, 1.2D);
			level.addParticle(ParticleTypes.DRIPPING_WATER, tap.x, tap.y, tap.z, 0.0D, 0.0D, 0.0D);
		}

		// The Kettle's lid up (screen open): a thin wisp from the open pot
		if (size == 1 && fires > 0 && state.getValue(BrewKettleBlock.OPEN) && !state.getValue(BrewKettleBlock.EXTENDED) && (servings > 0 || be.hasInput())
			&& random.nextFloat() < 0.1F) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = random.nextDouble() * 0.22D;
			level.addParticle(KettleRegistry.STEAM.get(), pos.getX() + 0.5D + Math.cos(angle) * r, pos.getY() + 0.5D, pos.getZ() + 0.5D + Math.sin(angle) * r,
				0.0D, 0.004D + random.nextDouble() * 0.006D, 0.0D);
		}
	}

	/** A point given in the kettle's own pixels (built facing north, from its bottom corner), in world coordinates. */
	static Vec3 localPoint(KettleMultiblock.Kettle kettle, double px, double py, double pz) {
		double half = 8.0D * kettle.shape().size();
		double dx = (px - half) / 16.0D;
		double dz = (pz - half) / 16.0D;
		double wx = switch (kettle.facing()) {
			case EAST -> -dz;
			case SOUTH -> -dx;
			case WEST -> dz;
			default -> dx;
		};
		double wz = switch (kettle.facing()) {
			case EAST -> dx;
			case SOUTH -> -dz;
			case WEST -> -dx;
			default -> dz;
		};
		Vec3 centre = kettle.centre();
		return new Vec3(centre.x + wx, kettle.controller().getY() + py / 16.0D, centre.z + wz);
	}

	/** One wisp of steam out of the chimney's cap, drifting up. */
	private static void chimneySteam(Level level, KettleMultiblock.Kettle kettle, RandomSource random, double strength) {
		Vec3 top = kettle.chimneyTop(level);
		double r = KettleGeometry.chimneyHalfWidth(kettle.shape().size()) / 16.0D * 0.6D;
		double angle = random.nextDouble() * Math.PI * 2.0D;
		level.addParticle(KettleRegistry.STEAM.get(), top.x + Math.cos(angle) * r * random.nextDouble(), top.y, top.z + Math.sin(angle) * r * random.nextDouble(),
			(random.nextDouble() - 0.5D) * 0.004D, (0.012D + random.nextDouble() * 0.008D) * strength, (random.nextDouble() - 0.5D) * 0.004D);
	}

	/** The fire's smoke out of the chimney, like a campfire's (a taller chimney sends it a little higher). */
	private static void chimneySmoke(Level level, KettleMultiblock.Kettle kettle, RandomSource random, int extension) {
		Vec3 top = kettle.chimneyTop(level);
		double r = KettleGeometry.chimneyHalfWidth(kettle.shape().size()) / 16.0D * 0.5D;
		level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, top.x + (random.nextDouble() - 0.5D) * r, top.y, top.z + (random.nextDouble() - 0.5D) * r,
			0.0D, 0.04D + 0.005D * extension, 0.0D);
	}

	/** The recipe the ingredient slots make, matched again only when they or the recipes changed since the last match. */
	private @Nullable RecipeHolder<BrewingRecipe> matchRecipe(ServerLevel level) {
		Collection<RecipeHolder<?>> recipes = level.getRecipeManager().getRecipes();
		boolean same = recipes == matchedRecipes;
		for (int i = 0; i < INPUT_SLOTS && same; ++i) {
			ItemStack now = inventory.getStack(i);
			ItemStack before = matchedInputs[i];
			same = before != null && (now.isEmpty() ? before.isEmpty() : ItemStack.matches(now, before));
		}
		if (!same) {
			matched = quickCheck.getRecipeFor(new KettleRecipeInput(inventory), level).orElse(null);
			matchedResult = matched == null ? ItemStack.EMPTY : matched.value().assemble(new KettleRecipeInput(inventory), level.registryAccess());
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

	/**
	 * How many sets of {@code recipe} the next batch brews: one per block of kettle, as many as the grid holds and as
	 * fit with the brew already stored (WP4). 0 if none: nothing to brew, or another brew is stored.
	 */
	int batchSets(BrewingRecipe recipe) {
		ItemStack result = matchedResult;
		if (result.isEmpty()) {
			return 0;
		}
		ItemStack stored = inventory.getStack(BREW_SLOT);
		if (!stored.isEmpty() && (!ItemStack.isSameItemSameComponents(stored, result) || !ItemStack.isSameItemSameComponents(bucketForm, recipe.bucket()))) {
			return 0;
		}
		int room = (capacity() - stored.getCount()) / result.getCount();
		int max = Math.min(shape().blocks(), room);
		if (max <= 0) {
			return 0;
		}
		return availableSets(recipe, max);
	}

	/** How many times (up to {@code max}) the grid makes {@code recipe}, taking each set's ingredients in turn. */
	private int availableSets(BrewingRecipe recipe, int max) {
		if (max == 1) {
			return recipe.consumption(new KettleRecipeInput(inventory)) != null ? 1 : 0;
		}
		ItemSlots copy = new ItemSlots(INPUT_SLOTS);
		for (int i = 0; i < INPUT_SLOTS; i++) {
			copy.set(i, inventory.copyStack(i));
		}
		int sets = 0;
		while (sets < max) {
			int[] taken = recipe.consumption(new KettleRecipeInput(copy));
			if (taken == null) {
				break;
			}
			for (int i = 0; i < taken.length; i++) {
				if (taken[i] > 0) {
					ItemStack stack = copy.copyStack(i);
					stack.shrink(taken[i]);
					copy.set(i, stack);
				}
			}
			sets++;
		}
		return sets;
	}

	/**
	 * One tick of a batch of {@code sets} sets. Every fire under the kettle boils one set's worth, but a batch never goes
	 * faster than one 1x1 kettle: a 1x1 on its fire takes the recipe's time, a 2x2 on four fires too, a 2x2 on one fire
	 * four times as long. Same output per fire as a 1x1 (decided with Jexxme).
	 */
	private boolean processBrewing(ServerLevel level, RecipeHolder<BrewingRecipe> holder, int sets, int fires) {
		BrewingRecipe recipe = holder.value();
		brewTime += Math.min(fires, sets);
		brewTimeTotal = recipe.brewingTime() * sets;
		if (brewTime < brewTimeTotal) {
			return false;
		}
		brewTime = 0;
		for (int set = 0; set < sets; set++) {
			if (!brewOneSet(holder)) {
				break;
			}
		}
		// K4: the brew is done, a puff of steam and a short hiss (like a furnace finishing)
		finishPuff(level);
		return true;
	}

	/** Takes one set's ingredients and adds one set's servings. */
	private boolean brewOneSet(RecipeHolder<BrewingRecipe> holder) {
		BrewingRecipe recipe = holder.value();
		// The pooled match decides which slots this batch takes from (DESIGN: hoppers stack repeated ingredients into one slot)
		int[] taken = recipe.consumption(new KettleRecipeInput(inventory));
		if (taken == null) {
			return false;
		}
		ItemStack result = recipe.result();
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
				ItemStack remainder = Services.PLATFORM.craftingRemainder(slotStack);
				if (remainder != null) {
					ejectIngredientRemainder(remainder);
				} else if (INGREDIENT_REMAINDER_OVERRIDES.containsKey(slotStack.getItem())) {
					ejectIngredientRemainder(INGREDIENT_REMAINDER_OVERRIDES.get(slotStack.getItem()).getDefaultInstance());
				}
			}
			slotStack.shrink(taken[i]);
			setStack(i, slotStack);
		}
		return true;
	}

	private void finishPuff(ServerLevel level) {
		KettleMultiblock.Kettle kettle = kettle();
		if (kettle.isBig()) {
			// a big kettle lets its puff out of the chimney, a little deeper
			Vec3 top = kettle.chimneyTop(level);
			RandomSource random = level.getRandom();
			for (int i = 0; i < 5 + 2 * kettle.shape().size(); i++) {
				level.sendParticles(KettleRegistry.STEAM.get(), top.x, top.y, top.z, 0, (random.nextDouble() - 0.5D) * 0.2D, 1.0D, (random.nextDouble() - 0.5D) * 0.2D,
					0.03D + random.nextDouble() * 0.02D);
			}
			level.playSound(null, BlockPos.containing(top), KettleRegistry.BREW_FINISHED.get(), SoundSource.BLOCKS, 0.7F, 0.85F);
			return;
		}
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
		ItemStack bottle = Services.PLATFORM.craftingRemainder(brew);
		if (bottle != null && container.is(bottle.getItem())) return 1;
		if (!bucketForm.isEmpty()) {
			ItemStack bucket = Services.PLATFORM.craftingRemainder(bucketForm);
			if (bucket != null && container.is(bucket.getItem())) return SERVINGS_PER_BUCKET;
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
		served.onCraftedBy(player.level(), player, count);
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
		applyLid();
	}

	/**
	 * Lifts a single kettle's lid while someone looks at its screen, with the lid sound (big kettles show their brew in the
	 * sight glass instead).
	 */
	private void applyLid() {
		if (level == null || level.isClientSide() || isRemoved()) return;
		BlockPos lidPos = worldPosition;
		BlockState lid = getBlockState();
		if (!lid.hasProperty(BrewKettleBlock.OPEN)) return;
		boolean open = openCount > 0;
		if (lid.getValue(BrewKettleBlock.OPEN) != open) {
			level.setBlock(lidPos, lid.setValue(BrewKettleBlock.OPEN, open), Block.UPDATE_ALL);
			level.playSound(null, lidPos, open ? KettleRegistry.LID_OPEN.get() : KettleRegistry.LID_CLOSE.get(), SoundSource.BLOCKS,
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
		for (Object2IntMap.Entry<ResourceLocation> entry : usedRecipeTracker.object2IntEntrySet()) {
			level.getRecipeManager().byKey(entry.getKey()).ifPresent(recipe -> {
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

	/** The kettle this block entity is the controller of: a single kettle, or a big one (WP4). */
	public KettleMultiblock.Kettle kettle() {
		KettleMultiblock.Kettle kettle = KettleMultiblock.kettleOf(getBlockState(), worldPosition);
		return kettle != null ? kettle : new KettleMultiblock.Kettle(KettleShape.SINGLE, worldPosition, Direction.NORTH);
	}

	public KettleShape shape() {
		BlockState state = getBlockState();
		return state.getBlock() instanceof BrewKettlePartBlock ? BrewKettlePartBlock.part(state).shape() : KettleShape.SINGLE;
	}

	/** How many servings this kettle holds: {@link #MAX_SERVINGS} per block. */
	public int capacity() {
		return MAX_SERVINGS * shape().blocks();
	}

	/** How many blocks of the kettle's bottom stand over a heat source (0: cold). */
	public int heatedBlocks() {
		if (level == null) return 0;
		KettleMultiblock.Kettle kettle = kettle();
		if (!kettle.isBig()) {
			return KettleTags.isHeated(level, worldPosition) ? 1 : 0;
		}
		int fires = 0;
		for (BlockPos pos : kettle.bottom()) {
			if (KettleTags.isHeated(level, pos)) fires++;
		}
		return fires;
	}

	/** Puts a brew into an empty kettle (splitting a big kettle hands each single one its share). */
	void setBrew(ItemStack brew, ItemStack bucket) {
		setStack(BREW_SLOT, brew);
		bucketForm = brew.isEmpty() ? ItemStack.EMPTY : bucket.copy();
	}

	/** What kettles joining into one bring together: the brew, the grid, the buffers, and what doesn't fit. */
	record Merged(ItemStack brew, ItemStack bucket, List<ItemStack> grid, ItemStack containers, ItemStack output,
		List<ItemStack> spills, Map<ResourceLocation, Integer> recipesUsed, @Nullable Component name) {
	}

	/**
	 * Pools the contents of kettles about to join, or null if they can't: different brews, or more ingredients than one
	 * grid holds. Buffered containers that don't fit spill out.
	 */
	static @Nullable Merged merge(List<BrewKettleBlockEntity> kettles) {
		ItemStack brew = ItemStack.EMPTY;
		ItemStack bucket = ItemStack.EMPTY;
		List<ItemStack> ingredients = new java.util.ArrayList<>();
		List<ItemStack> spills = new java.util.ArrayList<>();
		ItemStack containers = ItemStack.EMPTY;
		ItemStack output = ItemStack.EMPTY;
		Map<ResourceLocation, Integer> used = new java.util.HashMap<>();
		Component name = null;
		for (BrewKettleBlockEntity kettle : kettles) {
			ItemStack b = kettle.getBrew();
			if (!b.isEmpty()) {
				if (brew.isEmpty()) {
					brew = b.copy();
					bucket = kettle.getBucketForm();
				} else if (ItemStack.isSameItemSameComponents(brew, b) && ItemStack.isSameItemSameComponents(bucket, kettle.getBucketForm())) {
					brew.grow(b.getCount());
				} else {
					return null;
				}
			}
			for (int i = 0; i < INPUT_SLOTS; i++) {
				if (!kettle.stackIn(i).isEmpty()) ingredients.add(kettle.stackIn(i));
			}
			containers = mergeBuffer(containers, kettle.stackIn(CONTAINER_SLOT), spills);
			output = mergeBuffer(output, kettle.stackIn(OUTPUT_SLOT), spills);
			kettle.usedRecipeTracker.object2IntEntrySet().forEach(e -> used.merge(e.getKey(), e.getIntValue(), Integer::sum));
			if (name == null) name = kettle.customName;
		}
		List<ItemStack> grid = new java.util.ArrayList<>(java.util.Collections.nCopies(INPUT_SLOTS, ItemStack.EMPTY));
		if (!KettleMultiblock.pack(ingredients, grid, kettles.getFirst().inventory)) {
			return null;
		}
		return new Merged(brew, bucket, grid, containers, output, spills, used, name);
	}

	private static ItemStack mergeBuffer(ItemStack into, ItemStack add, List<ItemStack> spills) {
		if (add.isEmpty()) return into;
		if (into.isEmpty()) return add.copy();
		if (ItemStack.isSameItemSameComponents(into, add)) {
			int room = into.getMaxStackSize() - into.getCount();
			int moved = Math.min(room, add.getCount());
			into.grow(moved);
			if (add.getCount() > moved) spills.add(add.copyWithCount(add.getCount() - moved));
			return into;
		}
		spills.add(add.copy());
		return into;
	}

	/** Takes over joined kettles' contents (the block entity of the new big kettle's controller). */
	void load(Merged merged) {
		clearContent();
		for (int i = 0; i < INPUT_SLOTS; i++) {
			setStack(i, merged.grid().get(i));
		}
		setStack(CONTAINER_SLOT, merged.containers());
		setStack(OUTPUT_SLOT, merged.output());
		setBrew(merged.brew(), merged.bucket());
		usedRecipeTracker.clear();
		merged.recipesUsed().forEach(usedRecipeTracker::put);
		customName = merged.name();
		brewTime = 0;
		inventoryChanged();
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

	/** The sight glass's level (0 to {@link BrewKettlePartBlock#MAX_LEVEL}) for a brew of this many servings, never 0 while any is left. */
	public static int gaugeLevel(int servings, int capacity) {
		if (servings <= 0) {
			return 0;
		}
		return Mth.clamp(Mth.ceil(servings * (float) BrewKettlePartBlock.MAX_LEVEL / capacity), 1, BrewKettlePartBlock.MAX_LEVEL);
	}

	/** How far the current batch is (0 when nothing brews), out of {@link #getBrewTimeTotal()}. */
	public int getBrewTime() {
		return brewTime;
	}

	public int getBrewTimeTotal() {
		return brewTimeTotal;
	}

	public boolean isHeated() {
		return heatedBlocks() > 0;
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

	/**
	 * The kettle goes (its block changed to something that isn't this kettle): contents are dropped here. Called by the
	 * kettle blocks' onRemove, while the block entity is still there (26.x: BlockEntity#preRemoveSideEffects).
	 */
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (KettleMultiblock.isConverting()) {
			// joining, splitting or a change of copper moves the contents itself
			return;
		}
		if (level instanceof ServerLevel serverLevel && state.getBlock() instanceof BrewKettlePartBlock) {
			// a big kettle's controller went some other way (an explosion, a command): the rest falls apart and keeps the
			// brew. A player breaking it split it already (playerWillDestroy).
			KettleMultiblock.Kettle kettle = KettleMultiblock.kettleOf(state, pos);
			if (kettle != null && KettleMultiblock.restIntact(serverLevel, kettle, pos)) {
				KettleMultiblock.split(serverLevel, kettle, this, state.getBlock(), pos, false);
			}
			return;
		}
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
		if (customName != null) {
			return customName;
		}
		KettleShape shape = shape();
		return Component.translatable(switch (shape) {
			case SINGLE -> "container.vineandbarrel.brew_kettle";
			case BIG -> "container.vineandbarrel.brew_kettle.big";
			case GREAT -> "container.vineandbarrel.brew_kettle.great";
		});
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
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		inventory.deserialize(tag.getCompound("Inventory"), registries);
		// a big kettle's brew can exceed what an item stack saves (99): the true count is kept beside it
		int servings = tag.getInt("Servings");
		if (servings > 0 && !inventory.getStack(BREW_SLOT).isEmpty()) {
			inventory.setQuietly(BREW_SLOT, inventory.getStack(BREW_SLOT).copyWithCount(servings));
		}
		brewTime = tag.getInt("BrewTime");
		brewTimeTotal = tag.getInt("BrewTimeTotal");
		bucketForm = ItemStack.parseOptional(registries, tag.getCompound("BucketForm"));
		// (the name is saved as vanilla's block entities save theirs on this Minecraft: a JSON string)
		customName = tag.contains("CustomName", Tag.TAG_STRING) ? parseCustomNameSafe(tag.getString("CustomName"), registries) : null;
		// only in the sync data; a loaded world matches again on the first heated tick
		ingredientsMatch = tag.getBoolean("IngredientsMatch");
		usedRecipeTracker.clear();
		CompoundTag used = tag.getCompound("RecipesUsed");
		for (String id : used.getAllKeys()) {
			ResourceLocation recipe = ResourceLocation.tryParse(id);
			if (recipe != null) {
				usedRecipeTracker.put(recipe, used.getInt(id));
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("BrewTime", brewTime);
		tag.putInt("BrewTimeTotal", brewTimeTotal);
		saveBucketFormAndName(tag, registries);
		serializeInventory(tag, registries);
		CompoundTag used = new CompoundTag();
		usedRecipeTracker.forEach((recipe, count) -> used.putInt(recipe.toString(), count));
		tag.put("RecipesUsed", used);
	}

	private void saveBucketFormAndName(CompoundTag tag, HolderLookup.Provider registries) {
		if (!bucketForm.isEmpty()) {
			tag.put("BucketForm", bucketForm.save(registries));
		}
		if (customName != null) {
			tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
		}
	}

	/** What the client needs: the inventory (brew colour, tooltips, bubbling sound), the bucket form, the name and whether the batch brews. */
	private void writeSyncData(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putBoolean("IngredientsMatch", ingredientsMatch);
		saveBucketFormAndName(tag, registries);
		serializeInventory(tag, registries);
	}

	/** The inventory, with the brew's stack capped to what a stack saves (64) and its true count as "Servings" if larger. */
	private void serializeInventory(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag items = new CompoundTag();
		int servings = getServings();
		if (servings > MAX_SERVINGS) {
			ItemStack brew = inventory.getStack(BREW_SLOT);
			inventory.setQuietly(BREW_SLOT, brew.copyWithCount(MAX_SERVINGS));
			try {
				inventory.serialize(items, registries);
			} finally {
				inventory.setQuietly(BREW_SLOT, brew);
			}
			tag.putInt("Servings", servings);
		} else {
			inventory.serialize(items, registries);
		}
		tag.put("Inventory", items);
	}

	@Override
	public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		writeSyncData(tag, registries);
		return tag;
	}

	@Override
	public void onDataPacket(Connection connection, CompoundTag tag, HolderLookup.Provider registries) {
		loadWithComponents(tag, registries);
		// The open lid shows the brew's colour (K2): re-render when the brew changes while the lid is up
		if (level != null && level.isClientSide()) {
			BlockState state = getBlockState();
			if (state.getBlock() instanceof BrewKettleBlock && (state.getValue(BrewKettleBlock.OPEN) || state.getValue(BrewKettleBlock.FILL) > 0)) {
				level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
			} else if (state.getBlock() instanceof BrewKettlePartBlock) {
				// the brew's colour shows in the sight glass
				BlockPos gauge = kettle().gauge();
				BlockState glass = level.getBlockState(gauge);
				level.sendBlockUpdated(gauge, glass, glass, Block.UPDATE_CLIENTS);
			}
		}
	}

	protected void inventoryChanged() {
		super.setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			if (!level.isClientSide() && getBlockState().getBlock() instanceof BrewKettlePartBlock) {
				// comparators may stand by any block of a big kettle
				for (BlockPos pos : kettle().positions()) {
					if (!pos.equals(worldPosition)) {
						level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
					}
				}
			}
		}
	}

	/** Forgets the recipes used (their experience was handed out). */
	void clearUsedRecipes() {
		usedRecipeTracker.clear();
	}

	@Override
	protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
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
	public void removeComponentsFromTag(CompoundTag tag) {
		tag.remove("CustomName");
	}

	// ------------------------------------------------------------------ helpers

	private ItemSlots createHandler() {
		return new ItemSlots(INVENTORY_SIZE)
		{
			@Override
			public int getCapacity(int index, ItemStack stack) {
				if (index == BREW_SLOT) {
					return capacity();
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
	/** 4: how many servings the kettle holds; 5: how many blocks it is made of. */
	public static final int DATA_CAPACITY = 4;
	public static final int DATA_BLOCKS = 5;
	public static final int DATA_COUNT = 6;

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
					case DATA_CAPACITY -> capacity();
					case DATA_BLOCKS -> shape().blocks();
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
