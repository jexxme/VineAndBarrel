package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.advancement.TappedBarrelTrigger;
import jexxme.vineandbarrel.advancement.VBAdvancementGenerator;
import jexxme.vineandbarrel.advancement.VBAdvancements;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.barrel.BarrelStage;
import jexxme.vineandbarrel.drink.DrankTogether;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.KettleContents;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.tipsy.Tipsy;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import jexxme.vineandbarrel.platform.GameTestRegistrar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * M7 advancements (W2): the tab loads as generated, and every advancement is earned by the real gameplay path (kettle
 * use and menu, barrel taps, drinking, placing barrels, Cheers, sleeping) by a real server player, and not by the
 * near misses (a young drink, a barrel on the ground, a sober night). Headless ({@code ./gradlew runGameTestServer}).
 * <p>
 * A village can't be generated in a test structure, so On Tap's village check is covered at the trigger: the trigger
 * awards On Tap for a village barrel only, and a real tap outside a village earns Patience Pays but not On Tap.
 */
public final class AdvancementGameTests
{
	private static final ResourceLocation STRUCTURE = VineAndBarrel.id("gametest/empty_5x5");
	private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();
	private static final BlockPos AT = new BlockPos(2, 1, 2);

	static {
		test("advancements_tab_loads", AdvancementGameTests::tabLoads);
		test("advancement_first_brew_by_hand_and_menu", AdvancementGameTests::firstBrew);
		test("advancement_patience_pays_needs_aged_tap", AdvancementGameTests::patiencePays);
		test("advancement_patience_pays_survives_breaking", AdvancementGameTests::patiencePaysAfterBreaking);
		test("advancement_on_tap_needs_village_barrel", AdvancementGameTests::onTap);
		test("advancement_vintner_and_brewmaster", AdvancementGameTests::vintnerAndBrewmaster);
		test("advancement_mead_hall_and_cider_house", AdvancementGameTests::meadHallAndCiderHouse);
		test("advancement_cellar_master_needs_stack", AdvancementGameTests::cellarMaster);
		test("advancement_cheers_for_everyone", AdvancementGameTests::cheers);
		test("advancement_sleep_it_off_needs_tipsy", AdvancementGameTests::sleepItOff);
	}

	private AdvancementGameTests() {
	}

	private static void test(String name, Consumer<GameTestHelper> function) {
		TESTS.put(name, function);
	}

	public static void register() {
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(AdvancementGameTests::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		TESTS.forEach((name, function) -> VBGameTests.add(event, "advancements", name, STRUCTURE, 100, function));
	}

	// ------------------------------------------------------------------ helpers

	/** Runs {@code body} with a real server player in the level (it has advancement progress), removed afterwards. */
	private static void withPlayer(GameTestHelper helper, BiConsumer<GameTestHelper, ServerPlayer> body) {
		@SuppressWarnings("removal")
		ServerPlayer player = VBGameTests.mockServerPlayerInLevel(helper);
		try {
			body.accept(helper, player);
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}

	private static AdvancementHolder advancement(GameTestHelper helper, String name) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(VBAdvancementGenerator.id(name));
		helper.assertTrue(holder != null, "advancement " + name + " is loaded");
		return holder;
	}

	private static boolean done(GameTestHelper helper, ServerPlayer player, String name) {
		return player.getAdvancements().getOrStartProgress(advancement(helper, name)).isDone();
	}

	private static void assertDone(GameTestHelper helper, ServerPlayer player, String name, boolean expected, String when) {
		helper.assertValueEqual(done(helper, player, name), expected, name + " " + when);
	}

	/** Uses the held item on the block at {@code pos}, the way a right-click does. */
	private static void use(GameTestHelper helper, ServerPlayer player, ItemStack held, BlockPos pos) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		BlockPos absolute = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
		helper.getLevel().getBlockState(absolute).useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
	}

	private static AgingBarrelBlockEntity wineBarrel(GameTestHelper helper, BlockPos pos, long age) {
		helper.setBlock(pos, BarrelRegistry.AGING_BARREL.get());
		AgingBarrelBlockEntity barrel = VBGameTests.blockEntity(helper, pos, AgingBarrelBlockEntity.class);
		helper.assertTrue(barrel.pour(new ItemStack(BaseItems.GRAPE_JUICE_BUCKET.get()), null) != null, "the barrel takes a bucket of grape juice");
		barrel.setAge(age);
		return barrel;
	}

