package jexxme.vineandbarrel.drink;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.tipsy.DrinkBuffs;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * D2 "Cheers!": when two or more players finish a drink within {@link #WINDOW_TICKS} and {@link #RADIUS} blocks of
 * each other, glasses clink, a few sparkles go up, and everyone's buff from that drink lasts {@link #BONUS_PERCENT}%
 * longer (once per drink). Every cheers posts a {@link DrankTogether}, the hook for the "Cheers!" advancement (M7).
 * Server-side only.
 */
public final class Cheers
{
	/** About 3 seconds. */
	public static final int WINDOW_TICKS = 60;
	/** About 5 blocks. */
	public static final double RADIUS = 5.0;
	/** "Slightly longer effects". */
	public static final int BONUS_PERCENT = 20;

	/** A finished drink, remembered for the length of the window. */
	private static final class Sip
	{
		/** Held for the length of the window only (and dropped when the server stops). */
		final Player player;
		final long time;
		final Vec3 pos;
		final Holder<MobEffect> effect;
		final int amplifier;
		final int bonusTicks;
		/** Whether the drink gave its buff: from Tipsy IV it doesn't, and then a cheers adds nothing to it either. */
		final boolean buffed;
		/** Drunk from a mug (a clunk) rather than a wine glass (a ring). */
		final boolean mug;
		boolean cheered;

		Sip(Player player, long time, Vec3 pos, MobEffectInstance buff, boolean buffed, boolean mug) {
			this.player = player;
			this.time = time;
			this.pos = pos;
			this.effect = buff.getEffect();
			this.amplifier = buff.getAmplifier();
			this.bonusTicks = Math.max(1, buff.getDuration() * BONUS_PERCENT / 100);
			this.buffed = buffed;
			this.mug = mug;
		}
	}

	private static final Map<ResourceKey<Level>, Deque<Sip>> RECENT = new HashMap<>();

	private Cheers() {
	}

	/** Called once a player has finished a drink (its buff already applied). */
	public static void onDrink(LivingEntity drinker, DrinkItem drink, boolean buffed) {
		if (!(drinker instanceof Player player) || !(drinker.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Deque<Sip> recent = RECENT.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>());
		for (Iterator<Sip> it = recent.iterator(); it.hasNext(); ) {
			if (now - it.next().time > WINDOW_TICKS) {
				it.remove();
			}
		}

		PlacedDrink vessel = PlacedDrink.of(drink.getDefaultInstance());
		Sip sip = new Sip(player, now, player.position(), drink.buff(), buffed, vessel != null && vessel.isMug());
		List<Sip> partners = new ArrayList<>();
		for (Sip other : recent) {
			if (other.player != player && !other.player.isRemoved() && other.pos.closerThan(sip.pos, RADIUS)) {
				partners.add(other);
			}
		}
		recent.addLast(sip);
		if (partners.isEmpty()) {
			// Alone, but in front of a tavern keeper: he raises his mug to it (the bonus, not the advancement)
			if (jexxme.vineandbarrel.world.village.TavernKeeper.cheers(level, player, sip.mug)) {
				extend(player, sip);
				sip.cheered = true;
			}
			return;
		}

		List<Sip> circle = new ArrayList<>(partners);
		circle.add(sip);
		List<Player> players = new ArrayList<>();
		for (Sip member : circle) {
			Player drinker1 = member.player;
			if (!member.cheered) {
				extend(drinker1, member);
				member.cheered = true;
			}
			if (players.contains(drinker1)) {
				continue;
			}
			players.add(drinker1);
			// Each drinker's own vessel: glasses ring, mugs clunk, a glass and a mug together are both
			level.playSound(null, drinker1.getX(), drinker1.getEyeY(), drinker1.getZ(), member.mug ? BarrelRegistry.CHEERS_MUG.get() : BarrelRegistry.CHEERS_GLASS.get(),
				SoundSource.PLAYERS, 0.8F, 0.95F + level.getRandom().nextFloat() * 0.1F);
			level.sendParticles(BarrelRegistry.CHEERS.get(), drinker1.getX(), drinker1.getEyeY() + 0.3, drinker1.getZ(), 4, 0.25, 0.15, 0.25, 0.0);
		}
		DrankTogether.post(new DrankTogether(level, List.copyOf(players)));
	}

	/** The drink's buff lasts a little longer: added to what is left of it (or given again briefly if it is already over). */
	private static void extend(Player player, Sip sip) {
		if (!sip.buffed) {
			// Too drunk for the drink's buff, so the clink brings none back (but still counts as a cheers)
			return;
		}
		MobEffectInstance current = player.getEffect(sip.effect);
		if (current == null) {
			MobEffectInstance again = new MobEffectInstance(sip.effect, sip.bonusTicks, sip.amplifier);
			player.addEffect(again);
			// Still the drink's buff: too drunk later takes it away like the rest (DrinkBuffs)
			DrinkBuffs.remember(player, again);
		} else if (current.getAmplifier() == sip.amplifier && !current.isInfiniteDuration()) {
			// Read before adding: vanilla merges the longer one into this very instance
			int before = current.getDuration();
			int longer = before + sip.bonusTicks;
			player.addEffect(new MobEffectInstance(sip.effect, longer, current.getAmplifier(),
				current.isAmbient(), current.isVisible(), current.showIcon()));
			DrinkBuffs.extended(player, sip.effect, sip.amplifier, before, longer);
		}
	}

	/** When the server has stopped. */
	public static void onServerStopped() {
		RECENT.clear();
	}
}
