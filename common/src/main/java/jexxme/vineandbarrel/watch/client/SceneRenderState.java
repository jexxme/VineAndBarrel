package jexxme.vineandbarrel.watch.client;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * One frame of a "Watch it" scene for {@link SceneRenderer}: which scene, how it is looked at, and where on the screen
 * it goes (GUI pixels; {@code scale} = GUI units per block at zoom 1). The scene is centred in that rectangle and
 * clipped to it.
 */
public record SceneRenderState(
	ScenePlayer player,
	float yaw,
	float pitch,
	float zoom,
	float partialTick,
	int x0,
	int y0,
	int x1,
	int y1,
	float scale,
	@Nullable BlockPos outline
)
{
	public SceneRenderState(ScenePlayer player, SceneView view, float partialTick, int x0, int y0, int x1, int y1, float scale,
							@Nullable BlockPos outline) {
		this(player, view.yaw, view.pitch, view.zoom, partialTick, x0, y0, x1, y1, scale, outline);
	}
}
