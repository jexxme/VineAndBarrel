package jexxme.vineandbarrel.compat.jei;

import jexxme.vineandbarrel.watch.client.WatchClient;
import jexxme.vineandbarrel.watch.client.WatchIndex;
import jexxme.vineandbarrel.watch.client.WatchScreen;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.registration.IAdvancedRegistration;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewKettleMenu;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.kettle.client.BrewKettleScreen;
import jexxme.vineandbarrel.registry.VBCreativeTabs;
import jexxme.vineandbarrel.registry.VBItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * JEI integration (W3), set up like FD's: a brewing category on the kettle screen, an aging category for the barrel,
 * an info page on the barrel's stages and on where the wild crops grow. JEI is optional: only JEI loads this class.
 */
@JeiPlugin
public class VBJeiPlugin implements IModPlugin
{
	private static final ResourceLocation ID = VineAndBarrel.id("jei_plugin");
	// By id, not from the registered RecipeType: JEI loads this class while mods are still loading, before registration
	public static final RecipeType<BrewingRecipe> BREWING = RecipeType.create(VineAndBarrel.MODID, "brewing", BrewingRecipe.class);
	public static final RecipeType<AgingRecipe> AGING = RecipeType.create(VineAndBarrel.MODID, "aging", AgingRecipe.class);

	@Override
	public ResourceLocation getPluginUid() {
		return ID;
	}

	private static mezz.jei.api.runtime.@org.jspecify.annotations.Nullable IJeiRuntime runtime;

	@Override
	public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
		// "Watch it": the watch key over an item in JEI's list, its bookmarks or a recipe page opens that item's scene
		WatchClient.setJei(() -> {
			ItemStack stack = jeiRuntime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
			if (stack == null) {
				stack = jeiRuntime.getBookmarkOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
			}
			if (stack == null) {
				stack = jeiRuntime.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK).orElse(null);
			}
			return stack == null ? ItemStack.EMPTY : stack;
		}, () -> jeiRuntime.getIngredientListOverlay().hasKeyboardFocus());
		// Like the creative tab: no FD meals or melon wine without Farmer's Delight
		List<ItemStack> hidden = VBCreativeTabs.hidden().stream().map(ItemStack::new).toList();
		if (!hidden.isEmpty()) {
			jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
		}
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
		WatchClient.setJei(() -> ItemStack.EMPTY, () -> false);
	}

	/** "Watch it": a small button beside each recipe of a category that has a scene (our brewing and aging pages). */
	@Override
	public void registerAdvanced(IAdvancedRegistration registration) {
		IDrawableStatic icon = registration.getJeiHelpers().getGuiHelper().drawableBuilder(WATCH_ICON, 0, 0, 8, 8).setTextureSize(8, 8).build();
		registration.addRecipeButtonFactory(new IRecipeButtonControllerFactory()
		{
			@Override
			public <T> @org.jspecify.annotations.Nullable IIconButtonController createButtonController(IRecipeLayoutDrawable<T> layout) {
				ResourceLocation category = layout.getRecipeCategory().getRecipeType().getUid();
				return WatchIndex.INSTANCE.forJeiCategory(category).map(scene -> new WatchButton(scene, icon)).orElse(null);
			}
		});
	}

	private static final ResourceLocation WATCH_ICON = VineAndBarrel.id("textures/gui/watch_jei.png");

	/** Opens the category's scene; Escape comes back to this JEI page. */
	private record WatchButton(ResourceLocation scene, IDrawableStatic icon) implements IIconButtonController
	{
		@Override
		public void initState(IButtonState state) {
			state.setIcon(icon);
		}

		@Override
		public void getTooltips(ITooltipBuilder tooltip) {
			tooltip.add(Component.translatable("watch.vineandbarrel.button"));
		}

		@Override
		public boolean onPress(IJeiUserInput input) {
			if (!input.isSimulate()) {
				net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
				WatchScreen.open(scene, 0, null, minecraft.screen);
			}
			return true;
		}
	}

	/** Opens JEI's recipe screen on our brewing category (the dev screenshot tool's JEI check). */
	public static boolean showOurCategories() {
		if (runtime == null) {
			return false;
		}
		runtime.getRecipesGui().showTypes(List.of(BREWING));
		return true;
	}

	/** Opens JEI on how to get an item ("What you need" in a scene). */
	public static boolean showRecipes(net.minecraft.world.item.ItemStack stack) {
		if (runtime == null) {
			return false;
		}
		runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(
			mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
		return true;
	}

	/** Opens JEI's recipe screen on our aging category (the dev screenshot tool's watch check: its watch buttons). */
	public static boolean showAging() {
		if (runtime == null) {
			return false;
		}
		runtime.getRecipesGui().showTypes(List.of(AGING));
		return true;
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new BrewingCategory(registration.getJeiHelpers().getGuiHelper()));
		registration.addRecipeCategories(new AgingCategory(registration.getJeiHelpers().getGuiHelper()));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		registration.addRecipes(BREWING, JeiRecipeCache.brewing());
		registration.addRecipes(AGING, JeiRecipeCache.aging());

		registration.addIngredientInfo(new ItemStack(BarrelRegistry.AGING_BARREL_ITEM.get()), VanillaTypes.ITEM_STACK, info("aging_barrel"));
		registration.addIngredientInfo(List.of(new ItemStack(VBItems.WILD_GRAPES.get()), new ItemStack(VBItems.RED_GRAPES.get())),
			VanillaTypes.ITEM_STACK, info("wild_grapes"));
		registration.addIngredientInfo(List.of(new ItemStack(VBItems.WILD_WHITE_GRAPES.get()), new ItemStack(VBItems.WHITE_GRAPES.get())),
			VanillaTypes.ITEM_STACK, info("wild_white_grapes"));
		registration.addIngredientInfo(List.of(new ItemStack(VBItems.WILD_HOPS.get()), new ItemStack(VBItems.HOPS.get())),
			VanillaTypes.ITEM_STACK, info("wild_hops"));
		registration.addIngredientInfo(List.of(new ItemStack(VBItems.WILD_BARLEY.get()), new ItemStack(VBItems.BARLEY.get()),
			new ItemStack(VBItems.BARLEY_SEEDS.get())), VanillaTypes.ITEM_STACK, info("wild_barley"));
	}

	private static Component info(String name) {
		return Component.translatable("jei.vineandbarrel.info." + name);
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalysts(BREWING, KettleRegistry.kettleItem());
		registration.addRecipeCatalysts(AGING, BarrelRegistry.AGING_BARREL_ITEM.get());
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		// The kettle screen's progress arrow, as on FD's cooking pot
		registration.addRecipeClickArea(BrewKettleScreen.class, 89, 25, 24, 17, BREWING);
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		// Ingredient slots 0-5, then the brew, container and output slots, then the 36 player slots
		registration.addRecipeTransferHandler(BrewKettleMenu.class, KettleRegistry.BREW_KETTLE_MENU.get(), BREWING,
			0, BrewKettleBlockEntity.INPUT_SLOTS, BrewKettleMenu.INDEX_INVENTORY, 36);
	}
}
