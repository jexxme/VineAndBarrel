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
import jexxme.vineandbarrel.watch.WatchPath;
import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Every scene the loaded resource packs have ({@code assets/<namespace>/watch/*.json}), read on each resource reload,
 * and what opens which: an item's tooltip key, a block in the world, a JEI category. Also the Brewer's path
 * ({@code watch/path.json}, merged over all namespaces, ours first).
 */
public final class WatchIndex implements ResourceManagerReloadListener
{
	public static final ResourceLocation ID = VineAndBarrel.id("watch_scenes");
	public static final WatchIndex INSTANCE = new WatchIndex();
	private static final String FOLDER = "watch";

	private volatile Map<ResourceLocation, WatchScene> scenes = Map.of();
	private volatile WatchPath path = WatchPath.EMPTY;

	private WatchIndex() {
	}

	@Override
	public void onResourceManagerReload(ResourceManager resources) {
		Map<ResourceLocation, WatchScene> read = new LinkedHashMap<>();
		Map<String, WatchPath> paths = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> entry : resources.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation file = entry.getKey();
			if (file.getPath().equals(FOLDER + "/" + WatchPath.FILE)) {
				try (Reader reader = entry.getValue().openAsReader()) {
					WatchPath.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
						.resultOrPartial(error -> VineAndBarrel.LOGGER.error("Watch it: path {} can't be read: {}", file, error))
						.ifPresent(parsed -> paths.put(file.getNamespace(), parsed));
				} catch (Exception e) {
					VineAndBarrel.LOGGER.error("Watch it: path {} can't be read", file, e);
				}
				continue;
			}
			ResourceLocation id = file.withPath(path -> path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
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
		// ours first: our file sets the stations, the others add branches
		WatchPath merged = paths.getOrDefault(VineAndBarrel.MODID, WatchPath.EMPTY);
		for (Map.Entry<String, WatchPath> other : paths.entrySet()) {
			if (!other.getKey().equals(VineAndBarrel.MODID)) {
				merged = merged.with(other.getValue());
			}
		}
		path = merged;
	}

	/** {@return the Brewer's path, as the loaded resource packs give it (stations and branches may name missing scenes)} */
	public WatchPath path() {
		return path;
	}

	/** {@return the id a loaded scene goes by} */
	public @Nullable ResourceLocation idOf(WatchScene scene) {
		for (Map.Entry<ResourceLocation, WatchScene> entry : scenes.entrySet()) {
			if (entry.getValue() == scene) {
				return entry.getKey();
			}
		}
		return null;
	}

	public Optional<WatchScene> scene(ResourceLocation id) {
		return Optional.ofNullable(scenes.get(id));
	}

	/** {@return the scene this item opens from its tooltip} */
	public Optional<ResourceLocation> forItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		for (Map.Entry<ResourceLocation, WatchScene> entry : scenes.entrySet()) {
			for (String selector : entry.getValue().opens().items()) {
				if (matches(selector, stack)) {
					return Optional.of(entry.getKey());
				}
			}
		}
		return Optional.empty();
	}

	/** A block in the world that opens a scene, at a step, maybe with a line about what the block is doing. */
	public record BlockEntry(ResourceLocation scene, int step, Optional<String> now) {
	}

	public Optional<BlockEntry> forBlock(Level level, BlockPos pos, BlockState state) {
		// a big kettle's blocks and chimney pieces open what their kettle opens, as a kettle of their copper and level,
		// heated if any fire under it is
		Optional<Boolean> kettleHeat = Optional.empty();
		if (state.getBlock() instanceof jexxme.vineandbarrel.kettle.BrewKettlePartBlock || state.getBlock() instanceof jexxme.vineandbarrel.kettle.KettleChimneyBlock) {
			jexxme.vineandbarrel.kettle.BrewKettleBlockEntity kettle = jexxme.vineandbarrel.kettle.KettleMultiblock.controllerUnder(level, pos);
			if (kettle == null) {
				return Optional.empty();
			}
			BlockState own = kettle.getBlockState();
			state = own.getBlock() instanceof jexxme.vineandbarrel.kettle.BrewKettleBlock ? own
				: jexxme.vineandbarrel.kettle.KettleRegistry.singleOf(own.getBlock()).defaultBlockState()
					.setValue(jexxme.vineandbarrel.kettle.BrewKettleBlock.FILL, jexxme.vineandbarrel.kettle.BrewKettleBlockEntity.fillLevel(kettle.getServings()));
			kettleHeat = Optional.of(kettle.isHeated());
		}
		final BlockState look = state;
		final Optional<Boolean> heat = kettleHeat;
		for (Map.Entry<ResourceLocation, WatchScene> entry : scenes.entrySet()) {
			for (WatchScene.Entry block : entry.getValue().opens().blocks()) {
				if (block.fits(look) && block.heated().map(heated -> heated == heat.orElseGet(() -> KettleTags.isHeated(level, pos))).orElse(true)) {
					int step = Math.min(block.step(), entry.getValue().steps().size()) - 1;
					return Optional.of(new BlockEntry(entry.getKey(), step, block.now()));
				}
			}
		}
		return Optional.empty();
	}

	/** {@return the scene a JEI recipe category gets a button for} */
	public Optional<ResourceLocation> forJeiCategory(ResourceLocation category) {
		for (Map.Entry<ResourceLocation, WatchScene> entry : scenes.entrySet()) {
			if (entry.getValue().opens().jei().contains(category)) {
				return Optional.of(entry.getKey());
			}
		}
		return Optional.empty();
	}

	/** {@return the item that stands for a scene (its "Also watch" icon)} */
	public ItemStack icon(ResourceLocation id) {
		WatchScene scene = scenes.get(id);
		return scene == null ? ItemStack.EMPTY : new ItemStack(scene.icon());
	}

	private static boolean matches(String selector, ItemStack stack) {
		if (selector.startsWith("#")) {
			ResourceLocation tag = ResourceLocation.tryParse(selector.substring(1));
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
		ResourceLocation item = ResourceLocation.tryParse(selector);
		return item != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item);
	}
}
