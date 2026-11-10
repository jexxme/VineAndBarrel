package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** {@link ClientRegistrar} on Fabric: everything is registered once, at client start. */
final class FabricClientRegistrar implements ClientRegistrar
{
	@Override
	public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void menuScreen(MenuType<M> type, ScreenFactory<M, U> factory) {
		MenuScreens.<M, U>register(type, factory::create);
	}

	@Override
	public <T extends ParticleOptions> void particle(ParticleType<T> type, Function<SpriteSet, ParticleProvider<T>> provider) {
		ParticleFactoryRegistry.getInstance().register(type, (ParticleFactoryRegistry.PendingParticleFactory<T>) provider::apply);
	}

	@Override
	public <T extends TooltipComponent> void tooltip(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
		TooltipComponentCallback.EVENT.register(data -> type.isInstance(data) ? factory.apply(type.cast(data)) : null);
	}

	@Override
	public void blockTints(BlockColor color, Block... blocks) {
		ColorProviderRegistry.BLOCK.register(color, blocks);
	}

	@Override
	public void blockRenderLayer(RenderType layer, Block... blocks) {
		BlockRenderLayerMap.INSTANCE.putBlocks(layer, blocks);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> void blockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
		BlockEntityRenderers.register((BlockEntityType<T>) type, provider);
	}

	@Override
	public void keyMapping(KeyMapping key) {
		KeyBindingHelper.registerKeyBinding(key);
	}

	@Override
	public void reloadListener(ResourceLocation id, PreparableReloadListener listener) {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener()
		{
			@Override
			public ResourceLocation getFabricId() {
				return id;
			}

			@Override
			public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources, ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler,
				Executor prepareExecutor, Executor applyExecutor) {
				return listener.reload(barrier, resources, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor);
			}

			@Override
			public String getName() {
				return listener.getName();
			}
		});
	}

	/**
	 * The book's type and tabs are already constants of vanilla's enums ({@code RecipeBookTypeMixin},
	 * {@code RecipeBookCategoriesMixin}, which also gave the search tab its aggregate: the same {@code searched} tabs).
	 * What the mixins can't know while the enums load is kept for them here: the book's tab list and its recipes' tabs.
	 */
	@Override
	public void recipeBook(RecipeBookType type, List<RecipeBookCategories> tabs, RecipeBookCategories searchTab, List<RecipeBookCategories> searched,
		RecipeType<?> recipeType, Function<RecipeHolder<?>, RecipeBookCategories> tabOf) {
		if (!searched.equals(RecipeBookCategories.AGGREGATE_CATEGORIES.get(searchTab))) {
			throw new IllegalStateException("The search tab " + searchTab + " shows " + RecipeBookCategories.AGGREGATE_CATEGORIES.get(searchTab) + ", not " + searched);
		}
		FabricClientRecipeBook.set(type, tabs, recipeType, tabOf);
	}
}
