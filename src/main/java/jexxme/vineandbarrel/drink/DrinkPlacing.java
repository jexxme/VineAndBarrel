package jexxme.vineandbarrel.drink;

import jexxme.vineandbarrel.barrel.BarrelRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/** D1: putting a mug or wine glass (full or empty) down on top of a block, up to four per block. */
public final class DrinkPlacing
{
	/** FD's tooltip line for things placed with sneak-use (its skillet): dark grey, italic. */
	public static final Component PLACEABLE_SNEAKING = Component.translatable("tooltip.vineandbarrel.placeable_sneaking")
		.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);

	private DrinkPlacing() {
	}

	/**
	 * Sneak-use of a drink or vessel: onto a placed-drinks block, or onto the top of any sturdy block. Anything that
	 * can't take it (a side face, no sturdy top, a full table, someone standing there) passes, so sneak-drinking works.
	 */
	public static InteractionResult place(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		ItemStack stack = context.getItemInHand();
		if (PlacedDrink.of(stack) == null) {
			return InteractionResult.PASS;
		}
		if (level.getBlockState(clicked).is(BarrelRegistry.PLACED_DRINKS.get())) {
			return addTo(level, clicked, stack, context.getPlayer(), context.getClickLocation()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (context.getClickedFace() != Direction.UP) {
			return InteractionResult.PASS;
		}
		BlockPos target = clicked.above();
		BlockState there = level.getBlockState(target);
		if (there.is(BarrelRegistry.PLACED_DRINKS.get())) {
			return addTo(level, target, stack, context.getPlayer(), context.getClickLocation()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		BlockState placed = BarrelRegistry.PLACED_DRINKS.get().defaultBlockState();
		if (!there.canBeReplaced(new BlockPlaceContext(context)) || !placed.canSurvive(level, target)) {
			return InteractionResult.PASS;
		}
		PlacedDrink kind = PlacedDrink.of(stack);
		BlockState first = placed.setValue(PlacedDrinksBlock.SPOT.get(PlacedDrinksBlock.spotAt(target, context.getClickLocation())), kind);
		Player player = context.getPlayer();
		if (!level.isUnobstructed(first, target, player == null ? CollisionContext.empty() : CollisionContext.of(player))) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			level.setBlock(target, placed, Block.UPDATE_ALL);
			addTo(level, target, stack, player, context.getClickLocation());
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Puts one of {@code stack} on a free spot of the placed-drinks block at {@code pos}: the spot under {@code hit} if
	 * it is free, else the nearest free one. Uses up the item (not in creative). Returns false if all four are taken.
	 */
	public static boolean addTo(Level level, BlockPos pos, ItemStack stack, @Nullable Player player, Vec3 hit) {
		PlacedDrink kind = PlacedDrink.of(stack);
		if (kind == null || !(level.getBlockEntity(pos) instanceof PlacedDrinksBlockEntity drinks)) {
			return false;
		}
		int spot = -1;
		for (int candidate : PlacedDrinksBlock.nearestFirst(PlacedDrinksBlock.spotAt(pos, hit))) {
			if (drinks.getItem(candidate).isEmpty()) {
				spot = candidate;
				break;
			}
		}
		if (spot < 0) {
			return false;
		}
		// Like BlockItem: never into someone standing there
		BlockState grown = level.getBlockState(pos).setValue(PlacedDrinksBlock.SPOT.get(spot), kind);
		if (!level.isUnobstructed(grown, pos, player == null ? CollisionContext.empty() : CollisionContext.of(player))) {
			return false;
		}
		if (!level.isClientSide()) {
			// block first: NeoForge snapshots the block entity at setBlock, so a cancelled place event restores the
			// table without the new drink while it refunds the held item
			level.setBlock(pos, grown, Block.UPDATE_ALL);
			drinks.setItem(spot, stack.copyWithCount(1));
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
			level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
		}
		return true;
	}
}
