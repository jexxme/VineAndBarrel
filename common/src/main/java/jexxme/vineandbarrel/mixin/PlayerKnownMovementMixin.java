package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.TipsyLegs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Tells {@link TipsyLegs} how far a player moved with each movement packet: a player moves on its client, so this is
 * the server's only view of its walking speed (for the stumbles and trips at Tipsy V). Newer versions keep it on the
 * player themselves ({@code ServerPlayer#getKnownMovement}, set at this same place); 1.20.1 only passes it on to the
 * ground check, which is where this reads it. Changes nothing.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerKnownMovementMixin
{
	@Shadow
	public ServerPlayer player;

	@ModifyArg(method = "handleMovePlayer", index = 1, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerPlayer;setOnGroundWithKnownMovement(ZLnet/minecraft/world/phys/Vec3;)V"))
	private Vec3 vineandbarrel$knownMovement(Vec3 movement) {
		TipsyLegs.knownMovement(this.player, movement);
		return movement;
	}
}
