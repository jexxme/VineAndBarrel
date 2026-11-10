package jexxme.vineandbarrel.neoforge.client;

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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * A {@link ClientRegistrar} for one NeoForge registration event: it passes on the registrations of that event's kind
 * and ignores the rest, so the common classes can list all their registrations in one method.
 */
final class NeoForgeClientRegistrar implements ClientRegistrar
{
	private @Nullable RegisterMenuScreensEvent screens;
	private @Nullable RegisterRecipeBookCategoriesEvent recipeBook;
	private @Nullable RegisterParticleProvidersEvent particles;
	private @Nullable RegisterClientTooltipComponentFactoriesEvent tooltips;
	private RegisterColorHandlersEvent.@Nullable Block tints;
	private EntityRenderersEvent.@Nullable RegisterRenderers renderers;
	private boolean renderLayers;
	private @Nullable RegisterKeyMappingsEvent keys;
	private @Nullable RegisterClientReloadListenersEvent reloads;

	private NeoForgeClientRegistrar() {
	}

	static NeoForgeClientRegistrar screens(RegisterMenuScreensEvent event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.screens = event;
		return registrar;
	}

	static NeoForgeClientRegistrar recipeBook(RegisterRecipeBookCategoriesEvent event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.recipeBook = event;
		return registrar;
	}

	static NeoForgeClientRegistrar particles(RegisterParticleProvidersEvent event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.particles = event;
		return registrar;
	}

	static NeoForgeClientRegistrar tooltips(RegisterClientTooltipComponentFactoriesEvent event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.tooltips = event;
		return registrar;
	}

	static NeoForgeClientRegistrar tints(RegisterColorHandlersEvent.Block event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.tints = event;
		return registrar;
	}

	static NeoForgeClientRegistrar renderers(EntityRenderersEvent.RegisterRenderers event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.renderers = event;
		return registrar;
	}

	static NeoForgeClientRegistrar keys(RegisterKeyMappingsEvent event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.keys = event;
		return registrar;
	}

	static NeoForgeClientRegistrar reloads(RegisterClientReloadListenersEvent event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.reloads = event;
		return registrar;
	}

	/** For client setup (no event object): the blocks' render layers. */
	static NeoForgeClientRegistrar renderLayers() {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.renderLayers = true;
		return registrar;
	}

	@Override
	public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void menuScreen(MenuType<M> type, ScreenFactory<M, U> factory) {
		if (screens != null) {
			screens.register(type, factory::create);
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

	/**
	 * NeoForge would rather read the layer from each model's JSON; the models are shared with Fabric, so it is set here.
	 * NeoForge keeps what is set here in a table of its own, which only its own drawing reads; vanilla's table gets the
	 * layer too (access transformer), for what asks vanilla's {@code getMovingBlockRenderType}: our "Watch it" scenes,
	 * which drew the plants on black without it.
	 */
	@Override
	@SuppressWarnings("deprecation")
	public void blockRenderLayer(RenderType layer, Block... blocks) {
		if (renderLayers) {
			for (Block block : blocks) {
				ItemBlockRenderTypes.setRenderLayer(block, layer);
				ItemBlockRenderTypes.TYPE_BY_BLOCK.put(block, layer);
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
		RecipeType<?> recipeType, Function<RecipeHolder<?>, RecipeBookCategories> tabOf) {
		if (recipeBook != null) {
			recipeBook.registerBookCategories(type, tabs);
			recipeBook.registerAggregateCategory(searchTab, searched);
			recipeBook.registerRecipeCategoryFinder(recipeType, tabOf);
		}
	}
}
