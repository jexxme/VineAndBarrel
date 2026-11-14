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
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;

import static jexxme.vineandbarrel.gametest.VBGameTests.test;

/** The tavern keeper (1.2): his job site, his trades by region, the sealed cask, the restock and the cheers. */
final class KeeperGameTests
{
	private static final List<VillagerType> TYPES = List.of(VillagerType.PLAINS, VillagerType.DESERT, VillagerType.SAVANNA, VillagerType.SNOW,
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
		test("keeper_a_jobless_villager_takes_a_kettle_on_a_campfire", VBGameTests.SMALL, 4800, KeeperGameTests::hiringOnCampfire);
		test("keeper_works_at_a_kettle_on_a_campfire", KeeperGameTests::worksAtACampfireKettle);
		test("keeper_hired_beside_the_campfire", KeeperGameTests::hiredBesideTheCampfire);
		test("keeper_an_old_kettle_becomes_a_job_site", KeeperGameTests::oldKettle);
		test("keeper_works_at_a_kettle_on_a_campfire_unburnt", VBGameTests.LARGE, 1800, helper -> worksUnburnt(helper, false));
		test("keeper_works_at_a_big_kettle_on_four_fires_unburnt", VBGameTests.LARGE, 1800, helper -> worksUnburnt(helper, true));
		test("keeper_walks_round_no_fire_corner", KeeperGameTests::roundsNoFireCorner);
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
		var poi = helper.getLevel().registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(TavernKeeper.JOB_SITE);
		helper.assertTrue(poi.is(PoiTypeTags.ACQUIRABLE_JOB_SITE), "the kettle is a job site an unemployed villager can take");
		for (BlockState other : List.of(BarrelRegistry.AGING_BARREL.get().defaultBlockState(), Blocks.CAMPFIRE.defaultBlockState(), Blocks.BARREL.defaultBlockState(),
			Blocks.BREWING_STAND.defaultBlockState(), Blocks.CAULDRON.defaultBlockState())) {
			helper.assertFalse(TavernKeeper.isJobSite(other), "not a keeper's job site: " + other);
		}
		helper.assertTrue(PoiTypes.forState(Blocks.BARREL.defaultBlockState()).map(type -> type.is(PoiTypes.FISHERMAN)).orElse(false), "the barrel stays the fisherman's");
		var profession = TavernKeeper.TAVERN_KEEPER.get();
		helper.assertTrue(profession.heldJobSite().test(poi) && profession.acquirableJobSite().test(poi), "his profession holds the kettle");
		helper.succeed();
	}

	/** In every village region, a keeper of each level has at least two offers to draw from, and all of them work. */
	private static void tradesByRegion(GameTestHelper helper) {
		var pools = VillagerTrades.TRADES.get(TavernKeeper.TAVERN_KEEPER.get());
		helper.assertTrue(pools != null, "the keeper has trade pools");
		int all = 0;
		for (int lvl = 1; lvl <= 5; lvl++) {
			List<VillagerTrades.ItemListing> pool = Arrays.asList(pools.getOrDefault(lvl, new VillagerTrades.ItemListing[0]));
			for (VillageData.KeeperTrade trade : VillageData.keeperTrades(lvl)) {
				helper.assertTrue(pool.contains(trade), trade.id() + " is in the level " + lvl + " pool");
				all++;
			}
		}
		helper.assertValueEqual(all, 33, "keeper trades");
		RandomSource random = RandomSource.create(1);
		for (VillagerType type : TYPES) {
			Villager keeper = keeper(helper, type, new BlockPos(1, 1, 1));
			for (int lvl = 1; lvl <= 5; lvl++) {
				int offers = 0;
				for (VillageData.KeeperTrade trade : VillageData.keeperTrades(lvl)) {
					MerchantOffer offer = trade.getOffer(keeper, random);
					if (offer != null) {
						helper.assertFalse(offer.getResult().isEmpty() || offer.getCostA().isEmpty(), trade.id() + " offers something for something");
						offers++;
					}
				}
				helper.assertTrue(offers >= 2, type + " keeper, level " + lvl + ": " + offers + " offers");
			}
			keeper.discard();
		}
		helper.succeed();
	}

