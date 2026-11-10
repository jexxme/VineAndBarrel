package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.cauldron.BaseCauldronBlock;
import jexxme.vineandbarrel.cauldron.VBCauldrons;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * WP2: our bases in a cauldron, by water's rules. Every click goes through the real path (the block's
 * {@code use}, which asks the cauldron's interaction map), as a player's click does; the same tests run on
 * Forge and Fabric, with and without Farmer's Delight.
 */
public final class CauldronGameTests
{
	private static final ResourceLocation SMALL = VineAndBarrel.id("gametest/empty_5x5");
	private static final List<Entry> TESTS = new ArrayList<>();
	private static final BlockPos POS = new BlockPos(2, 1, 2);

	private record Entry(String name, int maxTicks, Consumer<GameTestHelper> function) {
	}

	static {
		test("cauldron_bucket_fills_and_empties", 20, CauldronGameTests::bucketFillsAndEmpties);
		test("cauldron_bottles_one_level_each", 20, CauldronGameTests::bottlesOneLevelEach);
		test("cauldron_bucket_takes_only_a_full_one", 20, CauldronGameTests::bucketTakesOnlyFull);
		test("cauldron_bottles_never_mix", 20, CauldronGameTests::bottlesNeverMix);
		test("cauldron_buckets_pour_over_like_vanilla", 20, CauldronGameTests::bucketsPourOver);
		test("cauldron_like_a_water_cauldron", 20, CauldronGameTests::likeWaterCauldron);
		test("cauldron_puts_out_fire", 100, CauldronGameTests::putsOutFire);
		test("cauldron_bucket_to_bottles_to_barrel", 20, CauldronGameTests::bucketToBottlesToBarrel);
		test("cauldron_keeps_the_leatherworker", 20, CauldronGameTests::keepsTheLeatherworker);
		test("cauldron_in_vanillas_dispatcher_list", 20, CauldronGameTests::inVanillasDispatcherList);
	}

	private CauldronGameTests() {
	}

	private static void test(String name, int timeout, Consumer<GameTestHelper> function) {
		TESTS.add(new Entry(name, timeout, function));
	}

