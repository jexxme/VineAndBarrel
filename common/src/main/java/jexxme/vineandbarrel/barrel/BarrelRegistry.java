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
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.util.Unit;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import jexxme.vineandbarrel.platform.registry.DeferredHolder;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

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
	public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, VineAndBarrel.MODID);

	// ------------------------------------------------------------------ the Aging Barrel (B16: vanilla barrel's wood properties)

	public static final DeferredBlock<AgingBarrelBlock> AGING_BARREL = BLOCKS.registerBlock("aging_barrel", AgingBarrelBlock::new,
		() -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.5F)
			.sound(SoundType.WOOD).ignitedByLava().noOcclusion());
	/** Keeps its contents when broken (B12), so it stacks to 1 like a shulker box or FD's cooking pot; burns like a vanilla barrel. */
	public static final DeferredItem<AgingBarrelItem> AGING_BARREL_ITEM = ITEMS.registerItem("aging_barrel",
		p -> new AgingBarrelItem(AGING_BARREL.get(), p), p -> p.stacksTo(1)); // fuel: AgingBarrelItem#getBurnTime

	public static final Supplier<BlockEntityType<AgingBarrelBlockEntity>> AGING_BARREL_BE = BLOCK_ENTITY_TYPES.register("aging_barrel",
		() -> BlockEntityType.Builder.of(AgingBarrelBlockEntity::new, AGING_BARREL.get()).build(null));

	// ------------------------------------------------------------------ placed drinks (D1): candles and sea pickles

	public static final DeferredBlock<PlacedDrinksBlock> PLACED_DRINKS = BLOCKS.registerBlock("placed_drinks", PlacedDrinksBlock::new,
		() -> BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.1F).sound(SoundType.GLASS).noOcclusion()
			.pushReaction(PushReaction.DESTROY));

	public static final Supplier<BlockEntityType<PlacedDrinksBlockEntity>> PLACED_DRINKS_BE = BLOCK_ENTITY_TYPES.register("placed_drinks",
		() -> BlockEntityType.Builder.of(PlacedDrinksBlockEntity::new, PLACED_DRINKS.get()).build(null));

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
		() -> new RecipeSerializer<>()
		{
			@Override
			public com.mojang.serialization.MapCodec<AgingRecipe> codec() {
				return AgingRecipe.CODEC;
			}

			@Override
			public StreamCodec<RegistryFriendlyByteBuf, AgingRecipe> streamCodec() {
				return AgingRecipe.STREAM_CODEC;
			}
		});
	// Aging recipes are not in the recipe book (like stonecutting); 1.21.1 has no recipe book category registry to name one in

	// ------------------------------------------------------------------ data component: what a barrel item holds (B12)

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<BarrelContents>> BARREL_CONTENTS = DATA_COMPONENTS.registerComponentType(
		"barrel_contents", builder -> builder.persistent(BarrelContents.CODEC).networkSynchronized(BarrelContents.STREAM_CODEC).cacheEncoding());

	/**
	 * The barrel item's batch was poured in during play (not a tavern's stock from world generation), so tapping it
	 * aged still earns Patience Pays after the barrel was moved (B12).
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> HOME_BREWED = DATA_COMPONENTS.registerComponentType(
		"home_brewed", builder -> builder.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

	// ------------------------------------------------------------------ tags

	/** Empty vessels a hopper may feed into a barrel's side (B14). Which vessel a drink needs is what the drink's own food converts to. */
	public static final TagKey<Item> DRINK_VESSELS = TagKey.create(Registries.ITEM, VineAndBarrel.id("drink_vessels"));

	// ------------------------------------------------------------------ particles (B5, B6, D2) and sounds

	public static final Supplier<SimpleParticleType> FERMENT_BUBBLE = PARTICLE_TYPES.register("ferment_bubble", () -> new SimpleParticleType(false) {});
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
	public static final Supplier<SimpleParticleType> CHEERS = PARTICLE_TYPES.register("cheers", () -> new SimpleParticleType(false) {});

	public static final Supplier<SoundEvent> BARREL_FILL = sound("block.aging_barrel.fill");
	public static final Supplier<SoundEvent> BARREL_FILL_BUCKET = sound("block.aging_barrel.fill_bucket");
	public static final Supplier<SoundEvent> BARREL_DRAIN = sound("block.aging_barrel.drain");
	public static final Supplier<SoundEvent> BARREL_DRAIN_BUCKET = sound("block.aging_barrel.drain_bucket");
	public static final Supplier<SoundEvent> BARREL_TAP = sound("block.aging_barrel.tap");
	public static final Supplier<SoundEvent> BARREL_FERMENT = sound("block.aging_barrel.ferment");
	public static final Supplier<SoundEvent> BARREL_AGED = sound("block.aging_barrel.aged");
	public static final Supplier<SoundEvent> BARREL_DRIP = sound("block.aging_barrel.drip");
	/** D2: wine glasses ring, mugs clunk; a mug and a glass together play both. */
	public static final Supplier<SoundEvent> CHEERS_GLASS = sound("item.drink.cheers_glass");
	public static final Supplier<SoundEvent> CHEERS_MUG = sound("item.drink.cheers_mug");
	/** Drinking: vanilla's drink sound, deeper from a mug, lighter from a wine glass. */
	public static final Holder<SoundEvent> MUG_DRINK = SOUNDS.register("item.mug.drink",
		() -> SoundEvent.createVariableRangeEvent(VineAndBarrel.id("item.mug.drink"))).asHolder();
	public static final Holder<SoundEvent> WINE_GLASS_DRINK = SOUNDS.register("item.wine_glass.drink",
		() -> SoundEvent.createVariableRangeEvent(VineAndBarrel.id("item.wine_glass.drink"))).asHolder();

	private static Supplier<SoundEvent> sound(String name) {
		return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(VineAndBarrel.id(name)));
	}

	private BarrelRegistry() {
	}

	public static void register() {
		BLOCKS.register();
		ITEMS.register();
		DrinkItems.ITEMS.register();
		BLOCK_ENTITY_TYPES.register();
		RECIPE_TYPES.register();
		RECIPE_SERIALIZERS.register();
		DATA_COMPONENTS.register();
		PARTICLE_TYPES.register();
		SOUNDS.register();
	}
}
