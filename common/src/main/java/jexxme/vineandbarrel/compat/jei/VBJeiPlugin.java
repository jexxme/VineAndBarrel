package jexxme.vineandbarrel.compat.jei;

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
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * JEI integration (W3), set up like FD's: a brewing category on the kettle screen, an aging category for the barrel,
 * an info page on the barrel's stages and on where the wild crops grow. JEI is optional: only JEI loads this class.
 */
@JeiPlugin
public class VBJeiPlugin implements IModPlugin
{
	private static final Identifier ID = VineAndBarrel.id("jei_plugin");
	// By id, not from the registered RecipeType: JEI loads this class while mods are still loading, before registration
	public static final IRecipeType<RecipeHolder<BrewingRecipe>> BREWING = IRecipeHolderType.create(VineAndBarrel.id("brewing"));
	public static final IRecipeType<RecipeHolder<AgingRecipe>> AGING = IRecipeHolderType.create(VineAndBarrel.id("aging"));

	@Override
	public Identifier getPluginUid() {
		return ID;
	}

	private static mezz.jei.api.runtime.@org.jspecify.annotations.Nullable IJeiRuntime runtime;

	@Override
	public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jeiRuntime) {
		runtime = jeiRuntime;
		// Like the creative tab: no FD meals or melon wine without Farmer's Delight
		List<ItemStack> hidden = VBCreativeTabs.hidden().stream().map(ItemStack::new).toList();
		if (!hidden.isEmpty()) {
			jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
		}
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
	}

	/** Opens JEI's recipe screen on our brewing category (the dev screenshot tool's JEI check). */
	public static boolean showOurCategories() {
		if (runtime == null) {
			return false;
		}
		runtime.getRecipesGui().showTypes(List.of(BREWING));
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
		registration.addCraftingStation(BREWING, KettleRegistry.kettleItem());
		registration.addCraftingStation(AGING, BarrelRegistry.AGING_BARREL_ITEM.get());
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
