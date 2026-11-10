package jexxme.vineandbarrel.barrel;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.drink.PlacedDrinksBlock;
import jexxme.vineandbarrel.drink.PlacedDrinksBlockEntity;
import jexxme.vineandbarrel.platform.Services;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import jexxme.vineandbarrel.platform.registry.DeferredBlock;
import jexxme.vineandbarrel.platform.registry.DeferredItem;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.function.Supplier;

/**
 * Everything the Aging Barrel and the drinks (M3) register: the barrel and its item, the placed-drinks block (D1), the
 * {@code vineandbarrel:aging} recipe type, particles and sounds (1.20.1 has no data components: what a barrel item
 * holds is in its NBT, see {@link AgingBarrelItem}). The drink and vessel
 * items are in {@link DrinkItems}. Kept apart from the other milestones' registries so their branches merge cleanly.
 */
public final class BarrelRegistry
{
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VineAndBarrel.MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VineAndBarrel.MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, VineAndBarrel.MODID);
	public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, VineAndBarrel.MODID);
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
		() -> Services.PLATFORM.blockEntityType(AgingBarrelBlockEntity::new, AGING_BARREL.get()));

	// ------------------------------------------------------------------ placed drinks (D1): candles and sea pickles

	public static final DeferredBlock<PlacedDrinksBlock> PLACED_DRINKS = BLOCKS.registerBlock("placed_drinks", PlacedDrinksBlock::new,
		() -> BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.1F).sound(SoundType.GLASS).noOcclusion()
			.pushReaction(PushReaction.DESTROY));

	public static final Supplier<BlockEntityType<PlacedDrinksBlockEntity>> PLACED_DRINKS_BE = BLOCK_ENTITY_TYPES.register("placed_drinks",
		() -> Services.PLATFORM.blockEntityType(PlacedDrinksBlockEntity::new, PLACED_DRINKS.get()));

	// ------------------------------------------------------------------ recipe: base -> young -> aged, pure data

	public static final Supplier<RecipeType<AgingRecipe>> AGING = RECIPE_TYPES.register("aging",
		() -> new RecipeType<>()
		{
			@Override
			public String toString() {
				return VineAndBarrel.MODID + ":aging";
			}
		});
	public static final Supplier<RecipeSerializer<AgingRecipe>> AGING_SERIALIZER = RECIPE_SERIALIZERS.register("aging", AgingRecipe.Serializer::new);
	// Aging recipes are not in the recipe book (like stonecutting); 1.20.1 has no recipe book category registry to name one in

	// What a barrel item holds (B12), and that its batch is home brewed: 1.20.1 has no data components, so both are in
	// the item's NBT, behind AgingBarrelItem.contentsOf / setContents and isHomeBrewed / setHomeBrewed

	// ------------------------------------------------------------------ tags

	/** Empty vessels a hopper may feed into a barrel's side (B14). Which vessel a drink needs is the drink's own ({@link AgingBarrelBlockEntity#vesselOf}). */
	public static final TagKey<Item> DRINK_VESSELS = TagKey.create(Registries.ITEM, VineAndBarrel.id("drink_vessels"));

	// ------------------------------------------------------------------ particles (B5, B6, D2) and sounds

	public static final Supplier<SimpleParticleType> FERMENT_BUBBLE = PARTICLE_TYPES.register("ferment_bubble", () -> new SimpleParticleType(false) {});
	public static final Supplier<ParticleType<ColorParticleOption>> TAP_DRIP = PARTICLE_TYPES.register("tap_drip",
		TapDripType::new);

	/**
	 * A named class, not an anonymous one: an anonymous class takes over the names of the parameters of the constructor
	 * it calls, and in the Minecraft jar a build server compiles against both of this one's share a name.
	 */
	private static final class TapDripType extends ParticleType<ColorParticleOption>
	{
		TapDripType() {
			super(false, ColorParticleOption.DESERIALIZER);
		}

		@Override
		public com.mojang.serialization.Codec<ColorParticleOption> codec() {
			return ColorParticleOption.codec(this);
		}
	}

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
	public static final Supplier<SoundEvent> MUG_DRINK = sound("item.mug.drink");
	public static final Supplier<SoundEvent> WINE_GLASS_DRINK = sound("item.wine_glass.drink");

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
		PARTICLE_TYPES.register();
		SOUNDS.register();
	}
}
