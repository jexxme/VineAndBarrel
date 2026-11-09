package jexxme.vineandbarrel.neoforge.client;

import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookSearchCategoriesEvent;
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
	private @Nullable RegisterRecipeBookSearchCategoriesEvent recipeBook;
	private @Nullable RegisterParticleProvidersEvent particles;
	private @Nullable RegisterClientTooltipComponentFactoriesEvent tooltips;
	private RegisterColorHandlersEvent.@Nullable BlockTintSources tints;
	private EntityRenderersEvent.@Nullable RegisterRenderers renderers;
	private @Nullable RegisterPictureInPictureRenderersEvent pictures;
	private @Nullable RegisterKeyMappingsEvent keys;
	private @Nullable AddClientReloadListenersEvent reloads;

	private NeoForgeClientRegistrar() {
	}

	static NeoForgeClientRegistrar screens(RegisterMenuScreensEvent event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.screens = event;
		return registrar;
	}

	static NeoForgeClientRegistrar recipeBook(RegisterRecipeBookSearchCategoriesEvent event) {
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

	static NeoForgeClientRegistrar tints(RegisterColorHandlersEvent.BlockTintSources event) {
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

	static NeoForgeClientRegistrar reloads(AddClientReloadListenersEvent event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.reloads = event;
		return registrar;
	}

	static NeoForgeClientRegistrar pictures(RegisterPictureInPictureRenderersEvent event) {
		NeoForgeClientRegistrar registrar = new NeoForgeClientRegistrar();
		registrar.pictures = event;
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
	public void blockTints(List<BlockTintSource> tintSources, Block... blocks) {
		if (tints != null) {
			tints.register(tintSources, blocks);
		}
	}

	@Override
	public <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
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
	public void reloadListener(Identifier id, PreparableReloadListener listener) {
		if (reloads != null) {
			reloads.addListener(id, listener);
		}
	}

	@Override
	public <T extends PictureInPictureRenderState> void pictureInPicture(Class<T> state,
		Function<MultiBufferSource.BufferSource, PictureInPictureRenderer<T>> renderer) {
		if (pictures != null) {
			pictures.register(state, renderer);
		}
	}

	@Override
	public ExtendedRecipeBookCategory recipeBookSearch(RecipeBookCategory search, RecipeBookCategory... included) {
		if (recipeBook != null) {
			recipeBook.register(search, included);
		}
		return search;
	}
}
