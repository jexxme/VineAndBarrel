package jexxme.vineandbarrel.barrel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.DrinkColors;
import jexxme.vineandbarrel.drink.PlacedDrink;
import jexxme.vineandbarrel.drink.PlacedDrinksBlock;
import jexxme.vineandbarrel.drink.PlacedDrinksBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The enchantment-style shimmer on aged drinks set down on a table (playtest 1: "glowing like a golden apple"). The
 * block model draws the drink as usual; this lays the same liquid (and a mug's foam) once more on top, a hair larger,
 * in vanilla's glint render type, so only aged drinks shimmer and the lighting stays normal.
 */
public class PlacedDrinksRenderer implements BlockEntityRenderer<PlacedDrinksBlockEntity, PlacedDrinksRenderer.State>
{
	private static final Identifier LIQUID = VineAndBarrel.id("block/placed_drink_liquid");
	private static final Identifier FOAM = VineAndBarrel.id("block/placed_drink_foam");
	/** Liquid boxes in spot 0 (pixels); spots 1-3 are moved by 8 px in x and/or z, as in the block models. */
	private static final float[] GLASS_LIQUID = {3, 3.5F, 3, 5, 5, 5};
	private static final float[] MUG_LIQUID = {2.5F, 1, 2.5F, 5.5F, 4, 5.5F};
	private static final float[] MUG_FOAM = {2.5F, 4, 2.5F, 5.5F, 5.25F, 5.5F};
	/** How far the shimmer stands out from the drink (pixels): clear of it under shaders too, still inside the 0.5 px glass. */
	private static final float GROW = 0.05F;

	public static class State extends BlockEntityRenderState
	{
		final PlacedDrink[] spots = new PlacedDrink[4];
		final int[] colors = new int[4];
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PlacedDrinksBlockEntity drinks, State state, float partialTick, Vec3 camera,
								   ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
		BlockEntityRenderState.extractBase(drinks, state, breaking);
		BlockState block = drinks.getBlockState();
		for (int i = 0; i < 4; i++) {
			state.spots[i] = block.getValue(PlacedDrinksBlock.SPOT.get(i));
			// Every table in view is extracted every frame: only an aged drink's colour is drawn (by submit), so only it is looked up
			if (state.spots[i].isAged()) {
				ItemStack drink = drinks.peekItem(i);
				state.colors[i] = 0xFF000000 | (drink.isEmpty() ? DrinkColors.FALLBACK : DrinkColors.of(drink));
			}
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		boolean any = false;
		for (PlacedDrink spot : state.spots) {
			any |= spot == PlacedDrink.GLASS_AGED || spot == PlacedDrink.MUG_AGED;
		}
		if (!any) {
			return;
		}
		TextureAtlas blocks = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
		TextureAtlasSprite liquid = blocks.getSprite(LIQUID);
		TextureAtlasSprite foam = blocks.getSprite(FOAM);
		// 26.2: the glint is its own pass over the same boxes, as for foil items (26.3 has one type with the glint built in)
		SubmitNodeCollector.CustomGeometryRenderer liquids = (at, consumer) -> {
			for (int i = 0; i < 4; i++) {
				float dx = (i & 1) * 8;
				float dz = (i >> 1) * 8;
				if (state.spots[i] == PlacedDrink.GLASS_AGED) {
					box(consumer, at, GLASS_LIQUID, dx, dz, liquid, state.colors[i], state.lightCoords, true);
				} else if (state.spots[i] == PlacedDrink.MUG_AGED) {
					// the beer up to exactly where the foam starts, without a top inside it: the two never overlap
					box(consumer, at, MUG_LIQUID, dx, dz, liquid, state.colors[i], state.lightCoords, false);
					box(consumer, at, MUG_FOAM, dx, dz, foam, 0xFFFFFFFF, state.lightCoords, true);
				}
			}
		};
		collector.submitCustomGeometry(pose, RenderTypes.itemTranslucent(TextureAtlas.LOCATION_BLOCKS), liquids);
		// The glint only draws where the depth equals what is already there, so it goes after the boxes (order 1)
		collector.order(1).submitCustomGeometry(pose, RenderTypes.glintTranslucent(), liquids);
	}

	/**
	 * The box a hair larger to the sides, with its top (a hair higher too) or, under the foam, without one. The bottom
	 * sits on the vessel or on the box below and never shows: no face, and it starts right where the drink does.
	 */
	private static void box(VertexConsumer out, PoseStack.Pose at, float[] px, float dx, float dz, TextureAtlasSprite sprite, int color, int light,
							boolean top) {
		float x0 = (px[0] + dx - GROW) / 16, y0 = px[1] / 16, z0 = (px[2] + dz - GROW) / 16;
		float x1 = (px[3] + dx + GROW) / 16, y1 = (px[4] + (top ? GROW : 0)) / 16, z1 = (px[5] + dz + GROW) / 16;
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
		// up, north, south, west, east; corners take the UVs (u0, v0), (u0, v1), (u1, v1), (u1, v0) in this order
		if (top) {
			vertex(out, at, x0, y1, z0, u0, v0, color, light, 0, 1, 0);
			vertex(out, at, x0, y1, z1, u0, v1, color, light, 0, 1, 0);
			vertex(out, at, x1, y1, z1, u1, v1, color, light, 0, 1, 0);
			vertex(out, at, x1, y1, z0, u1, v0, color, light, 0, 1, 0);
		}
		vertex(out, at, x1, y1, z0, u0, v0, color, light, 0, 0, -1);
		vertex(out, at, x1, y0, z0, u0, v1, color, light, 0, 0, -1);
		vertex(out, at, x0, y0, z0, u1, v1, color, light, 0, 0, -1);
		vertex(out, at, x0, y1, z0, u1, v0, color, light, 0, 0, -1);
		vertex(out, at, x0, y1, z1, u0, v0, color, light, 0, 0, 1);
		vertex(out, at, x0, y0, z1, u0, v1, color, light, 0, 0, 1);
		vertex(out, at, x1, y0, z1, u1, v1, color, light, 0, 0, 1);
		vertex(out, at, x1, y1, z1, u1, v0, color, light, 0, 0, 1);
		vertex(out, at, x0, y1, z0, u0, v0, color, light, -1, 0, 0);
		vertex(out, at, x0, y0, z0, u0, v1, color, light, -1, 0, 0);
		vertex(out, at, x0, y0, z1, u1, v1, color, light, -1, 0, 0);
		vertex(out, at, x0, y1, z1, u1, v0, color, light, -1, 0, 0);
		vertex(out, at, x1, y1, z1, u0, v0, color, light, 1, 0, 0);
		vertex(out, at, x1, y0, z1, u0, v1, color, light, 1, 0, 0);
		vertex(out, at, x1, y0, z0, u1, v1, color, light, 1, 0, 0);
		vertex(out, at, x1, y1, z0, u1, v0, color, light, 1, 0, 0);
	}

	/** One vertex, without the per-face arrays it was built from before (the glint is rebuilt every frame). */
	private static void vertex(VertexConsumer out, PoseStack.Pose at, float x, float y, float z, float u, float v, int color, int light,
							   float nx, float ny, float nz) {
		out.addVertex(at, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(at, nx, ny, nz);
	}
}
