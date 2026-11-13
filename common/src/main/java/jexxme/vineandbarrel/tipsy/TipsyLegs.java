package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.tipsy.TipsyLevels.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Unsteady legs at Tipsy V (Jexxme: "I can barely walk"). While walking, now and then one roll: most of the time a
 * <b>stumble</b> (a lurch to the side, the foot scuffs, a catch step), and about one in twenty (more when sprinting)
 * the stumble that isn't caught: a <b>trip</b>, forward onto the face, a moment on the ground, and up again. Neither
 * ever hurts or moves you anywhere you didn't walk: a trip only happens on flat solid ground with room ahead and no
 * monster near (otherwise it stays a stumble), never sneaking, swimming, riding or flying, and not twice in a row.
 * Both are switched off with the server's {@code stumbling} setting. The client draws them ({@link TipsyMotion}).
 */
public final class TipsyLegs
{
	/** Sideways speed of a stumble: about a step to the side. */
	public static final double STUMBLE_NUDGE = 0.34;
	/** Horizontal speed (blocks/tick) above which an entity counts as walking. Sneaking is slower and never stumbles. */
	private static final double WALKING_SPEED = 0.06;
	/** After a trip, none for this long (30 s). */
	public static final int TRIP_COOLDOWN = 30 * 20;
	/** No trip with a monster this close. */
	public static final double MONSTER_FREE = 10.0;
	/** A trip needs flat ground and free room this far ahead: where the body lands. */
	private static final double LANDING = 2.0;
	/**
	 * No trip faster than this (blocks/tick, a sprint on grip is about 0.28): on grip the body stops within a block,
	 * well inside {@link #LANDING}; anything faster is sliding.
	 */
	private static final double MAX_TRIP_SPEED = 0.33;
	/** No trip on slippery ground (vanilla's default friction is 0.6; ice is 0.98). */
	private static final float MAX_FRICTION = 0.6F;

	/** The legs of one entity: ticks into a trip (-1 for none), ticks of trip cooldown left, ticks to the catch step. */
	public record Legs(int trip, int cooldown, int catchStep)
	{
		public static final Legs STEADY = new Legs(-1, 0, 0);
	}

	private TipsyLegs() {
	}

	private static Legs legs(LivingEntity entity) {
		Legs legs = TipsyRegistry.LEGS.getExisting(entity);
		return legs == null ? Legs.STEADY : legs;
	}

	private static void set(LivingEntity entity, Legs legs) {
		if (legs.equals(Legs.STEADY)) {
			if (TipsyRegistry.LEGS.has(entity)) {
				TipsyRegistry.LEGS.remove(entity);
			}
		} else {
			TipsyRegistry.LEGS.set(entity, legs);
		}
	}

	/** {@return whether this entity is down after a trip right now (falling, lying or getting up)} */
	public static boolean isDown(LivingEntity entity) {
		Legs legs = TipsyRegistry.LEGS.getExisting(entity);
		return legs != null && legs.trip() >= 0;
	}

	/** The level's unsteady-legs roll, while walking. Called from {@link Tipsy#tick} unless paused or down. */
	static void tick(LivingEntity entity, Level at, RandomSource random) {
		if (!at.legs().unsteady() || !TipsyConfig.stumbling() || random.nextInt(at.legs().legsOneIn()) != 0) {
			return;
		}
		Vec3 walking = walkingMotion(entity);
		if (walking.lengthSqr() < 1.0E-6) {
			return;
		}
		int tripOneIn = entity.isSprinting() ? TipsyLevels.SPRINT_TRIP_ONE_IN : at.legs().tripOneIn();
		if (tripOneIn > 0 && random.nextInt(tripOneIn) == 0 && trip(entity)) {
			return;
		}
		stumble(entity, walking, random);
	}

	/** The trip's timeline and the catch step's scuff, every tick (also once the level has dropped: a trip ends). */
	static void tickTrip(LivingEntity entity) {
		Legs legs = TipsyRegistry.LEGS.getExisting(entity);
		if (legs == null) {
			return;
		}
		if (legs.catchStep() == 1) {
			TipsySounds.catchStep(entity, ground(entity));
		}
		int trip = legs.trip();
		if (trip >= 0) {
			trip++;
			if (trip == TipsyMotion.TRIP_THUD) {
				thud(entity);
			}
			if (trip >= TipsyMotion.TRIP_TICKS) {
				trip = -1;
			}
		}
		set(entity, new Legs(trip, Math.max(0, legs.cooldown() - 1), Math.max(0, legs.catchStep() - 1)));
	}

