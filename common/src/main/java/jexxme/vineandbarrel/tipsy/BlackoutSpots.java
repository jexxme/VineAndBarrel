package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.tag.VBTags;
import jexxme.vineandbarrel.world.village.VillageStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Where each {@link BlackoutEnding} can happen around a player, and the safety rules every wake-up spot follows: solid
 * ground, room to stand, no liquid, nothing that burns or pricks within a block, no monster close by (in chunks whose
 * entities are loaded, so that is known), inside the world border. Every finder returns null when its ending isn't
 * possible here.
 * <p>
 * <b>Nobody wakes up inside someone's build.</b> The ground endings (in the open, the farmyard, the hay, the roof) and
 * the boat need a spot under the open sky: nothing that blocks movement above it, glass included, in a dimension that
 * has a sky at all (never the Nether's bedrock roof or the End's spikes). The two that take you indoors only use what
 * world generation built for everyone: a tavern of ours, and a villager's bed in a generated village house. Your own
 * bed is your own.
 */
public final class BlackoutSpots
{
	public static final int OPEN_MIN = 20, OPEN_MAX = 60;
	/** You stumble home from down the road, not from across the world: a blackout is no way to travel. */
	public static final int HOME_RANGE = 128;
	public static final int ANIMAL_RANGE = 64;
	public static final int HAY_RANGE = 40;
	public static final int TAVERN_RANGE = 96;
	public static final int VILLAGER_RANGE = 64;
	/** The villager has to be this close to its bed to stand next to it. */
	public static final int VILLAGER_NEAR_BED = 16;
	public static final int ROOF_RANGE = 32;
	/**
	 * A roof stands at least this high above the ground around it, and nowhere within {@link #ROOF_EDGE} blocks does the
	 * ground lie lower than the cap below it: walking off any side is a bump (at most 5 damage), never a cliff.
	 */
	public static final int ROOF_MIN_HEIGHT = 4, ROOF_MAX_HEIGHT = 8;
	/** The ground around a roof is measured this far out from it. */
	static final int ROOF_RING = 5;
	/** How far around a roof the fall is checked. */
	static final int ROOF_EDGE = 8;
	public static final int BOAT_RANGE = 48;
	/**
	 * The farmyard's feet are this many blocks from the animal (or one more), and the tavern's from the barrel: the body
	 * lying toward it ends a block or two short of it.
	 */
	public static final int FARMYARD_FROM = 3, TAVERN_FROM = 3;
	/** No monster this close to a wake-up spot. */
	public static final int MONSTER_FREE = 8;

	private BlackoutSpots() {
	}

	/**
	 * Where a blackout ends: the ending, the exact spot and facing, and what belongs to it (the bed slept in, the animal
	 * or villager that stares).
	 */
	public record Wake(BlackoutEnding ending, Vec3 spot, float yaw, @Nullable BlockPos bed, @Nullable Entity friend) {
		static Wake at(BlackoutEnding ending, Vec3 spot, float yaw) {
			return new Wake(ending, spot, yaw, null, null);
		}
	}

	/** {@return where {@code ending} can happen for this player now, or null if it can't} */
	public static @Nullable Wake find(BlackoutEnding ending, ServerPlayer player, RandomSource random) {
		ServerLevel level = player.level();
		BlockPos from = player.blockPosition();
		if (ending.needsOpenSky() && !hasSky(level)) {
			return null;
		}
		return switch (ending) {
			case HOME -> home(player);
			case IN_THE_OPEN -> inTheOpen(level, from, random);
			case FARMYARD -> farmyard(level, player.position(), ANIMAL_RANGE, random);
			case IN_THE_HAY -> inTheHay(level, from, HAY_RANGE, random);
			case TAVERN -> tavern(level, from, TAVERN_RANGE, random);
			case SOMEONE_ELSES_BED -> someoneElsesBed(level, player, VILLAGER_RANGE, random);
			case ON_THE_ROOF -> onTheRoof(level, from, ROOF_RANGE, random);
			case IN_A_BOAT -> inABoat(level, from, BOAT_RANGE, random);
			case RIGHT_HERE -> new Wake(ending, player.position(), Blackout.fallYaw(player), null, null);
		};
	}

