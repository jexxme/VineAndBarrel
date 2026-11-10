package jexxme.vineandbarrel.compat.jei;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * The Brew Kettle's JEI category: FD's cooking category (MIT) on our kettle screen. The kettle screen has FD's cooking
 * pot layout, so the background is the same cut-out of the screen, with the same heat, arrow, time and XP sprites.
 */
public class BrewingCategory implements IRecipeCategory<BrewingRecipe>
{
	public static final int WIDTH = 118;
	public static final int HEIGHT = 58;
	private static final ResourceLocation SCREEN = VineAndBarrel.id("textures/gui/brew_kettle.png");

	private final IDrawable background;
	private final IDrawable icon;
	private final IDrawable heatIndicator;
	private final IDrawable timeIcon;
	private final IDrawable expIcon;
	private final IDrawableAnimated arrow;
	private final IDrawable arrowBase;

	public BrewingCategory(IGuiHelper helper) {
		// The screen's slots start at (30, 17), so the cut-out starts one pixel up and left of them, like FD's
		// Only the ingredient grid comes from the kettle screen (the brew is a gauge there now); JEI draws the other slots
		background = helper.createDrawable(SCREEN, 29, 16, 54, 36);
		icon = helper.createDrawableItemLike(KettleRegistry.kettleItem());
		heatIndicator = helper.createDrawable(SCREEN, 176, 0, 17, 15);
		timeIcon = helper.createDrawable(SCREEN, 176, 32, 8, 11);
		expIcon = helper.createDrawable(SCREEN, 176, 43, 9, 9);
		// The empty arrow sits in the screen's background, the filling one on the sprite sheet
		arrowBase = helper.createDrawable(SCREEN, 89, 25, 24, 17);
		arrow = helper.drawableBuilder(SCREEN, 176, 15, 24, 17).buildAnimated(200, IDrawableAnimated.StartDirection.LEFT, false);
	}

	@Override
	public RecipeType<BrewingRecipe> getRecipeType() {
		return VBJeiPlugin.BREWING;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("jei.vineandbarrel.brewing");
	}

	@Override
	public int getWidth() {
		return WIDTH;
	}

	@Override
	public int getHeight() {
		return HEIGHT;
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, BrewingRecipe recipe, IFocusGroup focuses) {
		List<Ingredient> ingredients = recipe.ingredients();
		for (int i = 0; i < ingredients.size(); i++) {
			builder.addSlot(RecipeIngredientRole.INPUT, (i % 3) * 18 + 1, (i / 3) * 18 + 1).addIngredients(ingredients.get(i));
		}
		// The brew slot holds the whole batch; a bottle takes one serving of it, a bucket all of them
		builder.addSlot(RecipeIngredientRole.OUTPUT, 96, 10).setOutputSlotBackground().addItemStack(recipe.result());
		IRecipeSlotBuilder container = builder.addSlot(RecipeIngredientRole.CATALYST, 63, 39).setStandardSlotBackground()
			.addItemStack(new ItemStack(Items.GLASS_BOTTLE)).addItemStack(new ItemStack(Items.BUCKET));
		IRecipeSlotBuilder output = builder.addSlot(RecipeIngredientRole.OUTPUT, 96, 39).setStandardSlotBackground()
			.addItemStack(recipe.result().copyWithCount(1)).addItemStack(recipe.bucket());
		builder.createFocusLink(container, output);
	}

	@Override
	public void draw(BrewingRecipe recipe, IRecipeSlotsView slots, GuiGraphics gui, double mouseX, double mouseY) {
		background.draw(gui, 0, 0);
		arrowBase.draw(gui, 60, 9);
		arrow.draw(gui, 60, 9);
		heatIndicator.draw(gui, 18, 39);
		timeIcon.draw(gui, 64, 2);
		if (recipe.experience() > 0) {
			expIcon.draw(gui, 63, 21);
		}
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, BrewingRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
		if (mouseX >= 61 && mouseX < 83 && mouseY >= 2 && mouseY < 30) {
			int seconds = recipe.brewingTime() / 20;
			if (seconds > 0) {
				tooltip.add(Component.translatable("gui.jei.category.smelting.time.seconds", seconds));
			}
			float experience = recipe.experience();
			if (experience > 0) {
				tooltip.add(Component.translatable("gui.jei.category.smelting.experience", experience));
			}
		} else if (mouseX >= 18 && mouseX < 35 && mouseY >= 39 && mouseY < 54) {
			tooltip.add(Component.translatable("jei.vineandbarrel.brewing.heat"));
			tooltip.add(Component.translatable("jei.vineandbarrel.brewing.bigger"));
		}
	}
}