	// ------------------------------------------------------------------ the stumble

	/** {@return the horizontal walking motion, or {@link Vec3#ZERO} when standing, sneaking, flying, riding or swimming} */
	public static Vec3 walkingMotion(LivingEntity entity) {
		if (!steadyGround(entity) || entity.isShiftKeyDown()) {
			return Vec3.ZERO;
		}
		Vec3 motion = currentMotion(entity);
		Vec3 horizontal = new Vec3(motion.x, 0.0, motion.z);
		return horizontal.lengthSqr() > WALKING_SPEED * WALKING_SPEED ? horizontal : Vec3.ZERO;
	}

	/**
	 * A stumble: a lurch to a random side, at right angles to the walking direction, the foot scuffing, and a catch
	 * step a moment later. Does nothing without unsteady legs at this level, when stumbling is switched off, or when not
	 * walking.
	 *
	 * @param walking the horizontal walking motion, see {@link #walkingMotion}
	 * @return whether the entity stumbled
	 */
	public static boolean stumble(LivingEntity entity, Vec3 walking, RandomSource random) {
		if (!TipsyConfig.stumbling() || !TipsyLevels.at(Tipsy.getLevel(entity)).legs().unsteady() || walking.lengthSqr() < 1.0E-6
			|| isDown(entity)) {
			return false;
		}
		boolean left = random.nextBoolean();
		Vec3 across = new Vec3(-walking.z, 0.0, walking.x).normalize();
		// The lurch only goes where a step is safe (no edge, no lava, fire or cactus); with neither side safe the legs
		// still give (the scuff, the dip) but the body stays put
		if (!safeStep(entity, across.scale(left ? 1.0 : -1.0))) {
			left = !left;
		}
		Vec3 side = across.scale(left ? 1.0 : -1.0);
		if (safeStep(entity, side)) {
			setMotion(entity, currentMotion(entity).add(side.scale(STUMBLE_NUDGE)));
		}
		TipsySounds.stumble(entity, ground(entity));
		Legs legs = legs(entity);
		set(entity, new Legs(legs.trip(), legs.cooldown(), TipsyMotion.CATCH_STEP_AFTER));
		TipsyMotion.stumble(entity, left);
		return true;
	}

	// ------------------------------------------------------------------ the trip

	/**
	 * A trip: the stumble that isn't caught. Only where it is safe ({@link #canTrip}); the body goes down where it stands.
	 *
	 * @return whether the entity tripped (else the caller lets it stumble)
	 */
	public static boolean trip(LivingEntity entity) {
		if (!TipsyConfig.stumbling() || !TipsyLevels.at(Tipsy.getLevel(entity)).legs().unsteady() || !canTrip(entity)) {
			return false;
		}
		TipsySounds.stumble(entity, ground(entity));
		set(entity, new Legs(0, TRIP_COOLDOWN, 0));
		TipsyMotion.trip(entity);
		return true;
	}

