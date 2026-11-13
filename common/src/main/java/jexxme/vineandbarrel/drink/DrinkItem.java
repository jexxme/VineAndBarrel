package jexxme.vineandbarrel.drink;

import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.GoingUnder;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.DrinkBuffs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * A wine, beer or cider (DESIGN §5). Drinking it works like any FD drink: the buff is given when the drink is
 * finished (1.20.1 has no consumable component) and the vessel comes back from the item itself (1.20.1's food names no
 * vessel: the drink knows its own, as vanilla's bowl foods and honey bottle do). It is drunk,
 * not eaten, and sounds like its vessel. On top of that it adds one level of Tipsy (nothing in
 * non-alcoholic mode), and drinking together within a few seconds is a {@linkplain Cheers cheers} (D2). An aged drink's
 * buff is level II and lasts longer ({@link DrinkItems}); it gives no FD Comfort (FD switched Comfort off for its own
 * foods, DESIGN log).
 * <p>
 * Sneak-use on top of a block puts the drink down (D1).
 */
public class DrinkItem extends Item
{
	private final int color;
	private final MobEffectInstance buff;
	private final boolean aged;
	private final Supplier<? extends Item> vessel;
	private final Supplier<SoundEvent> sound;

	/**
	 * @param vessel what the drink is served in and leaves behind once drunk: a mug or a wine glass
	 * @param sound  how drinking from this drink's vessel sounds (1.20.1: the item says it, not a component)
	 */
	public DrinkItem(Properties properties, int color, MobEffectInstance buff, boolean aged, Supplier<? extends Item> vessel, Supplier<SoundEvent> sound) {
		super(properties);
		this.color = color;
		this.buff = buff;
		this.aged = aged;
		this.vessel = vessel;
		this.sound = sound;
	}

	/** The liquid's colour (DESIGN §9: colour = which drink), for the tap drip and a placed drink. */
	public int color() {
		return color;
	}

	/** The one vanilla buff this drink gives (a template: never add this instance itself to an entity). */
	public MobEffectInstance buff() {
		return buff;
	}

	public boolean isAged() {
		return aged;
	}

	/** The vessel this drink is tapped into, stands in when put down, and gives back once drunk. */
	public Item vessel() {
		return vessel.get();
	}

	/** An aged drink has the enchantment glint (1.21.1, 26.x: the glint override component). */
	@Override
	public boolean isFoil(ItemStack stack) {
		return aged || super.isFoil(stack);
	}

	/** 1.20.1: a food is eaten unless its item says otherwise (26.x: the consumable component's animation). */
	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	/** The sips while drinking (26.x: the consumable component's sound). */
	@Override
	public SoundEvent getDrinkingSound() {
		return sound.get();
	}

	/** The gulp at the end: {@code LivingEntity#eat} plays the eating sound of whatever was finished. */
	@Override
	public SoundEvent getEatingSound() {
		return sound.get();
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		// Too drunk to enjoy it (DrinkBuffs): the drink that takes Tipsy to a level without buffs gives none, and the
		// buffs earlier drinks gave go too; whatever else was there (a potion's effect of the same kind) stays as it was
		boolean tooDrunk = DrinkBuffs.tooDrunk(entity);
		MobEffectInstance before = tooDrunk ? entity.getEffect(buff.getEffect()) : null;
		MobEffectInstance kept = before == null ? null : new MobEffectInstance(before);
		// Re-adding Absorption tops its hearts back up (the effect's attribute modifiers are taken off and put on again): keep what was left
		float absorption = entity.getAbsorptionAmount();
		// 1.20.1's eat gives nothing back: the vessel comes back here, dropped if the inventory has no room (as 26.x does)
		Remainder vessel = Remainder.before(stack);
		ItemStack result = vessel.after(super.finishUsingItem(stack, level, entity), level, entity);
		if (!level.isClientSide()) {
			// The buff is given here, not as an effect of the food: like 26.x's consume effect it is no part of the
			// food's data, so mods that read food effects (tooltips, food variety) see a drink as 26.x shows it
			entity.addEffect(new MobEffectInstance(buff));
			if (tooDrunk) {
				entity.removeEffect(buff.getEffect());
				if (kept != null) {
					entity.addEffect(kept);
				}
				DrinkBuffs.removeAll(entity);
				// What was left of the hearts stays, but never more than the Absorption that is still there gives: with
				// ours taken away and no other, the hearts go with it (1.20.1 has no max-absorption attribute to cap it)
				MobEffectInstance left = entity.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
				entity.setAbsorptionAmount(Math.min(absorption, left == null ? 0.0F : 4.0F * (left.getAmplifier() + 1)));
			} else {
				DrinkBuffs.remember(entity, buff);
			}
			boolean blackout = entity instanceof ServerPlayer player && Blackout.wouldBlackOut(player);
			Tipsy.addLevel(entity);
			Cheers.onDrink(entity, this, !tooDrunk);
			if (blackout) {
				// it catches up with you a little later (GoingUnder)
				GoingUnder.drankOneTooMany((ServerPlayer) entity, entity.getRandom());
			}
		}
		return result;
	}

	/**
	 * What a food leaves behind once eaten or drunk (the vessel, a bowl), given back the way 26.x's use remainder is.
	 * 1.20.1's {@code eat} only takes one off the stack, so all of it is done here: the last one of a stack turns into
	 * the remainder in the hand; otherwise it goes into the player's inventory, or drops at the player's feet if there
	 * is no room; nothing comes back in creative; and a mob (a fox) that finished its last one is left holding it.
	 *
	 * @param left the remainder (empty if the food has none)
	 */
	public record Remainder(ItemStack left)
	{
		/**
		 * Asked before {@code super.finishUsingItem}, which takes one from {@code stack}. The remainder is the item's own
		 * ({@link AgingBarrelBlockEntity#vesselOf}: a drink's vessel, a meal's crafting remainder), 1.20.1's food has none.
		 */
		public static Remainder before(ItemStack stack) {
			Item vessel = AgingBarrelBlockEntity.vesselOf(stack);
			return new Remainder(vessel == null ? ItemStack.EMPTY : new ItemStack(vessel));
		}

		/** Asked with what {@code super.finishUsingItem} returned; returns what the eater holds now. */
		public ItemStack after(ItemStack result, Level level, LivingEntity entity) {
			if (left.isEmpty()) {
				return result;
			}
			if (entity instanceof Player player) {
				if (player.getAbilities().instabuild) {
					return result;
				}
				if (result.isEmpty()) {
					return left.copy();
				}
				if (!level.isClientSide()) {
					ItemStack back = left.copy();
					if (!player.getInventory().add(back)) {
						player.drop(back, false);
					}
				}
				return result;
			}
			return result.isEmpty() ? left.copy() : result;
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return context.isSecondaryUseActive() ? DrinkPlacing.place(context) : InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> builder, TooltipFlag flag) {
		// Like FD's drinks: the buff in blue, with its duration
		builder.add(effectLine(buff));
		if (TipsyConfig.enabled()) {
			builder.add(Component.translatable("tooltip.vineandbarrel.drink.tipsy").withStyle(ChatFormatting.GRAY));
		}
		builder.add(DrinkPlacing.PLACEABLE_SNEAKING);
	}

	private static Component effectLine(MobEffectInstance effect) {
		// The name as a potion shows it (PotionUtils#addPotionTooltip)
		Component name = Component.translatable(effect.getDescriptionId());
		if (effect.getAmplifier() > 0) {
			name = Component.translatable("potion.withAmplifier", name, Component.translatable("potion.potency." + effect.getAmplifier()));
		}
		// Like a potion: no duration for effects that are over within a second (Saturation)
		return (effect.endsWithin(20) ? name.copy() : Component.translatable("potion.withDuration", name, MobEffectUtil.formatDuration(effect, 1.0F)))
			.withStyle(ChatFormatting.BLUE);
	}
}
