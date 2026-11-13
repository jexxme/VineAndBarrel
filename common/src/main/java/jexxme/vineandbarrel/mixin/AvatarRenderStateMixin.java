package jexxme.vineandbarrel.mixin;

import jexxme.vineandbarrel.tipsy.BlackoutPose;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Every player's render state can carry how they lie on the ground after a blackout (falling, lying, getting up), and
 * how far their arms go out for balance in a stumble, on both loaders, so the player model (and the armour drawn with it) can pose head and limbs from it
 * ({@link TipsyClient#poseBlackout}). Filled in where the loaders fill in the rest of the body's pose
 * ({@link TipsyClient#bodyPose(net.minecraft.world.entity.Entity, AvatarRenderState)}).
 */
@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements TipsyClient.BlackoutCarrier
{
	@Unique
	private BlackoutPose.@Nullable Body vineandbarrel$blackout;

	@Override
	public BlackoutPose.@Nullable Body vineandbarrel$blackout() {
		return this.vineandbarrel$blackout;
	}

	@Override
	public void vineandbarrel$setBlackout(BlackoutPose.@Nullable Body body) {
		this.vineandbarrel$blackout = body;
	}

	@Unique
	private float vineandbarrel$balance;

	@Override
	public float vineandbarrel$balance() {
		return this.vineandbarrel$balance;
	}

	@Override
	public void vineandbarrel$setBalance(float balance) {
		this.vineandbarrel$balance = balance;
	}

	@Unique
	private float vineandbarrel$windup;

	@Override
	public float vineandbarrel$windup() {
		return this.vineandbarrel$windup;
	}

	@Override
	public void vineandbarrel$setWindup(float windup) {
		this.vineandbarrel$windup = windup;
	}
}
