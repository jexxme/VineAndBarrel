package jexxme.vineandbarrel.tipsy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
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

	private static final UUID COURAGE_KNOCKBACK = modifierId(VineAndBarrel.id("tipsy_courage_knockback"));
	private static final UUID HEAVY_LEGS = modifierId(VineAndBarrel.id("tipsy_heavy_legs"));

	public TipsyEffect() {
		super(MobEffectCategory.NEUTRAL, COLOR);
		// Vanilla re-applies these whenever the level changes, also when a level decays down the hidden-effect chain.
		// Registered so vanilla knows them (adding, removal); the amounts per level come from getAttributeModifierValue
		// below, since vanilla's own templates only scale linearly with the level.
		// The courage's other half, less fall damage, has no attribute on 1.20.1: fallDamageFactor below, which
		// mixin/TipsyFallDamageMixin puts into vanilla's fall damage.
		addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, COURAGE_KNOCKBACK.toString(), 0.0, AttributeModifier.Operation.ADDITION);
		addAttributeModifier(Attributes.MOVEMENT_SPEED, HEAVY_LEGS.toString(), 0.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
	}

	/**
	 * The UUID of an attribute modifier that newer versions name by {@code id}: 1.20.1 knows modifiers by UUID only, so
	 * each one gets the name-based UUID of its id's text (e.g. {@code vineandbarrel:tipsy_courage_knockback}), the same
	 * in every world and on both sides.
	 */
	public static UUID modifierId(ResourceLocation id) {
		return UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
		TipsyLevels.Level level = TipsyLevels.at(amplifier + 1);
		if (HEAVY_LEGS.equals(modifier.getId())) {
			// Heavy legs at IV and V: slower, sprinting too (never no sprinting)
			return -level.legs().slower();
		}
		// "Liquid courage" (TipsyLevels): from I to III a little knockback resistance, none from IV
		return level.courage().knockbackResistance();
	}

	/**
	 * "Liquid courage", the other half: the factor on this entity's fall damage, 1 when sober and from IV. Newer
	 * versions take the level's reduction off the entity's fall damage multiplier attribute, whose base is 1 (0.5 for
	 * horses and their kin); this is that attribute's value over its {@code base}.
	 */
	public static float fallDamageFactor(LivingEntity entity, double base) {
		double reduction = TipsyLevels.at(Tipsy.getLevel(entity)).courage().fallDamageReduction();
		return reduction > 0.0 ? (float) (Math.max(0.0, base - reduction) / base) : 1.0F;
	}
}
