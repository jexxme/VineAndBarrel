package jexxme.vineandbarrel.watch;

import com.mojang.datafixers.util.Either;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.BarrelStatus;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * What a scene's items, captions and time-lapses are for the recipe it plays. A scene is written for one aging recipe
 * ({@link WatchScene#recipe()}); asked for another (a wort bucket, a mug, a barrel of ale), every part of the written one
 * it names becomes the asked one's: its bucket, its drinks, its vessel, its days, and the caption lines for it. Common
 * code with the recipes passed in, so the client plays it and the GameTests check it with the same rules.
 *
 * @param recipes where the recipes come from (the client's synced ones, or the server's)
 * @param written the recipe the scene is written for, if any
 * @param asked   the recipe to play instead, if any
 */
public record WatchParts(Recipes recipes, Optional<ResourceLocation> written, Optional<ResourceLocation> asked)
{
	/** The recipes a scene can name. */
	public interface Recipes
	{
		@Nullable AgingRecipe aging(ResourceLocation id);

		@Nullable BrewingRecipe brewing(ResourceLocation id);
	}

	public static WatchParts of(WatchScene scene, Recipes recipes, Optional<ResourceLocation> asked) {
		return new WatchParts(recipes, scene.recipe(), asked.filter(id -> scene.recipe().isPresent()));
	}

	/** {@return the recipe to read where the scene names this one} */
	public ResourceLocation recipe(ResourceLocation named) {
		return asked.isPresent() && written.isPresent() && written.get().equals(named) ? asked.get() : named;
	}

	/** {@return the item a reference stands for here (empty if its recipe isn't on this server)} */
	public ItemStack item(Either<Holder<Item>, WatchScene.RecipePart> ref) {
		return ref.map(ItemStack::new, this::fromRecipe);
	}

	private ItemStack fromRecipe(WatchScene.RecipePart part) {
		ResourceLocation id = recipe(part.recipe());
		if (part.part().equals("result") || part.part().equals("ingredient")) {
			BrewingRecipe brewing = recipes.brewing(id);
			if (brewing == null) {
				return ItemStack.EMPTY;
			}
			if (part.part().equals("result")) {
				return brewing.result().copyWithCount(1);
			}
			return brewing.ingredients().stream().findFirst()
				.flatMap(ingredient -> java.util.Arrays.stream(ingredient.getItems()).findFirst()).map(stack -> new ItemStack(stack.getItem())).orElse(ItemStack.EMPTY);
		}
		if (part.part().equals("bucket") && recipes.brewing(id) != null) {
			return recipes.brewing(id).bucket();
		}
		AgingRecipe recipe = recipes.aging(id);
		if (recipe == null) {
			return ItemStack.EMPTY;
		}
		return switch (part.part()) {
			case "base" -> new ItemStack(recipe.base());
			// FD's juices come in bottles only: the bottle then
			case "bucket" -> recipe.bucket().map(ItemStack::new).orElseGet(() -> new ItemStack(recipe.base()));
			case "young" -> recipe.young();
			case "aged" -> recipe.aged();
			case "vessel" -> vesselOf(recipe);
			default -> ItemStack.EMPTY;
		};
	}

	/** {@return the vessel an aging recipe's drinks are tapped into (empty if they have none)} */
	public static ItemStack vesselOf(AgingRecipe recipe) {
		Item vessel = AgingBarrelBlockEntity.vesselOf(recipe.young());
		return vessel == null ? ItemStack.EMPTY : new ItemStack(vessel);
	}

	/** {@return how many game days a recipe part of time stands for ({@code fermenting_time} or {@code aging_time}), if it is one} */
	public Optional<Integer> ticks(WatchScene.RecipePart part) {
		if (!part.part().equals("fermenting_time") && !part.part().equals("aging_time")) {
			return Optional.empty();
		}
		AgingRecipe recipe = recipes.aging(recipe(part.recipe()));
		if (recipe == null) {
			return Optional.empty();
		}
		return Optional.of(part.part().equals("fermenting_time") ? recipe.fermentingTime() : recipe.agingTime());
	}

	/** {@return how many days a time-lapse shows: as many as its recipe's time takes, else as written} */
	public float days(WatchScene.Timelapse lapse) {
		return lapse.daysOf().flatMap(this::ticks).map(ticks -> Math.max(0.25F, ticks / 24000.0F)).orElse(lapse.days());
	}

	/** {@return the lang key a step shows here: the first variant whose item fits, else its own} */
	public String captionKey(WatchScene.Step step) {
		for (WatchScene.CaptionVariant variant : step.variants()) {
			ItemStack of = item(variant.of());
			if (!of.isEmpty() && variant.fits(of)) {
				return variant.caption();
			}
		}
		return step.caption();
	}

	/** {@return the step's caption line here, with what its {@code %s} stand for} */
	public Component caption(WatchScene.Step step) {
		List<Object> args = new ArrayList<>();
		for (Either<Holder<Item>, WatchScene.RecipePart> ref : step.with()) {
			args.add(text(ref));
		}
		return Component.translatable(captionKey(step), args.toArray());
	}

	/** {@return what a caption's {@code %s} reads: an item's name, or a recipe's time in days} */
	public Component text(Either<Holder<Item>, WatchScene.RecipePart> ref) {
		Optional<Integer> time = ref.right().flatMap(this::ticks);
		if (time.isPresent()) {
			return BarrelStatus.days(time.get());
		}
		ItemStack stack = item(ref);
		return stack.isEmpty() ? Component.literal("?") : stack.getHoverName();
	}

	/**
	 * {@return the aging recipe a scene written for {@code written} plays when asked for this item}: the recipe it is the
	 * base or the bucket of; for a mug or a wine glass the written recipe if its drinks go in that, else the first that
	 * does (by id); none for anything else
	 */
	public static Optional<ResourceLocation> agingRecipeFor(ItemStack stack, Collection<RecipeHolder<AgingRecipe>> all, Optional<ResourceLocation> written) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		for (RecipeHolder<AgingRecipe> holder : all) {
			if (holder.value().servingsOf(stack) > 0) {
				return Optional.of(holder.id());
			}
		}
		Optional<ResourceLocation> byVessel = Optional.empty();
		for (RecipeHolder<AgingRecipe> holder : all) {
			ItemStack vessel = vesselOf(holder.value());
			if (!vessel.isEmpty() && ItemStack.isSameItem(vessel, stack)) {
				if (written.isPresent() && written.get().equals(holder.id())) {
					return written;
				}
				if (byVessel.isEmpty()) {
					byVessel = Optional.of(holder.id());
				}
			}
		}
		return byVessel;
	}

	/** {@return the aging recipe whose batch this base item starts (a barrel's contents), if any} */
	public static Optional<ResourceLocation> agingRecipeOfBase(ItemStack base, Collection<RecipeHolder<AgingRecipe>> all) {
		for (RecipeHolder<AgingRecipe> holder : all) {
			if (!base.isEmpty() && base.is(holder.value().base())) {
				return Optional.of(holder.id());
			}
		}
		return Optional.empty();
	}
}
