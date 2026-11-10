package jexxme.vineandbarrel.fabric.mixin;

import jexxme.vineandbarrel.fabric.FabricAttachments;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player starts tracking an entity: the entity's synced attachments follow its spawn packet ({@link FabricAttachments};
 * 1.21.1 and newer: Fabric API's own attachment sync). Fabric API's {@code EntityTrackingEvents.START_TRACKING} runs at
 * the head of this method on this Minecraft, before the spawn packet is sent, when the client could not find the entity
 * yet.
 */
@Mixin(ServerEntity.class)
abstract class ServerEntityMixin
{
	@Shadow
	@Final
	private Entity entity;

	@Inject(method = "addPairing", at = @At("TAIL"))
	private void vineandbarrel$syncAttachments(ServerPlayer player, CallbackInfo callback) {
		FabricAttachments.sendAll(entity, player);
	}
}
