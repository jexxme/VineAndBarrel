package jexxme.vineandbarrel.watch.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Draws a "Watch it" scene into its picture the way the level renderer draws the world: the view rotation goes into
 * the model-view matrix and every block, block entity, item and particle is submitted relative to the stage's centre.
 * So blocks are tessellated with their neighbours (culling, ambient occlusion, tints), block entities use their real
 * renderers, and particles are the real ones, billboarded to the same view.
 */
public final class SceneRenderer extends PictureInPictureRenderer<SceneRenderState>
{
	private final ParticlesRenderState particles = new ParticlesRenderState();
	private final SceneView.SceneCamera camera = new SceneView.SceneCamera();
	private final Set<String> failed = new HashSet<>();

	/** True while a scene is drawn (its particles then go into the scene's texture, see SceneParticleTargetMixin). */
	private static boolean drawing;

	public static boolean isDrawing() {
		return drawing;
	}

	@Override
	public void prepare(SceneRenderState state, GuiRenderState gui, FeatureRenderDispatcher features, int guiScale) {
		drawing = true;
		try {
			super.prepare(state, gui, features, guiScale);
		} finally {
			drawing = false;
		}
	}

	@Override
	public Class<SceneRenderState> getRenderStateClass() {
		return SceneRenderState.class;
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "vineandbarrel watch scene";
	}

	@Override
	protected void renderToTexture(SceneRenderState state, PoseStack base, SubmitNodeCollector collector) {
		ScenePlayer player = state.player();
		SceneWorld world = player.world();
		float partialTick = state.partialTick();
		Vec3 centre = SceneView.centre(player.scene());

		// The view, like the level renderer's camera rotation: on the model-view stack (prepare() pushed it and pops it
		// after the frame is drawn); everything below is submitted relative to the centre, unrotated
		Matrix4f view = SceneView.viewMatrix(state.yaw(), state.pitch(), state.zoom());
		RenderSystem.getModelViewStack().mul(base.last().pose()).mul(view);
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
		PoseStack pose = new PoseStack();

		// Blocks still dropping in are no one's neighbours yet (the floor under them keeps its face)
		Map<BlockPos, Float> drops = new HashMap<>();
		for (BlockPos pos : world.blocks().keySet()) {
			float drop = player.dropOffset(pos, partialTick);
			if (drop > 0.0F) {
				drops.put(pos, drop);
			}
		}
		for (Map.Entry<BlockPos, BlockState> entry : world.blocks().entrySet()) {
			BlockPos pos = entry.getKey();
			float drop = drops.getOrDefault(pos, 0.0F);
			pose.pushPose();
			pose.translate(pos.getX() - centre.x, pos.getY() - centre.y + drop, pos.getZ() - centre.z);
			collector.submitMovingBlock(pose, new StageBlock(world, pos, entry.getValue(), drop > 0.0F, drops.keySet()), 0);
			pose.popPose();
		}

		// The block the caption points at: an outline like the world's own block outline, in the caption's colour
		if (state.outline() != null && world.blocks().containsKey(state.outline())) {
			BlockPos pos = state.outline();
			pose.pushPose();
			pose.translate(pos.getX() - centre.x, pos.getY() - centre.y + drops.getOrDefault(pos, 0.0F), pos.getZ() - centre.z);
			collector.submitShapeOutline(pose, world.getBlockState(pos).getShape(world, pos), net.minecraft.client.renderer.rendertype.RenderTypes.linesTranslucent(),
				WatchScreen.ACCENT, 3.0F, false);
			pose.popPose();
		}

		CameraRenderState cameraState = new CameraRenderState();
		cameraState.pos = ScenePlayer.PARTICLE_ORIGIN.add(centre);
		cameraState.yRot = state.yaw();
		cameraState.xRot = state.pitch();
		cameraState.orientation.set(SceneView.cameraRotation(state.yaw(), state.pitch()));
		cameraState.initialized = true;
		BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
		for (Map.Entry<BlockPos, BlockEntity> entry : world.blockEntities().entrySet()) {
			BlockPos pos = entry.getKey();
			pose.pushPose();
			pose.translate(pos.getX() - centre.x, pos.getY() - centre.y + drops.getOrDefault(pos, 0.0F), pos.getZ() - centre.z);
			submitBlockEntity(dispatcher, entry.getValue(), pose, collector, cameraState, partialTick);
			pose.popPose();
		}

		// The figures, through their real renderer (skin, held items, arm swing), lit like everything else
		EntityRenderDispatcher entities = Minecraft.getInstance().getEntityRenderDispatcher();
		for (SceneFigure figure : player.figures()) {
			try {
				EntityRenderState figureState = entities.extractEntity(figure.entity(), partialTick);
				figureState.lightCoords = LightCoordsUtil.FULL_BRIGHT;
				figureState.shadowPieces.clear();
				figureState.nameTag = null;
				if (figure.isDrinking() && figureState instanceof ArmedEntityRenderState armed) {
					// no drinking pose in third person: the horn's raises the hand to the mouth
					armed.rightArmPose = HumanoidModel.ArmPose.TOOT_HORN;
				}
				Vec3 at = figure.entity().getPosition(partialTick).subtract(cameraState.pos);
				entities.submit(figureState, cameraState, at.x, at.y, at.z, new PoseStack(), collector);
			} catch (RuntimeException e) {
				if (failed.add("figure")) {
					VineAndBarrel.LOGGER.warn("Watch it: can't draw a figure in a scene", e);
				}
			}
		}

		camera.set(cameraState.pos, state.yaw(), state.pitch());
		particles.reset();
		player.particles().extract(particles, EVERYWHERE, camera, partialTick);
		particles.submit(collector, cameraState);
	}

