package jexxme.vineandbarrel.tipsy.client;

import jexxme.vineandbarrel.tipsy.Hangover;
import jexxme.vineandbarrel.tipsy.TipsyEyes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * The morning after on this client ({@link Hangover}): daylight under the open sky glares (drawn with the drunk eyes,
 * {@link DrunkEyes}), and a loud noise close by makes you wince, the lids squeezing shut for a moment. Nothing moves
 * the view. The glare follows the "Double vision" setting, the wince vanilla's Darkness Pulsing.
 */
public final class HangoverClient
{
	/** A sound this loud (its volume) counts as loud: explosions, thunder, bells, note blocks, horns. */
	private static final float LOUD = 1.5F;
	/** Ticks the lids squeeze shut, then ease open, and the shortest gap between two winces. */
	private static final int SQUEEZE = 2, OPEN = 12, GAP = 30;
	/** How far the lids close in a wince. */
	private static final float WINCE_LIDS = 0.5F;

	private static boolean listening;
	/** The tick the last wince started, or a long time ago. */
	private static long winceAt = -1000L;
	private static long now;

	private HangoverClient() {
	}

	/** The tick this client first saw the current hangover, or -1 without one. */
	private static long seenAt = -1L;

	/** Once a client tick. */
	static void tick(LocalPlayer player) {
		now = player.level().getGameTime();
		boolean hungover = Hangover.has(player);
		if (hungover && seenAt < 0L) {
			seenAt = now;
		} else if (!hungover) {
			seenAt = -1L;
		}
		if (!listening) {
			Minecraft.getInstance().getSoundManager().addListener(LISTENER);
			listening = true;
		}
	}

	/** A new world or a disconnect: no wince left over. */
	static void reset() {
		winceAt = -1000L;
		now = 0L;
		seenAt = -1L;
	}

	/** {@return how much daylight glares for this player now (0 to 1), squinting {@code squint}} */
	static float glare(LocalPlayer player, float squint) {
		Level level = player.level();
		boolean outdoors = level.isDay() && level.canSeeSky(BlockPos.containing(player.getEyePosition()));
		return TipsyEyes.glare(Hangover.has(player), outdoors, fresh(), level.getRainLevel(1.0F), squint);
	}

	/** {@return how fresh the hangover is: 1 the moment it began, easing off over half a minute} */
	static float fresh() {
		return seenAt < 0L ? 0.0F : TipsyEyes.fresh(now - seenAt);
	}

	/** {@return how far a wince has the lids shut now (0 to {@link #WINCE_LIDS})} */
	static float wince(float partial) {
		float t = now + partial - winceAt;
		if (t < 0.0F || t > SQUEEZE + OPEN) {
			return 0.0F;
		}
		float shut = t < SQUEEZE ? t / SQUEEZE : 1.0F - (t - SQUEEZE) / OPEN;
		return WINCE_LIDS * Mth.clamp(shut, 0.0F, 1.0F);
	}

	/** Squinting into the daylight: the lids this far shut at full glare. */
	private static final float GLARE_LIDS = 0.3F;

	/** Draws a wince's lids, and the squint against the glare (on the drunk eyes' layer), scaled by Darkness Pulsing. */
	static void render(GuiGraphics gui, float partial, float darkness) {
		float lids = Math.max(wince(partial), GLARE_LIDS * DrunkEyes.glare());
		if (lids > 0.0F && darkness > 0.0F) {
			BlackoutClient.drawLids(gui, lids * darkness);
		}
	}

	private static final SoundEventListener LISTENER = HangoverClient::onSound;

	private static void onSound(SoundInstance sound, WeighedSoundEvents events) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !Hangover.has(player) || now - winceAt < GAP) {
			return;
		}
		boolean loud = sound.getVolume() >= LOUD || sound.getLocation().getPath().contains("anvil");
		if (!loud) {
			return;
		}
		// how far it carries, as the sound engine works it out (newer versions hand it to the listener)
		float range = Math.max(sound.getVolume(), 1.0F) * sound.getSound().getAttenuationDistance();
		// close enough to be loud: within half the distance it carries (a sound in your own head always is)
		if (!sound.isRelative() && Float.isFinite(range)
			&& player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) > range * range * 0.25F) {
			return;
		}
		winceAt = now;
	}
}
