package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.BarrelContents;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.barrel.BarrelStage;
import jexxme.vineandbarrel.drink.Cheers;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.world.village.TavernKeeper;
import jexxme.vineandbarrel.world.village.VillageData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

import static jexxme.vineandbarrel.gametest.VBGameTests.test;

/** The tavern keeper (1.2): his job site, his trades by region, the sealed cask, the restock and the cheers. */
final class KeeperGameTests
{
	private static final List<ResourceKey<VillagerType>> TYPES = List.of(VillagerType.PLAINS, VillagerType.DESERT, VillagerType.SAVANNA, VillagerType.SNOW,
		VillagerType.TAIGA, VillagerType.JUNGLE, VillagerType.SWAMP);

	private KeeperGameTests() {
	}

	static void registerAll() {
		test("keeper_job_site_is_every_kettle", KeeperGameTests::jobSite);
		test("keeper_trades_two_per_level_in_every_region", KeeperGameTests::tradesByRegion);
		test("keeper_master_sells_a_sealed_cask", KeeperGameTests::sealedCask);
		test("keeper_restock_pours_young_servings", KeeperGameTests::restock);
		test("keeper_clinks_with_a_drink", KeeperGameTests::cheers);
		test("keeper_a_jobless_villager_takes_a_kettle", VBGameTests.SMALL, 4800, KeeperGameTests::hiring);
		test("keeper_an_old_kettle_becomes_a_job_site", KeeperGameTests::oldKettle);
	}

