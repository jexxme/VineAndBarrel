package jexxme.vineandbarrel.barrel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.drink.DrinkColors;
import jexxme.vineandbarrel.drink.PlacedDrink;
import jexxme.vineandbarrel.drink.PlacedDrinksBlock;
import jexxme.vineandbarrel.drink.PlacedDrinksBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The enchantment-style shimmer on aged drinks set down on a table (playtest 1: "glowing like a golden apple"). The
 * block model draws the drink as usual; this lays the same liquid (and a mug's foam) once more on top, a hair larger,
 * with vanilla's glint, the way an enchanted item is drawn, so only aged drinks shimmer and the lighting stays normal.
 */
public class PlacedDrinksRenderer implements BlockEntityRenderer<PlacedDrinksBlockEntity>
{
	private static final ResourceLocation LIQUID = VineAndBarrel.id("block/placed_drink_liquid");
	private static final ResourceLocation FOAM = VineAndBarrel.id("block/placed_drink_foam");
	/** Liquid boxes in spot 0 (pixels); spots 1-3 are moved by 8 px in x and/or z, as in the block models. */
	private static final float[] GLASS_LIQUID = {3, 3.5F, 3, 5, 5, 5};
	private static final float[] MUG_LIQUID = {2.5F, 1, 2.5F, 5.5F, 4, 5.5F};
	private static final float[] MUG_FOAM = {2.5F, 4, 2.5F, 5.5F, 5.25F, 5.5F};
	/** How far the shimmer stands out from the drink (pixels): clear of it under shaders too, still inside the 0.5 px glass. */
	private static final float GROW = 0.05F;

	@Override
	public void render(PlacedDrinksBlockEntity drinks, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		BlockState block = drinks.getBlockState();
		PlacedDrink[] spots = new PlacedDrink[4];
		boolean any = false;
		for (int i = 0; i < 4; i++) {
			spots[i] = block.getValue(PlacedDrinksBlock.SPOT.get(i));
			any |= spots[i] == PlacedDrink.GLASS_AGED || spots[i] == PlacedDrink.MUG_AGED;
		}
		if (!any) {
			return;
		}
		var blocks = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
		TextureAtlasSprite liquid = blocks.apply(LIQUID);
		TextureAtlasSprite foam = blocks.apply(FOAM);
		// As 1.21.1 draws a translucent item with a glint: the boxes go to the item sheet and to the glint at once, and
		// the glint (drawn after, only where the depth equals what is already there) lands on them
		RenderType sheet = Minecraft.useShaderTransparency() ? Sheets.translucentItemSheet() : Sheets.translucentCullBlockSheet();
		VertexConsumer consumer = ItemRenderer.getFoilBuffer(buffers, sheet, true, true);
		PoseStack.Pose at = pose.last();
		for (int i = 0; i < 4; i++) {
			float dx = (i & 1) * 8;
			float dz = (i >> 1) * 8;
			// Every table in view is drawn every frame: only an aged drink's colour is drawn, so only it is looked up
			if (spots[i] == PlacedDrink.GLASS_AGED) {
				box(consumer, at, GLASS_LIQUID, dx, dz, liquid, colorOf(drinks, i), light, true);
			} else if (spots[i] == PlacedDrink.MUG_AGED) {
				// the beer up to exactly where the foam starts, without a top inside it: the two never overlap
				box(consumer, at, MUG_LIQUID, dx, dz, liquid, colorOf(drinks, i), light, false);
				box(consumer, at, MUG_FOAM, dx, dz, foam, 0xFFFFFFFF, light, true);
			}
		}
	}

	private static int colorOf(PlacedDrinksBlockEntity drinks, int spot) {
		ItemStack drink = drinks.peekItem(spot);
		return 0xFF000000 | (drink.isEmpty() ? DrinkColors.FALLBACK : DrinkColors.of(drink));
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

	/** One vertex, without per-face arrays (the boxes are rebuilt every frame). */
	private static void vertex(VertexConsumer out, PoseStack.Pose at, float x, float y, float z, float u, float v, int color, int light,
							   float nx, float ny, float nz) {
		out.addVertex(at, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(at, nx, ny, nz);
	}
}
