package jexxme.vineandbarrel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Grapes, white grapes and hops: they plant their bush like sweet berries, and also straight into a fence, wall, upright
 * chain or bars standing on soil the bush grows on (the plant grows inside it, the block stays; see {@link VineOnSupportBlock}).
 * Not into one under water, as no bush grows there. (Planting into a trellis is the trellis's own business.)
 * Named like an item ("item.vineandbarrel.red_grapes"), not like its bush block.
 */
public class ClimbingPlantItem extends ItemNameBlockItem
{
	public ClimbingPlantItem(ClimbingBushBlock bush, Properties properties) {
		super(bush, properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState support = level.getBlockState(pos);
		if (getBlock() instanceof ClimbingBushBlock bush && VineSupports.isMakeshift(support) && support.getFluidState().isEmpty()) {
			BlockState root = bush.supportVine().defaultBlockState().setValue(VineOnSupportBlock.BASE, true);
			if (root.canSurvive(level, pos)) {
				if (level instanceof ServerLevel) {
					VineOnSupportBlock.place(level, pos, root, support, true, true);
					SoundType sound = bush.defaultBlockState().getSoundType();
					level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
					Player player = context.getPlayer();
					level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, level.getBlockState(pos)));
					if (player != null) {
						player.awardStat(Stats.ITEM_USED.get(this));
					}
					context.getItemInHand().consume(1, player);
				}
				return InteractionResult.sidedSuccess(level.isClientSide());
			}
		}
		return super.useOn(context);
	}
}
