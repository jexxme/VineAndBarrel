package jexxme.vineandbarrel.tipsy;

import com.mojang.serialization.Codec;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Everything Tipsy registers. Kept apart from the other milestones' registries so their branches merge cleanly. */
public final class TipsyRegistry
{
	private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, VineAndBarrel.MODID);
	private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, VineAndBarrel.MODID);
	private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, VineAndBarrel.MODID);
	private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, VineAndBarrel.MODID);

	public static final Holder<MobEffect> TIPSY = EFFECTS.register("tipsy", TipsyEffect::new);

	public static final Supplier<SimpleParticleType> HICCUP_PARTICLE = PARTICLES.register("hiccup", () -> new SimpleParticleType(false));

	public static final Holder<SoundEvent> HICCUP_SOUND = SOUNDS.register("entity.player.hiccup",
			() -> SoundEvent.createVariableRangeEvent(VineAndBarrel.id("entity.player.hiccup")));

	/**
	 * The Tipsy level of a player as other clients see it (T1: they see you sway). Not saved, only synced: the
	 * server recomputes it from the effect every tick, so it can never drift from the real effect.
	 */
	public static final Supplier<AttachmentType<Integer>> SYNCED_LEVEL = ATTACHMENTS.register("tipsy_level",
			() -> AttachmentType.builder(() -> 0).sync(ByteBufCodecs.VAR_INT).build());

	/**
	 * Which side effects Tipsy itself gave and still owns (bit 0 Slowness, bit 1 Mining Fatigue, bit 2 Weakness, bit 3
	 * Nausea; see {@code Tipsy.SIDE_EFFECTS}), so the clean-up never takes away one from a potion, a command or another
	 * mod. Saved, since the effects are. The id keeps its pre-playtest name so old saves still load.
	 */
	/** Ticks left of a blackout ({@link Blackout}); synced so the client draws the black screen, saved so a relog can't skip it. */
	public static final Supplier<AttachmentType<Integer>> BLACKOUT = ATTACHMENTS.register("blackout",
			() -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("ticks")).sync(ByteBufCodecs.VAR_INT).build());

	/** The effects our drinks gave this entity (effect ids), so reaching Tipsy IV takes away only those, never a potion's. */
	public static final Supplier<AttachmentType<java.util.List<String>>> DRINK_BUFFS = ATTACHMENTS.register("drink_buffs",
			() -> AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of()).serialize(Codec.STRING.listOf().fieldOf("effects")).build());

	public static final Supplier<AttachmentType<Integer>> HEAVY_EFFECTS_OWNED = ATTACHMENTS.register("tipsy_heavy_effects",
			() -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("owned")).build());

	/**
	 * Drinks that sober you up by one level (T3). Holds the water bottle; M2's grape juice only has to join it.
	 * A potion item counts only when it is plain water, so the tag can list {@code minecraft:potion}.
	 */
	public static final TagKey<Item> SOBERING_DRINKS = TagKey.create(Registries.ITEM, VineAndBarrel.id("sobering_drinks"));

	private TipsyRegistry() {
	}

	public static void register(IEventBus modEventBus) {
		EFFECTS.register(modEventBus);
		PARTICLES.register(modEventBus);
		SOUNDS.register(modEventBus);
		ATTACHMENTS.register(modEventBus);
	}
}
