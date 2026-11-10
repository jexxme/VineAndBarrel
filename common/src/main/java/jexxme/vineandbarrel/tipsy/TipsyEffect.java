package jexxme.vineandbarrel.tipsy;

import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
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

	public TipsyEffect() {
		super(MobEffectCategory.NEUTRAL, COLOR);
		// Vanilla re-applies these whenever the level changes, also when a level decays down the hidden-effect chain.
		// Registered so vanilla knows them (removal, tooltips); the amounts per level come from createModifiers below,
		// since vanilla's own templates only scale linearly with the level.
		addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, COURAGE_KNOCKBACK, 0.0, AttributeModifier.Operation.ADD_VALUE);
		addAttributeModifier(Attributes.FALL_DAMAGE_MULTIPLIER, COURAGE_FALL, 0.0, AttributeModifier.Operation.ADD_VALUE);
	}

	@Override
	public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
		// "Liquid courage" (TipsyLevels): from I to III a little knockback resistance and less fall damage, none from IV
		TipsyLevels.Level level = TipsyLevels.at(amplifier + 1);
		consumer.accept(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(COURAGE_KNOCKBACK, level.knockbackResistance(), AttributeModifier.Operation.ADD_VALUE));
		consumer.accept(Attributes.FALL_DAMAGE_MULTIPLIER, new AttributeModifier(COURAGE_FALL, -level.fallDamageReduction(), AttributeModifier.Operation.ADD_VALUE));
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
