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
	private static final float[] MUG_LIQUID = {2.5F, 0.75F, 2.5F, 5.5F, 4, 5.5F};
	private static final float[] MUG_FOAM = {2.5F, 4, 2.5F, 5.5F, 5.25F, 5.5F};
	private static final float GROW = 0.02F;

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
		for (int i = 0; i < 4; i++) {
			state.spots[i] = drinks.getBlockState().getValue(PlacedDrinksBlock.SPOT.get(i));
			ItemStack drink = drinks.getItem(i);
			state.colors[i] = 0xFF000000 | (drink.isEmpty() ? DrinkColors.FALLBACK : DrinkColors.of(drink));
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
		collector.submitCustomGeometry(pose, RenderTypes.itemTranslucentGlint(TextureAtlas.LOCATION_BLOCKS), (at, consumer) -> {
			for (int i = 0; i < 4; i++) {
				float dx = (i & 1) * 8;
				float dz = (i >> 1) * 8;
				if (state.spots[i] == PlacedDrink.GLASS_AGED) {
					box(consumer, at, GLASS_LIQUID, dx, dz, liquid, state.colors[i], state.lightCoords);
				} else if (state.spots[i] == PlacedDrink.MUG_AGED) {
					box(consumer, at, MUG_LIQUID, dx, dz, liquid, state.colors[i], state.lightCoords);
					box(consumer, at, MUG_FOAM, dx, dz, foam, 0xFFFFFFFF, state.lightCoords);
				}
			}
		});
	}

	private static void box(VertexConsumer out, PoseStack.Pose at, float[] px, float dx, float dz, TextureAtlasSprite sprite, int color, int light) {
		float x0 = (px[0] + dx - GROW) / 16, y0 = (px[1] - GROW) / 16, z0 = (px[2] + dz - GROW) / 16;
		float x1 = (px[3] + dx + GROW) / 16, y1 = (px[4] + GROW) / 16, z1 = (px[5] + dz + GROW) / 16;
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
		// up, north, south, west, east (the bottom sits on the vessel and never shows)
		quad(out, at, color, light, u0, v0, u1, v1, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(out, at, color, light, u0, v0, u1, v1, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
		quad(out, at, color, light, u0, v0, u1, v1, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
		quad(out, at, color, light, u0, v0, u1, v1, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
		quad(out, at, color, light, u0, v0, u1, v1, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
	}

	private static void quad(VertexConsumer out, PoseStack.Pose at, int color, int light, float u0, float v0, float u1, float v1,
							 float nx, float ny, float nz, float... corners) {
		float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
		for (int c = 0; c < 4; c++) {
			out.addVertex(at, corners[c * 3], corners[c * 3 + 1], corners[c * 3 + 2]).setColor(color).setUv(uv[c][0], uv[c][1])
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(at, nx, ny, nz);
		}
	}
}
