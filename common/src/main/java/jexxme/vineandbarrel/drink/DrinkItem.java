package jexxme.vineandbarrel.drink;

import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.DrinkBuffs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;

import java.util.List;

/**
 * A wine, beer or cider (DESIGN §5). Drinking it works like any FD drink: the buff is given when the drink is
 * finished (1.21.1 has no consumable component) and the vessel comes back as what the item's food converts to. It is drunk,
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
	private final Holder<SoundEvent> sound;

	/** @param sound how drinking from this drink's vessel sounds (1.21.1: the item says it, not a component) */
	public DrinkItem(Properties properties, int color, MobEffectInstance buff, boolean aged, Holder<SoundEvent> sound) {
		super(properties);
		this.color = color;
		this.buff = buff;
		this.aged = aged;
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

	/** 1.21.1: a food is eaten unless its item says otherwise (26.x: the consumable component's animation). */
	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	/** The sips while drinking (26.x: the consumable component's sound). */
	@Override
	public SoundEvent getDrinkingSound() {
		return sound.value();
	}

	/** The gulp at the end: {@code LivingEntity#eat} plays the eating sound of whatever was finished. */
	@Override
	public SoundEvent getEatingSound() {
		return sound.value();
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		// Too drunk to enjoy it (DrinkBuffs): the drink that takes Tipsy to a level without buffs gives none, and the
		// buffs earlier drinks gave go too; whatever else was there (a potion's effect of the same kind) stays as it was
		boolean tooDrunk = DrinkBuffs.tooDrunk(entity);
		MobEffectInstance before = tooDrunk ? entity.getEffect(buff.getEffect()) : null;
		MobEffectInstance kept = before == null ? null : new MobEffectInstance(before);
		// Re-adding Absorption tops its hearts back up (AbsorptionMobEffect.onEffectStarted): keep what was left
		float absorption = entity.getAbsorptionAmount();
		// 1.21.1's Player#eat puts the vessel into the inventory and forgets it if there is no room: drop it then (26.x does)
		Remainder vessel = Remainder.before(stack, entity);
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
				entity.setAbsorptionAmount(absorption);
			} else {
				DrinkBuffs.remember(entity, buff);
			}
			boolean blackout = entity instanceof ServerPlayer player && Blackout.wouldBlackOut(player);
			Tipsy.addLevel(entity);
			Cheers.onDrink(entity, this, !tooDrunk);
			if (blackout) {
				Blackout.start((ServerPlayer) entity);
			}
		}
		return result;
	}

	/**
	 * What a food leaves behind once eaten or drunk (its {@code using_converts_to}: the vessel, a bowl), given back the
	 * way 26.x's use remainder is. 1.21.1's {@code Player#eat} puts it into the inventory and forgets it if there is no
	 * room, and gives nothing to anyone who is not a player; here it drops at the player's feet then, and a mob (a fox)
	 * that finished its last one is left holding it.
	 *
	 * @param left   the remainder (empty if the food has none)
	 * @param noRoom whether vanilla is about to lose it: a player (not in creative) finishing one of several, with no
	 *               slot that takes the remainder
	 */
	public record Remainder(ItemStack left, boolean noRoom)
	{
		/** Asked before {@code super.finishUsingItem}, which takes one from {@code stack} and fills the inventory. */
		public static Remainder before(ItemStack stack, LivingEntity entity) {
			FoodProperties food = stack.get(DataComponents.FOOD);
			ItemStack left = food == null ? ItemStack.EMPTY : food.usingConvertsTo().map(ItemStack::copy).orElse(ItemStack.EMPTY);
			boolean noRoom = !left.isEmpty() && entity instanceof Player player && !player.hasInfiniteMaterials() && stack.getCount() > 1
				&& player.getInventory().getSlotWithRemainingSpace(left) == -1 && player.getInventory().getFreeSlot() == -1;
			return new Remainder(left, noRoom);
		}

		/** Asked with what {@code super.finishUsingItem} returned; returns what the eater holds now. */
		public ItemStack after(ItemStack result, Level level, LivingEntity entity) {
			if (noRoom && !level.isClientSide()) {
				((Player) entity).drop(left, false);
			}
			return !left.isEmpty() && !(entity instanceof Player) && result.isEmpty() ? left : result;
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return context.isSecondaryUseActive() ? DrinkPlacing.place(context) : InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> builder, TooltipFlag flag) {
		// Like FD's drinks: the buff in blue, with its duration
		builder.add(effectLine(buff, context.tickRate()));
		if (TipsyConfig.enabled()) {
			builder.add(Component.translatable("tooltip.vineandbarrel.drink.tipsy").withStyle(ChatFormatting.GRAY));
		}
		builder.add(DrinkPlacing.PLACEABLE_SNEAKING);
	}

	private static Component effectLine(MobEffectInstance effect, float tickRate) {
		// The name as a potion shows it (PotionContents#addPotionTooltip)
		Component name = Component.translatable(effect.getDescriptionId());
		if (effect.getAmplifier() > 0) {
			name = Component.translatable("potion.withAmplifier", name, Component.translatable("potion.potency." + effect.getAmplifier()));
		}
		// Like a potion: no duration for effects that are over within a second (Saturation)
		return (effect.endsWithin(20) ? name.copy() : Component.translatable("potion.withDuration", name, MobEffectUtil.formatDuration(effect, 1.0F, tickRate)))
			.withStyle(ChatFormatting.BLUE);
	}
}
