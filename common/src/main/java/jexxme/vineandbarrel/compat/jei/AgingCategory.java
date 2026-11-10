package jexxme.vineandbarrel.compat.jei;

import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The Aging Barrel's JEI category (W3): base → young drink → aged drink, with how long each step takes. The base comes
 * as a bottle (1 serving) or a bucket (4 servings); the drinks are tapped into the vessel shown under them.
 */
public class AgingCategory implements IRecipeCategory<RecipeHolder<AgingRecipe>>
{
	public static final int WIDTH = 120;
	public static final int HEIGHT = 58;
	private static final int DAY = 24000;
	private static final int BASE_X = 1, YOUNG_X = 51, AGED_X = 101, SLOT_Y = 5, VESSEL_Y = 28;
	private static final int ARROW_1_X = 22, ARROW_2_X = 72, ARROW_Y = 5;
	/** The day labels get their own row under the vessel slots, so a long one ("+3 days") never runs into a slot. */
	private static final int LABEL_Y = VESSEL_Y + 20;
	private static final int TEXT_COLOR = 0xFF808080;

	private final IDrawable icon;
	private final IDrawableAnimated fermentArrow;
	private final IDrawableAnimated ageArrow;

	public AgingCategory(IGuiHelper helper) {
		icon = helper.createDrawableItemLike(BarrelRegistry.AGING_BARREL_ITEM.get());
		fermentArrow = helper.createAnimatedRecipeArrow(100);
		ageArrow = helper.createAnimatedRecipeArrow(300);
	}

	@Override
	public RecipeType<RecipeHolder<AgingRecipe>> getRecipeType() {
		return VBJeiPlugin.AGING;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("jei.vineandbarrel.aging");
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
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AgingRecipe> holder, IFocusGroup focuses) {
		AgingRecipe recipe = holder.value();
		IRecipeSlotBuilder base = builder.addSlot(RecipeIngredientRole.INPUT, BASE_X, SLOT_Y).setStandardSlotBackground()
			.addItemStack(new ItemStack(recipe.base()));
		recipe.bucket().ifPresent(bucket -> base.addItemStack(new ItemStack(bucket)));
		builder.addSlot(RecipeIngredientRole.OUTPUT, YOUNG_X, SLOT_Y).setOutputSlotBackground().addItemStack(recipe.young());
		builder.addSlot(RecipeIngredientRole.OUTPUT, AGED_X, SLOT_Y).setOutputSlotBackground().addItemStack(recipe.aged());
		// The vessels each drink is tapped into
		Item youngVessel = AgingBarrelBlockEntity.vesselOf(recipe.young());
		if (youngVessel != null) {
			builder.addSlot(RecipeIngredientRole.CATALYST, YOUNG_X, VESSEL_Y).addItemLike(youngVessel);
		}
		Item agedVessel = AgingBarrelBlockEntity.vesselOf(recipe.aged());
		if (agedVessel != null) {
			builder.addSlot(RecipeIngredientRole.CATALYST, AGED_X, VESSEL_Y).addItemLike(agedVessel);
		}
	}

	@Override
	public void draw(RecipeHolder<AgingRecipe> holder, IRecipeSlotsView slots, GuiGraphics gui, double mouseX, double mouseY) {
		AgingRecipe recipe = holder.value();
		fermentArrow.draw(gui, ARROW_1_X, ARROW_Y);
		ageArrow.draw(gui, ARROW_2_X, ARROW_Y);
		Font font = Minecraft.getInstance().font;
		label(gui, font, days(recipe.fermentingTime()), ARROW_1_X + 12, LABEL_Y);
		label(gui, font, Component.translatable("jei.vineandbarrel.aging.more", days(recipe.agingTime())), ARROW_2_X + 12, LABEL_Y);
	}

	private static void label(GuiGraphics gui, Font font, Component text, int centerX, int y) {
		gui.drawString(font, text, centerX - font.width(text) / 2, y, TEXT_COLOR, false);
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, RecipeHolder<AgingRecipe> holder, IRecipeSlotsView slots, double mouseX, double mouseY) {
		if (mouseY < ARROW_Y || mouseY >= LABEL_Y + 10) {
			return;
		}
		AgingRecipe recipe = holder.value();
		if (mouseX >= ARROW_1_X && mouseX < ARROW_1_X + 24) {
			tooltip.add(Component.translatable("jei.vineandbarrel.aging.fermenting", days(recipe.fermentingTime())));
			// FD's juices come in bottles only
			tooltip.add((recipe.bucket().isPresent()
					? Component.translatable("jei.vineandbarrel.aging.servings", AgingBarrelBlockEntity.SERVINGS_PER_BUCKET)
					: Component.translatable("jei.vineandbarrel.aging.servings_bottle")).withStyle(ChatFormatting.GRAY));
		} else if (mouseX >= ARROW_2_X && mouseX < ARROW_2_X + 24) {
			tooltip.add(Component.translatable("jei.vineandbarrel.aging.aging", days(recipe.agingTime())));
		}
	}

	/** In-game days: "1 day", "3 days", "1.5 days". */
	static Component days(int ticks) {
		if (ticks == DAY) {
			return Component.translatable("jei.vineandbarrel.aging.one_day");
		}
		String amount = ticks % DAY == 0 ? Integer.toString(ticks / DAY) : String.format(java.util.Locale.ROOT, "%.1f", ticks / (double) DAY);
		return Component.translatable("jei.vineandbarrel.aging.days", amount);
	}
}
