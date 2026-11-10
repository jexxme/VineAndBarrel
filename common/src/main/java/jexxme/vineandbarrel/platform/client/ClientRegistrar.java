package jexxme.vineandbarrel.platform.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;
import java.util.function.Function;

/**
 * The client registrations the common client classes make ({@code KettleClient}, {@code BarrelClient},
 * {@code TipsyClient}), each loader doing them its own way: NeoForge in its registration events (it calls each class's
 * {@code register} once per event and keeps only that event's kind), Fabric once during client start.
 */
public interface ClientRegistrar
{
	<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void menuScreen(MenuType<M> type, ScreenFactory<M, U> factory);

	<T extends ParticleOptions> void particle(ParticleType<T> type, Function<SpriteSet, ParticleProvider<T>> provider);

	<T extends TooltipComponent> void tooltip(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory);

	/** The colour of these blocks' tinted faces, by tint index (26.x: one BlockTintSource per index). */
	void blockTints(BlockColor color, Block... blocks);

	/** The layer these blocks are drawn in (cutout, translucent); see {@code VBRenderLayers}. */
	void blockRenderLayer(net.minecraft.client.renderer.RenderType layer, Block... blocks);

	<T extends BlockEntity> void blockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider);

	/** A key the player can rebind in the controls screen. */
	void keyMapping(KeyMapping key);

	/** Runs on each client resource reload (resource packs changed, F3+T). */
	void reloadListener(ResourceLocation id, PreparableReloadListener listener);

	// ---- kettle (1.21.1 port; 1.20.1 port: a recipe is a plain Recipe, there is no RecipeHolder)

	/**
	 * A recipe book of our own (the Brew Kettle's), told to the client's recipe book the loader's way. The loader has
	 * already added {@code type} to {@link RecipeBookType} and every tab to {@link RecipeBookCategories} (both are enums
	 * on this Minecraft: Forge extends them with their {@code create} methods, Fabric with mixins; see
	 * {@code BrewingRecipeBookCategories} for the tabs' names and icons). This says how they belong together, which is
	 * exactly what Forge's {@code RegisterRecipeBookCategoriesEvent} takes:
	 *
	 * @param type       the book: a menu with this {@link RecipeBookType} shows it
	 * @param tabs       the book's tabs, top to bottom, the search tab first
	 * @param searchTab  the tab that shows every recipe of the book (one of {@code tabs})
	 * @param searched   the tabs whose recipes {@code searchTab} shows (the other {@code tabs})
	 * @param recipeType the recipes the book lists: all of this type
	 * @param tabOf      the tab (one of {@code searched}) a recipe of {@code recipeType} is on
	 */
	void recipeBook(RecipeBookType type, List<RecipeBookCategories> tabs, RecipeBookCategories searchTab, List<RecipeBookCategories> searched,
		RecipeType<?> recipeType, Function<Recipe<?>, RecipeBookCategories> tabOf);

	@FunctionalInterface
	interface ScreenFactory<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>>
	{
		U create(M menu, Inventory playerInventory, Component title);
	}
}