	// ------------------------------------------------------------------ safety

	/**
	 * {@return where a player can lie down safely with their feet in this block (on top of whatever floor it has: a
	 * slab, a path, a full block), or null}
	 */
	public static @Nullable Vec3 standAt(ServerLevel level, BlockPos feet) {
		if (feet.getY() <= level.getMinY() + 1 || !level.hasChunkAt(feet)) {
			return null;
		}
		Vec3 spot = DismountHelper.findSafeDismountLocation(EntityTypes.PLAYER, level, feet, true);
		return spot != null && isSafe(level, spot) ? spot : null;
	}

	/** {@return whether a player may wake up with their feet at {@code spot}: the rules of {@link #standAt}} */
	public static boolean isSafe(ServerLevel level, Vec3 spot) {
		AABB body = EntityTypes.PLAYER.getDimensions().makeBoundingBox(spot);
		if (!level.getWorldBorder().isWithinBounds(body) || !level.noCollision(body)) {
			return false;
		}
		BlockPos feet = BlockPos.containing(spot);
		BlockPos floor = BlockPos.containing(spot.x, spot.y - 0.01, spot.z);
		BlockState ground = level.getBlockState(floor);
		if (ground.getCollisionShape(level, floor).isEmpty() || !ground.getFluidState().isEmpty()) {
			return false;
		}
		for (BlockPos around : BlockPos.betweenClosed(floor.offset(-1, -1, -1), feet.offset(1, 2, 1))) {
			BlockState state = level.getBlockState(around);
			FluidState fluid = state.getFluidState();
			if (EntityTypes.PLAYER.isBlockDangerous(state) || fluid.is(FluidTags.LAVA)) {
				return false;
			}
			// Water around is fine (a pond next to you), but not where you lie
			if (!fluid.isEmpty() && (around.equals(feet) || around.equals(feet.above()))) {
				return false;
			}
		}
		return noMonsters(level, body);
	}

