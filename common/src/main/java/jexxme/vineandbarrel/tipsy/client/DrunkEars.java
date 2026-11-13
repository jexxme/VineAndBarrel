package jexxme.vineandbarrel.tipsy.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

/**
 * Woolly ears (Tipsy overhaul, step 4): the world sounds muffled, more with each level ({@link TipsyLevels.Ears#muffle}),
 * as if through cotton. Each world sound (one that fades with distance) gets a low-pass filter as it starts
 * ({@code jexxme.vineandbarrel.mixin.ChannelMixin}); music, the menus and your own heartbeat stay clear. Needs OpenAL's
 * EFX extension (OpenAL Soft, which Minecraft ships, has it); without it the ears stay clear. Off with the player's own
 * "Woolly sound" setting.
 */
public final class DrunkEars
{
	/** At full muffle the highs are down to this share, the whole sound to this. */
	private static final float HIGHS_AT_FULL = 0.12F, GAIN_AT_FULL = 0.8F;
	/** Per tick, how far the muffle eases toward a new level: over about two seconds. */
	private static final float EASE = 0.025F;

	/** How muffled now (0 clear .. 1), set on the client thread, read on the sound thread. */
	private static volatile float muffle;
	/** -1 not tried yet, 0 no EFX, 1 EFX there. Sound thread only. */
	private static int efx = -1;

	private DrunkEars() {
	}

	/** Once a client tick: eases toward this level's muffle. */
	static void tick(LocalPlayer player) {
		float target = TipsyConfig.woollySound() ? TipsyLevels.at(Tipsy.getLevel(player)).ears().muffle() : 0.0F;
		// going under, the world goes far away (the blackout ends it either way)
		float under = TipsyConfig.woollySound() ? jexxme.vineandbarrel.tipsy.GoingUnder.under(player, 0.0F) : 0.0F;
		target = Math.max(target, under);
		muffle = under > 0.0F ? Math.max(target, Mth.approach(muffle, target, EASE)) : Mth.approach(muffle, target, EASE);
	}

	/** A new world or a disconnect: clear ears. */
	static void reset() {
		muffle = 0.0F;
	}

	/** {@return how muffled the world sounds now, 0 to 1} */
	public static float muffle() {
		return muffle;
	}

	/** A world sound starts on this OpenAL source: muffled as the ears are now (sound thread). */
	public static void onWorldSound(int source) {
		float m = muffle;
		if (m <= 0.001F || !efx()) {
			clear(source);
			return;
		}
		try {
			int filter = EXTEfx.alGenFilters();
			EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
			EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, Mth.lerp(m, 1.0F, GAIN_AT_FULL) * (m > 0.9F ? 1.0F - (m - 0.9F) * 5.0F : 1.0F));
			EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, Mth.lerp(m, 1.0F, HIGHS_AT_FULL));
			// the source copies the filter's settings: the filter itself can go at once
			AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
			EXTEfx.alDeleteFilters(filter);
		} catch (RuntimeException | LinkageError e) {
			efx = 0;
			VineAndBarrel.LOGGER.warn("Tipsy: no muffled sound, OpenAL's EFX filters failed", e);
		}
	}

	/** A sound that doesn't fade with distance (music, menus, the heartbeat) starts on this source: clear. */
	public static void onFlatSound(int source) {
		clear(source);
	}

	private static void clear(int source) {
		if (efx == 1) {
			AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
		}
	}

	private static boolean efx() {
		if (efx < 0) {
			try {
				efx = AL.getCapabilities().alGenFilters != 0L ? 1 : 0;
			} catch (RuntimeException | LinkageError e) {
				efx = 0;
			}
		}
		return efx == 1;
	}
}
