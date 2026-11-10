package jexxme.vineandbarrel.kettle;

import jexxme.vineandbarrel.VineAndBarrel;
import com.google.gson.JsonObject;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Everything the Brew Kettle (M2) registers: the eight copper kettle blocks (four weathering stages, each also waxed),
 * their items, the block entity, menu, the {@code vineandbarrel:brewing} recipe type, steam particle and sounds (what a
 * kettle item holds is in its NBT on this Minecraft: {@link BrewKettleItem#CONTENTS_TAG}). The bases (juice and wort) are in {@link BaseItems}. The recipe book's tabs are client
 * enum constants on this Minecraft (see {@code BrewingRecipeBookCategories}).
 */
public final class KettleRegistry
{
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VineAndBarrel.MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, VineAndBarrel.MODID);
	public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, VineAndBarrel.MODID);

	// ------------------------------------------------------------------ blocks: copper -> exposed -> weathered -> oxidized, plus waxed

	private static final String[] PREFIX = {"", "exposed_", "weathered_", "oxidized_"};

	public static final Map<WeatherState, DeferredBlock<WeatheringBrewKettleBlock>> KETTLES = new EnumMap<>(WeatherState.class);
	public static final Map<WeatherState, DeferredBlock<BrewKettleBlock>> WAXED_KETTLES = new EnumMap<>(WeatherState.class);
	public static final Map<WeatherState, DeferredItem<BrewKettleItem>> KETTLE_ITEMS = new EnumMap<>(WeatherState.class);
	public static final Map<WeatherState, DeferredItem<BrewKettleItem>> WAXED_KETTLE_ITEMS = new EnumMap<>(WeatherState.class);
	/** The blocks of big kettles (no items: a broken part drops the kettle of its stage). */
	public static final Map<WeatherState, DeferredBlock<WeatheringBrewKettlePartBlock>> PARTS = new EnumMap<>(WeatherState.class);
	public static final Map<WeatherState, DeferredBlock<BrewKettlePartBlock>> WAXED_PARTS = new EnumMap<>(WeatherState.class);
	/** A chimney's extension blocks (technical: no items; each drops the copper ingot it was made from). */
	public static final Map<WeatherState, DeferredBlock<KettleChimneyBlock>> CHIMNEYS = new EnumMap<>(WeatherState.class);
	public static final Map<WeatherState, DeferredBlock<KettleChimneyBlock>> WAXED_CHIMNEYS = new EnumMap<>(WeatherState.class);

	static {
		for (WeatherState stage : WeatherState.values()) {
			String name = PREFIX[stage.ordinal()] + "brew_kettle";
			DeferredBlock<WeatheringBrewKettleBlock> block = BLOCKS.registerBlock(name, p -> new WeatheringBrewKettleBlock(stage, p), () -> kettleProperties(stage));
			DeferredBlock<BrewKettleBlock> waxed = BLOCKS.registerBlock("waxed_" + name, p -> new BrewKettleBlock(stage, p), () -> kettleProperties(stage));
			KETTLES.put(stage, block);
			WAXED_KETTLES.put(stage, waxed);
			KETTLE_ITEMS.put(stage, ITEMS.registerItem(name, p -> new BrewKettleItem(block.get(), p), p -> p.stacksTo(1)));
			WAXED_KETTLE_ITEMS.put(stage, ITEMS.registerItem("waxed_" + name, p -> new BrewKettleItem(waxed.get(), p), p -> p.stacksTo(1)));
			PARTS.put(stage, BLOCKS.registerBlock(name + "_part", p -> new WeatheringBrewKettlePartBlock(stage, p), () -> partProperties(stage)));
			WAXED_PARTS.put(stage, BLOCKS.registerBlock("waxed_" + name + "_part", p -> new BrewKettlePartBlock(stage, p), () -> partProperties(stage)));
			CHIMNEYS.put(stage, BLOCKS.registerBlock(name + "_chimney", p -> new KettleChimneyBlock(stage, false, p), () -> partProperties(stage)));
			WAXED_CHIMNEYS.put(stage, BLOCKS.registerBlock("waxed_" + name + "_chimney", p -> new KettleChimneyBlock(stage, true, p), () -> partProperties(stage)));
		}
	}

	/** FD's cooking pot properties (strength 0.5 / 6.0, no tool needed), with copper's look and sounds (K9). */
	private static BlockBehaviour.Properties kettleProperties(WeatherState stage) {
		MapColor color = switch (stage) {
			case UNAFFECTED -> MapColor.COLOR_ORANGE;
			case EXPOSED -> MapColor.TERRACOTTA_LIGHT_GRAY;
			case WEATHERED -> MapColor.WARPED_STEM;
			case OXIDIZED -> MapColor.WARPED_NYLIUM;
		};
		return BlockBehaviour.Properties.of().mapColor(color).strength(0.5F, 6.0F).sound(SoundType.COPPER)
			.noOcclusion();
	}

	/** A part never moves on its own: pistons would tear a big kettle apart. */
	private static BlockBehaviour.Properties partProperties(WeatherState stage) {
		return kettleProperties(stage).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noLootTable();
	}

	/** All eight part blocks, unwaxed first, each in weathering order. */
	public static List<Block> allPartBlocks() {
		List<Block> blocks = new ArrayList<>();
		PARTS.values().forEach(b -> blocks.add(b.get()));
		WAXED_PARTS.values().forEach(b -> blocks.add(b.get()));
		return blocks;
	}

	/** All eight chimney extension blocks. */
	public static List<Block> allChimneyBlocks() {
		List<Block> blocks = new ArrayList<>();
		CHIMNEYS.values().forEach(b -> blocks.add(b.get()));
		WAXED_CHIMNEYS.values().forEach(b -> blocks.add(b.get()));
		return blocks;
	}

	/** The single kettle of a kettle block's look (a part's or chimney piece's stage and wax; a single kettle is itself). */
	public static Block singleOf(Block block) {
		if (block instanceof BrewKettlePartBlock part) {
			return (part.isWaxed() ? WAXED_KETTLES : KETTLES).get(part.getWeatherState()).get();
		}
		if (block instanceof KettleChimneyBlock chimney) {
			return (chimney.isWaxed() ? WAXED_KETTLES : KETTLES).get(chimney.getWeatherState()).get();
		}
		return block;
	}

	/** The chimney extension block of a single kettle's look. */
	public static Block chimneyOf(Block single) {
		BrewKettleBlock kettle = (BrewKettleBlock) singleOf(single);
		return (kettle.isWaxed() ? WAXED_CHIMNEYS : CHIMNEYS).get(kettle.getWeatherState()).get();
	}

	/** The part block of a single kettle's look. */
	public static Block partOf(Block single) {
		if (single instanceof BrewKettlePartBlock) {
			return single;
		}
		BrewKettleBlock kettle = (BrewKettleBlock) single;
		return (kettle.isWaxed() ? WAXED_PARTS : PARTS).get(kettle.getWeatherState()).get();
	}

	/** The next weathering stage of an unwaxed single kettle, or null (oxidized, or waxed). */
	public static @org.jspecify.annotations.Nullable Block nextStage(Block single) {
		if (!(single instanceof BrewKettleBlock kettle) || kettle.isWaxed() || kettle.getWeatherState() == WeatherState.OXIDIZED) {
			return null;
		}
		return KETTLES.get(WeatherState.values()[kettle.getWeatherState().ordinal() + 1]).get();
	}

	/** The waxed twin of an unwaxed single kettle, or null. */
	public static @org.jspecify.annotations.Nullable Block waxedOf(Block single) {
		return single instanceof BrewKettleBlock kettle && !kettle.isWaxed() ? WAXED_KETTLES.get(kettle.getWeatherState()).get() : null;
	}

	/** One step back with an axe, as for a single kettle: wax off first, otherwise one stage of oxidation; null if clean. */
	public static @org.jspecify.annotations.Nullable Block scrapedOf(Block single) {
		if (!(single instanceof BrewKettleBlock kettle)) {
			return null;
		}
		if (kettle.isWaxed()) {
			return KETTLES.get(kettle.getWeatherState()).get();
		}
		return kettle.getWeatherState() == WeatherState.UNAFFECTED ? null : KETTLES.get(WeatherState.values()[kettle.getWeatherState().ordinal() - 1]).get();
	}

	/** All eight kettle blocks, unwaxed first, each in weathering order. */
	public static List<Block> allKettleBlocks() {
		List<Block> blocks = new ArrayList<>();
		KETTLES.values().forEach(b -> blocks.add(b.get()));
		WAXED_KETTLES.values().forEach(b -> blocks.add(b.get()));
		return blocks;
	}

	/** All eight kettle items, in the same order as {@link #allKettleBlocks()}. */
	public static List<Item> allKettleItems() {
		List<Item> items = new ArrayList<>();
		KETTLE_ITEMS.values().forEach(i -> items.add(i.get()));
		WAXED_KETTLE_ITEMS.values().forEach(i -> items.add(i.get()));
		return items;
	}

	public static Block kettle() {
		return KETTLES.get(WeatherState.UNAFFECTED).get();
	}

	public static Item kettleItem() {
		return KETTLE_ITEMS.get(WeatherState.UNAFFECTED).get();
	}

	// ------------------------------------------------------------------ block entity, menu

	public static final Supplier<BlockEntityType<BrewKettleBlockEntity>> BREW_KETTLE_BE = BLOCK_ENTITY_TYPES.register("brew_kettle",
		() -> {
			java.util.Set<Block> valid = new java.util.HashSet<>(allKettleBlocks());
			valid.addAll(allPartBlocks());
			return Services.PLATFORM.blockEntityType(BrewKettleBlockEntity::new, valid.toArray(Block[]::new));
		});

	public static final Supplier<MenuType<BrewKettleMenu>> BREW_KETTLE_MENU = MENU_TYPES.register("brew_kettle",
		() -> Services.PLATFORM.blockMenuType(BrewKettleMenu::new));

	// ------------------------------------------------------------------ recipe

	public static final Supplier<RecipeType<BrewingRecipe>> BREWING = RECIPE_TYPES.register("brewing",
		() -> new RecipeType<>()
		{
			@Override
			public String toString() {
				return VineAndBarrel.MODID + ":brewing";
			}
		});
	public static final Supplier<RecipeSerializer<BrewingRecipe>> BREWING_SERIALIZER = RECIPE_SERIALIZERS.register("brewing",
		() -> new RecipeSerializer<>()
		{
			@Override
			public BrewingRecipe fromJson(ResourceLocation id, JsonObject json) {
				return BrewingRecipe.fromJson(id, json);
			}

			@Override
			public BrewingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
				return BrewingRecipe.fromNetwork(id, buffer);
			}

			@Override
			public void toNetwork(FriendlyByteBuf buffer, BrewingRecipe recipe) {
				BrewingRecipe.toNetwork(buffer, recipe);
			}
		});

	// ------------------------------------------------------------------ particles and sounds (K3, K4, K9)

	public static final Supplier<SimpleParticleType> STEAM = PARTICLE_TYPES.register("steam", () -> new SimpleParticleType(true) {});

	public static final Supplier<SoundEvent> BOIL = sound("block.brew_kettle.boil");
	public static final Supplier<SoundEvent> BOIL_BREW = sound("block.brew_kettle.boil_brew");
	public static final Supplier<SoundEvent> LID_OPEN = sound("block.brew_kettle.open");
	public static final Supplier<SoundEvent> LID_CLOSE = sound("block.brew_kettle.close");
	public static final Supplier<SoundEvent> BREW_FINISHED = sound("block.brew_kettle.finish");

	private static Supplier<SoundEvent> sound(String name) {
		return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(VineAndBarrel.id(name)));
	}

	private KettleRegistry() {
	}

	public static void register() {
		BLOCKS.register();
		ITEMS.register();
		BaseItems.ITEMS.register();
		BLOCK_ENTITY_TYPES.register();
		MENU_TYPES.register();
		RECIPE_TYPES.register();
		RECIPE_SERIALIZERS.register();
		PARTICLE_TYPES.register();
		SOUNDS.register();
	}
}