	// ------------------------------------------------------------------ the tab

	/** All nine are loaded, in one tab under our root (with its background), with the frames FD uses. */
	private static void tabLoads(GameTestHelper helper) {
		AdvancementHolder root = advancement(helper, "root");
		DisplayInfo rootDisplay = root.value().display().orElseThrow();
		helper.assertTrue(rootDisplay.getBackground().isPresent(), "the root has a tab background");
		helper.assertTrue(root.value().parent().isEmpty(), "the root starts its own tab");
		for (String name : VBAdvancementGenerator.ALL) {
			AdvancementHolder holder = advancement(helper, name);
			helper.assertTrue(holder.value().display().isPresent(), name + " is shown");
			if (!name.equals("root")) {
				ResourceLocation parent = holder.value().parent().orElseThrow();
				helper.assertTrue(parent.getNamespace().equals(VineAndBarrel.MODID), name + " sits in our tab");
			}
		}
		helper.assertValueEqual(advancement(helper, "vintner").value().display().orElseThrow().getType(), AdvancementType.CHALLENGE, "Vintner frame");
		helper.assertValueEqual(advancement(helper, "patience_pays").value().display().orElseThrow().getType(), AdvancementType.GOAL, "Patience Pays frame");
		helper.succeed();
	}

	// ------------------------------------------------------------------ brewing branch

	/** First Brew: filling a bottle or a bucket at the kettle by hand. */
	private static void firstBrew(GameTestHelper helper) {
		helper.setBlock(AT, KettleRegistry.kettle());
		BrewKettleBlockEntity kettle = VBGameTests.blockEntity(helper, AT, BrewKettleBlockEntity.class);
		ItemStack item = new ItemStack(KettleRegistry.kettleItem());
		item.set(KettleRegistry.KETTLE_CONTENTS.get(), new KettleContents(new ItemStack(BaseItems.GRAPE_JUICE.get(), 4),
			new ItemStack(BaseItems.GRAPE_JUICE_BUCKET.get())));
		kettle.applyComponentsFromItemStack(item);

		withPlayer(helper, (h, byHand) -> {
			assertDone(h, byHand, "first_brew", false, "before brewing");
			use(h, byHand, new ItemStack(Items.GLASS_BOTTLE), AT);
			helper.assertValueEqual(kettle.getServings(), 3, "the bottle took a serving");
			assertDone(h, byHand, "first_brew", true, "after bottling a serving by hand");
		});
		withPlayer(helper, (h, byBucket) -> {
			kettle.serveInto(new ItemStack(Items.GLASS_BOTTLE), null);
			helper.assertValueEqual(kettle.getServings(), 2, "two servings left");
			use(h, byBucket, new ItemStack(Items.BUCKET), AT);
			assertDone(h, byBucket, "first_brew", false, "a bucket needs four servings");
			kettle.setStack(BrewKettleBlockEntity.BREW_SLOT, new ItemStack(BaseItems.GRAPE_JUICE.get(), 4));
			use(h, byBucket, new ItemStack(Items.BUCKET), AT);
			helper.assertValueEqual(kettle.getServings(), 0, "the bucket took four servings");
			assertDone(h, byBucket, "first_brew", true, "after filling a bucket by hand");
		});
		helper.succeed();
	}

	/** Patience Pays: tapping the aged drink; a young one doesn't count. Outside a village, so no On Tap. */
	private static void patiencePays(GameTestHelper helper) {
		AgingBarrelBlockEntity barrel = wineBarrel(helper, AT, 24000L + 100L);
		withPlayer(helper, (h, player) -> {
			helper.assertValueEqual(barrel.stage(), BarrelStage.YOUNG, "stage");
			use(h, player, new ItemStack(DrinkItems.WINE_GLASS.get()), AT);
			helper.assertValueEqual(barrel.servings(), 3, "a young serving was tapped");
			assertDone(h, player, "patience_pays", false, "after tapping young wine");

			barrel.setAge(4L * 24000L + 100L);
			helper.assertValueEqual(barrel.stage(), BarrelStage.AGED, "stage");
			use(h, player, new ItemStack(DrinkItems.WINE_GLASS.get()), AT);
			helper.assertValueEqual(barrel.servings(), 2, "an aged serving was tapped");
			helper.assertTrue(barrel.isHomeBrewed(), "a poured batch is home-brewed");
			assertDone(h, player, "patience_pays", true, "after tapping aged wine");
			assertDone(h, player, "on_tap", false, "for a barrel outside a village");
		});
		helper.succeed();
	}

