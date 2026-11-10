package jexxme.vineandbarrel.forge.mixin.client;

import jexxme.vineandbarrel.platform.hooks.DataPacketReceiver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Our block entities read their update packets themselves ({@link DataPacketReceiver}). Forge 47's own
 * {@code IForgeBlockEntity#onDataPacket} takes the packet, not its tag, so the common block entities can't override
 * it as they do on 26.x: the receiver is called instead of it, with the packet's tag (an empty one when the block
 * entity has nothing to tell, as on 26.x). Vanilla handles the packet in a lambda, whose name differs between the dev
 * game and the shipped one; so the packet is taken over right after the switch to the client thread, for our block
 * entities only (what vanilla does after it, refreshing an open command block screen, is not for them).
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin
{
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private Connection connection;

	@Inject(method = "handleBlockEntityData", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"))
	private void vineandbarrel$onDataPacket(ClientboundBlockEntityDataPacket packet, CallbackInfo callback) {
		if (this.minecraft.level == null) {
			return;
		}
		BlockEntity blockEntity = this.minecraft.level.getBlockEntity(packet.getPos(), packet.getType()).orElse(null);
		if (blockEntity instanceof DataPacketReceiver receiver) {
			CompoundTag tag = packet.getTag();
			receiver.onDataPacket(this.connection, tag == null ? new CompoundTag() : tag, this.minecraft.level.registryAccess());
			callback.cancel();
		}
	}
}
