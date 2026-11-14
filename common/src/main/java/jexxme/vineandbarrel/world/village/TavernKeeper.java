package jexxme.vineandbarrel.world.village;

import com.google.common.collect.ImmutableSet;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.barrel.BarrelContents;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.platform.EntityAttachment;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.registry.DeferredHolder;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The tavern keeper (1.2): a villager profession whose job site is the Brew Kettle (every kettle, any copper stage,
 * waxed or not). One kettle stands behind the bar of every tavern we generate, so the keeper works behind his bar, and a
 * player who brews has one or two, not the dozens of casks a cellar holds (an Aging Barrel as job site would have turned
 * every jobless villager near a tavern into a keeper). No vanilla job site is a kettle, and no tavern holds a vanilla one.
 * <p>
 * Vanilla villager behaviour throughout; on top: he clinks with you when you finish a drink in front of him (the Cheers
 * bonus, and regulars get a little cheaper) and fills one empty cask of his own tavern a day.
 * <p>
 * This Minecraft has no data-driven trades: his trades are code ({@link VillageData#keeperTrades}), handed to the loader
 * by level like the farmer's.
 */
public final class TavernKeeper
{
	public static final String NAME = "tavern_keeper";

	private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, VineAndBarrel.MODID);
	private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, VineAndBarrel.MODID);

	public static final ResourceKey<PoiType> JOB_SITE = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, VineAndBarrel.id(NAME));
	public static final ResourceKey<VillagerProfession> PROFESSION = ResourceKey.create(Registries.VILLAGER_PROFESSION, VineAndBarrel.id(NAME));

	/**
	 * A villager takes a job site he can walk to within this many blocks (vanilla's job blocks: 1). A kettle often sits on
	 * a campfire, a block above where anyone stands: with 1 no path ever counted as reaching it and nobody took it. Only
	 * finding and handing on the job site use it; to be hired he still steps up next to it.
	 */
	public static final int JOB_SITE_RANGE = 2;
	/**
	 * He is hired at his kettle ({@link #hire}) and works at it ({@code mixin.WorkAtPoiMixin}) from this close: blocks
	 * from his feet to its middle. Vanilla's 2 and 1.73 are for a job block on the floor; next to the campfire a kettle
	 * sits on he stands 1.8 to 2.2 away, wherever in the block beside the fire he stops.
	 */
	public static final double KETTLE_REACH = 2.5;

	public static final DeferredHolder<PoiType, PoiType> JOB_SITE_TYPE = POI_TYPES.register(NAME, () -> new PoiType(kettleStates(), 1, JOB_SITE_RANGE));

	public static final DeferredHolder<VillagerProfession, VillagerProfession> TAVERN_KEEPER = PROFESSIONS.register(NAME, () -> new VillagerProfession(NAME,
		poi -> poi.is(JOB_SITE), poi -> poi.is(JOB_SITE), ImmutableSet.of(), ImmutableSet.of(), BarrelRegistry.BARREL_TAP.get()));

	/** The game time of the keeper's last restock (-1: none yet); saved, server only. */
	public static final EntityAttachment<Long> RESTOCKED = Services.PLATFORM.attachment(VineAndBarrel.id("keeper_restocked"),
		EntityAttachment.Spec.builder(() -> -1L).serialize(com.mojang.serialization.Codec.LONG.fieldOf("game_time")).build());

	/**
	 * The kettle of the tavern a keeper came with, until he holds it as his job site (server only, saved). A village
	 * places its tavern's keeper with the chunk, before the game lists the kettle as a job site, so he takes it on his
	 * first tick in the world ({@link #takeTavernKettle}).
	 */
	public static final EntityAttachment<BlockPos> TAVERN_KETTLE = Services.PLATFORM.attachment(VineAndBarrel.id("keeper_kettle"),
		EntityAttachment.Spec.builder(() -> BlockPos.ZERO).serialize(BlockPos.CODEC.fieldOf("pos")).build());

	/** He raises his mug to a drink finished this close to him, if he can see the drinker. */
	public static final double CHEERS_RANGE = 6.0;
	/** Each cheers with him is a little good word about you, up to vanilla's cap for it (25). */
	public static final int CHEERS_GOSSIP = 2;
	/** A day of play, in ticks: he restocks once in each. */
	public static final long DAY = 24000L;
	/** He gives up on a cask he hasn't reached after this long (a minute). */
	public static final long ERRAND_TICKS = 1200L;
	/** He looks for an empty cask of his tavern this far from his kettle, and fills it when he is this close. */
	public static final int RESTOCK_RANGE = 16;
	public static final double RESTOCK_REACH = 2.5;
	/** Servings a restock pours: two bottles of the region's young drink. */
	public static final int RESTOCK_SERVINGS = 2;
	/** Servings in the master's sealed cask. */
	public static final int SEALED_SERVINGS = 4;

	private TavernKeeper() {
	}

	/**
	 * Every state of every single Brew Kettle (all copper stages, waxed or not), and of a Big Kettle's controller (the
	 * kettle the rest join: a big brewery hires one keeper, and a keeper's kettle that grows into a Big Kettle stays a
	 * job site).
	 */
	public static Set<BlockState> kettleStates() {
		Set<BlockState> states = new HashSet<>();
		for (Block kettle : KettleRegistry.allKettleBlocks()) {
			states.addAll(kettle.getStateDefinition().getPossibleStates());
		}
		for (Block part : KettleRegistry.allPartBlocks()) {
			for (BlockState state : part.getStateDefinition().getPossibleStates()) {
				if (jexxme.vineandbarrel.kettle.KettlePart.byId(state.getValue(jexxme.vineandbarrel.kettle.BrewKettlePartBlock.PART)).isController()) {
					states.add(state);
				}
			}
		}
		return Set.copyOf(states);
	}

	public static void register() {
		POI_TYPES.register();
		PROFESSIONS.register();
	}

	public static boolean isKeeper(Villager villager) {
		return villager.getVillagerData().getProfession() == TAVERN_KEEPER.get();
	}

	// ------------------------------------------------------------------ the region's drinks

	/** A village region's drinks: the young one he restocks, the aged best he sells sealed. */
	public record Region(Item young, Item aged) {
	}

	/** By the villager's type (his biome): plains ale, desert red wine, savanna melon wine, snowy mead, taiga berry wine. */
	public static Region region(VillagerType type) {
		if (type == VillagerType.DESERT) {
			return new Region(DrinkItems.RED_WINE.get(), DrinkItems.AGED_RED_WINE.get());
		}
		if (type == VillagerType.SAVANNA || type == VillagerType.JUNGLE) {
			return new Region(DrinkItems.MELON_WINE.get(), DrinkItems.AGED_MELON_WINE.get());
		}
		if (type == VillagerType.SNOW) {
			return new Region(DrinkItems.MEAD.get(), DrinkItems.AGED_MEAD.get());
		}
		if (type == VillagerType.TAIGA) {
			return new Region(DrinkItems.SWEET_BERRY_WINE.get(), DrinkItems.AGED_SWEET_BERRY_WINE.get());
		}
		if (type == VillagerType.SWAMP) {
			return new Region(DrinkItems.HARD_CIDER.get(), DrinkItems.AGED_HARD_CIDER.get());
		}
		return new Region(DrinkItems.BARLEY_ALE.get(), DrinkItems.AGED_BARLEY_ALE.get());
	}

	/** The aging recipe whose young or aged drink is {@code drink}, if any. */
	public static @Nullable AgingRecipe recipeOf(ServerLevel level, Item drink) {
		for (AgingRecipe recipe : AgingRecipes.all(level)) {
			if (recipe.young().is(drink) || recipe.aged().is(drink)) {
				return recipe;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ behaviour (server, from the entity tick)

	/** Every tick for every villager: hired at a kettle ({@link #hire}); once a second for every keeper: the daily restock. */
	public static void tick(Villager keeper) {
		if (keeper.level() instanceof ServerLevel server) {
			takeTavernKettle(server, keeper);
			hire(server, keeper);
		}
		if (!(keeper.level() instanceof ServerLevel level) || keeper.tickCount % 20 != 0 || !isKeeper(keeper) || keeper.isBaby()) {
			return;
		}
		Optional<GlobalPos> site = keeper.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		BlockPos kettle = site.filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos).orElse(null);
		if (kettle != null) {
			restock(level, keeper, kettle);
		}
	}

	/**
	 * A villager who has set out for a kettle as his job site is hired there (or, a keeper already, takes it as his) once
	 * he is within {@link #KETTLE_REACH} of it, as vanilla's {@code AssignProfessionFromJobSite} does within 2 blocks of
	 * any job site: from beside the campfire a kettle sits on he never or only now and then got that close.
	 */
	public static void hire(ServerLevel level, Villager villager) {
		Optional<GlobalPos> potential = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		if (potential == null || potential.isEmpty()) {
			return;
		}
		GlobalPos site = potential.get();
		if (site.dimension() != level.dimension() || !reaches(level, site.pos(), villager.position())
			|| !level.getPoiManager().getType(site.pos()).map(type -> type.is(JOB_SITE)).orElse(false)) {
			return;
		}
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, site);
		level.broadcastEntityEvent(villager, (byte) 14);
		if (villager.getVillagerData().getProfession() == VillagerProfession.NONE) {
			villager.setVillagerData(villager.getVillagerData().setProfession(TAVERN_KEEPER.get()));
			villager.refreshBrain(level);
		}
	}

	/**
	 * The keeper a tavern brought takes its kettle as his job site, as a villager who walked up to it would (its one
	 * ticket his), once the game lists it as one. Should another villager hold it already, or the kettle be gone, he
	 * looks for a kettle as any keeper does.
	 */
	public static void takeTavernKettle(ServerLevel level, Villager keeper) {
		if (!TAVERN_KETTLE.has(keeper)) {
			return;
		}
		BlockPos kettle = TAVERN_KETTLE.get(keeper);
		if (!level.isLoaded(kettle)) {
			return;
		}
		var brain = keeper.getBrain();
		if (!isKeeper(keeper) || brain.hasMemoryValue(MemoryModuleType.JOB_SITE) || !isJobSite(level.getBlockState(kettle))) {
			TAVERN_KETTLE.remove(keeper);
			return;
		}
		var pois = level.getPoiManager();
		if (pois.getType(kettle).isEmpty()) {
			return; // listed on its next tick (ensureJobSite)
		}
		TAVERN_KETTLE.remove(keeper);
		if (pois.take(type -> type.is(JOB_SITE), (type, pos) -> pos.equals(kettle), kettle, 1).isPresent()) {
			keeper.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
			brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
			brain.setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), kettle));
		}
	}

	/**
	 * Whether a villager at {@code at} is at the kettle whose job site is {@code site}: within {@link #KETTLE_REACH} of
	 * the middle of its block, or of any block of a Big Kettle's bottom layer (he stands beside the whole kettle).
	 */
	public static boolean reaches(net.minecraft.world.level.BlockGetter level, BlockPos site, net.minecraft.core.Position at) {
		for (BlockPos part : bottom(level, site)) {
			if (part.closerToCenterThan(at, KETTLE_REACH)) {
				return true;
			}
		}
		return false;
	}

	/** The kettle's bottom layer: the kettle itself, or the four (nine) blocks a Big Kettle stands on its fires with. */
	private static List<BlockPos> bottom(net.minecraft.world.level.BlockGetter level, BlockPos site) {
		var kettle = jexxme.vineandbarrel.kettle.KettleMultiblock.kettleAt(level, site);
		if (kettle == null || !kettle.isBig()) {
			return List.of(site);
		}
		return kettle.positions().stream().filter(pos -> pos.getY() == kettle.controller().getY()).toList();
	}

	/**
	 * Where a villager walking to the kettle at {@code kettle} can stand at it, best first: open spots beside the fire it
	 * sits on (beside the whole fire under a Big Kettle), then beside the kettle itself, each nearer to {@code from}
	 * first, all within {@link #reaches reach}. Never in, on or under anything that hurts (a lit campfire, fire, magma,
	 * lava, a cactus or berry bush: what the game's paths treat as burning or damaging). Walking to the kettle itself, a
	 * villager aims above it (a path's target moves up out of solid blocks, through the shelf over a tavern's kettle) and
	 * ends up on the bar, the roof or the fire.
	 */
	public static List<BlockPos> standingSpots(net.minecraft.world.level.BlockGetter level, BlockPos kettle, BlockPos from) {
		List<BlockPos> bottom = bottom(level, kettle);
		List<BlockPos> spots = new java.util.ArrayList<>();
		for (int down = 1; down >= 0; down--) {
			List<BlockPos> ring = new java.util.ArrayList<>();
			for (BlockPos part : bottom) {
				for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
					BlockPos feet = part.relative(side).below(down);
					if (!bottom.contains(feet.above(down)) && !ring.contains(feet) && open(level, feet) && open(level, feet.above()) && !open(level, feet.below())
						&& !hurts(level, feet) && !hurts(level, feet.above()) && !hurts(level, feet.below())
						&& reaches(level, kettle, net.minecraft.world.phys.Vec3.atBottomCenterOf(feet))) {
						ring.add(feet);
					}
				}
			}
			ring.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(from)));
			spots.addAll(ring);
		}
		return spots;
	}

	/** Spots tried for a way to a kettle (each try plans a path). */
	private static final int SPOTS_TRIED = 4;

	/**
	 * A villager's way to where he stands at the kettle at {@code kettle} ({@link #standingSpots}), clear of everything
	 * that hurts, or null when no spot has one. A way that rounds the corner of a fire ({@link #roundsAHurtingCorner})
	 * is taken only when every spot tried at the same height (beside the fire, else beside the kettle) needs one.
	 */
	public static net.minecraft.world.level.pathfinder.@Nullable Path wayToKettle(net.minecraft.world.entity.Mob body, BlockPos kettle) {
		List<BlockPos> spots = standingSpots(body.level(), kettle, body.blockPosition());
		net.minecraft.world.level.pathfinder.Path rounding = null;
		for (BlockPos spot : spots.subList(0, Math.min(SPOTS_TRIED, spots.size()))) {
			if (rounding != null && spot.getY() != rounding.getTarget().getY()) {
				break; // never the counter beside the kettle for a way round a fire beside it
			}
			var path = body.getNavigation().createPath(spot, 0);
			if (path != null && path.canReach() && isSafeWalk(body.level(), path)) {
				if (!roundsAHurtingCorner(body.level(), path)) {
					return path;
				}
				if (rounding == null) {
					rounding = path;
				}
			}
		}
		return rounding;
	}

	/**
	 * Whether a walk rounds the corner of something that hurts: a diagonal step past it, or a turn with it on the inside.
	 * No step is on it, but walking that way a villager cuts the corner (he turns for the next step a little before he
	 * is on this one) and clips its block.
	 */
	public static boolean roundsAHurtingCorner(net.minecraft.world.level.BlockGetter level, net.minecraft.world.level.pathfinder.Path path) {
		for (int i = 0; i + 1 < path.getNodeCount(); i++) {
			BlockPos at = path.getNode(i).asBlockPos();
			BlockPos next = path.getNode(i + 1).asBlockPos();
			if (at.getX() != next.getX() && at.getZ() != next.getZ()) {
				for (int y = Math.min(at.getY(), next.getY()); y <= Math.max(at.getY(), next.getY()); y++) {
					if (hurtsAround(level, new BlockPos(at.getX(), y, next.getZ())) || hurtsAround(level, new BlockPos(next.getX(), y, at.getZ()))) {
						return true;
					}
				}
			}
			if (i > 0) {
				// a turn here: the blocks beside this step that lie toward both the step before and the next one
				BlockPos before = path.getNode(i - 1).asBlockPos();
				int inX = before.getX() - at.getX(), inZ = before.getZ() - at.getZ(), outX = next.getX() - at.getX(), outZ = next.getZ() - at.getZ();
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx * inX + dz * inZ > 0 && dx * outX + dz * outZ > 0 && hurtsAround(level, at.offset(dx, 0, dz))) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static boolean hurtsAround(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		return hurts(level, pos) || hurts(level, pos.above()) || hurts(level, pos.below());
	}

	/** Whether a walk keeps clear of everything that hurts: no step in, on or under it. */
	public static boolean isSafeWalk(net.minecraft.world.level.BlockGetter level, net.minecraft.world.level.pathfinder.Path path) {
		for (int i = 0; i < path.getNodeCount(); i++) {
			BlockPos step = path.getNode(i).asBlockPos();
			if (hurts(level, step) || hurts(level, step.above()) || hurts(level, step.below())) {
				return false;
			}
		}
		return true;
	}

	/** What hurts to stand in or on: what the game's paths call burning (lit campfires, fire, magma, lava) or damaging. */
	private static boolean hurts(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return net.minecraft.world.level.pathfinder.WalkNodeEvaluator.isBurningBlock(state) || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
			|| state.is(net.minecraft.world.level.block.Blocks.CACTUS) || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
			|| state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE) || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
	}

	private static boolean open(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** A restock under way: the cask he walks to, and when he gives up on it. */
	private record Errand(BlockPos cask, long until) {
	}

	/** Errands under way, per keeper; not saved (a restock interrupted by a restart is simply done the next day). */
	private static final Map<Villager, Errand> ERRANDS = new java.util.WeakHashMap<>();

	/**
	 * Once a day of play (game time, so a frozen or turned-back clock changes nothing), while he works: to an empty cask
	 * of his own tavern that he can walk to, and two young servings of his region's drink go in. Never a player's cask
	 * (only the casks placed with a tavern, not one a player put in it), never an aged batch, never home-brewed. Whatever happens (no empty cask, no
	 * way there, a minute gone by), the day's restock is done.
	 */
	private static void restock(ServerLevel level, Villager keeper, BlockPos kettle) {
		long now = level.getGameTime();
		long last = RESTOCKED.get(keeper);
		if (last < 0L) {
			RESTOCKED.set(keeper, now); // his first day starts now
			return;
		}
		if (now - last < DAY || !keeper.getBrain().isActive(Activity.WORK)) {
			return;
		}
		Errand errand = ERRANDS.get(keeper);
		if (errand == null) {
			BlockPos cask = restockCask(level, keeper, kettle);
			if (cask == null) {
				restocked(keeper, now);
				return;
			}
			errand = new Errand(cask, now + ERRAND_TICKS);
			ERRANDS.put(keeper, errand);
		}
		if (keeper.position().closerThan(net.minecraft.world.phys.Vec3.atCenterOf(errand.cask()), RESTOCK_REACH + 0.5)) {
			if (fill(level, errand.cask(), region(keeper.getVillagerData().getType()).young())) {
				keeper.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
				level.playSound(null, errand.cask(), BarrelRegistry.BARREL_TAP.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
			}
			restocked(keeper, now);
		} else if (now > errand.until()) {
			restocked(keeper, now);
		} else {
			BlockPos front = front(level, errand.cask());
			if (!keeper.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(target -> target.getTarget().currentBlockPosition().equals(front)).orElse(false)) {
				keeper.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(front, 0.5F, 0));
			}
		}
	}

	private static void restocked(Villager keeper, long now) {
		RESTOCKED.set(keeper, now);
		ERRANDS.remove(keeper);
	}

	/**
	 * The cask he restocks: the nearest empty one of a tavern's own casks (placed with the tavern, never one a player put
	 * there: {@link AgingBarrelBlockEntity#isTavernCask}) within {@link #RESTOCK_RANGE} of his kettle that he can walk up
	 * to (the first few nearest are tried), or null.
	 */
	public static @Nullable BlockPos restockCask(ServerLevel level, Villager keeper, BlockPos kettle) {
		List<BlockPos> empty = new java.util.ArrayList<>();
		for (BlockPos pos : jexxme.vineandbarrel.tipsy.BlackoutSpots.tavernBarrels(level, kettle, RESTOCK_RANGE)) {
			if (Math.abs(pos.getY() - kettle.getY()) <= 10 && level.getBlockEntity(pos) instanceof AgingBarrelBlockEntity barrel && barrel.isTavernCask()
				&& barrel.contents().isEmpty()) {
				empty.add(pos);
			}
		}
		empty.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(kettle)));
		for (BlockPos cask : empty.subList(0, Math.min(4, empty.size()))) {
			// to the cell in front of its tap, where he stands to fill it
			var path = keeper.getNavigation().createPath(front(level, cask), 0);
			if (path != null && path.canReach()) {
				return cask;
			}
		}
		return null;
	}

	/** The cell in front of a cask's tap. */
	private static BlockPos front(ServerLevel level, BlockPos cask) {
		BlockState state = level.getBlockState(cask);
		return state.hasProperty(AgingBarrelBlock.FACING) ? cask.relative(state.getValue(AgingBarrelBlock.FACING)) : cask;
	}

	/** Pours {@link #RESTOCK_SERVINGS} of {@code drink}'s batch into the empty cask, fermented (young), as tavern stock. */
	public static boolean fill(ServerLevel level, BlockPos pos, Item drink) {
		AgingRecipe recipe = recipeOf(level, drink);
		return recipe != null && level.getBlockEntity(pos) instanceof AgingBarrelBlockEntity barrel
			&& barrel.stock(recipe, RESTOCK_SERVINGS, recipe.fermentingTime() + recipe.agingTime() / 6L);
	}

	/**
	 * A drink finished alone near a keeper who can see it: he clinks with you (the Cheers sound and sparkles at him) and the
	 * drinker gets the Cheers bonus; each one is a little good word about the drinker. Returns whether a keeper did.
	 */
	public static boolean cheers(ServerLevel level, Player drinker, boolean mug) {
		Villager keeper = null;
		for (Villager villager : level.getEntitiesOfClass(Villager.class, drinker.getBoundingBox().inflate(CHEERS_RANGE), TavernKeeper::isKeeper)) {
			if (!villager.isSleeping() && !villager.isBaby() && villager.hasLineOfSight(drinker)
				&& (keeper == null || villager.distanceToSqr(drinker) < keeper.distanceToSqr(drinker))) {
				keeper = villager;
			}
		}
		if (keeper == null) {
			return false;
		}
		keeper.getLookControl().setLookAt(drinker, 30.0F, 30.0F);
		level.playSound(null, keeper.getX(), keeper.getEyeY(), keeper.getZ(), mug ? BarrelRegistry.CHEERS_MUG.get() : BarrelRegistry.CHEERS_GLASS.get(),
			net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.1F);
		level.sendParticles(BarrelRegistry.CHEERS.get(), keeper.getX(), keeper.getEyeY() + 0.3, keeper.getZ(), 4, 0.25, 0.15, 0.25, 0.0);
		keeper.getGossips().add(drinker.getUUID(), GossipType.MINOR_POSITIVE, CHEERS_GOSSIP);
		return true;
	}

	// ------------------------------------------------------------------ the master's sealed cask

	/**
	 * The master's sealed cask, made when the trade is drawn: an Aging Barrel holding {@code servings} of {@code drink}'s
	 * batch, aged (a day past it), so it places full and sealed. From the world's own aging recipes, so it matches what
	 * brews (26.x: the {@code vineandbarrel:sealed_cask} loot function of the trade's data file).
	 */
	public static ItemStack sealedCask(ServerLevel level, Item drink, int servings) {
		ItemStack stack = new ItemStack(BarrelRegistry.AGING_BARREL_ITEM.get());
		AgingRecipe recipe = recipeOf(level, drink);
		if (recipe != null) {
			BarrelContents batch = recipe.newBatch().withServings(servings)
				.withElapsed((long) recipe.fermentingTime() + recipe.agingTime() + 24000L);
			jexxme.vineandbarrel.barrel.AgingBarrelItem.setContents(stack, batch);
		}
		return stack;
	}

	/**
	 * Makes sure the kettle at {@code pos} is listed as a job site. The game lists a section's points of interest once,
	 * when it first loads it; a kettle placed before 1.2 in a section that already had one (a bed, a bell) was never
	 * listed, and only placing it again would list it.
	 */
	public static void ensureJobSite(ServerLevel level, BlockPos pos, BlockState state) {
		var type = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state);
		if (type.isPresent() && type.get().is(JOB_SITE) && level.getPoiManager().getType(pos).isEmpty()) {
			level.getPoiManager().add(pos, type.get());
		}
	}

	/** For tests: whether this state is a keeper's job site. */
	public static boolean isJobSite(BlockState state) {
		return net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state).map(type -> type.is(JOB_SITE)).orElse(false);
	}
}
