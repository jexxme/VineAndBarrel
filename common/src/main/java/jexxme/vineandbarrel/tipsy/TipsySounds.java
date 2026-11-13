package jexxme.vineandbarrel.tipsy;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Every sound Tipsy makes, in one place, so the sound work can change them without touching the levels or the
 * movements: each method is one moment (one hiccup, one stumble...) and plays on the server for everyone nearby.
 * <p>
 * The hiccup is our own sound event ({@link TipsyRegistry#HICCUP_SOUND}): three takes of a real, dry hiccup (CC0),
 * and the legs use the vanilla foley of the block underfoot (its step for the scuff, its fall and vanilla's big fall for
 * the thud), so they always fit the ground. The blackout's wake-up sounds like the place you wake up in, and Tipsy V
 * has a heartbeat that only you hear (played in your head by the client, {@code client.TipsyClient}).
 */
public final class TipsySounds
{
	/** The first level you hear your own heart at ({@link TipsyLevels.Ears#heartbeat}). */
	public static final int HEARTBEAT_LEVEL = TipsyLevels.first(level -> level.ears().heartbeat() > 0.0F);
	/** A heartbeat about every this many ticks (a little over 50 a minute, slow and heavy). */
	public static final int HEARTBEAT_GAP = 22;
	/** The heartbeat's loudness at full {@link TipsyConfig#heartbeatVolume()}: quiet, under everything else. */
	public static final float HEARTBEAT_VOLUME = 0.35F;

	/** One sound with its volume and pitch. */
	public record Sound(SoundEvent event, float volume, float pitch) {
	}

	private TipsySounds() {
	}


	/**
	 * As the eyes open after a blackout: the place you woke up in. The sheets of a bed, the water under the boat, the
	 * animal that stares at you in the farmyard, and otherwise the ground you lie on (the hay, the grass, the tavern's
	 * floorboards, the roof).
	 */
	public static void wakeUp(LivingEntity entity, BlackoutEnding ending, @Nullable Entity friend, BlockState ground) {
		if (ending == BlackoutEnding.FARMYARD && friend instanceof Mob animal) {
			animal.playAmbientSound();
			return;
		}
		Sound sound = wakeSound(ending, ground);
		play(entity, sound.event(), sound.volume(), sound.pitch());
	}

	/** {@return the sound of waking up in this ending's place, lying on {@code ground} (the farmyard's animal aside)} */
	public static Sound wakeSound(BlackoutEnding ending, BlockState ground) {
		return switch (ending.pose()) {
			case BED -> new Sound(TipsyRegistry.WAKE_IN_BED_SOUND.value(), 0.6F, 0.9F);
			case SEAT -> new Sound(SoundEvents.BOAT_PADDLE_WATER, 0.4F, 0.9F);
			case GROUND -> {
				SoundType type = soundOf(ground);
				yield new Sound(type.getStepSound(), type.getVolume() * 0.5F, type.getPitch() * 0.8F);
			}
		};
	}

	/** Getting up at the end of a blackout (out of the bed, off the ground): a soft thud on the ground underfoot. */
	public static void getUp(LivingEntity entity, BlackoutEnding.Pose pose, BlockState ground) {
		Sound sound = getUpSound(pose, ground);
		if (sound != null) {
			play(entity, sound.event(), sound.volume(), sound.pitch());
		}
	}

	/** {@return the thud of getting up from {@code pose} onto {@code ground}, or null for none (still sitting in the boat)} */
	public static @Nullable Sound getUpSound(BlackoutEnding.Pose pose, BlockState ground) {
		if (pose == BlackoutEnding.Pose.SEAT) {
			return null;
		}
		SoundType type = soundOf(ground);
		return new Sound(type.getFallSound(), type.getVolume() * 0.5F, type.getPitch() * 0.75F);
	}

	/**
	 * One hiccup.
	 *
	 * @param index which hiccup of its bout this is (0 is the first): a bout's hiccups may differ a little
	 */
	public static void hiccup(LivingEntity entity, int index) {
		RandomSource random = entity.getRandom();
		// +-10% around the sound's own pitch, so no two hiccups sound quite the same
		play(entity, TipsyRegistry.HICCUP_SOUND.value(), 0.7F, 0.9F + random.nextFloat() * 0.2F);
	}

	/** A stumble's (and a trip's) first moment: the foot catches and scuffs over the ground. */
	public static void stumble(LivingEntity entity, BlockState ground) {
		SoundType sound = soundOf(ground);
		play(entity, sound.getStepSound(), sound.getVolume() * 0.6F, sound.getPitch() * 0.75F);
	}

	/** A stumble's catch step: the foot finds the ground again. */
	public static void catchStep(LivingEntity entity, BlockState ground) {
		SoundType sound = soundOf(ground);
		play(entity, sound.getStepSound(), sound.getVolume() * 0.45F, sound.getPitch() * 0.9F);
	}

	/**
	 * Nodding off: the jerk awake, a short snort. A vanilla stand-in (a horse breathing out, higher) until a real, dry snort
	 * is recorded and picked by ear.
	 */
	public static void snort(LivingEntity entity) {
		play(entity, SoundEvents.HORSE_BREATHE, 0.45F, 1.35F + entity.getRandom().nextFloat() * 0.1F);
	}

	/** A trip's thud: the body hits the ground. */
	public static void trip(LivingEntity entity, BlockState ground) {
		SoundType sound = soundOf(ground);
		play(entity, sound.getFallSound(), sound.getVolume() * 0.8F, sound.getPitch() * 0.8F);
		play(entity, SoundEvents.PLAYER_BIG_FALL, 0.7F, 0.95F);
	}

	private static SoundType soundOf(BlockState ground) {
		return ground.isAir() ? SoundType.GRASS : ground.getSoundType();
	}

	private static void play(LivingEntity entity, SoundEvent sound, float volume, float pitch) {
		if (!entity.level().isClientSide()) {
			entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, entity.getSoundSource(), volume, pitch);
		}
	}
}
