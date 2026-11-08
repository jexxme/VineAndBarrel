package jexxme.vineandbarrel.compat.jade;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.BarrelStatus;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Brew Kettle in Jade (client side): the brew and its servings, how far the current batch is, and a hint when it has no
 * heat. The brew is synced to clients anyway; the progress comes from the server ({@link KettleDataProvider}). Since
 * Minecraft 1.21.6 Jade rejects a provider that is both, so they are two classes sharing one id.
 */
public enum KettleProvider implements IBlockComponentProvider
{
	INSTANCE;

	static final Identifier UID = VineAndBarrel.id("brew_kettle");

	@Override
	public Identifier getUid() {
		return UID;
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		if (!(accessor.getBlockEntity() instanceof BrewKettleBlockEntity kettle)) {
			return;
		}
		ItemStack brew = kettle.getBrew();
		if (!brew.isEmpty()) {
			tooltip.add(Component.translatable("jade.vineandbarrel.serving", brew.getHoverName(), BarrelStatus.servings(brew.getCount())));
		}
		KettleDataProvider.INSTANCE.decodeFromData(accessor).filter(progress -> progress.time() > 0 && progress.total() > 0).ifPresent(progress ->
			tooltip.add(Component.translatable("jade.vineandbarrel.brew_kettle.brewing", Math.min(100, progress.time() * 100 / progress.total()))));
		if (kettle.hasIngredients() && !kettle.isHeated()) {
			tooltip.add(Component.translatable("container.vineandbarrel.brew_kettle.not_heated").withStyle(ChatFormatting.GRAY));
		}
	}
}
