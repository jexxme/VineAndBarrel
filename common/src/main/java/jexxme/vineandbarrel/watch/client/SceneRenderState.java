package jexxme.vineandbarrel.watch.client;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * One frame of a "Watch it" scene for vanilla's GUI renderer: which scene, how it is looked at, and where on the screen
 * it goes ({@code scale} = GUI units per block at zoom 1).
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
	@Nullable BlockPos outline,
	@Nullable ScreenRectangle scissorArea,
	@Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState
{
	public SceneRenderState(ScenePlayer player, SceneView view, float partialTick, int x0, int y0, int x1, int y1, float scale,
							@Nullable BlockPos outline, @Nullable ScreenRectangle scissorArea) {
		this(player, view.yaw, view.pitch, view.zoom, partialTick, x0, y0, x1, y1, scale, outline, scissorArea,
			PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
	}
}
