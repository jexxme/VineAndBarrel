package jexxme.vineandbarrel.watch.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws a "Watch it" scene into its rectangle of the screen the way the level renderer draws the world: the view
 * rotation goes into the model-view matrix and every block, block entity, figure and particle is drawn relative to the
 * stage's centre, unrotated. So blocks are tessellated with their neighbours (culling, ambient occlusion, tints), block
 * entities use their real renderers, the light falls on everything as in the world however the view is turned, and
 * particles are the real ones, billboarded to the same view. On this Minecraft version the scene is drawn straight
 * into the screen (clipped to its rectangle, with the depth buffer to itself), not into a picture of its own.
 */
public final class SceneRenderer
{
	/** The outline's width as this Minecraft's lines shader takes it; it draws as wide as 26.x draws its 3. */
	private static final float OUTLINE_WIDTH = 2.0F;
	/** What the lines shader shrinks the view by (to lift lines off faces in the world's perspective view). */
	private static final float LINES_VIEW_SHRINK = 1.0F - 1.0F / 256.0F;
	/** How far the outline is lifted toward the viewer instead (GUI units), so it wins over the faces it runs along. */
	private static final float OUTLINE_LIFT = 1.0F;
	private static final int DEPTH_BUFFER_BIT = 256;

	private static final SceneView.SceneCamera CAMERA = new SceneView.SceneCamera();
	private static final Set<String> FAILED = new HashSet<>();

	/**
	 * Draws one block of a stage into its layer's buffer. Vanilla's block renderer does, by default; NeoForge's client
	 * sets its own, which first asks the block's model what it wants to know about the spot (without that a trellis in
	 * a scene had no frame there).
	 */
	@FunctionalInterface
	public interface BlockDrawer
	{
		void draw(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose, VertexConsumer buffer, RandomSource random);
	}

	private static BlockDrawer blockDrawer = (state, pos, level, pose, buffer, random) ->
		Minecraft.getInstance().getBlockRenderer().renderBatched(state, pos, level, pose, buffer, true, random);

	private SceneRenderer() {
	}

	public static void setBlockDrawer(BlockDrawer drawer) {
		blockDrawer = drawer;
	}

	/** {@return whether this player is a scene's figure that is drinking now} (its main arm then goes to the mouth) */
	public static boolean isDrinking(AbstractClientPlayer player) {
		return player instanceof SceneFigure.Brewer brewer && brewer.drinking;
	}

