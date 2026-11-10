package jexxme.vineandbarrel.kettle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import jexxme.vineandbarrel.inventory.ItemSlots;
import jexxme.vineandbarrel.platform.Services;
import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * The Brew Kettle's screen: the 3x2 ingredient grid of FD's cooking pot ({@code CookingPotMenu}, MIT) with its recipe
 * book handling, the heat, the progress, and a gauge for the brew (its servings are synced as data). The brew itself is
 * never a slot: it is served by hand like a cauldron, or by hoppers.
 */
public class BrewKettleMenu extends RecipeBookMenu<Container>
{
	/** The menu's first player inventory slot: right after the ingredient grid. */
	public static final int INDEX_INVENTORY = BrewKettleBlockEntity.INPUT_SLOTS;

	public final BrewKettleBlockEntity blockEntity;
	public final ItemSlots inventory;
	private final ContainerData kettleData;
	private final ContainerLevelAccess access;
	private final Player player;
	protected final Level level;

	/** The client side: the kettle at the position the server sent (see {@link Services#PLATFORM}'s block menus). */
	public BrewKettleMenu(int windowId, Inventory playerInventory, BlockPos pos) {
		this(windowId, playerInventory, blockEntityFrom(playerInventory, pos), new SimpleContainerData(BrewKettleBlockEntity.DATA_COUNT));
	}

	public BrewKettleMenu(int windowId, Inventory playerInventory, BrewKettleBlockEntity blockEntity, ContainerData kettleData) {
		super(KettleRegistry.BREW_KETTLE_MENU.get(), windowId);
		this.blockEntity = blockEntity;
		this.inventory = blockEntity.getInventory();
		this.kettleData = kettleData;
		this.player = playerInventory.player;
		this.level = playerInventory.player.level();
		this.access = ContainerLevelAccess.create(Objects.requireNonNull(blockEntity.getLevel()), blockEntity.getBlockPos());

		// Ingredients: 2 rows x 3 columns
		for (int row = 0; row < 2; ++row) {
			for (int column = 0; column < 3; ++column) {
				this.addSlot(new ItemSlots.MenuSlot(inventory, row * 3 + column, 30 + column * 18, 17 + row * 18));
			}
		}

		for (int row = 0; row < 3; ++row) {
			for (int column = 0; column < 9; ++column) {
				this.addSlot(new Slot(playerInventory, 9 + row * 9 + column, 8 + column * 18, 84 + row * 18));
			}
		}
		for (int column = 0; column < 9; ++column) {
			this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
		}

		this.addDataSlots(kettleData);
		if (!level.isClientSide()) {
			blockEntity.startOpen(player);
		}
	}

	private static BrewKettleBlockEntity blockEntityFrom(Inventory playerInventory, BlockPos pos) {
		BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
		if (blockEntity instanceof BrewKettleBlockEntity kettle) {
			return kettle;
		}
		throw new IllegalStateException("Not a Brew Kettle: " + blockEntity);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!player.level().isClientSide()) {
			blockEntity.stopOpen(player);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		// Like vanilla's container menus: valid only while this very kettle is still there. A kettle that was broken
		// (and maybe replaced by a new one) must not keep serving items through an old screen.
		return !blockEntity.isRemoved() && net.minecraft.world.Container.stillValidBlockEntity(blockEntity, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		int inventoryEnd = INDEX_INVENTORY + 36;
		ItemStack copy = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot.hasItem()) {
			ItemStack stack = slot.getItem();
			copy = stack.copy();
			if (index < INDEX_INVENTORY) {
				if (!this.moveItemStackTo(stack, INDEX_INVENTORY, inventoryEnd, false)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, INDEX_INVENTORY, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.set(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
			if (stack.getCount() == copy.getCount()) {
				return ItemStack.EMPTY;
			}
			slot.onTake(player, stack);
		}
		return copy;
	}

	/** Progress for the arrow, 0..24 px like FD. */
	public int getBrewProgressScaled() {
		int time = kettleData.get(0);
		int total = kettleData.get(1);
		return total != 0 && time != 0 ? time * 24 / total : 0;
	}

	/** The servings in the kettle, for the gauge (synced: the client's kettle is only updated with block updates). */
	public int getServings() {
		return kettleData.get(BrewKettleBlockEntity.DATA_SERVINGS);
	}

	/** The brew the gauge shows; its amount is {@link #getServings()}. */
	public ItemStack getBrew() {
		return blockEntity.getBrew();
	}

	public boolean isHeated() {
		return blockEntity.isHeated();
	}

	/** How many servings this kettle holds (synced: 64 per block). */
	public int getCapacity() {
		return Math.max(1, kettleData.get(BrewKettleBlockEntity.DATA_CAPACITY));
	}

	/** How many blocks the kettle is made of (synced: 1 for a single kettle). */
	public int getBlocks() {
		return Math.max(1, kettleData.get(BrewKettleBlockEntity.DATA_BLOCKS));
	}

	/** Whether the ingredients in the grid make no recipe (synced from the server, which knows the recipes). */
	public boolean ingredientsMakeNothing() {
		return kettleData.get(BrewKettleBlockEntity.DATA_MAKES_NOTHING) != 0;
	}

	/**
	 * FD's recipe book placement, on a scratch grid. Vanilla's placer grows {@code slot.getItem()} in place when one
	 * inventory stack can't fill a slot, which the kettle's slots must hear about (and a NeoForge resource handler slot
	 * would only hand out copies). So the placer works on plain container slots seeded from the kettle
	 * ({@link PlacementGrid}), and the grid is written back after.
	 */
	@Override
	public void handlePlacement(boolean placeAll, Recipe<?> recipe, ServerPlayer player) {
		if (!(recipe instanceof BrewingRecipe brewing)) {
			return;
		}
		SimpleContainer grid = new SimpleContainer(BrewKettleBlockEntity.INPUT_SLOTS);
		for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
			grid.setItem(i, blockEntity.stackIn(i));
		}
		try {
			new ServerPlaceRecipe<>(new PlacementGrid(grid, player.level())).recipeClicked(player, brewing, placeAll);
		} finally {
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				ItemStack placed = grid.getItem(i);
				if (!ItemStack.matches(placed, blockEntity.stackIn(i))) {
					slots.get(i).set(placed.copy());
				}
			}
		}
	}

	/**
	 * The scratch grid as the menu vanilla's placer works on (on this Minecraft it takes a whole {@link RecipeBookMenu}):
	 * the six ingredient slots and nothing else. It is never opened or synced; the placer talks to the player's real
	 * menu (this kettle's) by itself.
	 */
	private static final class PlacementGrid extends RecipeBookMenu<Container>
	{
		private final SimpleContainer grid;
		private final Level level;

		PlacementGrid(SimpleContainer grid, Level level) {
			super(null, 0);
			this.grid = grid;
			this.level = level;
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				this.addSlot(new Slot(grid, i, 0, 0));
			}
		}

		@Override
		public void fillCraftSlotsStackedContents(StackedContents contents) {
			for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
				contents.accountSimpleStack(grid.getItem(i));
			}
		}

