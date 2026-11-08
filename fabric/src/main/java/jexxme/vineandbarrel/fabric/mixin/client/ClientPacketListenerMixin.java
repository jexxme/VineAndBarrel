package jexxme.vineandbarrel.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.platform.hooks.DataPacketReceiver;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Our block entities read their update packets themselves (NeoForge's {@code onDataPacket}; see {@link DataPacketReceiver}).
 * The packet's handling runs in a lambda, matched by its name pattern rather than its number, so a renumbering in a
 * Minecraft update doesn't break it (only the load call inside is pinned).
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin
{
	@WrapOperation(method = "/^lambda\\$handleBlockEntityData\\$\\d+$/", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/world/level/storage/ValueInput;)V"))
	private void vineandbarrel$onDataPacket(BlockEntity blockEntity, ValueInput input, Operation<Void> original) {
		if (blockEntity instanceof DataPacketReceiver receiver) {
			receiver.onDataPacket(((ClientPacketListener) (Object) this).getConnection(), input);
		} else {
			original.call(blockEntity, input);
		}
	}
}
