package jexxme.vineandbarrel.platform.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

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

	void blockTints(List<BlockTintSource> tints, Block... blocks);

	<T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider);

	/** A key the player can rebind in the controls screen. */
	void keyMapping(KeyMapping key);

	/** Runs on each client resource reload (resource packs changed, F3+T). */
	void reloadListener(Identifier id, PreparableReloadListener listener);

	/** A 3D picture inside a screen (the "Watch it" scenes), drawn by vanilla's GUI renderer for states of this class. */
	<T extends PictureInPictureRenderState> void pictureInPicture(Class<T> state, Supplier<PictureInPictureRenderer<T>> renderer);

	/**
	 * A recipe book search tab that shows these categories (the kettle's recipe book); returns the category the tab
	 * uses ({@code search} on NeoForge, a search tab constant of vanilla's on Fabric).
	 */
	ExtendedRecipeBookCategory recipeBookSearch(RecipeBookCategory search, RecipeBookCategory... included);

	@FunctionalInterface
	interface ScreenFactory<M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>>
	{
		U create(M menu, Inventory playerInventory, Component title);
	}
}
