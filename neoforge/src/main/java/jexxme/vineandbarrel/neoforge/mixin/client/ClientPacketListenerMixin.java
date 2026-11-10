package jexxme.vineandbarrel.neoforge.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.platform.hooks.DataPacketReceiver;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Our block entities read their update packets themselves ({@link DataPacketReceiver}). NeoForge 21.1's own
 * {@code IBlockEntityExtension#onDataPacket} takes the packet, not its tag, so the common block entities can't override
 * it as they do on 26.x: the receiver is called where NeoForge calls its method, with the packet's tag (an empty one
 * when the block entity has nothing to tell, as on 26.x). The packet's handling runs in a lambda, matched by its name
 * pattern rather than its number.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin
{
	@WrapOperation(method = "/^lambda\\$handleBlockEntityData\\$\\d+$/", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;onDataPacket(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;Lnet/minecraft/core/HolderLookup$Provider;)V"))
	private void vineandbarrel$onDataPacket(BlockEntity blockEntity, Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries,
		Operation<Void> original) {
		if (blockEntity instanceof DataPacketReceiver receiver) {
			CompoundTag tag = packet.getTag();
			receiver.onDataPacket(connection, tag == null ? new CompoundTag() : tag, registries);
		} else {
			original.call(blockEntity, connection, packet, registries);
		}
	}
}