	private <E extends BlockEntity, S extends BlockEntityRenderState> void submitBlockEntity(BlockEntityRenderDispatcher dispatcher, E entity,
																							  PoseStack pose, SubmitNodeCollector collector,
																							  CameraRenderState cameraState, float partialTick) {
		BlockEntityRenderer<E, S> renderer = dispatcher.getRenderer(entity);
		if (renderer == null) {
			return;
		}
		try {
			S state = renderer.createRenderState();
			renderer.extractRenderState(entity, state, partialTick, cameraState.pos, null);
			state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
			renderer.submit(state, pose, collector, cameraState);
		} catch (RuntimeException e) {
			// A block entity renderer that needs a real level (Farmer's Delight's stove, for the items cooking on it): the
			// block still shows, only its extras don't
			String type = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()));
			if (failed.add(type)) {
				VineAndBarrel.LOGGER.debug("Watch it: {}'s extras aren't drawn in scenes ({})", type, e.toString());
			}
		}
	}

	/** Takes every particle: the scene's particles are all inside the window. */
	private static final Frustum EVERYWHERE = new Frustum(new Matrix4f(), new Matrix4f())
	{
		@Override
		public boolean pointInFrustum(double x, double y, double z) {
			return true;
		}
	};

	/**
	 * One block of the stage as vanilla's moving-block renderer wants it: its own state at its position, its neighbours
	 * from the stage (for culling and ambient occlusion), full light. A block still dropping in has no neighbours yet, and
	 * is no one's neighbour.
	 */
	private static final class StageBlock extends MovingBlockRenderState
	{
		private final SceneWorld world;
		private final boolean alone;
		private final Set<BlockPos> dropping;

		StageBlock(SceneWorld world, BlockPos pos, BlockState state, boolean alone, Set<BlockPos> dropping) {
			this.world = world;
			this.alone = alone;
			this.dropping = dropping;
			this.blockPos = pos;
			this.randomSeedPos = pos;
			this.blockState = state;
			this.biome = world.biome();
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			if (pos.equals(blockPos)) {
				return blockState;
			}
			return alone || dropping.contains(pos) ? Blocks.AIR.defaultBlockState() : world.getBlockState(pos);
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return getBlockState(pos).getFluidState();
		}

		@Override
		public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
			return world.getBlockEntity(pos);
		}

		@Override
		public int getBlockTint(BlockPos pos, ColorResolver color) {
			return world.getBlockTint(pos, color);
		}

		@Override
		public CardinalLighting cardinalLighting() {
			return world.cardinalLighting();
		}

		@Override
		public int getBrightness(LightLayer layer, BlockPos pos) {
			return 15;
		}

		@Override
		public int getRawBrightness(BlockPos pos, int darkening) {
			return 15;
		}

		@Override
		public int getHeight() {
			return SceneWorld.HEIGHT;
		}

		@Override
		public int getMinY() {
			return 0;
		}
	}

}