	/**
	 * Whether a trip is safe here: on the ground and walking normally (not sneaking, swimming, riding, flying or
	 * climbing), not cooling down from the last one, on flat solid ground that goes on where the body will land with room
	 * to lie there, and no monster near.
	 */
	public static boolean canTrip(LivingEntity entity) {
		Legs legs = legs(entity);
		if (legs.trip() >= 0 || legs.cooldown() > 0 || !steadyGround(entity) || entity.isShiftKeyDown() || entity.onClimbable()
			|| entity.isInLava() || entity instanceof Player player && Blackout.isOut(player)) {
			return false;
		}
		ServerLevel level = entity.level() instanceof ServerLevel server ? server : null;
		if (level == null) {
			return false;
		}
		Vec3 forward = Vec3.directionFromRotation(0.0F, entity.getYRot());
		BlockPos feet = entity.blockPosition();
		// Flat: standing on the top of a full block (not a slab, a carpet or snow), and the same under the landing spot
		if (Math.abs(entity.getY() - feet.getY()) > 1.0E-3) {
			return false;
		}
		// Not while sliding: the body would go on past where it was checked (ice; a running start is fine on grip)
		Vec3 motion = currentMotion(entity);
		if (motion.horizontalDistance() > MAX_TRIP_SPEED) {
			return false;
		}
		for (double ahead = 0.0; ahead <= LANDING; ahead += 0.5) {
			BlockPos under = BlockPos.containing(entity.position().add(forward.scale(ahead))).below();
			if (under.getY() != feet.getY() - 1 || !safeFloor(level, under)) {
				return false;
			}
		}
		// Room to lie down: nothing in the way of the body, no water, lava or fire where the face lands
		Vec3 from = entity.position();
		Vec3 to = from.add(forward.scale(LANDING));
		AABB body = new AABB(from.x, from.y + 0.05, from.z, to.x, from.y + 0.6, to.z).inflate(0.3, 0.0, 0.3);
		if (!level.noCollision(entity, body) || level.containsAnyLiquid(body)
			|| BlockPos.betweenClosedStream(body).anyMatch(pos -> hurts(level.getBlockState(pos)))) {
			return false;
		}
		return level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(MONSTER_FREE), mob -> mob instanceof Enemy && mob.isAlive()).isEmpty();
	}

	/** The body hits the ground: the thud, and crumbs of what it landed on. */
	private static void thud(LivingEntity entity) {
		BlockState ground = ground(entity);
		TipsySounds.trip(entity, ground);
		if (entity.level() instanceof ServerLevel level && !ground.isAir()) {
			Vec3 face = entity.position().add(Vec3.directionFromRotation(0.0F, entity.getYRot()).scale(1.2));
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), face.x, face.y + 0.05, face.z, 14, 0.35, 0.02, 0.35, 0.05);
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Whether a step about a block to {@code side} lands on safe ground at the same height, with room to stand. */
	private static boolean safeStep(LivingEntity entity, Vec3 side) {
		net.minecraft.world.level.Level level = entity.level();
		BlockPos to = BlockPos.containing(entity.position().add(side));
		BlockPos under = to.below();
		if (to.getY() != entity.blockPosition().getY() || !safeFloor(level, under)) {
			return false;
		}
		for (BlockPos space : new BlockPos[] {to, to.above()}) {
			BlockState state = level.getBlockState(space);
			if (!state.getCollisionShape(level, space).isEmpty() || !state.getFluidState().isEmpty() || hurts(state)) {
				return false;
			}
		}
		return true;
	}

	/** Solid, grippy ground that doesn't hurt to land on. */
	private static boolean safeFloor(net.minecraft.world.level.Level level, BlockPos under) {
		BlockState floor = level.getBlockState(under);
		return floor.isFaceSturdy(level, under, Direction.UP) && floor.getBlock().getFriction() <= MAX_FRICTION && !hurts(floor);
	}

	/** Blocks that hurt to stand in or on, or to fall into. */
	private static boolean hurts(BlockState state) {
		return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
			|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.LAVA)
			|| state.is(Blocks.POINTED_DRIPSTONE);
	}

	/** The block the entity stands on. */
	static BlockState ground(LivingEntity entity) {
		return entity.level().getBlockState(entity.getBlockPosBelowThatAffectsMyMovement());
	}

	private static boolean steadyGround(LivingEntity entity) {
		return entity.onGround() && !entity.isPassenger() && !entity.isInWater() && !entity.isFallFlying() && !entity.isSwimming()
				&& !entity.isSleeping() && !(entity instanceof Player player && player.getAbilities().flying);
	}

	/** Players move on their client, so the server's view of their speed is the last movement the client reported. */
	private static Vec3 currentMotion(LivingEntity entity) {
		return entity instanceof Player ? entity.getKnownMovement() : entity.getDeltaMovement();
	}

	private static void setMotion(LivingEntity entity, Vec3 motion) {
		entity.setDeltaMovement(motion);
		entity.needsSync = true;
		// Sends the new speed to the entity's own client too (a player moves on its client), like knockback does
		entity.syncVelocity = true;
	}
}
