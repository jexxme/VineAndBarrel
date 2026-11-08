package jexxme.vineandbarrel.drink;

import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.DrinkBuffs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;

import java.util.function.Consumer;

/**
 * A wine, beer or cider (DESIGN §5). Drinking it works like any FD drink: the buff comes from the item's consumable
 * component and the vessel comes back as its use remainder. On top of that it adds one level of Tipsy (nothing in
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

	public DrinkItem(Properties properties, int color, MobEffectInstance buff, boolean aged) {
		super(properties);
		this.color = color;
		this.buff = buff;
		this.aged = aged;
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

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		// Too drunk to enjoy it (DrinkBuffs): the drink that takes Tipsy to a level without buffs gives none, and the
		// buffs earlier drinks gave go too; whatever else was there (a potion's effect of the same kind) stays as it was
		boolean tooDrunk = DrinkBuffs.tooDrunk(entity);
		MobEffectInstance before = tooDrunk ? entity.getEffect(buff.getEffect()) : null;
		MobEffectInstance kept = before == null ? null : new MobEffectInstance(before);
		// Re-adding Absorption tops its hearts back up (AbsorptionMobEffect.onEffectStarted): keep what was left
		float absorption = entity.getAbsorptionAmount();
		ItemStack result = super.finishUsingItem(stack, level, entity);
		if (!level.isClientSide()) {
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

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return context.isSecondaryUseActive() ? DrinkPlacing.place(context) : InteractionResult.PASS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		// Like FD's drinks: the buff in blue, with its duration
		builder.accept(effectLine(buff, context.tickRate()));
		if (TipsyConfig.enabled()) {
			builder.accept(Component.translatable("tooltip.vineandbarrel.drink.tipsy").withStyle(ChatFormatting.GRAY));
		}
		builder.accept(DrinkPlacing.PLACEABLE_SNEAKING);
	}

	private static Component effectLine(MobEffectInstance effect, float tickRate) {
		Component name = PotionContents.getPotionDescription(effect.getEffect(), effect.getAmplifier());
		// Like a potion: no duration for effects that are over within a second (Saturation)
		return (effect.endsWithin(20) ? name.copy() : Component.translatable("potion.withDuration", name, MobEffectUtil.formatDuration(effect, 1.0F, tickRate)))
			.withStyle(ChatFormatting.BLUE);
	}
}
