package jexxme.vineandbarrel.compat.jade;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.BarrelStage;
import jexxme.vineandbarrel.barrel.BarrelStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Aging Barrel in Jade: the same status line an empty-hand click shows (B10), split in two lines: the drink and its
 * servings, then the stage and when the next one comes. The client knows the batch and its age, so no server data.
 */
public enum BarrelProvider implements IBlockComponentProvider
{
	INSTANCE;

	private static final Identifier UID = VineAndBarrel.id("aging_barrel");

	@Override
	public Identifier getUid() {
		return UID;
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		if (!(accessor.getBlockEntity() instanceof AgingBarrelBlockEntity barrel)) {
			return;
		}
		if (barrel.stage() == BarrelStage.EMPTY) {
			tooltip.add(Component.translatable("jade.vineandbarrel.aging_barrel.empty"));
			return;
		}
		tooltip.add(Component.translatable("jade.vineandbarrel.serving", barrel.drink().getHoverName(), BarrelStatus.servings(barrel.servings())));
		tooltip.add(BarrelStatus.state(barrel));
	}
}