	/** The master's cask: an Aging Barrel with four servings of the region's aged best, which places full and aged. */
	private static void sealedCask(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager keeper = keeper(helper, VillagerType.SNOW, new BlockPos(1, 1, 1));
		VillageData.KeeperTrade trade = VillageData.KEEPER_TRADES.stream()
			.filter(t -> t.id().equals(jexxme.vineandbarrel.VineAndBarrel.id("tavern_keeper/5/sealed_cask_mead"))).findFirst().orElseThrow();
		MerchantOffer offer = trade.getOffer(keeper, RandomSource.create(1));
		helper.assertTrue(offer != null, "a snowy keeper sells the sealed mead");
		ItemStack cask = offer.getResult();
		helper.assertTrue(cask.is(BarrelRegistry.AGING_BARREL_ITEM.get()), "an Aging Barrel: " + cask);
		BarrelContents batch = cask.get(BarrelRegistry.BARREL_CONTENTS.get());
		helper.assertTrue(batch != null && batch.servings() == TavernKeeper.SEALED_SERVINGS, "four servings in it: " + batch);
		helper.assertValueEqual(batch.stageAt(batch.elapsed()), BarrelStage.AGED, "already aged");
		helper.assertTrue(batch.aged().is(DrinkItems.AGED_MEAD.get()), "aged mead: " + batch.aged());
		helper.assertTrue(trade.getOffer(keeper(helper, VillagerType.PLAINS, new BlockPos(3, 1, 1)), RandomSource.create(1)) == null,
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
		helper.assertValueEqual(TavernKeeper.region(VillagerType.DESERT).young(), DrinkItems.RED_WINE.get(), "desert restocks red wine");
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
		int plain = hidden.getEffect(MobEffects.DIG_SPEED).getDuration();
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
			int toasted = guest.getEffect(MobEffects.DIG_SPEED).getDuration();
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

	/**
	 * A villager without a job, next to a kettle, takes it as his job site and becomes a keeper, as with any job block.
	 * He stands next to it from the start: villagers only walk to a job site by day, and the test may run at night.
	 */
	private static void hiring(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(3, 1, 3)), KettleRegistry.kettle().defaultBlockState());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 3));
		helper.succeedWhen(() -> helper.assertTrue(TavernKeeper.isKeeper(villager), "still " + villager.getVillagerData().getProfession()
			+ ", memories " + villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE)));
	}

	/**
	 * Audit 1.2: a kettle on a campfire (as behind every tavern's bar) is a block above where the villager stands, and he
	 * takes it all the same: he can't get within a block of it, so the job site counts as reached two blocks away. He
	 * stands beside the fire from the start: villagers only walk to a job site by day, and the test may run at night.
	 */
	private static void hiringOnCampfire(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertValueEqual(TavernKeeper.JOB_SITE_TYPE.get().validRange(), TavernKeeper.JOB_SITE_RANGE, "the kettle's reach");
		// the campfire on the floor the villager walks on, the kettle on it at head height
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(3, 0, 3)), Blocks.CAMPFIRE.defaultBlockState());
		level.setBlockAndUpdate(helper.absolutePos(new BlockPos(3, 1, 3)), KettleRegistry.kettle().defaultBlockState());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 3));
		helper.succeedWhen(() -> helper.assertTrue(TavernKeeper.isKeeper(villager), "still " + villager.getVillagerData().getProfession()
			+ ", memories " + villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE)));
	}

	/** Vanilla's work at the job site, as it decides whether he is close enough to work there. */
	private static final class Work extends net.minecraft.world.entity.ai.behavior.WorkAtPoi
	{
		boolean closeEnough(ServerLevel level, Villager villager) {
			return canStillUse(level, villager, level.getGameTime());
		}
	}

	/**
	 * Audit 1.2: a keeper works at his kettle on a campfire from the block beside the fire (he restocks his trades when
	 * he works); any other villager still has to stand as close to his job site as ever, and he has to come up close.
	 */
	private static void worksAtACampfireKettle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos kettle = helper.absolutePos(new BlockPos(3, 1, 3));
		level.setBlockAndUpdate(kettle.below(), Blocks.CAMPFIRE.defaultBlockState());
		level.setBlockAndUpdate(kettle, KettleRegistry.kettle().defaultBlockState());
		Villager keeper = keeper(helper, VillagerType.PLAINS, new BlockPos(2, 0, 3));
		keeper.getBrain().setMemory(MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(level.dimension(), kettle));
		net.minecraft.world.phys.Vec3 beside = helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 0.0, 3.5));
		keeper.setPos(beside.x, beside.y, beside.z);
		helper.assertTrue(new Work().closeEnough(level, keeper), "beside the campfire he works at his kettle");
		net.minecraft.world.phys.Vec3 off = helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 0.0, 3.5));
		keeper.setPos(off.x, off.y, off.z);
		helper.assertFalse(new Work().closeEnough(level, keeper), "three blocks off he doesn't");
		Villager farmer = keeper(helper, VillagerType.PLAINS, new BlockPos(2, 0, 3));
		farmer.setVillagerData(farmer.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
		farmer.getBrain().setMemory(MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(level.dimension(), kettle));
		farmer.setPos(beside.x, beside.y, beside.z);
		helper.assertFalse(new Work().closeEnough(level, farmer), "any other villager: vanilla's reach");
		helper.succeed();
	}

	/**
	 * Audit 1.2: a villager who has set out for a kettle on a campfire is hired as soon as he stands beside the fire,
	 * wherever in that block he stops (vanilla's 2 blocks reach a kettle on the floor, not one on a fire); farther off,
	 * not yet.
	 */
	private static void hiredBesideTheCampfire(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos kettle = helper.absolutePos(new BlockPos(3, 1, 3));
		level.setBlockAndUpdate(kettle.below(), Blocks.CAMPFIRE.defaultBlockState());
		level.setBlockAndUpdate(kettle, KettleRegistry.kettle().defaultBlockState());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(0, 0, 3));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, net.minecraft.core.GlobalPos.of(level.dimension(), kettle));
		net.minecraft.world.phys.Vec3 far = helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 0.0, 3.5));
		villager.setPos(far.x, far.y, far.z);
		TavernKeeper.hire(level, villager);
		helper.assertFalse(TavernKeeper.isKeeper(villager), "three blocks off: not yet");
		// beside the fire, off the middle of the block: 2.02 from the kettle's middle, out of vanilla's reach
		net.minecraft.world.phys.Vec3 beside = helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.2, 0.0, 3.9));
		villager.setPos(beside.x, beside.y, beside.z);
		TavernKeeper.hire(level, villager);
		helper.assertTrue(TavernKeeper.isKeeper(villager), "beside the fire: hired");
		helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(site -> site.pos().equals(kettle)).orElse(false), "the kettle is his job site");
		helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty(), "no longer on his way");
		helper.succeed();
	}

	/**
	 * Review 1.2: a villager walks up to a kettle on a lit campfire (a Big Kettle on its four), is hired there, and works
	 * at it for a good while without ever getting burnt. Two more lit campfires beside the kettle's own fire, by its
	 * controller, are where a keeper once stood (on one, in reach of the kettle) and burned: nobody stands on, in or
	 * under a fire, nor walks across one; and where a Big Kettle's corner by its controller has no room, he still finds
	 * it. Once hired he stays where he stands (his strolls about the village are the game's own).
	 */
	private static void worksUnburnt(GameTestHelper helper, boolean big) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(12, 1, 12); // the fires on the ground (y 0), as behind a tavern's bar
		int size = big ? 2 : 1;
		BlockState fire = Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true);
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(at.offset(x, -1, z), fire);
				for (int y = 0; y < size; y++) {
					helper.setBlock(at.offset(x, y, z), KettleRegistry.kettle());
				}
			}
		}
		if (big) {
			jexxme.vineandbarrel.kettle.KettleMultiblock.tryJoin(level, helper.absolutePos(at));
		}
		var made = jexxme.vineandbarrel.kettle.KettleMultiblock.kettleAt(level, helper.absolutePos(at));
		helper.assertTrue(made != null && made.isBig() == big, "the kettle: " + made);
		BlockPos kettle = made.controller();
		helper.assertTrue(TavernKeeper.isJobSite(level.getBlockState(kettle)), "the kettle is a job site");
		helper.setBlock(at.offset(-1, -1, 0), fire);
		helper.setBlock(at.offset(0, -1, -1), fire);
		Villager villager = helper.spawn(EntityType.VILLAGER, at.offset(-4, -1, size + 3));
		// a mob plans paths only while it stands on the ground; it has not fallen onto it yet on its first tick
		villager.setOnGround(true);
		// he finds the kettle as a job site he can walk up to (a Big Kettle's corner by its controller has no room)
		var site = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE).getOrThrow(TavernKeeper.JOB_SITE);
		var found = net.minecraft.world.entity.ai.behavior.AcquirePoi.findPathToPois(villager, java.util.Set.of(com.mojang.datafixers.util.Pair.of(site, kettle)));
		helper.assertTrue(found != null && found.canReach() && kettle.equals(found.getTarget()), "he finds the kettle: " + found);
		// and sets out for it, as finding it makes him (taken here, so he takes no other test's kettle)
		helper.assertTrue(level.getPoiManager().take(type -> type.is(TavernKeeper.JOB_SITE), (type, pos) -> pos.equals(kettle), kettle, 1).isPresent(), "the kettle is free");
		villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, net.minecraft.core.GlobalPos.of(level.dimension(), kettle));
		float health = villager.getHealth();
		int[] worked = {0};
		String[] burnt = {null};
		// every tick: hurt yet? at his kettle, working? sent there again every second until he gets there (as his way to a
		// job site sends him by day); once hired he stays where he stands (his strolls about the village are the game's)
		Runnable watch = () -> {
			if (burnt[0] == null && (villager.getHealth() < health || villager.isOnFire())) {
				burnt[0] = "hurt at " + villager.position() + " (kettle " + kettle + "): " + villager.getLastDamageSource();
			}
			boolean hired = TavernKeeper.isKeeper(villager) && villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(job -> job.pos().equals(kettle)).orElse(false);
			if (hired) {
				villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
				if (new Work().closeEnough(level, villager)) {
					worked[0]++;
				}
			} else if (helper.getTick() % 20 == 0) {
				villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new net.minecraft.world.entity.ai.memory.WalkTarget(kettle, 0.5F, 1));
			}
		};
		helper.startSequence()
			.thenWaitUntil(() -> {
				watch.run();
				helper.assertTrue(burnt[0] == null, "the villager got burnt: " + burnt[0]);
				helper.assertTrue(TavernKeeper.isKeeper(villager) && villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
					.map(job -> job.pos().equals(kettle)).orElse(false), "hired at the kettle: " + net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession())
					+ " at " + villager.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO))) + ", kettle at "
					+ kettle.subtract(helper.absolutePos(BlockPos.ZERO)) + ", spots " + TavernKeeper.standingSpots(level, kettle, villager.blockPosition()).stream()
					.map(spot -> spot.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()).toList());
			})
			.thenExecuteFor(600, () -> {
				watch.run();
				helper.assertTrue(burnt[0] == null, "the keeper got burnt: " + burnt[0]);
			})
			.thenExecute(() -> helper.assertTrue(worked[0] >= 500, "he stood at his kettle, close enough to work, for " + worked[0] + " ticks of 600"))
			.thenSucceed();
	}

	/**
	 * A way to a kettle that rounds the corner of a lit campfire comes last: a diagonal step past the fire, or a turn
	 * with the fire on its inside, where a villager cuts the corner and clips the fire though no step is on it. Straight
	 * past it, or turning with the fire on the outside, is fine.
	 */
	private static void roundsNoFireCorner(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true));
		java.util.function.Function<int[][], net.minecraft.world.level.pathfinder.Path> way = steps -> {
			List<net.minecraft.world.level.pathfinder.Node> nodes = new java.util.ArrayList<>();
			for (int[] step : steps) {
				BlockPos pos = helper.absolutePos(new BlockPos(step[0], 1, step[1]));
				nodes.add(new net.minecraft.world.level.pathfinder.Node(pos.getX(), pos.getY(), pos.getZ()));
			}
			return new net.minecraft.world.level.pathfinder.Path(nodes, nodes.get(nodes.size() - 1).asBlockPos(), true);
		};
		ServerLevel level = helper.getLevel();
		helper.assertTrue(TavernKeeper.roundsAHurtingCorner(level, way.apply(new int[][] {{3, 2}, {2, 3}})), "a diagonal step past the fire rounds it");
		helper.assertTrue(TavernKeeper.roundsAHurtingCorner(level, way.apply(new int[][] {{3, 1}, {3, 2}, {3, 3}, {2, 3}})), "a turn round the fire rounds it");
		helper.assertTrue(TavernKeeper.roundsAHurtingCorner(level, way.apply(new int[][] {{1, 1}, {1, 2}, {1, 3}, {2, 3}, {3, 3}, {3, 2}})), "a way round it");
		helper.assertFalse(TavernKeeper.roundsAHurtingCorner(level, way.apply(new int[][] {{3, 0}, {3, 1}, {3, 2}, {3, 3}, {3, 4}})), "straight past the fire");
		helper.assertFalse(TavernKeeper.roundsAHurtingCorner(level, way.apply(new int[][] {{4, 3}, {3, 3}, {3, 4}})), "a turn with the fire outside it");
		helper.assertFalse(TavernKeeper.roundsAHurtingCorner(level, way.apply(new int[][] {{4, 2}, {3, 3}, {3, 4}})), "a diagonal step a block off the fire");
		helper.succeed();
	}

	private static Villager keeper(GameTestHelper helper, VillagerType type, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setVillagerData(villager.getVillagerData().setType(type).setProfession(TavernKeeper.TAVERN_KEEPER.get()).setLevel(5));
		return villager;
	}
}
