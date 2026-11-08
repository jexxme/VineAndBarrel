package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.fabric.FabricRecipeBook;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;
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
		ParticleProviderRegistry.getInstance().register(type, provider::apply);
	}

	@Override
	public <T extends TooltipComponent> void tooltip(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
		ClientTooltipComponentCallback.EVENT.register(data -> type.isInstance(data) ? factory.apply(type.cast(data)) : null);
	}

	@Override
	public void blockTints(List<BlockTintSource> tints, Block... blocks) {
		BlockColorRegistry.register(tints, blocks);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
		BlockEntityRendererRegistry.register((BlockEntityType<T>) type, provider);
	}

	/** The kettle book's own search tab constant ({@code SearchRecipeBookCategoryMixin}), showing these categories. */
	@Override
	public ExtendedRecipeBookCategory recipeBookSearch(RecipeBookCategory search, RecipeBookCategory... included) {
		FabricRecipeBook.setSearchIncluded(List.of(included));
		return SearchRecipeBookCategory.valueOf(FabricRecipeBook.BREWING);
	}
}
