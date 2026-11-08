package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Tipsy effect. Level = amplifier + 1. Decay uses vanilla's own "hidden effect" chain (the one that makes a
 * weaker, longer potion come back after a stronger one runs out), so each level steps down after a fixed time and the
 * HUD timer shows when the next drop comes. See {@link Tipsy} for the API and the level table.
 * <p>
 * Neutral, not harmful: it is the cozy side effect of a drink, nothing to fear (DESIGN pillar "no punishing mechanics").
 */
public class TipsyEffect extends MobEffect
{
	/** Warm amber, the colour of the icon's drink. */
	public static final int COLOR = 0xE8A23C;

	/** "Liquid courage" at level 1, 2, 3: this much knockback resistance (1.0 is full resistance). None from level 4. */
	public static final double[] COURAGE_KNOCKBACK_RESISTANCE = {0.05, 0.10, 0.15};
	/** "Liquid courage" at level 1, 2, 3: fall damage is this much lower (0.1 = 10% less). None from level 4. */
	public static final double[] COURAGE_FALL_DAMAGE_REDUCTION = {0.10, 0.15, 0.20};

	public TipsyEffect() {
		super(MobEffectCategory.NEUTRAL, COLOR);
		// Vanilla re-applies these whenever the level changes, also when a level decays down the hidden-effect chain
		addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, VineAndBarrel.id("tipsy_courage_knockback"), AttributeModifier.Operation.ADD_VALUE,
				amplifier -> courage(COURAGE_KNOCKBACK_RESISTANCE, amplifier));
		addAttributeModifier(Attributes.FALL_DAMAGE_MULTIPLIER, VineAndBarrel.id("tipsy_courage_fall"), AttributeModifier.Operation.ADD_VALUE,
				amplifier -> -courage(COURAGE_FALL_DAMAGE_REDUCTION, amplifier));
	}

	/** The courage value for an amplifier (level - 1); 0 from level 4 on, where the downside takes over. */
	public static double courage(double[] perLevel, int amplifier) {
		return amplifier >= 0 && amplifier < perLevel.length ? perLevel[amplifier] : 0.0;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
		return true;
	}

	/**
	 * Runs inside the entity's effect loop, so it only moves the entity, plays sounds and spawns particles. Adding or
	 * removing effects here would break vanilla's iteration; the side effects (Nausea, Weakness, ...) are kept in sync from
	 * {@link TipsyEvents} instead.
	 */
	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		int tipsyLevel = amplifier + 1;
		var random = entity.getRandom();
		if (Tipsy.rollsHiccup(tipsyLevel, random)) {
			Tipsy.hiccup(entity);
		}
		if (tipsyLevel >= Tipsy.STUMBLE_LEVEL && random.nextInt(Tipsy.STUMBLE_ONE_IN) == 0) {
			Tipsy.stumble(entity, Tipsy.walkingMotion(entity), random);
		}
		return true;
	}
}
