package jexxme.vineandbarrel.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.forge.ForgeCopper;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Lightning cleans copper kettles as it cleans vanilla copper (NeoForge 21.1: the oxidizables data map): the struck
 * block goes back to its first stage, blocks around it one stage. Vanilla asks its own fixed table, which has no kettle
 * in it, so the two questions are answered for our blocks here (see {@link ForgeCopper}).
 */
@Mixin(LightningBolt.class)
abstract class LightningBoltMixin
{
	@WrapOperation(method = "clearCopperOnLightningStrike", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/WeatheringCopper;getFirst(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
	private static BlockState vineandbarrel$firstStage(BlockState state, Operation<BlockState> original) {
		Block first = ForgeCopper.first(state.getBlock());
		return first != null ? first.withPropertiesOf(state) : original.call(state);
	}

	@WrapOperation(method = "randomStepCleaningCopper", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/WeatheringCopper;getPrevious(Lnet/minecraft/world/level/block/state/BlockState;)Ljava/util/Optional;"))
	private static Optional<BlockState> vineandbarrel$previousStage(BlockState state, Operation<Optional<BlockState>> original) {
		Block previous = ForgeCopper.previous(state.getBlock());
		return previous != null ? Optional.of(previous.withPropertiesOf(state)) : original.call(state);
	}
}