	/** Patience Pays after the barrel was broken (survival drop, through the loot table), placed again and tapped once aged. */
	private static void patiencePaysAfterBreaking(GameTestHelper helper) {
		wineBarrel(helper, AT, 100L);
		helper.getLevel().destroyBlock(helper.absolutePos(AT), true);
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(AT)).inflate(2.0D));
		ItemStack dropped = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(BarrelRegistry.AGING_BARREL_ITEM.get()))
				.findFirst().orElseThrow(() -> new IllegalStateException("the broken barrel drops itself")).copy();
		drops.forEach(ItemEntity::discard);
		helper.assertTrue(dropped.has(BarrelRegistry.HOME_BREWED.get()), "the dropped barrel stays home-brewed");
		withPlayer(helper, (h, player) -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, dropped);
			helper.placeAt(player, dropped, AT.below(), Direction.UP);
			AgingBarrelBlockEntity barrel = VBGameTests.blockEntity(helper, AT, AgingBarrelBlockEntity.class);
			helper.assertTrue(barrel.isHomeBrewed(), "the placed barrel is home-brewed");
			barrel.setAge(4L * 24000L + 100L);
			helper.assertValueEqual(barrel.stage(), BarrelStage.AGED, "stage");
			use(h, player, new ItemStack(DrinkItems.WINE_GLASS.get()), AT);
			assertDone(h, player, "patience_pays", true, "after tapping the placed barrel's aged wine");
		});
		helper.succeed();
	}

	/**
	 * On Tap: any tap from a village barrel (young or aged). Patience Pays never comes from a tavern's own stock, but
	 * does from a batch the player aged themselves, also inside a village house.
	 */
	private static void onTap(GameTestHelper helper) {
		TappedBarrelTrigger.TriggerInstance onTap = new TappedBarrelTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.of(true), Optional.empty());
		helper.assertTrue(onTap.matches(false, true, false) && onTap.matches(true, true, true), "On Tap matches any village tap");
		helper.assertFalse(onTap.matches(true, false, true), "On Tap doesn't match a barrel outside a village");
		withPlayer(helper, (h, player) -> {
			VBAdvancements.TAPPED_BARREL.get().trigger(player, true, true, false);
			assertDone(h, player, "on_tap", true, "after tapping a village barrel");
			assertDone(h, player, "patience_pays", false, "after tapping a village tavern's aged stock");
			VBAdvancements.TAPPED_BARREL.get().trigger(player, true, true, true);
			assertDone(h, player, "patience_pays", true, "after tapping an own aged batch inside a village house");
		});
		helper.succeed();
	}

	/** Vintner and Brewmaster: every wine (or beer) of the base game, young and aged, drunk the way the game does it. */
	private static void vintnerAndBrewmaster(GameTestHelper helper) {
		withPlayer(helper, (h, player) -> {
			List<Item> wines = List.of(DrinkItems.RED_WINE.get(), DrinkItems.AGED_RED_WINE.get(), DrinkItems.SWEET_BERRY_WINE.get(),
				DrinkItems.AGED_SWEET_BERRY_WINE.get(), DrinkItems.WHITE_WINE.get(), DrinkItems.AGED_WHITE_WINE.get());
			for (int i = 0; i < wines.size(); i++) {
				assertDone(h, player, "vintner", false, "after " + i + " of the wines");
				new ItemStack(wines.get(i)).finishUsingItem(helper.getLevel(), player);
			}
			assertDone(h, player, "vintner", true, "after every wine");
			assertDone(h, player, "brewmaster", false, "after only wines");
			new ItemStack(DrinkItems.HARD_CIDER.get()).finishUsingItem(helper.getLevel(), player);
			for (Item beer : List.of(DrinkItems.BARLEY_ALE.get(), DrinkItems.AGED_BARLEY_ALE.get(), DrinkItems.WHEAT_BEER.get())) {
				new ItemStack(beer).finishUsingItem(helper.getLevel(), player);
			}
			assertDone(h, player, "brewmaster", false, "with one beer missing (cider is no beer)");
			new ItemStack(DrinkItems.AGED_WHEAT_BEER.get()).finishUsingItem(helper.getLevel(), player);
			assertDone(h, player, "brewmaster", true, "after every beer");
		});
		helper.succeed();
	}

	/** Mead Hall and Cider House: each needs its drink young and aged; neither counts toward the other or toward Vintner. */
	private static void meadHallAndCiderHouse(GameTestHelper helper) {
		withPlayer(helper, (h, player) -> {
			new ItemStack(DrinkItems.MEAD.get()).finishUsingItem(helper.getLevel(), player);
			assertDone(h, player, "mead_hall", false, "after young mead only");
			new ItemStack(DrinkItems.AGED_HARD_CIDER.get()).finishUsingItem(helper.getLevel(), player);
			assertDone(h, player, "cider_house", false, "after aged hard cider only");
			new ItemStack(DrinkItems.AGED_MEAD.get()).finishUsingItem(helper.getLevel(), player);
			assertDone(h, player, "mead_hall", true, "after mead, young and aged");
			assertDone(h, player, "cider_house", false, "mead is no cider");
			new ItemStack(DrinkItems.HARD_CIDER.get()).finishUsingItem(helper.getLevel(), player);
			assertDone(h, player, "cider_house", true, "after hard cider, young and aged");
			assertDone(h, player, "vintner", false, "mead and cider are no wines");
		});
		helper.assertValueEqual(advancement(helper, "mead_hall").value().parent().orElseThrow(), VBAdvancementGenerator.id("patience_pays"), "Mead Hall's parent");
		helper.assertValueEqual(advancement(helper, "cider_house").value().parent().orElseThrow(), VBAdvancementGenerator.id("patience_pays"), "Cider House's parent");
		helper.succeed();
	}

	/** Cellar Master: placing a barrel on top of another one; one on the ground doesn't count. */
	private static void cellarMaster(GameTestHelper helper) {
		withPlayer(helper, (h, player) -> {
			placeBarrel(h, player, AT.below());
			helper.assertFalse(helper.getBlockState(AT).getValue(AgingBarrelBlock.STACKED), "the first barrel stands on the ground");
			assertDone(h, player, "cellar_master", false, "after placing a barrel on the ground");
			placeBarrel(h, player, AT);
			helper.assertTrue(helper.getBlockState(AT.above()).getValue(AgingBarrelBlock.STACKED), "the second barrel rests on the first");
			assertDone(h, player, "cellar_master", true, "after stacking a barrel");
		});
		helper.succeed();
	}

	private static void placeBarrel(GameTestHelper helper, ServerPlayer player, BlockPos below) {
		ItemStack stack = new ItemStack(BarrelRegistry.AGING_BARREL_ITEM.get());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		helper.placeAt(player, stack, below, Direction.UP);
	}

	// ------------------------------------------------------------------ tavern branch

	/** Cheers!: everyone who drank together gets it (the D2 hook); nobody else. */
	private static void cheers(GameTestHelper helper) {
		withPlayer(helper, (h, a) -> withPlayer(h, (h2, b) -> withPlayer(h2, (h3, bystander) -> {
			DrankTogether.post(new DrankTogether(helper.getLevel(), List.of(a, b)));
			assertDone(h3, a, "cheers", true, "for the first drinker");
			assertDone(h3, b, "cheers", true, "for the second drinker");
			assertDone(h3, bystander, "cheers", false, "for someone who didn't drink");
		})));
		helper.succeed();
	}

	/** Sleep It Off: waking up after a night slept through while Tipsy; a sober night or leaving the bed early doesn't count. */
	private static void sleepItOff(GameTestHelper helper) {
		withPlayer(helper, (h, player) -> {
			player.stopSleepInBed(false, false);
			assertDone(h, player, "sleep_it_off", false, "after a sober night");
			Tipsy.setLevel(player, 3);
			player.stopSleepInBed(true, true);
			assertDone(h, player, "sleep_it_off", false, "after leaving the bed early");
			helper.assertValueEqual(Tipsy.getLevel(player), 3, "still tipsy");
			player.stopSleepInBed(false, false);
			assertDone(h, player, "sleep_it_off", true, "after sleeping through the night tipsy");
			helper.assertValueEqual(Tipsy.getLevel(player), 0, "sober in the morning");
		});
		helper.succeed();
	}
}
