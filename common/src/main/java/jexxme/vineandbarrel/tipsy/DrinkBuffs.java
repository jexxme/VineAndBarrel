package jexxme.vineandbarrel.tipsy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The buffs our drinks gave, so that "too drunk to enjoy it" (Tipsy IV, a blackout) takes away exactly those and never a
 * potion's, a beacon's or another mod's. Each one is remembered with its strength and the ticks it has left, counted
 * down with the entity's own ticks (like the effect itself: a relog or a frozen tick rate changes nothing). An entry is
 * our instance only while an instance of that effect (the visible one or one vanilla keeps hidden underneath, say under
 * a stronger potion) has that strength and that time left; then removing it takes out just that instance and puts the
 * others back as they were. A buff that ran out or went (milk, sleep) is forgotten. Saved in
 * {@link TipsyRegistry#DRINK_BUFFS}.
 */
public final class DrinkBuffs
{
	/** How far (ticks) the time left may differ from what we expect and still count as our instance. */
	private static final int SAME_INSTANCE_SLACK = 2;

	/** One buff a drink gave: which effect, how strong, and the ticks it has left. */
	public record Buff(String effect, int amplifier, int left)
	{
		public static final Codec<Buff> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("effect").forGetter(Buff::effect),
			Codec.INT.fieldOf("amplifier").forGetter(Buff::amplifier),
			Codec.INT.fieldOf("left").forGetter(Buff::left)
		).apply(inst, Buff::new));

		Optional<Holder.Reference<MobEffect>> holder() {
			ResourceLocation id = ResourceLocation.tryParse(effect);
			return id == null ? Optional.empty() : BuiltInRegistries.MOB_EFFECT.getHolder(id);
		}

		/** Whether {@code instance} is the one this drink gave. */
		boolean matches(MobEffectInstance instance) {
			return instance.getAmplifier() == amplifier && !instance.isInfiniteDuration()
				&& Math.abs(instance.getDuration() - left) <= SAME_INSTANCE_SLACK;
		}
	}

	/**
	 * Saved as {@code buffs}. Saves from before held only effect ids under {@code effects}; those can't be told apart
	 * from a potion's, so they are dropped (the codec ignores the old field).
	 */
	public static final MapCodec<List<Buff>> CODEC = Buff.CODEC.listOf().optionalFieldOf("buffs", List.of());

	private DrinkBuffs() {
	}

	/** {@return whether the drink being finished now is one too many to enjoy: it takes Tipsy to a level without buffs} */
	public static boolean tooDrunk(LivingEntity entity) {
		return TipsyConfig.enabled() && !entity.level().isClientSide()
			&& !TipsyLevels.at(Math.min(Tipsy.getLevel(entity) + 1, TipsyConfig.maxLevel())).rules().buffs();
	}

	/**
	 * Right after a drink (or a cheers) gave {@code given}: remembers it, if an instance of the effect now is exactly that
	 * (on top, or underneath a stronger one). When a longer effect of the same strength was there already, vanilla kept
	 * that one, and the drink added nothing of its own to remember.
	 */
	public static void remember(LivingEntity entity, MobEffectInstance given) {
		if (given.isInfiniteDuration() || entity.level().isClientSide()) {
			return;
		}
		MobEffectInstance current = entity.getEffect(given.getEffect());
		boolean there = chain(current).stream().anyMatch(instance -> instance.getAmplifier() == given.getAmplifier()
			&& instance.getDuration() == given.getDuration());
		if (there) {
			List<Buff> buffs = new ArrayList<>(list(entity));
			buffs.add(new Buff(given.getEffect().getRegisteredName(), given.getAmplifier(), given.getDuration()));
			TipsyRegistry.DRINK_BUFFS.set(entity, List.copyOf(buffs));
		}
	}

	/**
	 * A cheers made our buff last longer ({@code Cheers}): the entry for the instance that had {@code before} ticks left
	 * now has {@code after}.
	 */
	public static void extended(LivingEntity entity, Holder<MobEffect> effect, int amplifier, int before, int after) {
		String id = effect.getRegisteredName();
		List<Buff> buffs = new ArrayList<>(list(entity));
		for (int i = 0; i < buffs.size(); i++) {
			Buff buff = buffs.get(i);
			if (buff.effect().equals(id) && buff.amplifier() == amplifier && Math.abs(buff.left() - before) <= SAME_INSTANCE_SLACK) {
				buffs.set(i, new Buff(id, amplifier, after));
				TipsyRegistry.DRINK_BUFFS.set(entity, List.copyOf(buffs));
				return;
			}
		}
	}

	/**
	 * Takes away the buffs our drinks gave that are still running as they gave them, wherever they are in vanilla's
	 * stack of instances; a potion's, a beacon's or another mod's effect of the same kind stays exactly as it was.
	 */
	public static void removeAll(LivingEntity entity) {
		Map<String, List<Buff>> byEffect = new LinkedHashMap<>();
		for (Buff buff : list(entity)) {
			byEffect.computeIfAbsent(buff.effect(), k -> new ArrayList<>()).add(buff);
		}
		for (List<Buff> buffs : byEffect.values()) {
			buffs.getFirst().holder().ifPresent(effect -> removeOurs(entity, effect, buffs));
		}
		forget(entity);
	}

	private static void removeOurs(LivingEntity entity, Holder<MobEffect> effect, List<Buff> buffs) {
		List<MobEffectInstance> chain = chain(entity.getEffect(effect));
		if (chain.isEmpty()) {
			return;
		}
		List<Buff> unmatched = new ArrayList<>(buffs);
		List<MobEffectInstance> kept = new ArrayList<>();
		for (MobEffectInstance instance : chain) {
			boolean ours = false;
			for (Iterator<Buff> it = unmatched.iterator(); it.hasNext(); ) {
				if (it.next().matches(instance)) {
					it.remove();
					ours = true;
					break;
				}
			}
			if (!ours) {
				kept.add(instance);
			}
		}
		if (kept.size() == chain.size()) {
			return;
		}
		// Rebuild the stack without ours: the strongest first, vanilla tucks each weaker, longer one underneath. Re-adding
		// Absorption would top its hearts back up (AbsorptionMobEffect.onEffectStarted), so what was left stays (clamped
		// to what the kept instances allow)
		float absorption = entity.getAbsorptionAmount();
		entity.removeEffect(effect);
		for (MobEffectInstance instance : kept) {
			entity.addEffect(new MobEffectInstance(instance.getEffect(), instance.getDuration(), instance.getAmplifier(), instance.isAmbient(),
				instance.isVisible(), instance.showIcon()));
		}
		entity.setAbsorptionAmount(absorption);
	}

	/**
	 * Once a tick, with the entity's own tick (from {@link Tipsy#tick}): counts the time left down and forgets the buffs
	 * that ran out or aren't there any more (milk, replaced). Cheap when there is nothing to remember.
	 */
	public static void tick(LivingEntity entity) {
		List<Buff> buffs = TipsyRegistry.DRINK_BUFFS.getExisting(entity);
		if (buffs == null || buffs.isEmpty()) {
			return;
		}
		List<Buff> kept = new ArrayList<>(buffs.size());
		for (Buff buff : buffs) {
			Buff next = new Buff(buff.effect(), buff.amplifier(), buff.left() - 1);
			MobEffectInstance current = buff.holder().map(entity::getEffect).orElse(null);
			if (next.left() > 0 && chain(current).stream().anyMatch(next::matches)) {
				kept.add(next);
			}
		}
		if (kept.isEmpty()) {
			forget(entity);
		} else {
			TipsyRegistry.DRINK_BUFFS.set(entity, List.copyOf(kept));
		}
	}

	/** Forgets every buff (sober again: the next night starts with a clean slate). The effects themselves stay. */
	public static void forget(LivingEntity entity) {
		if (TipsyRegistry.DRINK_BUFFS.has(entity)) {
			TipsyRegistry.DRINK_BUFFS.remove(entity);
		}
	}

	public static List<Buff> list(LivingEntity entity) {
		List<Buff> buffs = TipsyRegistry.DRINK_BUFFS.getExisting(entity);
		return buffs == null ? List.of() : buffs;
	}

	/**
	 * {@return the instance and every one vanilla keeps hidden underneath it, strongest first; empty if there is none,
	 * or if one underneath can't be read back (then nothing is touched)}
	 */
	static List<MobEffectInstance> chain(@Nullable MobEffectInstance top) {
		List<MobEffectInstance> chain = new ArrayList<>();
		MobEffectInstance instance = top;
		while (instance != null) {
			chain.add(instance);
			MobEffectInstance below = Tipsy.hiddenEffect(instance);
			if (below == null && Tipsy.hasHiddenEffect(instance)) {
				return List.of();
			}
			instance = below;
		}
		return chain;
	}
}