	public static void register() {
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(CauldronGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		for (Entry test : TESTS) {
			VBGameTests.add(event, "cauldron", test.name(), SMALL, test.maxTicks(), test.function());
		}
	}

	// ------------------------------------------------------------------ helpers

	/** A right-click on the block with {@code held} in the main hand, the way the game handles it. */
	private static InteractionResult click(GameTestHelper helper, Player player, BlockPos pos, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		BlockPos absolute = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0.0, 0.5, 0.0), Direction.UP, absolute, false);
		return helper.getBlockState(pos).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
	}

	private static Player player(GameTestHelper helper) {
		return VBGameTests.makeMockPlayer(helper, GameType.SURVIVAL);
	}

	private static BlockState filled(BaseCauldronBlock cauldron, int level) {
		return cauldron.defaultBlockState().setValue(BaseCauldronBlock.LEVEL, level);
	}

	private static void assertState(GameTestHelper helper, BlockState expected, String what) {
		BlockState actual = helper.getBlockState(POS);
		helper.assertTrue(actual == expected, what + ": expected " + expected + ", got " + actual);
	}

	private static int count(Player player, Item item) {
		int n = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) n += stack.getCount();
		}
		return n;
	}

	// ------------------------------------------------------------------ the tests

	/** Every base: its bucket fills an empty cauldron to the top, an empty bucket takes it all back. */
	private static void bucketFillsAndEmpties(GameTestHelper helper) {
		Player player = player(helper);
		for (BaseCauldronBlock cauldron : VBCauldrons.all()) {
			helper.setBlock(POS, Blocks.CAULDRON);
			helper.assertTrue(click(helper, player, POS, new ItemStack(cauldron.bucket())) == InteractionResult.CONSUME, cauldron + ": the bucket pours");
			assertState(helper, filled(cauldron, BaseCauldronBlock.MAX_LEVEL), cauldron + " after the bucket");
			helper.assertTrue(player.getMainHandItem().is(Items.BUCKET) && player.getMainHandItem().getCount() == 1, "the empty bucket comes back");
			helper.assertTrue(click(helper, player, POS, new ItemStack(Items.BUCKET)) == InteractionResult.CONSUME, cauldron + ": the bucket fills");
			assertState(helper, Blocks.CAULDRON.defaultBlockState(), cauldron + " emptied");
			helper.assertTrue(player.getMainHandItem().is(cauldron.bucket()), "the base's bucket comes back: " + player.getMainHandItem());
		}
		helper.succeed();
	}

	/** Bottles: one level each, four make it full and a fifth isn't taken; empty bottles take the four back out. */
	private static void bottlesOneLevelEach(GameTestHelper helper) {
		for (BaseCauldronBlock cauldron : VBCauldrons.all()) {
			Player player = player(helper);
			helper.setBlock(POS, Blocks.CAULDRON);
			ItemStack bottles = new ItemStack(cauldron.bottle(), 5);
			for (int level = 1; level <= BaseCauldronBlock.MAX_LEVEL; level++) {
				helper.assertTrue(click(helper, player, POS, bottles) == InteractionResult.CONSUME, cauldron + ": bottle " + level + " pours");
				assertState(helper, filled(cauldron, level), cauldron + " after " + level + " bottles");
				bottles = player.getMainHandItem();
			}
			helper.assertTrue(click(helper, player, POS, bottles) == InteractionResult.PASS, cauldron + ": a full cauldron takes no fifth bottle");
			VBGameTests.assertValueEqual(helper, count(player, cauldron.bottle()), 1, cauldron + ": the fifth bottle is kept");
			VBGameTests.assertValueEqual(helper, count(player, Items.GLASS_BOTTLE), 4, cauldron + ": four empty bottles back");

			ItemStack empties = new ItemStack(Items.GLASS_BOTTLE, 4);
			player.getInventory().clearContent();
			for (int level = BaseCauldronBlock.MAX_LEVEL - 1; level >= 0; level--) {
				helper.assertTrue(click(helper, player, POS, empties) == InteractionResult.CONSUME, cauldron + ": an empty bottle fills");
				assertState(helper, level == 0 ? Blocks.CAULDRON.defaultBlockState() : filled(cauldron, level), cauldron + " down to " + level);
				empties = player.getMainHandItem();
			}
			VBGameTests.assertValueEqual(helper, count(player, cauldron.bottle()), 4, cauldron + ": four bottles of it out again");
			VBGameTests.assertValueEqual(helper, count(player, Items.GLASS_BOTTLE), 0, cauldron + ": every empty bottle used");
		}
		helper.succeed();
	}

	/** An empty bucket takes a full cauldron only, like water; below that the bucket stays empty and the cauldron as it was. */
	private static void bucketTakesOnlyFull(GameTestHelper helper) {
		Player player = player(helper);
		BaseCauldronBlock cauldron = VBCauldrons.BARLEY_WORT.get();
		for (int level = BaseCauldronBlock.MIN_LEVEL; level < BaseCauldronBlock.MAX_LEVEL; level++) {
			helper.setBlock(POS, filled(cauldron, level));
			helper.assertTrue(click(helper, player, POS, new ItemStack(Items.BUCKET)) == InteractionResult.PASS, "no bucket at level " + level);
			assertState(helper, filled(cauldron, level), "unchanged at level " + level);
			helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the bucket stays empty at level " + level);
		}
		helper.succeed();
	}

	/** A bottle goes only into an empty cauldron or its own base: not into another base, water, lava or powder snow. */
	private static void bottlesNeverMix(GameTestHelper helper) {
		Player player = player(helper);
		List<BlockState> others = List.of(filled(VBCauldrons.GRAPE_JUICE.get(), 2), Blocks.WATER_CAULDRON.defaultBlockState(),
			Blocks.LAVA_CAULDRON.defaultBlockState(), Blocks.POWDER_SNOW_CAULDRON.defaultBlockState());
		for (BlockState other : others) {
			helper.setBlock(POS, other);
			ItemStack wort = new ItemStack(BaseItems.BARLEY_WORT_BOTTLE.get());
			helper.assertTrue(click(helper, player, POS, wort) == InteractionResult.PASS, "a wort bottle on " + other + " is refused");
			assertState(helper, other, "unchanged " + other);
			helper.assertTrue(player.getMainHandItem().is(BaseItems.BARLEY_WORT_BOTTLE.get()), "the bottle is kept");
		}
		// An empty bottle on a water cauldron still gives water, not one of ours
		helper.setBlock(POS, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		click(helper, player, POS, new ItemStack(Items.GLASS_BOTTLE));
		helper.assertTrue(player.getMainHandItem().is(Items.POTION), "water stays water: " + player.getMainHandItem());
		helper.succeed();
	}

	/**
	 * Buckets pour over whatever is in a cauldron, as vanilla's do over water, lava and powder snow: ours over theirs, theirs
	 * over ours, ours over another base. Only a cauldron of the same base takes nothing, at any level: a bucket never
	 * throws servings away topping one up (bottles do that).
	 */
	private static void bucketsPourOver(GameTestHelper helper) {
		Player player = player(helper);
		BaseCauldronBlock wort = VBCauldrons.BARLEY_WORT.get();
		BaseCauldronBlock juice = VBCauldrons.GRAPE_JUICE.get();
		BaseCauldronBlock must = VBCauldrons.HONEY_MUST.get();
		record Pour(BlockState before, Item bucket, BlockState after) {
		}
		List<Pour> pours = List.of(
			new Pour(Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2), wort.bucket(), filled(wort, 4)),
			new Pour(Blocks.LAVA_CAULDRON.defaultBlockState(), juice.bucket(), filled(juice, 4)),
			new Pour(Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), must.bucket(), filled(must, 4)),
			new Pour(filled(juice, 2), must.bucket(), filled(must, 4)),
			new Pour(filled(wort, 3), Items.WATER_BUCKET, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)),
			new Pour(filled(juice, 4), Items.LAVA_BUCKET, Blocks.LAVA_CAULDRON.defaultBlockState()),
			new Pour(filled(must, 1), Items.POWDER_SNOW_BUCKET, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)));
		for (Pour pour : pours) {
			helper.setBlock(POS, pour.before());
			helper.assertTrue(click(helper, player, POS, new ItemStack(pour.bucket())) == InteractionResult.CONSUME, pour.bucket() + " into " + pour.before());
			assertState(helper, pour.after(), pour.bucket() + " into " + pour.before());
			helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the empty bucket comes back from " + pour.bucket());
		}
		// The same base into a cauldron of it, at any level: nothing happens, the bucket stays full
		for (int level = BaseCauldronBlock.MIN_LEVEL; level <= BaseCauldronBlock.MAX_LEVEL; level++) {
			helper.setBlock(POS, filled(wort, level));
			helper.assertTrue(click(helper, player, POS, new ItemStack(wort.bucket())) == InteractionResult.PASS,
				"a cauldron of wort at level " + level + " takes no bucket of wort");
			assertState(helper, filled(wort, level), "unchanged at level " + level);
			helper.assertTrue(player.getMainHandItem().is(wort.bucket()), "the bucket of wort is kept at level " + level);
		}
		helper.succeed();
	}

	/**
	 * Everything else a water cauldron does: a comparator reads the level, it breaks into a plain cauldron (pickaxe), is
	 * picked as one, is a cauldron by tag, takes no rain, is named after its base and coloured like it, and has no item.
	 */
	private static void likeWaterCauldron(GameTestHelper helper) {
		BlockPos absolute = helper.absolutePos(POS);
		Player player = player(helper);
		for (BaseCauldronBlock cauldron : VBCauldrons.all()) {
			for (int level = BaseCauldronBlock.MIN_LEVEL; level <= BaseCauldronBlock.MAX_LEVEL; level++) {
				helper.setBlock(POS, filled(cauldron, level));
				BlockState state = helper.getBlockState(POS);
				VBGameTests.assertValueEqual(helper, state.getAnalogOutputSignal(helper.getLevel(), absolute), level, cauldron + " comparator at " + level);
				state.getBlock().handlePrecipitation(state, helper.getLevel(), absolute, Biome.Precipitation.RAIN);
				assertState(helper, state, cauldron + " takes no rain");
			}
			BlockState state = helper.getBlockState(POS);
			List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), absolute, null, player, new ItemStack(Items.IRON_PICKAXE));
			helper.assertTrue(drops.size() == 1 && drops.get(0).is(Items.CAULDRON) && drops.get(0).getCount() == 1, cauldron + " drops a cauldron: " + drops);
			helper.assertTrue(state.getBlock().getCloneItemStack(helper.getLevel(), absolute, state).is(Items.CAULDRON), cauldron + " is picked as a cauldron");
			helper.assertTrue(state.is(BlockTags.CAULDRONS), cauldron + " is in #minecraft:cauldrons");
			helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), cauldron + " is mined with a pickaxe");
			helper.assertTrue(cauldron.asItem() == Items.AIR, cauldron + " has no item of its own");
			String base = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cauldron).getPath().replace("_cauldron", "");
			VBGameTests.assertValueEqual(helper, cauldron.getDescriptionId(), "block.vineandbarrel." + base + "_cauldron", "the cauldron's name");
			helper.assertTrue(VBCauldrons.color(cauldron) != -1 && VBCauldrons.color(cauldron) == BaseItems.liquidColor(new ItemStack(cauldron.bucket())),
				cauldron + " has its base's colour");
		}
		helper.succeed();
	}

	/** A burning mob in it is put out and uses up a level, as in a water cauldron. */
	private static void putsOutFire(GameTestHelper helper) {
		BaseCauldronBlock cauldron = VBCauldrons.APPLE_JUICE.get();
		helper.setBlock(POS, filled(cauldron, 2));
		// on the cauldron's floor, in the liquid
		Pig pig = helper.spawn(EntityType.PIG, new Vec3(POS.getX() + 0.5, POS.getY() + 0.25, POS.getZ() + 0.5));
		pig.setSecondsOnFire(10);
		helper.succeedWhen(() -> {
			helper.assertFalse(pig.isOnFire(), "the pig is put out");
			assertState(helper, filled(cauldron, 1), "putting it out used a level");
		});
	}

	/** The point of it: a kettle's bucket poured in, taken out as four bottles, and all four go into the barrel. */
	private static void bucketToBottlesToBarrel(GameTestHelper helper) {
		Player player = player(helper);
		helper.setBlock(POS, Blocks.CAULDRON);
		click(helper, player, POS, new ItemStack(BaseItems.WHEAT_WORT_BUCKET.get()));
		ItemStack empties = new ItemStack(Items.GLASS_BOTTLE, 4);
		for (int i = 0; i < 4; i++) {
			click(helper, player, POS, empties);
			empties = player.getMainHandItem();
		}
		assertState(helper, Blocks.CAULDRON.defaultBlockState(), "the cauldron is empty again");
		VBGameTests.assertValueEqual(helper, count(player, BaseItems.WHEAT_WORT_BOTTLE.get()), 4, "four bottles of wort");
		BlockPos barrelPos = new BlockPos(1, 1, 1);
		helper.setBlock(barrelPos, BarrelRegistry.AGING_BARREL.get().defaultBlockState().setValue(AgingBarrelBlock.FACING, Direction.NORTH));
		AgingBarrelBlockEntity barrel = VBGameTests.blockEntity(helper, barrelPos, AgingBarrelBlockEntity.class);
		for (int i = 0; i < 4; i++) {
			helper.assertTrue(barrel.pour(new ItemStack(BaseItems.WHEAT_WORT_BOTTLE.get()), null) != null, "the barrel takes bottle " + (i + 1));
		}
		VBGameTests.assertValueEqual(helper, barrel.servings(), 4, "four servings in the barrel");
		helper.succeed();
	}

	/**
	 * Every state of every base's cauldron is a leatherworker's job site, like every vanilla cauldron: filling the
	 * leatherworker's water cauldron with wort, or emptying it again with bottles, never takes his job away.
	 */
	private static void keepsTheLeatherworker(GameTestHelper helper) {
		for (BaseCauldronBlock cauldron : VBCauldrons.all()) {
			for (BlockState state : cauldron.getStateDefinition().getPossibleStates()) {
				helper.assertTrue(net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state)
					.filter(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.LEATHERWORKER)).isPresent(), state + " is a leatherworker's job site");
			}
		}
		net.minecraft.world.entity.ai.village.poi.PoiManager pois = helper.getLevel().getPoiManager();
		BlockPos absolute = helper.absolutePos(POS);
		java.util.function.Supplier<Boolean> leatherworker = () -> pois.getType(absolute)
			.filter(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.LEATHERWORKER)).isPresent();
		Player player = player(helper);
		helper.setBlock(POS, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		helper.assertTrue(leatherworker.get(), "the water cauldron is a job site");
		click(helper, player, POS, new ItemStack(BaseItems.BARLEY_WORT_BUCKET.get()));
		assertState(helper, filled(VBCauldrons.BARLEY_WORT.get(), BaseCauldronBlock.MAX_LEVEL), "filled with wort");
		helper.assertTrue(leatherworker.get(), "still a job site with wort in it");
		ItemStack empties = new ItemStack(Items.GLASS_BOTTLE, 4);
		for (int i = 0; i < 4; i++) {
			click(helper, player, POS, empties);
			empties = player.getMainHandItem();
			helper.assertTrue(leatherworker.get(), "still a job site after bottle " + (i + 1));
		}
		assertState(helper, Blocks.CAULDRON.defaultBlockState(), "empty again");
		helper.succeed();
	}

	/**
	 * 1.20.1 has no list of every cauldron's interaction map (1.21.1: {@code CauldronInteraction.INTERACTIONS}, whose codec
	 * names them), so there is nothing of vanilla's to be listed in, and a mod can reach "every cauldron" only through
	 * vanilla's four fixed maps. What remains true is asserted here: each of our cauldrons has an interaction map of its
	 * own, which is the one its clicks go through; it holds vanilla's defaults (every vanilla bucket pours over it,
	 * {@code CauldronInteraction.addDefaultInteractions}) and our own entries (its bottle, every base's bucket, the empty
	 * bottle and the empty bucket); and vanilla's four maps hold our buckets, the empty cauldron's also our bottles.
	 */
	private static void inVanillasDispatcherList(GameTestHelper helper) {
		List<Map<Item, CauldronInteraction>> vanilla = List.of(CauldronInteraction.EMPTY, CauldronInteraction.WATER, CauldronInteraction.LAVA,
			CauldronInteraction.POWDER_SNOW);
		Set<Map<Item, CauldronInteraction>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Player player = player(helper);
		for (BaseCauldronBlock cauldron : VBCauldrons.all()) {
			String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cauldron).toString();
			Map<Item, CauldronInteraction> map = cauldron.interactions();
			helper.assertTrue(map != null && seen.add(map), name + " has an interaction map of its own");
			helper.assertTrue(vanilla.stream().noneMatch(other -> other == map), name + "'s interaction map is none of vanilla's");
			// vanilla's defaults, the same entries as in vanilla's own maps
			for (Item bucket : List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.POWDER_SNOW_BUCKET)) {
				helper.assertTrue(map.containsKey(bucket), name + "'s interaction map has vanilla's " + bucket);
				helper.assertTrue(map.get(bucket) == CauldronInteraction.WATER.get(bucket), name + " takes a " + bucket + " exactly as vanilla's cauldrons do");
			}
			// our own entries
			helper.assertTrue(map.containsKey(cauldron.bottle()) && map.containsKey(Items.GLASS_BOTTLE) && map.containsKey(Items.BUCKET),
				name + "'s interaction map has its bottle, the empty bottle and the empty bucket");
			for (BaseCauldronBlock other : VBCauldrons.all()) {
				helper.assertTrue(map.containsKey(other.bucket()), name + "'s interaction map has the " + other.bucket());
				if (other != cauldron) {
					helper.assertFalse(map.containsKey(other.bottle()), name + "'s interaction map has no bottle of another base: " + other.bottle());
				}
			}
			// vanilla's own maps know our bucket (it pours over water, lava and powder snow), the empty cauldron's also our bottle
			for (Map<Item, CauldronInteraction> theirs : vanilla) {
				helper.assertTrue(theirs.containsKey(cauldron.bucket()), "vanilla's cauldrons take the " + cauldron.bucket());
			}
			helper.assertTrue(CauldronInteraction.EMPTY.containsKey(cauldron.bottle()), "vanilla's empty cauldron takes the " + cauldron.bottle());
			// the map is the one the block's clicks go through: an entry put into it is what a click with that item does
			helper.setBlock(POS, filled(cauldron, 2));
			helper.assertFalse(map.containsKey(Items.STICK), name + " has no entry for a stick");
			helper.assertTrue(click(helper, player, POS, new ItemStack(Items.STICK)) == InteractionResult.PASS, name + " has no use for a stick");
			map.put(Items.STICK, (state, level, pos, clicker, hand, held) -> InteractionResult.CONSUME);
			try {
				helper.assertTrue(click(helper, player, POS, new ItemStack(Items.STICK)) == InteractionResult.CONSUME, name + "'s clicks go through its interaction map");
			} finally {
				map.remove(Items.STICK);
			}
			assertState(helper, filled(cauldron, 2), name + " unchanged");
		}
		helper.succeed();
	}
}
