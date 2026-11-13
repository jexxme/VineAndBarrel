package jexxme.vineandbarrel.mixin;

import com.mojang.blaze3d.audio.Channel;
import jexxme.vineandbarrel.tipsy.client.DrunkEars;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Woolly ears ({@link DrunkEars}): a sound that fades with distance is a world sound and is muffled as the ears are when
 * it starts; one that doesn't (music, menus, the heartbeat) stays clear. Both loaders.
 */
@Mixin(Channel.class)
abstract class ChannelMixin
{
	@Shadow
	@Final
	private int source;

	@Inject(method = "linearAttenuation", at = @At("TAIL"))
	private void vineandbarrel$muffleWorldSound(float maxDistance, CallbackInfo ci) {
		DrunkEars.onWorldSound(this.source);
	}

	@Inject(method = "disableAttenuation", at = @At("TAIL"))
	private void vineandbarrel$clearFlatSound(CallbackInfo ci) {
		DrunkEars.onFlatSound(this.source);
	}
}