	/**
	 * Every single kettle (any copper stage, waxed or not) is his job site and an unemployed villager may take it; no
	 * vanilla job block is his, and the cask, the fire under the kettle and a cauldron of a base keep what they are.
	 */
	private static void jobSite(GameTestHelper helper) {
		for (Block kettle : KettleRegistry.allKettleBlocks()) {
			for (BlockState state : kettle.getStateDefinition().getPossibleStates()) {
				helper.assertTrue(TavernKeeper.isJobSite(state), "a keeper's job site: " + state);
			}
		}
		helper.assertTrue(KettleRegistry.allKettleBlocks().size() == 8, "four copper stages, waxed and not: " + KettleRegistry.allKettleBlocks().size());
		// a Big Kettle: its controller is a job site, its other parts are not
		for (Block part : KettleRegistry.allPartBlocks()) {
			for (BlockState state : part.getStateDefinition().getPossibleStates()) {
				boolean controller = jexxme.vineandbarrel.kettle.KettlePart.byId(state.getValue(jexxme.vineandbarrel.kettle.BrewKettlePartBlock.PART)).isController();
				helper.assertValueEqual(TavernKeeper.isJobSite(state), controller, "a Big Kettle part is a job site only as its controller: " + state);
			}
		}
		var poi = helper.getLevel().registryAccess().lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).getOrThrow(TavernKeeper.JOB_SITE);
		helper.assertTrue(poi.is(PoiTypeTags.ACQUIRABLE_JOB_SITE), "the kettle is a job site an unemployed villager can take");
		for (BlockState other : List.of(BarrelRegistry.AGING_BARREL.get().defaultBlockState(), Blocks.CAMPFIRE.defaultBlockState(), Blocks.BARREL.defaultBlockState(),
			Blocks.BREWING_STAND.defaultBlockState(), Blocks.CAULDRON.defaultBlockState())) {
			helper.assertFalse(TavernKeeper.isJobSite(other), "not a keeper's job site: " + other);
		}
		helper.assertTrue(PoiTypes.forState(Blocks.BARREL.defaultBlockState()).map(type -> type.is(PoiTypes.FISHERMAN)).orElse(false), "the barrel stays the fisherman's");
		var profession = helper.getLevel().registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(TavernKeeper.PROFESSION).value();
		helper.assertTrue(profession.heldJobSite().test(poi) && profession.acquirableJobSite().test(poi), "his profession holds the kettle");
		helper.succeed();
	}

	/** In every village region, a keeper of each level has at least two offers to draw from, and all of them work. */
	private static void tradesByRegion(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var sets = level.registryAccess().lookupOrThrow(Registries.TRADE_SET);
		var trades = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
		int all = 0;
		for (int lvl = 1; lvl <= 5; lvl++) {
			for (ResourceKey<VillagerTrade> key : VillageData.keeperTradeKeys(lvl)) {
				TradeSet set = sets.getValue(TavernKeeper.TRADE_SETS.get(lvl - 1));
				helper.assertTrue(set != null && set.getTrades().stream().anyMatch(h -> h.is(key)), key.identifier() + " is in the level " + lvl + " set");
				helper.assertTrue(trades.getValue(key) != null, "trade " + key.identifier());
				all++;
			}
		}
		helper.assertValueEqual(all, 33, "keeper trades");
		for (ResourceKey<VillagerType> type : TYPES) {
			Villager keeper = keeper(helper, type, new BlockPos(1, 1, 1));
			LootContext context = context(level, keeper);
			for (int lvl = 1; lvl <= 5; lvl++) {
				int offers = 0;
				for (ResourceKey<VillagerTrade> key : VillageData.keeperTradeKeys(lvl)) {
					MerchantOffer offer = trades.getValue(key).getOffer(context);
					if (offer != null) {
						helper.assertFalse(offer.getResult().isEmpty() || offer.getCostA().isEmpty(), key.identifier() + " offers something for something");
						offers++;
					}
				}
				helper.assertTrue(offers >= 2, type.identifier() + " keeper, level " + lvl + ": " + offers + " offers");
			}
			keeper.discard();
		}
		helper.succeed();
	}

	/** The master's cask: an Aging Barrel with four servings of the region's aged best, which places full and aged. */
	private static void sealedCask(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager keeper = keeper(helper, VillagerType.SNOW, new BlockPos(1, 1, 1));
		VillagerTrade trade = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).getValue(
			ResourceKey.create(Registries.VILLAGER_TRADE, jexxme.vineandbarrel.VineAndBarrel.id("tavern_keeper/5/sealed_cask_mead")));
		MerchantOffer offer = trade.getOffer(context(level, keeper));
		helper.assertTrue(offer != null, "a snowy keeper sells the sealed mead");
		ItemStack cask = offer.getResult();
		helper.assertTrue(cask.is(BarrelRegistry.AGING_BARREL_ITEM.get()), "an Aging Barrel: " + cask);
		BarrelContents batch = cask.get(BarrelRegistry.BARREL_CONTENTS.get());
		helper.assertTrue(batch != null && batch.servings() == TavernKeeper.SEALED_SERVINGS, "four servings in it: " + batch);
		helper.assertValueEqual(batch.stageAt(batch.elapsed()), BarrelStage.AGED, "already aged");
		helper.assertTrue(batch.aged().is(DrinkItems.AGED_MEAD.get()), "aged mead: " + batch.aged());
		helper.assertTrue(trade.getOffer(context(level, keeper(helper, VillagerType.PLAINS, new BlockPos(3, 1, 1)))) == null,
			"a plains keeper doesn't sell the snowy mead");
		// placed, the barrel holds it, aged
		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 4));
		level.setBlockAndUpdate(at, BarrelRegistry.AGING_BARREL.get().defaultBlockState());
		AgingBarrelBlockEntity barrel = (AgingBarrelBlockEntity) level.getBlockEntity(at);
		barrel.applyComponentsFromItemStack(cask);
		helper.assertValueEqual(barrel.servings(), TavernKeeper.SEALED_SERVINGS, "servings once placed");
		helper.assertValueEqual(barrel.stage(), BarrelStage.AGED, "aged once placed");
		helper.succeed();
	}

	/** A restock pours two servings of the region's drink into an empty cask, ready to tap (young); never into a full one. */
	private static void restock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlockAndUpdate(at, BarrelRegistry.AGING_BARREL.get().defaultBlockState());
		helper.assertTrue(TavernKeeper.fill(level, at, DrinkItems.BARLEY_ALE.get()), "filled");
		AgingBarrelBlockEntity barrel = (AgingBarrelBlockEntity) level.getBlockEntity(at);
		helper.assertValueEqual(barrel.servings(), TavernKeeper.RESTOCK_SERVINGS, "servings");
		helper.assertValueEqual(barrel.stage(), BarrelStage.YOUNG, "young, ready to tap");
		helper.assertTrue(barrel.drink().is(DrinkItems.BARLEY_ALE.get()), "barley ale: " + barrel.drink());
		helper.assertFalse(barrel.isHomeBrewed(), "the keeper's stock is not home-brewed (tapping it earns no Patience Pays)");
		helper.assertFalse(TavernKeeper.fill(level, at, DrinkItems.MEAD.get()), "a cask with something in it is left alone");
		helper.assertValueEqual(TavernKeeper.region(level.registryAccess().getOrThrow(VillagerType.DESERT)).young(), DrinkItems.RED_WINE.get(), "desert restocks red wine");
		helper.succeed();
	}

	/**
	 * A drink finished alone in front of a keeper: he clinks with you (the drink's buff lasts the Cheers bonus longer) and
	 * thinks a little better of the drinker; out of his sight, nothing.
	 */
	private static void cheers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager keeper = keeper(helper, VillagerType.PLAINS, new BlockPos(2, 1, 4));
		// first behind a wall, in range but out of his sight: nothing
		for (int x = 0; x <= 4; x++) {
			for (int y = 1; y <= 3; y++) {
				level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, 3)), Blocks.STONE.defaultBlockState());
			}
		}
		TipsyGameTests.TestPlayer hidden = TipsyGameTests.player(helper);
		helper.assertTrue(hidden.distanceTo(keeper) < TavernKeeper.CHEERS_RANGE, "the hidden drinker is within range");
		TipsyGameTests.drink(hidden, new ItemStack(DrinkItems.BARLEY_ALE.get()));
		int plain = hidden.getEffect(MobEffects.HASTE).getDuration();
		helper.assertValueEqual(keeper.getGossips().getReputation(hidden.getUUID(), type -> type == GossipType.MINOR_POSITIVE), 0, "no word about a drinker he can't see");
		// then in his sight, once the first drink's Cheers window is over (a drink with the hidden player would be a cheers of its own)
		helper.runAfterDelay(Cheers.WINDOW_TICKS + 10, () -> {
			for (int x = 0; x <= 4; x++) {
				for (int y = 1; y <= 3; y++) {
					level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, 3)), Blocks.AIR.defaultBlockState());
				}
			}
			TipsyGameTests.TestPlayer guest = TipsyGameTests.player(helper);
			TipsyGameTests.drink(guest, new ItemStack(DrinkItems.BARLEY_ALE.get()));
			int toasted = guest.getEffect(MobEffects.HASTE).getDuration();
			helper.assertTrue(toasted > plain, "the keeper's cheers made the Haste last longer: " + toasted + " against " + plain);
			helper.assertValueEqual(keeper.getGossips().getReputation(guest.getUUID(), type -> type == GossipType.MINOR_POSITIVE), TavernKeeper.CHEERS_GOSSIP,
				"a little good word about the guest");
			helper.succeed();
		});
	}

	/**
	 * A kettle that is no job site yet (placed before 1.2 in a section whose points of interest the game had listed
	 * already) becomes one the first time it ticks.
	 */
	private static void oldKettle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlockAndUpdate(at, KettleRegistry.kettle().defaultBlockState());
		level.getPoiManager().remove(at);
		helper.assertTrue(level.getPoiManager().getType(at).isEmpty(), "unlisted, as an old kettle would be");
		helper.succeedWhen(() -> helper.assertTrue(level.getPoiManager().existsAtPosition(TavernKeeper.JOB_SITE, at), "the kettle is a job site again"));
	}

	/** A villager without a job, next to a kettle, takes it as his job site and becomes a keeper, as with any job block. */
	private static void hiring(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(3, 1, 3)), KettleRegistry.kettle().defaultBlockState());
		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
		helper.succeedWhen(() -> helper.assertTrue(TavernKeeper.isKeeper(villager), "still " + villager.getVillagerData().profession().getRegisteredName()
			+ ", memories " + villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE)));
	}

	private static Villager keeper(GameTestHelper helper, ResourceKey<VillagerType> type, BlockPos at) {
		Villager villager = helper.spawn(EntityTypes.VILLAGER, at);
		villager.setNoAi(true);
		villager.setVillagerData(villager.getVillagerData().withType(helper.getLevel().registryAccess(), type)
			.withProfession(helper.getLevel().registryAccess(), TavernKeeper.PROFESSION).withLevel(5));
		return villager;
	}

	private static LootContext context(ServerLevel level, Villager villager) {
		return new LootContext.Builder(new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, villager.position())
			.withParameter(LootContextParams.THIS_ENTITY, villager)
			.withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
			.create(LootContextParamSets.VILLAGER_TRADE)).create(Optional.empty());
	}
}