	/** Draws the frame now, over what the screen has drawn so far; what the screen draws next goes over the scene. */
	public static void render(GuiGraphics gui, SceneRenderState state) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		MultiBufferSource.BufferSource buffers = gui.bufferSource();
		GameRenderer gameRenderer = minecraft.gameRenderer;
		EntityRenderDispatcher entities = minecraft.getEntityRenderDispatcher();
		gui.flush();
		gui.enableScissor(state.x0(), state.y0(), state.x1(), state.y1());
		// the scene has the depth buffer to itself: nothing of the screen hides it or is cut by it
		RenderSystem.clear(DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		Matrix4f projection = RenderSystem.getProjectionMatrix();
		VertexSorting sorting = RenderSystem.getVertexSorting();
		boolean panoramic = gameRenderer.isPanoramicMode();
		boolean hitBoxes = entities.shouldRenderHitBoxes();
		modelView.pushMatrix();
		try {
			// With Fabulous graphics some render types (translucent blocks and items, lines) draw into the level's own
			// layers, which nothing puts on the screen while a screen is drawn. Panoramic mode switches those layers off:
			// everything goes where the scene is.
			gameRenderer.setPanoramicMode(true);
			entities.setRenderShadow(false);
			entities.setRenderHitBoxes(false);

			// The picture's space: the rectangle's middle, y down, z toward the viewer (as vanilla sets an entity into the
			// inventory), then the view, like the level renderer's camera rotation. All on the model-view matrix:
			// everything below is drawn relative to the stage's centre, unrotated.
			Matrix4f view = SceneView.viewMatrix(state.yaw(), state.pitch(), state.zoom());
			modelView.mul(gui.pose().last().pose());
			modelView.translate((state.x0() + state.x1()) / 2.0F, (state.y0() + state.y1()) / 2.0F, 0.0F);
			modelView.scale(state.scale(), state.scale(), -state.scale());
			modelView.mul(view);
			RenderSystem.applyModelViewMatrix();
			// translucent faces are sorted back to front along the view (the screen's own sorting is along the unturned z)
			Vector3f depth = new Matrix4f().scaling(1.0F, 1.0F, -1.0F).mul(view).getRow(2, new Vector3f());
			RenderSystem.setProjectionMatrix(projection, VertexSorting.byDistance(vertex -> -vertex.dot(depth)));
			if (level.effects().constantAmbientLight()) {
				Lighting.setupNetherLevel();
			} else {
				Lighting.setupLevel();
			}
			draw(state, buffers, entities, modelView);
		} finally {
			buffers.endBatch();
			modelView.popMatrix();
			RenderSystem.applyModelViewMatrix();
			RenderSystem.setProjectionMatrix(projection, sorting);
			gameRenderer.setPanoramicMode(panoramic);
			entities.setRenderShadow(true);
			entities.setRenderHitBoxes(hitBoxes);
			Lighting.setupFor3DItems();
			RenderSystem.clear(DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
			gui.disableScissor();
		}
	}

	private static void draw(SceneRenderState state, MultiBufferSource.BufferSource buffers, EntityRenderDispatcher entities, Matrix4fStack modelView) {
		Minecraft minecraft = Minecraft.getInstance();
		ScenePlayer player = state.player();
		SceneWorld world = player.world();
		float partialTick = state.partialTick();
		Vec3 centre = SceneView.centre(player.scene());

		// Blocks still dropping in are no one's neighbours yet (the floor under them keeps its face)
		Map<BlockPos, Float> drops = new HashMap<>();
		for (BlockPos pos : world.blocks().keySet()) {
			float drop = player.dropOffset(pos, partialTick);
			if (drop > 0.0F) {
				drops.put(pos, drop);
			}
		}
		// The blocks, layer by layer as the level draws them (the translucent ones after everything solid)
		Map<RenderType, List<Map.Entry<BlockPos, BlockState>>> layers = new LinkedHashMap<>();
		for (Map.Entry<BlockPos, BlockState> entry : world.blocks().entrySet()) {
			if (entry.getValue().getRenderShape() == RenderShape.MODEL) {
				layers.computeIfAbsent(ItemBlockRenderTypes.getMovingBlockRenderType(entry.getValue()), type -> new ArrayList<>()).add(entry);
			}
		}
		for (Map.Entry<RenderType, List<Map.Entry<BlockPos, BlockState>>> layer : layers.entrySet()) {
			if (isSolidLayer(layer.getKey())) {
				drawBlocks(layer.getKey(), layer.getValue(), world, centre, drops, buffers);
			}
		}

		Vec3 cameraPos = ScenePlayer.PARTICLE_ORIGIN.add(centre);
		BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
		for (Map.Entry<BlockPos, BlockEntity> entry : world.blockEntities().entrySet()) {
			BlockPos pos = entry.getKey();
			// a pose of its own: a renderer that gives up half-way leaves nothing behind
			PoseStack pose = new PoseStack();
			pose.translate(pos.getX() - centre.x, pos.getY() - centre.y + drops.getOrDefault(pos, 0.0F), pos.getZ() - centre.z);
			drawBlockEntity(dispatcher, entry.getValue(), pose, buffers, partialTick);
		}

		// The figures, through their real renderer (skin, held items, arm swing), lit like everything else
		entities.overrideCameraOrientation(SceneView.cameraRotation(state.yaw(), state.pitch()));
		for (SceneFigure figure : player.figures()) {
			try {
				AbstractClientPlayer entity = figure.entity();
				Vec3 at = entity.getPosition(partialTick).subtract(cameraPos);
				entities.render(entity, at.x, at.y, at.z, Mth.lerp(partialTick, entity.yRotO, entity.getYRot()), partialTick, new PoseStack(), buffers,
					LightTexture.FULL_BRIGHT);
			} catch (RuntimeException e) {
				if (FAILED.add("figure")) {
					VineAndBarrel.LOGGER.warn("Watch it: can't draw a figure in a scene", e);
				}
			}
		}
		buffers.endBatch();

		for (Map.Entry<RenderType, List<Map.Entry<BlockPos, BlockState>>> layer : layers.entrySet()) {
			if (!isSolidLayer(layer.getKey())) {
				drawBlocks(layer.getKey(), layer.getValue(), world, centre, drops, buffers);
			}
		}

		// The block the caption points at: an outline like the world's own block outline, in the caption's colour
		if (state.outline() != null && world.blocks().containsKey(state.outline())) {
			BlockPos pos = state.outline();
			drawOutline(world.getBlockState(pos).getShape(world, pos), (float) (pos.getX() - centre.x),
				(float) (pos.getY() - centre.y + drops.getOrDefault(pos, 0.0F)), (float) (pos.getZ() - centre.z), modelView);
		}

		CAMERA.set(cameraPos, state.yaw(), state.pitch());
		player.particles().render(CAMERA, partialTick);
	}

	private static boolean isSolidLayer(RenderType type) {
		return type == RenderType.solid() || type == RenderType.cutoutMipped() || type == RenderType.cutout();
	}

	private static void drawBlocks(RenderType type, List<Map.Entry<BlockPos, BlockState>> blocks, SceneWorld world, Vec3 centre,
								   Map<BlockPos, Float> drops, MultiBufferSource.BufferSource buffers) {
		RandomSource random = RandomSource.create();
		VertexConsumer buffer = buffers.getBuffer(type);
		PoseStack pose = new PoseStack();
		for (Map.Entry<BlockPos, BlockState> entry : blocks) {
			BlockPos pos = entry.getKey();
			float drop = drops.getOrDefault(pos, 0.0F);
			pose.pushPose();
			pose.translate(pos.getX() - centre.x, pos.getY() - centre.y + drop, pos.getZ() - centre.z);
			try {
				blockDrawer.draw(entry.getValue(), pos, new StageBlock(world, pos, entry.getValue(), drop > 0.0F, drops.keySet()), pose, buffer, random);
			} catch (RuntimeException e) {
				String block = String.valueOf(BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlock()));
				if (FAILED.add(block)) {
					VineAndBarrel.LOGGER.warn("Watch it: can't draw {} in a scene", block, e);
				}
			}
			pose.popPose();
		}
		buffers.endBatch(type);
	}

