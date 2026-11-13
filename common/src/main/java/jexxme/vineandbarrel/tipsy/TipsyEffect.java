package jexxme.vineandbarrel.tipsy;

import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Tipsy effect. Level = amplifier + 1. Decay uses vanilla's own "hidden effect" chain (the one that makes a
 * weaker, longer potion come back after a stronger one runs out), so each level steps down after a fixed time and the
 * HUD timer shows when the next drop comes. See {@link Tipsy} for the API and {@link TipsyLevels} for the level table;
 * everything the levels do runs from {@link Tipsy#tick} (once a tick, outside vanilla's effect loop), so the effect
 * itself only carries the courage.
 * <p>
 * Neutral, not harmful: it is the cozy side effect of a drink, nothing to fear (DESIGN pillar "no punishing mechanics").
 */
public class TipsyEffect extends MobEffect
{
	/** Warm amber, the colour of the icon's drink. */
	public static final int COLOR = 0xE8A23C;

	private static final ResourceLocation COURAGE_KNOCKBACK = VineAndBarrel.id("tipsy_courage_knockback");
	private static final ResourceLocation COURAGE_FALL = VineAndBarrel.id("tipsy_courage_fall");
	private static final ResourceLocation HEAVY_LEGS = VineAndBarrel.id("tipsy_heavy_legs");

	public TipsyEffect() {
		super(MobEffectCategory.NEUTRAL, COLOR);
		// Vanilla re-applies these whenever the level changes, also when a level decays down the hidden-effect chain.
		// Registered so vanilla knows them (removal, tooltips); the amounts per level come from createModifiers below,
		// since vanilla's own templates only scale linearly with the level.
		addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, COURAGE_KNOCKBACK, 0.0, AttributeModifier.Operation.ADD_VALUE);
		addAttributeModifier(Attributes.FALL_DAMAGE_MULTIPLIER, COURAGE_FALL, 0.0, AttributeModifier.Operation.ADD_VALUE);
		addAttributeModifier(Attributes.MOVEMENT_SPEED, HEAVY_LEGS, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	@Override
	public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
		// "Liquid courage" (TipsyLevels): from I to III a little knockback resistance and less fall damage, none from IV
		TipsyLevels.Level level = TipsyLevels.at(amplifier + 1);
		consumer.accept(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(COURAGE_KNOCKBACK, level.courage().knockbackResistance(), AttributeModifier.Operation.ADD_VALUE));
		consumer.accept(Attributes.FALL_DAMAGE_MULTIPLIER, new AttributeModifier(COURAGE_FALL, -level.courage().fallDamageReduction(), AttributeModifier.Operation.ADD_VALUE));
		// Heavy legs at IV and V: slower, sprinting too (never no sprinting)
		consumer.accept(Attributes.MOVEMENT_SPEED, new AttributeModifier(HEAVY_LEGS, -heavyLegs(amplifier + 1), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	/**
	 * {@return how much slower heavy legs make you at this level (a share of your speed)} Part of the drunk walk, so the
	 * server's {@code stumbling} switch turns it off with the rest.
	 */
	public static float heavyLegs(int level) {
		return TipsyConfig.stumbling() ? TipsyLevels.at(level).legs().slower() : 0.0F;
	}

	/**
	 * Once a tick for a tipsy entity: the heavy legs follow the {@code stumbling} switch when it is changed while the
	 * effect is on (vanilla only sets the speed when the level changes).
	 */
	static void keepHeavyLegs(LivingEntity entity, int level) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeModifier now = speed == null ? null : speed.getModifier(HEAVY_LEGS);
		double want = -heavyLegs(level);
		if (now != null && now.amount() != want) {
			speed.removeModifier(HEAVY_LEGS);
			speed.addPermanentModifier(new AttributeModifier(HEAVY_LEGS, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	@Override
	public void addAttributeModifiers(AttributeMap attributes, int amplifier) {
		createModifiers(amplifier, (attribute, modifier) -> {
			AttributeInstance instance = attributes.getInstance(attribute);
			if (instance != null) {
				instance.removeModifier(modifier.id());
				instance.addPermanentModifier(modifier);
			}
		});
	}
}
