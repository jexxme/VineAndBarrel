package jexxme.vineandbarrel.watch.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.drink.DrinkItem;
import jexxme.vineandbarrel.kettle.KettleTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Every scene the loaded resource packs have ({@code assets/<namespace>/watch/*.json}), read on each resource reload,
 * and what opens which: an item's tooltip key, a block in the world, a JEI category.
 */
public final class WatchIndex implements ResourceManagerReloadListener
{
	public static final Identifier ID = VineAndBarrel.id("watch_scenes");
	public static final WatchIndex INSTANCE = new WatchIndex();
	private static final String FOLDER = "watch";

	private volatile Map<Identifier, WatchScene> scenes = Map.of();

	private WatchIndex() {
	}

	@Override
	public void onResourceManagerReload(ResourceManager resources) {
		Map<Identifier, WatchScene> read = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Resource> entry : resources.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
			Identifier file = entry.getKey();
			Identifier id = file.withPath(path -> path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				if (!WatchScene.requirementsMet(json)) {
					// a scene of another mod's blocks (Farmer's Delight's stove): only when that mod is here
					continue;
				}
				WatchScene.CODEC.parse(JsonOps.INSTANCE, json)
					.resultOrPartial(error -> VineAndBarrel.LOGGER.error("Watch it: scene {} can't be read: {}", file, error))
					.ifPresent(scene -> read.put(id, scene));
			} catch (Exception e) {
				VineAndBarrel.LOGGER.error("Watch it: scene {} can't be read", file, e);
			}
		}
		scenes = Map.copyOf(read);
	}

	public Optional<WatchScene> scene(Identifier id) {
		return Optional.ofNullable(scenes.get(id));
	}

	/** {@return the scene this item opens from its tooltip} */
	public Optional<Identifier> forItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		for (Map.Entry<Identifier, WatchScene> entry : scenes.entrySet()) {
			for (String selector : entry.getValue().opens().items()) {
				if (matches(selector, stack)) {
					return Optional.of(entry.getKey());
				}
			}
		}
		return Optional.empty();
	}

	/** A block in the world that opens a scene, at a step, maybe with a line about what the block is doing. */
	public record BlockEntry(Identifier scene, int step, Optional<String> now) {
	}

	public Optional<BlockEntry> forBlock(Level level, BlockPos pos, BlockState state) {
		for (Map.Entry<Identifier, WatchScene> entry : scenes.entrySet()) {
			for (WatchScene.Entry block : entry.getValue().opens().blocks()) {
				if (block.fits(state) && block.heated().map(heated -> heated == KettleTags.isHeated(level, pos)).orElse(true)) {
					int step = Math.min(block.step(), entry.getValue().steps().size()) - 1;
					return Optional.of(new BlockEntry(entry.getKey(), step, block.now()));
				}
			}
		}
		return Optional.empty();
	}

	/** {@return the scene a JEI recipe category gets a button for} */
	public Optional<Identifier> forJeiCategory(Identifier category) {
		for (Map.Entry<Identifier, WatchScene> entry : scenes.entrySet()) {
			if (entry.getValue().opens().jei().contains(category)) {
				return Optional.of(entry.getKey());
			}
		}
		return Optional.empty();
	}

	/** {@return the item that stands for a scene (its "Also watch" icon)} */
	public ItemStack icon(Identifier id) {
		WatchScene scene = scenes.get(id);
		return scene == null ? ItemStack.EMPTY : new ItemStack(scene.icon());
	}

	private static boolean matches(String selector, ItemStack stack) {
		if (selector.startsWith("#")) {
			Identifier tag = Identifier.tryParse(selector.substring(1));
			return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
		}
		if (selector.equals("@drinks")) {
			return stack.getItem() instanceof DrinkItem;
		}
		if (selector.equals("@aging_bases")) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level == null) {
				return false;
			}
			for (RecipeHolder<AgingRecipe> holder : AgingRecipes.all(minecraft.level)) {
				if (holder.value().servingsOf(stack) > 0) {
					return true;
				}
			}
			return false;
		}
		Identifier item = Identifier.tryParse(selector);
		return item != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item);
	}
}
