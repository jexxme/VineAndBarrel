package jexxme.vineandbarrel.watch.client;

import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How the scene is looked at: from above at {@link #pitch} degrees, turned by {@link #yaw} (Minecraft's yaw: 0 looks
 * south), {@link #zoom} times closer. One rotation for the blocks, the particles' billboards (through a camera) and the
 * screen's own math (the pointer, which block the mouse is over), so all three always agree.
 */
public final class SceneView
{
	static final float MIN_PITCH = 5.0F;
	static final float MAX_PITCH = 80.0F;
	static final float MIN_ZOOM = 0.6F;
	static final float MAX_ZOOM = 2.0F;
	private final WatchScene.View start;
	public float yaw;
	public float pitch;
	public float zoom = 1.0F;

	SceneView(WatchScene.View start) {
		this.start = start;
		reset();
	}

	void turn(float dYaw, float dPitch) {
		yaw = Mth.wrapDegrees(yaw + dYaw);
		pitch = Mth.clamp(pitch + dPitch, MIN_PITCH, MAX_PITCH);
	}

	void zoomBy(float factor) {
		zoom = Mth.clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
	}

	void reset() {
		yaw = start.yaw();
		pitch = start.pitch();
		zoom = 1.0F;
	}

	/** The point the view turns about and centres on: the middle of the stage, a little above its floor. */
	static Vec3 centre(WatchScene scene) {
		return new Vec3(scene.width() / 2.0, (1.0 + scene.view().height()) / 2.0, scene.depth() / 2.0);
	}

	/**
	 * GUI units per block at zoom 1: the stage, from its floor to a little above where things happen, fits the window
	 * as seen from the scene's starting view (so turning it doesn't change its size).
	 */
	static float scale(WatchScene scene, int windowWidth, int windowHeight) {
		Matrix4f view = viewMatrix(scene.view().yaw(), scene.view().pitch(), 1.0F);
		Vec3 centre = centre(scene);
		float maxX = 0.01F, maxY = 0.01F;
		for (int corner = 0; corner < 8; corner++) {
			double x = (corner & 1) == 0 ? 0 : scene.width();
			double y = (corner & 2) == 0 ? 0 : 1 + scene.view().height();
			double z = (corner & 4) == 0 ? 0 : scene.depth();
			Vector3f v = view.transformPosition((float) (x - centre.x), (float) (y - centre.y), (float) (z - centre.z), new Vector3f());
			maxX = Math.max(maxX, Math.abs(v.x));
			maxY = Math.max(maxY, Math.abs(v.y));
		}
		return Math.min(windowWidth * 0.5F / maxX, windowHeight * 0.53F / maxY);
	}

	/** The camera's rotation, exactly as {@link Camera} builds it from a yaw and a pitch. */
	static Quaternionf cameraRotation(float yaw, float pitch) {
		return new Quaternionf().rotationYXZ((float) Math.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, 0.0F);
	}

	/**
	 * From the stage (relative to its centre) into the picture's model space: the camera's view rotation, then a half
	 * turn about x, because the picture's space has y down and z going away from the viewer.
	 */
	static Matrix4f viewMatrix(float yaw, float pitch, float zoom) {
		Quaternionf view = cameraRotation(yaw, pitch).conjugate();
		return new Matrix4f().rotationX((float) Math.PI).rotate(view).scale(zoom);
	}

	/** Where a stage point (relative to the stage's centre) lands, in GUI units from the picture's centre. */
	static Vector3f project(Matrix4f view, Vec3 relative, float scale) {
		Vector3f v = view.transformPosition((float) relative.x, (float) relative.y, (float) relative.z, new Vector3f());
		return v.mul(scale, scale, 1.0F);
	}

	/** The camera the scene's particles face (their billboards are turned by its rotation). */
	static final class SceneCamera extends Camera
	{
		void set(Vec3 position, float yaw, float pitch) {
			setPosition(position);
			setRotation(yaw, pitch);
		}
	}
}
