package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/** What a player's render state carries for the tipsy poses on Fabric (NeoForge keeps it in its render data). */
public final class TipsySway
{
	/** How the body is posed (TipsyClient#bodyPose: sway, hiccup, stumble, trip, blackout), absent when it stands as vanilla draws it. */
	public static final RenderStateDataKey<TipsyClient.BodyPose> POSE = RenderStateDataKey.create(() -> "vineandbarrel:tipsy_body_pose");
	/** Set while a lean is pushed onto the pose stack, so the matching pop happens exactly once. */
	public static final RenderStateDataKey<Boolean> PUSHED = RenderStateDataKey.create(() -> "vineandbarrel:tipsy_sway_pushed");

	private TipsySway() {
	}
}
