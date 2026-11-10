package jexxme.vineandbarrel.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.platform.hooks.DataPacketReceiver;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Our block entities read their update packets themselves (NeoForge's {@code onDataPacket}; see {@link DataPacketReceiver}).
 * The packet's handling runs in a lambda of {@code handleBlockEntityData}: {@code method_38542} is that lambda
 * {@code (ClientboundBlockEntityDataPacket, BlockEntity)} by its intermediary name, which it has both in development
 * and in a released game on this Minecraft (lambdas have no other name in the mappings). As in vanilla, a packet with
 * an empty tag loads nothing.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin
{
	// The lambda keeps its own name, so only the call inside goes through the refmap
	@WrapOperation(method = "method_38542", remap = false,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", remap = true))
	private void vineandbarrel$onDataPacket(BlockEntity blockEntity, CompoundTag tag, HolderLookup.Provider registries, Operation<Void> original) {
		if (blockEntity instanceof DataPacketReceiver receiver) {
			receiver.onDataPacket(((ClientPacketListener) (Object) this).getConnection(), tag, registries);
		} else {
			original.call(blockEntity, tag, registries);
		}
	}
}
