package jexxme.vineandbarrel.tipsy;

import com.mojang.serialization.Codec;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import jexxme.vineandbarrel.platform.EntityAttachment;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.platform.registry.DeferredHolder;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

/** Everything Tipsy registers. Kept apart from the other milestones' registries so their branches merge cleanly. */
public final class TipsyRegistry
{
	private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, VineAndBarrel.MODID);
	private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, VineAndBarrel.MODID);
	private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, VineAndBarrel.MODID);

	/** The effect itself: on 1.20.1 effects are plain objects (no holders), so everything takes {@code TIPSY.get()}. */
	public static final DeferredHolder<MobEffect, TipsyEffect> TIPSY = EFFECTS.register("tipsy", TipsyEffect::new);
	/** The morning after ({@link Hangover}). */
	public static final DeferredHolder<MobEffect, Hangover.Effect> HANGOVER = EFFECTS.register("hangover", Hangover.Effect::new);

	public static final Supplier<SimpleParticleType> HICCUP_PARTICLE = PARTICLES.register("hiccup", () -> new SimpleParticleType(false) {});
	/** Gold stars circling above the head of a blacked-out player (client-side only, spawned by every client itself). */
	public static final Supplier<SimpleParticleType> DIZZY_STAR_PARTICLE = PARTICLES.register("dizzy_star", () -> new SimpleParticleType(false) {});

	public static final DeferredHolder<SoundEvent, SoundEvent> HICCUP_SOUND = SOUNDS.register("entity.player.hiccup",
			() -> SoundEvent.createVariableRangeEvent(VineAndBarrel.id("entity.player.hiccup")));
	/** Tipsy V: your own heart, heard only by you (vanilla's Warden heartbeat, lower and quieter). */
	public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT_SOUND = SOUNDS.register("entity.player.heartbeat",
			() -> SoundEvent.createVariableRangeEvent(VineAndBarrel.id("entity.player.heartbeat")));
	/** Waking up from a blackout in a bed: the sheets rustle (vanilla's cloth steps). */
	public static final DeferredHolder<SoundEvent, SoundEvent> WAKE_IN_BED_SOUND = SOUNDS.register("entity.player.wake_in_bed",
			() -> SoundEvent.createVariableRangeEvent(VineAndBarrel.id("entity.player.wake_in_bed")));

	/**
	 * The Tipsy level of a player as other clients see it (T1: they see you sway). Not saved, only synced: the
	 * server recomputes it from the effect every tick, so it can never drift from the real effect.
	 */
	public static final EntityAttachment<Integer> SYNCED_LEVEL = Services.PLATFORM.attachment(VineAndBarrel.id("tipsy_level"),
			EntityAttachment.Spec.builder(() -> 0).sync(EntityAttachment.SyncCodec.VAR_INT).build());

	/** Ticks left of a blackout ({@link Blackout}); synced so the client draws the black screen, saved so a relog can't skip it. */
	public static final EntityAttachment<Integer> BLACKOUT = Services.PLATFORM.attachment(VineAndBarrel.id("blackout"),
			EntityAttachment.Spec.builder(() -> 0).serialize(Codec.INT.fieldOf("ticks")).sync(EntityAttachment.SyncCodec.VAR_INT).build());

	/** How a blacked-out player lies ({@link BlackoutEnding.Pose} ordinal); synced so every client draws the pose. */
	public static final EntityAttachment<Integer> BLACKOUT_POSE = Services.PLATFORM.attachment(VineAndBarrel.id("blackout_pose"),
			EntityAttachment.Spec.builder(() -> 0).serialize(Codec.INT.fieldOf("pose")).sync(EntityAttachment.SyncCodec.VAR_INT).build());

	/**
	 * The way a blacked-out player's body lies on the face (yaw, degrees): chosen once by the server as they black out
	 * (where it has room, {@link Blackout#start}) and then where they wake up; synced so every client draws the same fall.
	 */
	public static final EntityAttachment<Float> BLACKOUT_YAW = Services.PLATFORM.attachment(VineAndBarrel.id("blackout_yaw"),
			EntityAttachment.Spec.builder(() -> 0.0F).serialize(Codec.FLOAT.fieldOf("yaw")).sync(EntityAttachment.SyncCodec.FLOAT).build());

	/** The ending of the blackout in progress, once the player woke up ({@link Blackout.Scene}); saved, server only. */
	public static final EntityAttachment<Blackout.Scene> BLACKOUT_SCENE = Services.PLATFORM.attachment(VineAndBarrel.id("blackout_scene"),
			EntityAttachment.Spec.<Blackout.Scene>builder(() -> new Blackout.Scene("right_here", "", java.util.Optional.empty(), java.util.Optional.empty()))
				.serialize(Blackout.Scene.CODEC).build());

	/** Which endings a player has had, and the last one and its line ({@link Blackout.Memory}); kept through death. */
	public static final EntityAttachment<Blackout.Memory> BLACKOUT_MEMORY = Services.PLATFORM.attachment(VineAndBarrel.id("blackout_memory"),
			EntityAttachment.Spec.builder(() -> Blackout.Memory.NONE).serialize(Blackout.Memory.CODEC).copyOnDeath().build());

	/** The buffs our drinks gave this entity ({@link DrinkBuffs}), so reaching Tipsy IV takes away only those, never a potion's. */
	public static final EntityAttachment<List<DrinkBuffs.Buff>> DRINK_BUFFS = Services.PLATFORM.attachment(VineAndBarrel.id("drink_buffs"),
			EntityAttachment.Spec.<List<DrinkBuffs.Buff>>builder(List::of).serialize(DrinkBuffs.CODEC).build());

	/**
	 * Old saves only: before the overhaul, which side effects Tipsy itself gave and still owned (Slowness, Mining
	 * Fatigue, Weakness, Nausea). Nothing sets it any more; it stays registered so a save that has it still loads, and
	 * {@code Tipsy.forgetOldSideEffects} drops it.
	 */
	public static final EntityAttachment<Integer> HEAVY_EFFECTS_OWNED = Services.PLATFORM.attachment(VineAndBarrel.id("tipsy_heavy_effects"),
			EntityAttachment.Spec.builder(() -> 0).serialize(Codec.INT.fieldOf("owned")).build());

	/** The bout of hiccups in progress ({@link Tipsy.HiccupBout}); server only, not saved (a relog ends the bout). */
	public static final EntityAttachment<Tipsy.HiccupBout> HICCUPS = Services.PLATFORM.attachment(VineAndBarrel.id("tipsy_hiccups"),
			EntityAttachment.Spec.builder(() -> new Tipsy.HiccupBout(0, 0, 0)).build());

	/** A trip in progress, its cooldown and a stumble's catch step ({@link TipsyLegs.Legs}); server only, not saved. */
	public static final EntityAttachment<TipsyLegs.Legs> LEGS = Services.PLATFORM.attachment(VineAndBarrel.id("tipsy_legs"),
			EntityAttachment.Spec.builder(() -> TipsyLegs.Legs.STEADY).build());

	/**
	 * How many hiccups, stumbles and trips a player has had ({@link TipsyMotion.Counts}): synced to the player and
	 * everyone near, so every client plays each movement as it comes. Not saved.
	 */
	public static final EntityAttachment<TipsyMotion.Counts> MOTION = Services.PLATFORM.attachment(VineAndBarrel.id("tipsy_motion"),
			EntityAttachment.Spec.builder(() -> TipsyMotion.Counts.NONE).sync(TipsyMotion.Counts.SYNC_CODEC).build());

	/**
	 * The game time a player's nodding off started ({@link TipsyNod}), synced to the player and everyone near, so every
	 * client draws the same nod; absent when awake. Not saved.
	 */
	/** The game time the blackout catches up with a player who drank one too many ({@link GoingUnder}); synced, saved. */
	public static final EntityAttachment<Long> GOING_UNDER = Services.PLATFORM.attachment(VineAndBarrel.id("going_under"),
			EntityAttachment.Spec.builder(() -> -1L).serialize(Codec.LONG.fieldOf("at")).sync(EntityAttachment.SyncCodec.VAR_LONG).build());

	/** The game time a Rowdy player started winding up a wild swing ({@link Rowdy}); synced so everyone sees the arm go back. */
	/** How much longer a Rowdy player is spoiling for a fight ({@link Rowdy#ITCH}); server only, not saved. */
	public static final EntityAttachment<Integer> ROWDY_ITCH = Services.PLATFORM.attachment(VineAndBarrel.id("rowdy_itch"),
			EntityAttachment.Spec.builder(() -> 0).build());

	public static final EntityAttachment<Long> ROWDY_WINDUP = Services.PLATFORM.attachment(VineAndBarrel.id("rowdy_windup"),
			EntityAttachment.Spec.builder(() -> -1L).sync(EntityAttachment.SyncCodec.VAR_LONG).build());

	public static final EntityAttachment<Long> NOD = Services.PLATFORM.attachment(VineAndBarrel.id("tipsy_nod"),
			EntityAttachment.Spec.builder(() -> -1L).sync(EntityAttachment.SyncCodec.VAR_LONG).build());

	/** How long a player has stood still, and where ({@link TipsyNod.Stillness}); server only, not saved. */
	public static final EntityAttachment<TipsyNod.Stillness> STILL = Services.PLATFORM.attachment(VineAndBarrel.id("tipsy_still"),
			EntityAttachment.Spec.builder(() -> new TipsyNod.Stillness(0.0, 0.0, 0.0, 0.0F, 0.0F, 0L, 0)).build());

	/**
	 * Drinks that sober you up by one level (T3). Holds the water bottle; M2's grape juice only has to join it.
	 * A potion item counts only when it is plain water, so the tag can list {@code minecraft:potion}.
	 */
	public static final TagKey<Item> SOBERING_DRINKS = TagKey.create(Registries.ITEM, VineAndBarrel.id("sobering_drinks"));

	private TipsyRegistry() {
	}

	public static void register() {
		EFFECTS.register();
		PARTICLES.register();
		SOUNDS.register();
	}
}
