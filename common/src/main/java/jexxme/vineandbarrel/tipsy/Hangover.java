package jexxme.vineandbarrel.tipsy;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The morning after (Tipsy overhaul, step 7): after a blackout, or a night's sleep at Tipsy IV or V, you wake up
 * hungover, and hungry: no more than {@link #HUNGRY_FOOD} food left. Bright daylight hurts (the picture washes out, under
 * the open sky and at first indoors too; squinting, holding sneak, helps), and loud noises make you wince. Nothing moves
 * your view and nothing makes you sick. A water bottle or a juice (anything that sobers you up) takes a good part of it
 * away. Off with the server's {@code hangover} switch.
 * <p>
 * The glare and the wince are the client's ({@code client.DrunkEyes}, {@code client.HangoverClient}), from the effect
 * the player's own client knows about.
 */
public final class Hangover
{
	/** How long it lasts after a blackout, and after a night's sleep at IV and at V (ticks). */
	public static final int AFTER_BLACKOUT = 4 * 60 * 20, AFTER_SLEEP_IV = 2 * 60 * 20, AFTER_SLEEP_V = 3 * 60 * 20;
	/** Waking up hungover, you have at most this much food left (three drumsticks), and nothing to spare. */
	public static final int HUNGRY_FOOD = 6;
	/** A sobering drink leaves this share of what is left. */
	public static final float DRINK_LEAVES = 0.55F;
	/** Grey-green, the colour of the morning after. */
	public static final int COLOR = 0x9AA58A;

	private Hangover() {
	}

	/** The effect: neutral, like Tipsy, part of the evening, not a punishment. Its work is the client's and {@link #start}'s. */
	public static final class Effect extends MobEffect
	{
		public Effect() {
			super(MobEffectCategory.NEUTRAL, COLOR);
		}
	}

	/** {@return whether this entity is hungover} */
	public static boolean has(LivingEntity entity) {
		return entity.hasEffect(TipsyRegistry.HANGOVER);
	}

	/** Wakes the player up hungover for {@code ticks} (or longer, if they already were), when the server allows it. */
	public static void start(LivingEntity entity, int ticks) {
		if (!TipsyConfig.hangover() || ticks <= 0) {
			return;
		}
		MobEffectInstance now = entity.getEffect(TipsyRegistry.HANGOVER);
		if (now == null || now.getDuration() < ticks) {
			entity.addEffect(new MobEffectInstance(TipsyRegistry.HANGOVER, ticks, 0, false, false, true));
		}
		if (entity instanceof Player player) {
			// waking up hungry: three drumsticks at most, nothing to spare
			var food = player.getFoodData();
			food.setFoodLevel(Math.min(food.getFoodLevel(), HUNGRY_FOOD));
			food.setSaturation(0.0F);
		}
	}

	/** {@return how long a night's sleep at this Tipsy level leaves you hungover (0 = not at all)} */
	public static int afterSleep(int level) {
		return level >= 5 ? AFTER_SLEEP_V : level == 4 ? AFTER_SLEEP_IV : 0;
	}

	/** A sobering drink (water, a juice): a good part of the hangover goes. */
	public static void onSoberingDrink(LivingEntity entity) {
		MobEffectInstance now = entity.getEffect(TipsyRegistry.HANGOVER);
		if (now == null || now.isInfiniteDuration()) {
			return;
		}
		int left = Math.round(now.getDuration() * DRINK_LEAVES);
		entity.removeEffect(TipsyRegistry.HANGOVER);
		if (left > 20) {
			entity.addEffect(new MobEffectInstance(TipsyRegistry.HANGOVER, left, 0, false, false, true));
		}
	}
}