	private static <E extends BlockEntity> void drawBlockEntity(BlockEntityRenderDispatcher dispatcher, E entity, PoseStack pose,
																 MultiBufferSource buffers, float partialTick) {
		BlockEntityRenderer<E> renderer = dispatcher.getRenderer(entity);
		if (renderer == null) {
			return;
		}
		try {
			renderer.render(entity, partialTick, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		} catch (RuntimeException e) {
			// A block entity renderer that needs a real level (Farmer's Delight's stove, for the items cooking on it): the
			// block still shows, only its extras don't
			String type = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()));
			if (FAILED.add(type)) {
				VineAndBarrel.LOGGER.debug("Watch it: {}'s extras aren't drawn in scenes ({})", type, e.toString());
			}
		}
	}

	/**
	 * A shape's edges as lines of the outline's width. The lines shader shrinks the view a little toward its origin,
	 * which lifts lines off the faces they run along in the world; in a screen that origin is the screen's corner and the
	 * lines would slide off their block. So the shrink is undone and the lines are lifted straight toward the viewer.
	 */
	private static void drawOutline(VoxelShape shape, float x, float y, float z, Matrix4fStack modelView) {
		if (shape.isEmpty()) {
			return;
		}
		Matrix4f lifted = new Matrix4f().scaling(1.0F / LINES_VIEW_SHRINK).translate(0.0F, 0.0F, OUTLINE_LIFT).mul(modelView);
		modelView.pushMatrix();
		modelView.set(lifted);
		RenderSystem.applyModelViewMatrix();
		RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
		RenderSystem.lineWidth(OUTLINE_WIDTH);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.disableCull();
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
		shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
			Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1)).normalize();
			buffer.addVertex(x + (float) x1, y + (float) y1, z + (float) z1).setColor(WatchScreen.ACCENT).setNormal(normal.x, normal.y, normal.z);
			buffer.addVertex(x + (float) x2, y + (float) y2, z + (float) z2).setColor(WatchScreen.ACCENT).setNormal(normal.x, normal.y, normal.z);
		});
		MeshData mesh = buffer.build();
		if (mesh != null) {
			BufferUploader.drawWithShader(mesh);
		}
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		RenderSystem.lineWidth(1.0F);
		modelView.popMatrix();
		RenderSystem.applyModelViewMatrix();
	}

	/**
	 * One block of the stage as vanilla's block renderer wants it: its own state at its position, its neighbours from the
	 * stage (for culling and ambient occlusion), full light. A block still dropping in has no neighbours yet, and is no
	 * one's neighbour.
	 */
	private static final class StageBlock implements BlockAndTintGetter
	{
		private final SceneWorld world;
		private final BlockPos blockPos;
		private final BlockState blockState;
		private final boolean alone;
		private final Set<BlockPos> dropping;

		StageBlock(SceneWorld world, BlockPos pos, BlockState state, boolean alone, Set<BlockPos> dropping) {
			this.world = world;
			this.alone = alone;
			this.dropping = dropping;
			this.blockPos = pos;
			this.blockState = state;
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
		public float getShade(Direction direction, boolean shade) {
			return world.getShade(direction, shade);
		}

		@Override
		public LevelLightEngine getLightEngine() {
			return world.getLightEngine();
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
		public int getMinBuildHeight() {
			return 0;
		}
	}

}