	/**
	 * {@return whether no monster is near this box; false too when the entities there aren't loaded yet (a chunk just
	 * loaded from disk gets its entities a little later), since then nobody knows}
	 */
	public static boolean noMonsters(ServerLevel level, AABB box) {
		AABB around = box.inflate(MONSTER_FREE);
		for (int cx = SectionPos.blockToSectionCoord(around.minX); cx <= SectionPos.blockToSectionCoord(around.maxX); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(around.minZ); cz <= SectionPos.blockToSectionCoord(around.maxZ); cz++) {
				if (!level.areEntitiesLoaded(ChunkPos.pack(cx, cz))) {
					return false;
				}
			}
		}
		return level.getEntitiesOfClass(Entity.class, around, e -> e instanceof Enemy && e.isAlive()).isEmpty();
	}

	/**
	 * {@return whether this dimension has an open sky at all: a sky like the overworld's, with sky light and no ceiling.
	 * Not the Nether under its roof, and not the End (which has sky light in 26.x, but its void and spikes)}
	 */
	public static boolean hasSky(ServerLevel level) {
		DimensionType type = level.dimensionType();
		return type.hasSkyLight() && !type.hasCeiling() && type.skybox() == DimensionType.Skybox.OVERWORLD;
	}

	/**
	 * {@return whether nothing that blocks movement (glass and leaves included) is above this spot, in a dimension with a
	 * sky: outdoors, not inside anyone's build}
	 */
	public static boolean underOpenSky(ServerLevel level, Vec3 spot) {
		BlockPos feet = BlockPos.containing(spot);
		return hasSky(level) && loaded(level, feet.getX(), feet.getZ())
			&& level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ()) <= feet.getY();
	}

	/** {@return whether this block lies in a piece of a generated village (a house, a street, a tavern)} */
	public static boolean inVillage(ServerLevel level, BlockPos pos) {
		return VillageStructures.villageAt(level, pos).isValid();
	}

	// ------------------------------------------------------------------ room to lie on the face

	/**
	 * A body lying on its face reaches this far ahead of its feet (blocks, to the top of the head); the body (torso, head,
	 * legs) is this wide either side of the middle, and the arms reach this far out by the shoulders and hips.
	 */
	static final double LIE_LENGTH = 2.05, BODY_HALF = 0.22, ARM_REACH = 0.6;
	/** The arms lie out to the sides between these distances ahead of the feet, and rise this high getting up (blocks). */
	private static final double ARMS_FROM = 0.75, ARMS_TO = 1.5, ARMS_HIGH = 0.6;
	/** The ways round tried for a body that has no room ahead (degrees from where it faces): the nearest first. */
	private static final float[] LIE_TURNS = {0.0F, 22.5F, -22.5F, 45.0F, -45.0F, 67.5F, -67.5F, 90.0F, -90.0F, 112.5F, -112.5F, 135.0F, -135.0F,
		157.5F, -157.5F, 180.0F};
	/**
	 * On a small pile of hay, the body only needs hay under the hips, belly and chest (this far ahead of the feet, blocks):
	 * the legs and the head may stick out over the edge of the pile. Hay piles are small humps of bales at two heights.
	 */
	private static final double HAY_FROM = 1.0, HAY_TO = 1.8;

	/**
	 * {@return whether a player with the feet at {@code feet} has room to fall on the face facing {@code yaw}, lie there and
	 * get up again} Under the body (torso, head, legs) the floor goes on level for its whole length, no edge to hang
	 * over and no step up into, and nothing is in its way as it goes down (the quarter circle the head sweeps); out to
	 * the sides the arms only need nothing to be inside (one may hang over the edge of a haystack or a roof). A sixteenth
	 * of a block either way is the same floor (a carpet, a path). Both sides use it, the server for where a player falls
	 * and for the wake-up spots, so it only reads blocks.
	 */
	public static boolean lieRoom(CollisionGetter level, Vec3 feet, float yaw) {
		return lieRoom(level, feet, yaw, 0.0, LIE_LENGTH);
	}

	/** {@link #lieRoom} with the level floor needed under the body only from {@code from} to {@code to} blocks ahead of the feet. */
	private static boolean lieRoom(CollisionGetter level, Vec3 feet, float yaw, double from, double to) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 across = new Vec3(-ahead.z, 0.0, ahead.x);
		for (double d = 0.25; d <= LIE_LENGTH + 1.0E-6; d += 0.25) {
			double high = Math.min(1.9, Math.sqrt(Math.max(0.0, (LIE_LENGTH + 0.1) * (LIE_LENGTH + 0.1) - d * d)) + 0.1);
			for (double side : new double[]{-BODY_HALF, 0.0, BODY_HALF}) {
				Vec3 at = feet.add(ahead.scale(d)).add(across.scale(side));
				if (!level.noCollision(new AABB(at.x - 0.08, feet.y + 0.07, at.z - 0.08, at.x + 0.08, feet.y + high, at.z + 0.08))
					|| d >= from - 1.0E-6 && d <= to + 1.0E-6
					&& level.noCollision(new AABB(at.x - 0.05, feet.y - 0.07, at.z - 0.05, at.x + 0.05, feet.y - 0.01, at.z + 0.05))) {
					return false;
				}
			}
			if (d >= ARMS_FROM - 1.0E-6 && d <= ARMS_TO + 1.0E-6) {
				for (double side : new double[]{-ARM_REACH, ARM_REACH}) {
					Vec3 at = feet.add(ahead.scale(d)).add(across.scale(side));
					if (!level.noCollision(new AABB(at.x - 0.1, feet.y + 0.07, at.z - 0.1, at.x + 0.1, feet.y + ARMS_HIGH, at.z + 0.1))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * {@return the way a player at {@code feet} facing {@code yaw} falls on the face: that way if there is room
	 * ({@link #lieRoom}), else the nearest way round that has room, else null}
	 */
	public static @Nullable Float lieYaw(CollisionGetter level, Vec3 feet, float yaw) {
		return lieYaw(level, feet, yaw, 0.0, LIE_LENGTH);
	}

	private static @Nullable Float lieYaw(CollisionGetter level, Vec3 feet, float yaw, double from, double to) {
		for (float turn : LIE_TURNS) {
			if (lieRoom(level, feet, yaw + turn, from, to)) {
				return Mth.wrapDegrees(yaw + turn);
			}
		}
		return null;
	}

	/** {@return the way a player at {@code feet} facing {@code yaw} falls on the face ({@link #lieYaw}), or {@code yaw} itself where nothing has room} */
	public static float fallYaw(CollisionGetter level, Vec3 feet, float yaw) {
		Float lie = lieYaw(level, feet, yaw);
		return lie == null ? yaw : lie;
	}

	/**
	 * {@return the way a player at {@code feet}, who may slide on to {@code stop} before the body is down, falls on the
	 * face: the nearest way round with room from both; else with room from where they stop, else from where they are;
	 * else {@code yaw} itself}
	 */
	public static float fallYaw(CollisionGetter level, Vec3 feet, Vec3 stop, float yaw) {
		if (feet.distanceToSqr(stop) > 0.01) {
			for (float turn : LIE_TURNS) {
				if (lieRoom(level, feet, yaw + turn) && lieRoom(level, stop, yaw + turn)) {
					return Mth.wrapDegrees(yaw + turn);
				}
			}
			Float lie = lieYaw(level, stop, yaw);
			if (lie != null) {
				return lie;
			}
		}
		return fallYaw(level, feet, yaw);
	}

	/** {@return a yaw that looks from {@code from} toward {@code to}} */
	static float yawTo(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
	}

	private static boolean loaded(ServerLevel level, int x, int z) {
		return level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)) != null;
	}

	/** {@return the first free block above the surface of this column (the chunk must be loaded)} */
	private static int surface(ServerLevel level, Heightmap.Types type, int x, int z) {
		return level.getHeight(type, x, z);
	}

	// ------------------------------------------------------------------ beds

	/**
	 * {@return the head of the bed at {@code pos} (either half), if it is a bed nobody lies in, a player fits in and it
	 * survives being lain in (not a straw bed, which falls apart when you leave it; not a bed that blows up here)}
	 */
	public static @Nullable BlockPos freeBedHead(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof AbstractBedBlock)) {
			return null;
		}
		Direction facing = state.getValue(AbstractBedBlock.FACING);
		BlockPos head = state.getValue(AbstractBedBlock.PART) == BedPart.HEAD ? pos : pos.relative(facing);
		BlockState headState = level.getBlockState(head);
		if (!(headState.getBlock() instanceof AbstractBedBlock bed) || headState.getValue(AbstractBedBlock.PART) != BedPart.HEAD
			|| headState.getValue(AbstractBedBlock.OCCUPIED) || bed.getSleepHeight(headState, level, head).isEmpty()) {
			return null;
		}
		BedRule rule = bed.getBedRule(level, head);
		if (rule.destroyOnUse() || rule.destroyOnLeave()) {
			return null;
		}
		BlockPos foot = head.relative(facing.getOpposite());
		for (BlockPos part : List.of(head.above(), foot.above())) {
			if (!level.getBlockState(part).getCollisionShape(level, part).isEmpty() || !level.getFluidState(part).isEmpty()) {
				return null;
			}
		}
		// Somewhere safe to get up again, as vanilla looks for it
		Vec3 standUp = AbstractBedBlock.findStandUpPosition(EntityTypes.PLAYER, level, head, facing, facing.toYRot()).orElse(null);
		if (standUp == null || !isSafe(level, standUp)) {
			return null;
		}
		return head;
	}

	/**
	 * Home: the player's own bed, if it is in this dimension, within {@link #HOME_RANGE} blocks (across the ground), in a
	 * loaded chunk, still stands and is free.
	 */
	public static @Nullable Wake home(ServerPlayer player) {
		ServerPlayer.RespawnConfig config = player.getRespawnConfig();
		ServerLevel level = player.level();
		if (config == null || !config.respawnData().dimension().equals(level.dimension())) {
			return null;
		}
		BlockPos pos = config.respawnData().pos();
		// Only a bed in a loaded chunk: one just read from disk has no entities yet, so nobody could tell whether a
		// monster waits next to it (and it is no use loading chunks for a blackout)
		if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !loaded(level, pos.getX(), pos.getZ())
			|| Math.hypot(pos.getX() + 0.5 - player.getX(), pos.getZ() + 0.5 - player.getZ()) > HOME_RANGE) {
			return null;
		}
		BlockPos head = freeBedHead(level, pos);
		if (head == null) {
			return null;
		}
		return new Wake(BlackoutEnding.HOME, Vec3.atBottomCenterOf(head), level.getBlockState(head).getValue(AbstractBedBlock.FACING).toYRot(), head, null);
	}

	/**
	 * Someone else's bed: a villager's bed in a generated village house nearby that is free, with its villager awake and
	 * close to it (the villager isn't moved: a trading hall stays as it is; and a bed in a player's own build never counts).
	 * That villager is the friend who will shake its head. It keeps its bed: see {@code ValidateNearbyPoiMixin}.
	 */
	public static @Nullable Wake someoneElsesBed(ServerLevel level, ServerPlayer player, int range, RandomSource random) {
		List<Villager> villagers = new ArrayList<>(level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(range),
			v -> v.isAlive() && !v.isSleeping()));
		shuffle(villagers, random);
		BlockPos own = ownBed(player);
		for (Villager villager : villagers) {
			GlobalPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
			if (home == null || !home.dimension().equals(level.dimension()) || !level.hasChunkAt(home.pos())
				|| villager.blockPosition().distSqr(home.pos()) > VILLAGER_NEAR_BED * VILLAGER_NEAR_BED) {
				continue;
			}
			BlockPos head = freeBedHead(level, home.pos());
			if (head == null || head.equals(own) || !inVillage(level, head)) {
				continue;
			}
			return new Wake(BlackoutEnding.SOMEONE_ELSES_BED, Vec3.atBottomCenterOf(head), level.getBlockState(head).getValue(AbstractBedBlock.FACING).toYRot(),
				head, villager);
		}
		return null;
	}

	/** {@return the head of the player's own bed in this dimension, or null} */
	private static @Nullable BlockPos ownBed(ServerPlayer player) {
		ServerPlayer.RespawnConfig config = player.getRespawnConfig();
		if (config == null || !config.respawnData().dimension().equals(player.level().dimension())) {
			return null;
		}
		BlockPos pos = config.respawnData().pos();
		BlockState state = player.level().getBlockState(pos);
		if (!(state.getBlock() instanceof AbstractBedBlock)) {
			return pos;
		}
		return state.getValue(AbstractBedBlock.PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(AbstractBedBlock.FACING));
	}

	// ------------------------------------------------------------------ on the ground

	/** In the open: a safe spot on the surface 20-60 blocks away, under the open sky, with room to lie on the face. */
	public static @Nullable Wake inTheOpen(ServerLevel level, BlockPos from, RandomSource random) {
		if (!hasSky(level)) {
			return null;
		}
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = OPEN_MIN + random.nextInt(OPEN_MAX - OPEN_MIN + 1);
			int x = from.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = from.getZ() + (int) Math.round(Math.sin(angle) * distance);
			if (!loaded(level, x, z)) {
				continue;
			}
			Vec3 spot = standAt(level, new BlockPos(x, surface(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z));
			Float yaw = spot != null && underOpenSky(level, spot) ? lieYaw(level, spot, random.nextFloat() * 360.0F) : null;
			if (yaw != null) {
				return Wake.at(BlackoutEnding.IN_THE_OPEN, spot, yaw);
			}
		}
		return null;
	}

	/**
	 * Farmyard: lying on the face in front of an animal on land, the head a block or two short of it, facing it (it will
	 * stare back), under the open sky (a pasture, not a barn). Animals that might bite or sting back (wolves, bees, polar
	 * bears; anything hostile) don't count.
	 */
	public static @Nullable Wake farmyard(ServerLevel level, Vec3 from, int range, RandomSource random) {
		if (!hasSky(level)) {
			return null;
		}
		List<Animal> animals = new ArrayList<>(level.getEntitiesOfClass(Animal.class, new AABB(from, from).inflate(range),
			a -> a.isAlive() && !(a instanceof NeutralMob) && !(a instanceof Enemy) && !a.isInWater() && !a.isPassenger()));
		shuffle(animals, random);
		for (Animal animal : animals.subList(0, Math.min(animals.size(), 12))) {
			BlockPos at = animal.blockPosition();
			List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
			shuffle(sides, random);
			for (Direction side : sides) {
				for (int out = FARMYARD_FROM; out <= FARMYARD_FROM + 1; out++) {
					for (int dy : new int[]{0, 1, -1}) {
						BlockPos feet = at.relative(side, out).above(dy);
						Vec3 spot = standAt(level, feet);
						if (spot != null && underOpenSky(level, spot) && sees(level, spot, animal)
							&& !animal.getBoundingBox().intersects(EntityTypes.PLAYER.getDimensions().makeBoundingBox(spot))
							&& lieRoom(level, spot, yawTo(spot, animal.position()))) {
							return new Wake(BlackoutEnding.FARMYARD, spot, yawTo(spot, animal.position()), null, animal);
						}
					}
				}
			}
		}
		return null;
	}

	/** {@return whether nothing solid stands between a player lying at {@code spot} and the entity's eyes} */
	private static boolean sees(ServerLevel level, Vec3 spot, Entity entity) {
		Vec3 eyes = spot.add(0.0, 0.5, 0.0);
		return level.clip(new ClipContext(eyes, entity.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS;
	}

	/**
	 * In the hay: on top of a pile of bales ({@code #vineandbarrel:hay_bales}) under the open sky (a haystack, not a barn), lying on
	 * the face: on hay all along where there is a stack big enough, else across a small pile (the hips, belly and chest on
	 * the hay, the legs and the head over its edge); never on a single bale.
	 */
	public static @Nullable Wake inTheHay(ServerLevel level, BlockPos from, int range, RandomSource random) {
		if (!hasSky(level)) {
			return null;
		}
		List<BlockPos> tops = new ArrayList<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = from.getX() - range; x <= from.getX() + range; x++) {
			for (int z = from.getZ() - range; z <= from.getZ() + range; z++) {
				if (!loaded(level, x, z)) {
					continue;
				}
				// From a little above the heightmap: a modded bale outside #minecraft:blocks_motion isn't in it
				int top = surface(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				for (int y = top + 1; y >= top - 2 && y > level.getMinY(); y--) {
					cursor.set(x, y, z);
					if (level.getBlockState(cursor).is(VBTags.HAY_BALES)) {
						tops.add(cursor.above());
					}
				}
			}
		}
		// Any of them, tried in a random order until one has hay enough to lie on whole; else, on a small pile, the first with
		// hay under the middle of the body (the legs and the head over its edge)
		shuffle(tops, random);
		List<Vec3> spots = new ArrayList<>();
		for (BlockPos feet : tops) {
			Vec3 spot = standAt(level, feet);
			if (spot == null || !underOpenSky(level, spot)) {
				continue;
			}
			Float yaw = lieYaw(level, spot, random.nextFloat() * 360.0F);
			if (yaw != null) {
				return Wake.at(BlackoutEnding.IN_THE_HAY, spot, yaw);
			}
			spots.add(spot);
		}
		for (Vec3 spot : spots) {
			Float yaw = lieYaw(level, spot, random.nextFloat() * 360.0F, HAY_FROM, HAY_TO);
			if (yaw != null) {
				return Wake.at(BlackoutEnding.IN_THE_HAY, spot, yaw);
			}
		}
		return null;
	}

	/**
	 * Tavern: lying on the face in front of one of the barrels of a tavern of ours nearby, toward it; where the floor in
	 * front of it has no room for that, lying along the row of barrels two blocks in front of them.
	 */
	public static @Nullable Wake tavern(ServerLevel level, BlockPos from, int range, RandomSource random) {
		List<BlockPos> barrels = tavernBarrels(level, from, range);
		shuffle(barrels, random);
		for (BlockPos barrel : barrels) {
			Direction front = level.getBlockState(barrel).getValue(AgingBarrelBlock.FACING);
			for (int out = TAVERN_FROM; out <= TAVERN_FROM + 1; out++) {
				Vec3 toward = standAt(level, barrel.relative(front, out));
				if (toward != null && lieRoom(level, toward, front.getOpposite().toYRot())) {
					return Wake.at(BlackoutEnding.TAVERN, toward, front.getOpposite().toYRot());
				}
			}
			// Else along the row of barrels, two blocks out (right in front of them an arm would lie in them)
			Vec3 spot = standAt(level, barrel.relative(front, 2));
			for (Direction along : new Direction[]{front.getClockWise(), front.getCounterClockWise()}) {
				if (spot != null && lieRoom(level, spot, along.toYRot())) {
					return Wake.at(BlackoutEnding.TAVERN, spot, along.toYRot());
				}
			}
		}
		return null;
	}

	/** {@return every Aging Barrel within {@code range} blocks that stands in one of our taverns} */
	public static List<BlockPos> tavernBarrels(ServerLevel level, BlockPos from, int range) {
		List<BlockPos> barrels = new ArrayList<>();
		int chunks = SectionPos.blockToSectionCoord(range) + 1;
		int cx = SectionPos.blockToSectionCoord(from.getX()), cz = SectionPos.blockToSectionCoord(from.getZ());
		for (int x = cx - chunks; x <= cx + chunks; x++) {
			for (int z = cz - chunks; z <= cz + chunks; z++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity entity : chunk.getBlockEntities().values()) {
					BlockPos pos = entity.getBlockPos();
					if (entity instanceof AgingBarrelBlockEntity && pos.closerThan(from, range) && inTavern(level, pos)) {
						barrels.add(pos);
					}
				}
			}
		}
		return barrels;
	}

	/** {@return whether this block lies inside a tavern of a village, its cellar under the tavern piece included} */
	public static boolean inTavern(ServerLevel level, BlockPos pos) {
		return VillageStructures.inTavern(level, pos);
	}

	/**
	 * On the roof: the highest spot nearby that stands 4 to 8 blocks above the ground around it (measured as the middle of
	 * eight columns 5 blocks out), with no lower ground than that anywhere within 8 blocks (so no cliff edge), under the
	 * open sky, flat enough to lie on the face there. A house roof, a tower, a tall rock.
	 */
	public static @Nullable Wake onTheRoof(ServerLevel level, BlockPos from, int range, RandomSource random) {
		if (!hasSky(level)) {
			return null;
		}
		List<BlockPos> roofs = new ArrayList<>();
		int[][] ring = {{ROOF_RING, 0}, {-ROOF_RING, 0}, {0, ROOF_RING}, {0, -ROOF_RING}, {ROOF_RING, ROOF_RING}, {-ROOF_RING, ROOF_RING},
			{ROOF_RING, -ROOF_RING}, {-ROOF_RING, -ROOF_RING}};
		int[] around = new int[ring.length];
		for (int x = from.getX() - range; x <= from.getX() + range; x++) {
			for (int z = from.getZ() - range; z <= from.getZ() + range; z++) {
				if (!loaded(level, x, z)) {
					continue;
				}
				int top = surface(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				boolean ringLoaded = true;
				for (int i = 0; i < ring.length && ringLoaded; i++) {
					int rx = x + ring[i][0], rz = z + ring[i][1];
					ringLoaded = loaded(level, rx, rz);
					around[i] = ringLoaded ? surface(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rx, rz) : 0;
				}
				if (!ringLoaded) {
					continue;
				}
				Arrays.sort(around);
				int ground = (around[3] + around[4]) / 2;
				if (top - ground >= ROOF_MIN_HEIGHT && top - ground <= ROOF_MAX_HEIGHT) {
					roofs.add(new BlockPos(x, top, z));
				}
			}
		}
		// The highest first (a ridge, a tower's top), in a random order among the same height, until one is safe and has
		// room to lie on the face
		shuffle(roofs, random);
		roofs.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
		for (BlockPos feet : roofs) {
			Vec3 spot = standAt(level, feet);
			if (spot == null || !underOpenSky(level, spot) || !gentleFall(level, feet.getX(), feet.getZ(), feet.getY())) {
				continue;
			}
			Float yaw = lieYaw(level, spot, random.nextFloat() * 360.0F);
			if (yaw != null) {
				return Wake.at(BlackoutEnding.ON_THE_ROOF, spot, yaw);
			}
		}
		return null;
	}

	/** {@return whether walking off this roof (feet at {@code top}) in any direction lands at most the cap lower} */
	static boolean gentleFall(ServerLevel level, int x, int z, int top) {
		for (int dx = -ROOF_EDGE; dx <= ROOF_EDGE; dx++) {
			for (int dz = -ROOF_EDGE; dz <= ROOF_EDGE; dz++) {
				if (!loaded(level, x + dx, z + dz)
					|| top - surface(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) > ROOF_MAX_HEIGHT) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * In a boat: calm water under the open sky (2x2 still water with air above) nearby, where a boat fits, nothing else
	 * floats and no monster swims ({@link #boatSafe}). The spot is where the boat goes, in the middle of the four blocks.
	 */
	public static @Nullable Wake inABoat(ServerLevel level, BlockPos from, int range, RandomSource random) {
		if (!hasSky(level)) {
			return null;
		}
		List<Vec3> spots = new ArrayList<>();
		for (int x = from.getX() - range; x <= from.getX() + range; x++) {
			for (int z = from.getZ() - range; z <= from.getZ() + range; z++) {
				if (!loaded(level, x, z) || !loaded(level, x + 1, z + 1)) {
					continue;
				}
				int top = surface(level, Heightmap.Types.WORLD_SURFACE, x, z);
				BlockPos water = new BlockPos(x, top - 1, z);
				if (calmWater(level, water) && calmWater(level, water.east()) && calmWater(level, water.south()) && calmWater(level, water.south().east())) {
					Vec3 spot = new Vec3(x + 1.0, water.getY() + 1.0, z + 1.0);
					if (boatSafe(level, spot)) {
						spots.add(spot);
					}
				}
			}
		}
		spots.sort(Comparator.comparingDouble(s -> s.distanceToSqr(Vec3.atCenterOf(from))));
		// Not always the closest: any of the nearer half
		Vec3 spot = spots.isEmpty() ? null : spots.get(random.nextInt(Math.max(1, (spots.size() + 1) / 2)));
		return spot == null ? null : Wake.at(BlackoutEnding.IN_A_BOAT, spot, random.nextFloat() * 360.0F);
	}

	/**
	 * {@return whether a boat with its rider may wait at {@code spot} (on the water's surface): room for both, inside the
	 * world border, under the open sky, nothing else there (another boat, a turtle) and no monster near, drowned and
	 * guardians included}
	 */
	public static boolean boatSafe(ServerLevel level, Vec3 spot) {
		AABB boat = EntityTypes.OAK_BOAT.getDimensions().makeBoundingBox(spot);
		AABB both = boat.expandTowards(0.0, EntityTypes.PLAYER.getDimensions().height(), 0.0);
		return level.getWorldBorder().isWithinBounds(both) && level.noCollision(both) && underOpenSky(level, spot)
			&& level.getEntities((Entity) null, both.inflate(0.5)).isEmpty() && noMonsters(level, both);
	}

	/** Still water you can float on: a water source with two free blocks of air above it. */
	public static boolean calmWater(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		FluidState fluid = state.getFluidState();
		if (!fluid.is(FluidTags.WATER) || !fluid.isSource() || !state.getCollisionShape(level, pos).isEmpty() || state.hasProperty(
			net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
			return false;
		}
		for (BlockPos up : List.of(pos.above(), pos.above(2))) {
			BlockState above = level.getBlockState(up);
			if (!above.isAir()) {
				return false;
			}
		}
		return true;
	}

	private static <T> void shuffle(List<T> list, RandomSource random) {
		for (int i = list.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			T swap = list.get(i);
			list.set(i, list.get(j));
			list.set(j, swap);
		}
	}
}
