package jexxme.vineandbarrel.forge.client;

import jexxme.vineandbarrel.forge.mixin.client.ItemBlockRenderTypesAccessor;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterRecipeBookCategoriesEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * A {@link ClientRegistrar} for one Forge registration event (or for client setup, where Forge 47 has no event: menu
 * screens and render layers): it passes on the registrations of that kind and ignores the rest, so the common classes
 * can list all their registrations in one method.
 */
final class ForgeClientRegistrar implements ClientRegistrar
{
	private boolean screens;
	private @Nullable RegisterRecipeBookCategoriesEvent recipeBook;
	private @Nullable RegisterParticleProvidersEvent particles;
	private @Nullable RegisterClientTooltipComponentFactoriesEvent tooltips;
	private RegisterColorHandlersEvent.@Nullable Block tints;
	private EntityRenderersEvent.@Nullable RegisterRenderers renderers;
	private boolean renderLayers;
	private @Nullable RegisterKeyMappingsEvent keys;
	private @Nullable RegisterClientReloadListenersEvent reloads;

	private ForgeClientRegistrar() {
	}

	/** For client setup (no event object): the menus' screens, into vanilla's own table. */
	static ForgeClientRegistrar screens() {
		ForgeClientRegistrar registrar = new ForgeClientRegistrar();
		registrar.screens = true;
		return registrar;
	}

	static ForgeClientRegistrar recipeBook(RegisterRecipeBookCategoriesEvent event) {
		ForgeClientRegistrar registrar = new ForgeClientRegistrar();
		registrar.recipeBook = event;
		return registrar;
	}

	static ForgeClientRegistrar particles(RegisterParticleProvidersEvent event) {
		ForgeClientRegistrar registrar = new ForgeClientRegistrar();
		registrar.particles = event;
		return registrar;
	}

	static ForgeClientRegistrar tooltips(RegisterClientTooltipComponentFactoriesEvent event) {
		ForgeClientRegistrar registrar = new ForgeClientRegistrar();
		registrar.tooltips = event;
		return registrar;
	}

	static ForgeClientRegistrar tints(RegisterColorHandlersEvent.Block event) {
		ForgeClientRegistrar registrar = new ForgeClientRegistrar();
		registrar.tints = event;
		return registrar;
	}

	static ForgeClientRegistrar renderers(EntityRenderersEvent.RegisterRenderers event) {
		ForgeClientRegistrar registrar = new ForgeClientRegistrar();
		registrar.renderers = event;
		return registrar;
	}

	static ForgeClientRegistrar keys(RegisterKeyMappingsEvent event) {
		ForgeClientRegistrar registrar = new ForgeClientRegistrar();
		registrar.keys = event;
		return registrar;
	}

	static ForgeClientRegistrar reloads(RegisterClientReloadListenersEvent event) {
		ForgeClientRegistrar registrar = new ForgeClientRegistrar();
		registrar.reloads = event;
		return registrar;
	}

	/** For client setup (no event object): the blocks' render layers. */
	static ForgeClientRegistrar renderLayers() {
		ForgeClientRegistrar registrar = new ForgeClientRegistrar();
		registrar.renderLayers = true;
		return registrar;
	}

	@Override
	public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void menuScreen(MenuType<M> type, ScreenFactory<M, U> factory) {
		if (screens) {
			MenuScreens.register(type, factory::create);
		}
	}

	@Override
	public <T extends ParticleOptions> void particle(ParticleType<T> type, Function<SpriteSet, ParticleProvider<T>> provider) {
		if (particles != null) {
			particles.registerSpriteSet(type, provider::apply);
		}
	}

	@Override
	public <T extends TooltipComponent> void tooltip(Class<T> type, Function<? super T, ? extends ClientTooltipComponent> factory) {
		if (tooltips != null) {
			tooltips.register(type, factory);
		}
	}

	@Override
	public void blockTints(BlockColor color, Block... blocks) {
		if (tints != null) {
			tints.register(color, blocks);
		}
	}

	/** Forge would rather read the layer from each model's JSON; the models are shared with Fabric, so it is set here. */
	@Override
	@SuppressWarnings("deprecation")
	public void blockRenderLayer(RenderType layer, Block... blocks) {
		if (renderLayers) {
			for (Block block : blocks) {
				ItemBlockRenderTypes.setRenderLayer(block, layer);
				// Forge keeps that in a table of its own; vanilla's gets the layer too, for vanilla's
				// getMovingBlockRenderType: our "Watch it" scenes, which drew the plants on black without it
				ItemBlockRenderTypesAccessor.vineandbarrel$typeByBlock().put(block, layer);
			}
		}
	}

	@Override
	public <T extends BlockEntity> void blockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
		if (renderers != null) {
			renderers.registerBlockEntityRenderer(type, provider);
		}
	}

	@Override
	public void keyMapping(KeyMapping key) {
		if (keys != null) {
			keys.register(key);
		}
	}

	@Override
	public void reloadListener(ResourceLocation id, PreparableReloadListener listener) {
		if (reloads != null) {
			reloads.registerReloadListener(listener);
		}
	}

	@Override
	public void recipeBook(RecipeBookType type, List<RecipeBookCategories> tabs, RecipeBookCategories searchTab, List<RecipeBookCategories> searched,
		RecipeType<?> recipeType, Function<Recipe<?>, RecipeBookCategories> tabOf) {
		if (recipeBook != null) {
			recipeBook.registerBookCategories(type, tabs);
			recipeBook.registerAggregateCategory(searchTab, searched);
			recipeBook.registerRecipeCategoryFinder(recipeType, tabOf);
		}
	}
}