		@Override
		public void clearCraftingContent() {
			grid.clearContent();
		}

		@Override
		public boolean recipeMatches(Recipe<? super Container> recipe) {
			return recipe.matches(grid, level);
		}

		@Override
		public int getResultSlotIndex() {
			return RESULT_SLOT_INDEX;
		}

		@Override
		public int getGridWidth() {
			return GRID_WIDTH;
		}

		@Override
		public int getGridHeight() {
			return GRID_HEIGHT;
		}

		@Override
		public int getSize() {
			return BrewKettleBlockEntity.INPUT_SLOTS;
		}

		@Override
		public RecipeBookType getRecipeBookType() {
			return Services.PLATFORM.brewingRecipeBookType();
		}

		@Override
		public boolean shouldMoveToInventory(int slot) {
			return slot < BrewKettleBlockEntity.INPUT_SLOTS;
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}

	// ------------------------------------------------------------------ what the recipe book asks of the menu (the client's book, mostly)

	/** The ingredient grid: 3 columns, 2 rows. */
	private static final int GRID_WIDTH = 3;
	private static final int GRID_HEIGHT = 2;
	/**
	 * The brew is never a slot. Vanilla's recipe book skips this index when it walks "the grid and the result", so it
	 * is the first index after the grid: nothing of ours is skipped, and the walk never leaves the grid.
	 */
	private static final int RESULT_SLOT_INDEX = BrewKettleBlockEntity.INPUT_SLOTS;

	@Override
	public void fillCraftSlotsStackedContents(StackedContents contents) {
		// Only the ingredient slots: the brew, container and output are not recipe book ingredients
		for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
			contents.accountSimpleStack(inventory.copyStack(i));
		}
	}

	@Override
	public void clearCraftingContent() {
		for (int i = 0; i < BrewKettleBlockEntity.INPUT_SLOTS; i++) {
			slots.get(i).set(ItemStack.EMPTY);
		}
	}

	@Override
	public boolean recipeMatches(Recipe<? super Container> recipe) {
		return recipe.matches(new KettleRecipeInput(inventory), level);
	}

	@Override
	public int getResultSlotIndex() {
		return RESULT_SLOT_INDEX;
	}

	@Override
	public int getGridWidth() {
		return GRID_WIDTH;
	}

	@Override
	public int getGridHeight() {
		return GRID_HEIGHT;
	}

	/** The slots the recipe book watches (a click on one clears its ghost recipe): the ingredient grid. */
	@Override
	public int getSize() {
		return INDEX_INVENTORY;
	}

	@Override
	public boolean shouldMoveToInventory(int slot) {
		return slot < INDEX_INVENTORY;
	}

	@Override
	public RecipeBookType getRecipeBookType() {
		return Services.PLATFORM.brewingRecipeBookType();
	}
}
