package jexxme.vineandbarrel.barrel;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.drink.PlacedDrinksBlock;
import jexxme.vineandbarrel.drink.PlacedDrinksBlockEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.util.Unit;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Everything the Aging Barrel and the drinks (M3) register: the barrel and its item, the placed-drinks block (D1), the
 * {@code vineandbarrel:aging} recipe type, the barrel's data component, particles and sounds. The drink and vessel
 * items are in {@link DrinkItems}. Kept apart from the other milestones' registries so their branches merge cleanly.
 */
public final class BarrelRegistry
{
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VineAndBarrel.MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeBookCategory> RECIPE_BOOK_CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, VineAndBarrel.MODID);
	public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, VineAndBarrel.MODID);

	// ------------------------------------------------------------------ the Aging Barrel (B16: vanilla barrel's wood properties)

	public static final DeferredBlock<AgingBarrelBlock> AGING_BARREL = BLOCKS.registerBlock("aging_barrel", AgingBarrelBlock::new,
		() -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F)
			.sound(SoundType.WOOD).ignitedByLava().noOcclusion());
	/** Keeps its contents when broken (B12), so it stacks to 1 like a shulker box or FD's cooking pot; burns like a vanilla barrel. */
	public static final DeferredItem<AgingBarrelItem> AGING_BARREL_ITEM = ITEMS.registerItem("aging_barrel",
		p -> new AgingBarrelItem(AGING_BARREL.get(), p), p -> p.stacksTo(1).useBlockDescriptionPrefix()
			.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));

	public static final Supplier<BlockEntityType<AgingBarrelBlockEntity>> AGING_BARREL_BE = BLOCK_ENTITY_TYPES.register("aging_barrel",
		() -> new BlockEntityType<>(AgingBarrelBlockEntity::new, AGING_BARREL.get()));

	// ------------------------------------------------------------------ placed drinks (D1): candles and sea pickles

	public static final DeferredBlock<PlacedDrinksBlock> PLACED_DRINKS = BLOCKS.registerBlock("placed_drinks", PlacedDrinksBlock::new,
		() -> BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.1F).sound(SoundType.GLASS).noOcclusion()
			.pushReaction(PushReaction.POPPED));

	public static final Supplier<BlockEntityType<PlacedDrinksBlockEntity>> PLACED_DRINKS_BE = BLOCK_ENTITY_TYPES.register("placed_drinks",
		() -> new BlockEntityType<>(PlacedDrinksBlockEntity::new, PLACED_DRINKS.get()));

	// ------------------------------------------------------------------ recipe: base -> young -> aged, pure data

	public static final Supplier<RecipeType<AgingRecipe>> AGING = RECIPE_TYPES.register("aging",
		() -> new RecipeType<>()
		{
			@Override
			public String toString() {
				return VineAndBarrel.MODID + ":aging";
			}
		});
	public static final Supplier<RecipeSerializer<AgingRecipe>> AGING_SERIALIZER = RECIPE_SERIALIZERS.register("aging",
		() -> new RecipeSerializer<>(AgingRecipe.CODEC, AgingRecipe.STREAM_CODEC));
	/** Aging recipes are not in the recipe book (like stonecutting), but every recipe must name a category. */
	public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> AGING_CATEGORY = RECIPE_BOOK_CATEGORIES.register("aging", RecipeBookCategory::new);

	// ------------------------------------------------------------------ data component: what a barrel item holds (B12)

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<BarrelContents>> BARREL_CONTENTS = DATA_COMPONENTS.registerComponentType(
		"barrel_contents", builder -> builder.persistent(BarrelContents.CODEC).networkSynchronized(BarrelContents.STREAM_CODEC).cacheEncoding());

	/**
	 * The barrel item's batch was poured in during play (not a tavern's stock from world generation), so tapping it
	 * aged still earns Patience Pays after the barrel was moved (B12).
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> HOME_BREWED = DATA_COMPONENTS.registerComponentType(
		"home_brewed", builder -> builder.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));

	// ------------------------------------------------------------------ tags

	/** Empty vessels a hopper may feed into a barrel's side (B14). Which vessel a drink needs is the drink's own use remainder. */
	public static final TagKey<Item> DRINK_VESSELS = TagKey.create(Registries.ITEM, VineAndBarrel.id("drink_vessels"));

	// ------------------------------------------------------------------ particles (B5, B6, D2) and sounds

	public static final Supplier<SimpleParticleType> FERMENT_BUBBLE = PARTICLE_TYPES.register("ferment_bubble", () -> new SimpleParticleType(false));
	public static final Supplier<ParticleType<ColorParticleOption>> TAP_DRIP = PARTICLE_TYPES.register("tap_drip",
		() -> new ParticleType<ColorParticleOption>(false)
		{
			@Override
			public com.mojang.serialization.MapCodec<ColorParticleOption> codec() {
				return ColorParticleOption.codec(this);
			}

			@Override
			public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
				return ColorParticleOption.streamCodec(this);
			}
		});
	public static final Supplier<SimpleParticleType> CHEERS = PARTICLE_TYPES.register("cheers", () -> new SimpleParticleType(false));

	public static final Supplier<SoundEvent> BARREL_FILL = sound("block.aging_barrel.fill");
	public static final Supplier<SoundEvent> BARREL_FILL_BUCKET = sound("block.aging_barrel.fill_bucket");
	public static final Supplier<SoundEvent> BARREL_DRAIN = sound("block.aging_barrel.drain");
	public static final Supplier<SoundEvent> BARREL_DRAIN_BUCKET = sound("block.aging_barrel.drain_bucket");
	public static final Supplier<SoundEvent> BARREL_TAP = sound("block.aging_barrel.tap");
	public static final Supplier<SoundEvent> BARREL_FERMENT = sound("block.aging_barrel.ferment");
	public static final Supplier<SoundEvent> BARREL_AGED = sound("block.aging_barrel.aged");
	public static final Supplier<SoundEvent> BARREL_DRIP = sound("block.aging_barrel.drip");
	public static final Supplier<SoundEvent> CHEERS_CLINK = sound("item.drink.cheers");

	private static Supplier<SoundEvent> sound(String name) {
		return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(VineAndBarrel.id(name)));
	}

	private BarrelRegistry() {
	}

	public static void register(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		DrinkItems.ITEMS.register(modEventBus);
		BLOCK_ENTITY_TYPES.register(modEventBus);
		RECIPE_TYPES.register(modEventBus);
		RECIPE_SERIALIZERS.register(modEventBus);
		RECIPE_BOOK_CATEGORIES.register(modEventBus);
		DATA_COMPONENTS.register(modEventBus);
		PARTICLE_TYPES.register(modEventBus);
		SOUNDS.register(modEventBus);
		modEventBus.addListener(AgingBarrelBlockEntity::registerCapabilities);
		modEventBus.addListener(BarrelDispenserBehaviors::register);
	}
}
