package jexxme.vineandbarrel.kettle;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.component.DataComponentType;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import jexxme.vineandbarrel.platform.registry.DeferredHolder;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Everything the Brew Kettle (M2) registers: the eight copper kettle blocks (four weathering stages, each also waxed),
 * their items, the block entity, menu, the {@code vineandbarrel:brewing} recipe type, recipe book categories,
 * the kettle's data component, steam particle and sounds. The bases (juice and wort) are in {@link BaseItems}.
 */
public final class KettleRegistry
{
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VineAndBarrel.MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeDisplay.Type<?>> RECIPE_DISPLAYS = DeferredRegister.create(Registries.RECIPE_DISPLAY, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeBookCategory> RECIPE_BOOK_CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, VineAndBarrel.MODID);
	public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, VineAndBarrel.MODID);

	// ------------------------------------------------------------------ blocks: copper -> exposed -> weathered -> oxidized, plus waxed

	private static final String[] PREFIX = {"", "exposed_", "weathered_", "oxidized_"};

	public static final Map<WeatherState, DeferredBlock<WeatheringBrewKettleBlock>> KETTLES = new EnumMap<>(WeatherState.class);
	public static final Map<WeatherState, DeferredBlock<BrewKettleBlock>> WAXED_KETTLES = new EnumMap<>(WeatherState.class);
	public static final Map<WeatherState, DeferredItem<BrewKettleItem>> KETTLE_ITEMS = new EnumMap<>(WeatherState.class);
	public static final Map<WeatherState, DeferredItem<BrewKettleItem>> WAXED_KETTLE_ITEMS = new EnumMap<>(WeatherState.class);

	static {
		for (WeatherState stage : WeatherState.values()) {
			String name = PREFIX[stage.ordinal()] + "brew_kettle";
			DeferredBlock<WeatheringBrewKettleBlock> block = BLOCKS.registerBlock(name, p -> new WeatheringBrewKettleBlock(stage, p), () -> kettleProperties(stage));
			DeferredBlock<BrewKettleBlock> waxed = BLOCKS.registerBlock("waxed_" + name, p -> new BrewKettleBlock(stage, p), () -> kettleProperties(stage));
			KETTLES.put(stage, block);
			WAXED_KETTLES.put(stage, waxed);
			KETTLE_ITEMS.put(stage, ITEMS.registerItem(name, p -> new BrewKettleItem(block.get(), p), p -> p.stacksTo(1).useBlockDescriptionPrefix()));
			WAXED_KETTLE_ITEMS.put(stage, ITEMS.registerItem("waxed_" + name, p -> new BrewKettleItem(waxed.get(), p), p -> p.stacksTo(1).useBlockDescriptionPrefix()));
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
		() -> new BlockEntityType<>(BrewKettleBlockEntity::new, java.util.Set.copyOf(allKettleBlocks())));

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
		() -> new RecipeSerializer<>(BrewingRecipe.CODEC, BrewingRecipe.STREAM_CODEC));
	public static final Supplier<RecipeDisplay.Type<BrewingRecipeDisplay>> BREWING_DISPLAY = RECIPE_DISPLAYS.register("brewing",
		() -> BrewingRecipeDisplay.TYPE);

	public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> BREWING_SEARCH = RECIPE_BOOK_CATEGORIES.register("brewing_search", RecipeBookCategory::new);
	public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> BREWING_JUICES = RECIPE_BOOK_CATEGORIES.register("brewing_juices", RecipeBookCategory::new);
	public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> BREWING_WORTS = RECIPE_BOOK_CATEGORIES.register("brewing_worts", RecipeBookCategory::new);
	public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> BREWING_MISC = RECIPE_BOOK_CATEGORIES.register("brewing_misc", RecipeBookCategory::new);

	// ------------------------------------------------------------------ data component: what a kettle item holds (K6)

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<KettleContents>> KETTLE_CONTENTS = DATA_COMPONENTS.registerComponentType(
		"kettle_contents", builder -> builder.persistent(KettleContents.CODEC).networkSynchronized(KettleContents.STREAM_CODEC).cacheEncoding());

	// ------------------------------------------------------------------ particles and sounds (K3, K4, K9)

	public static final Supplier<SimpleParticleType> STEAM = PARTICLE_TYPES.register("steam", () -> new SimpleParticleType(true) {});

	public static final Supplier<SoundEvent> BOIL = sound("block.brew_kettle.boil");
	public static final Supplier<SoundEvent> BOIL_BREW = sound("block.brew_kettle.boil_brew");
	public static final Supplier<SoundEvent> LID_RATTLE = sound("block.brew_kettle.rattle");
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
		RECIPE_DISPLAYS.register();
		RECIPE_BOOK_CATEGORIES.register();
		DATA_COMPONENTS.register();
		PARTICLE_TYPES.register();
		SOUNDS.register();
	}
}
