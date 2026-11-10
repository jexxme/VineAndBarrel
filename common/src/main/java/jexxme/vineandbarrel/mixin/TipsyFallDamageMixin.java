package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.TipsyEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Tipsy's "liquid courage" takes a share of the fall damage away ({@link TipsyEffect#fallDamageFactor}). Newer versions
 * have an attribute for it (the fall damage multiplier, read where the damage of a fall is worked out); 1.20.1 has none,
 * so the factor goes onto the damage multiplier of the same calculation, on both loaders. Horses have their own
 * calculation on 1.20.1 (newer versions give them the attribute at a base of one half instead), so they are targeted
 * as well; frogs and goats call {@code LivingEntity}'s, foxes ignore the attribute there as here.
 */
@Mixin({LivingEntity.class, AbstractHorse.class})
public abstract class TipsyFallDamageMixin
{
	/** The base of a horse's fall damage multiplier on newer versions; 1.20.1 has the half in its formula. */
	private static final double HORSE_BASE = 0.5;

	@ModifyVariable(method = "calculateFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float vineandbarrel$liquidCourage(float damageMultiplier) {
		LivingEntity entity = (LivingEntity) (Object) this;
		return damageMultiplier * TipsyEffect.fallDamageFactor(entity, entity instanceof AbstractHorse ? HORSE_BASE : 1.0);
	}
}
