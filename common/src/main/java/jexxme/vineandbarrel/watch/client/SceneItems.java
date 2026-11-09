package jexxme.vineandbarrel.watch.client;

import com.mojang.datafixers.util.Either;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The items a scene names: plain item ids, or parts of the real recipes the server sent (so a data pack's changes show). */
final class SceneItems
{
	private SceneItems() {
	}

	static ItemStack resolve(Either<Holder<Item>, WatchScene.RecipePart> item) {
		return item.map(ItemStack::new, SceneItems::fromRecipe);
	}

	private static ItemStack fromRecipe(WatchScene.RecipePart part) {
		if (part.part().equals("result") || part.part().equals("ingredient")) {
			BrewingRecipe brewing = ScenePlayer.brewingRecipe(part.recipe());
			if (brewing == null) {
				return ItemStack.EMPTY;
			}
			if (part.part().equals("result")) {
				return brewing.result().copyWithCount(1);
			}
			return brewing.ingredients().stream().findFirst()
				.flatMap(ingredient -> ingredient.items().findFirst()).map(ItemStack::new).orElse(ItemStack.EMPTY);
		}
		if (part.part().equals("bucket") && ScenePlayer.brewingRecipe(part.recipe(), false) != null) {
			return ScenePlayer.brewingRecipe(part.recipe(), false).bucket();
		}
		AgingRecipe recipe = ScenePlayer.agingRecipe(part.recipe());
		if (recipe == null) {
			return ItemStack.EMPTY;
		}
		return switch (part.part()) {
			case "base" -> new ItemStack(recipe.base());
			case "bucket" -> recipe.bucket().map(ItemStack::new).orElseGet(() -> new ItemStack(recipe.base()));
			case "young" -> recipe.young();
			case "aged" -> recipe.aged();
			default -> ItemStack.EMPTY;
		};
	}
}
