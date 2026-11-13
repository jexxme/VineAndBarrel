package jexxme.vineandbarrel.forge.client;

import jexxme.vineandbarrel.barrel.client.BarrelClient;
import jexxme.vineandbarrel.cauldron.client.CauldronClient;
import jexxme.vineandbarrel.client.VBRenderLayers;
import jexxme.vineandbarrel.kettle.client.BrewingRecipeBookCategories;
import jexxme.vineandbarrel.kettle.client.KettleClient;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import jexxme.vineandbarrel.tipsy.TipsyWobble;
import jexxme.vineandbarrel.tipsy.client.BlackoutClient;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import jexxme.vineandbarrel.watch.client.SceneRenderer;
import jexxme.vineandbarrel.watch.client.WatchClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterRecipeBookCategoriesEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * The client side on Forge: the common client classes' registrations and hooks, wired to Forge's client events. (Forge
 * 47 builds no config screen from a mod's config files as NeoForge 21.1 does, so the mod list has no Config button:
 * the settings are the two files in the config folders.)
 */
public final class ForgeClient
{
	/**
	 * Whether a tipsy (or blacked-out) body pose was pushed for the living entity being drawn right now
	 * (TipsyClient#beforeRenderLiving), kept from the render's Pre event to its Post. A stack, in case a renderer draws
	 * another living entity inside its own render.
	 */
	private static final java.util.ArrayDeque<Boolean> SWAY_PUSHED = new java.util.ArrayDeque<>();
	/** The Brew Kettle's recipe book tabs exist (see {@link #makeBookTabs}). */
	private static boolean bookTabsMade;
	/** The model bakery of the reload that is baking, and its sprite lookup (see {@code ModelBakeryMixin}). */
	private static volatile @Nullable ModelBakery bakery;
	private static volatile @Nullable BiFunction<ResourceLocation, Material, TextureAtlasSprite> bakingSprites;
	/** The inventory's effect list as it was last laid out (see {@code EffectRenderingInventoryScreenMixin}). */
	private static int effectListLeft;
	private static boolean effectListCompact;
	private static boolean effectListShown;

	private ForgeClient() {
	}

	public static void init(IEventBus modBus) {
		// Registrations: each event runs every class's register(...) and keeps its own kind (see ForgeClientRegistrar)
		modBus.addListener((RegisterRecipeBookCategoriesEvent event) -> registerAll(ForgeClientRegistrar.recipeBook(event)));
		modBus.addListener((RegisterParticleProvidersEvent event) -> registerAll(ForgeClientRegistrar.particles(event)));
		modBus.addListener((RegisterClientTooltipComponentFactoriesEvent event) -> registerAll(ForgeClientRegistrar.tooltips(event)));
		modBus.addListener((RegisterColorHandlersEvent.Block event) -> registerAll(ForgeClientRegistrar.tints(event)));
		modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> registerAll(ForgeClientRegistrar.renderers(event)));
		modBus.addListener((RegisterKeyMappingsEvent event) -> registerAll(ForgeClientRegistrar.keys(event)));
		modBus.addListener((RegisterClientReloadListenersEvent event) -> registerAll(ForgeClientRegistrar.reloads(event)));

		// Menu screens (Forge 47 has no event for them: vanilla's table, on the main thread), and cutout and translucent
		// blocks (this Minecraft doesn't read that off the textures)
		modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
			registerAll(ForgeClientRegistrar.screens());
			VBRenderLayers.register(ForgeClientRegistrar.renderLayers());
		}));

		// Vines on fences, walls, chains and bars draw the block they hold, and leaves that follow its shape
		modBus.addListener(SupportedVineModel::registerPieces);
		modBus.addListener(SupportedVineModel::wrap);
		// Trellis rows draw their frame (end posts, top rail) from their neighbours
		modBus.addListener(TrellisFrameModel::registerParts);
		modBus.addListener(TrellisFrameModel::wrap);
		// The blocks with glass draw each face in the layer of its own texture
		modBus.addListener(TextureLayeredModel::wrap);
		// "Watch it" scenes draw their blocks with what each model wants to know about its spot (the trellis frame)
		SceneRenderer.setBlockDrawer((state, pos, level, pose, buffer, random) -> {
			BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();
			ModelData data = renderer.getBlockModel(state).getModelData(level, pos, state, ModelData.EMPTY);
			renderer.renderBatched(state, pos, level, pose, buffer, true, random, data, null);
		});
		// The vision over vanilla's camera overlays (the portal's is the last of them) and under the hotbar; the blackout
		// over everything. Forge puts the mod's namespace before the name. With F1 neither is drawn by the HUD on this
		// Minecraft: the common GameRendererMixin does it then
		modBus.addListener((RegisterGuiOverlaysEvent event) -> {
			event.registerAbove(VanillaGuiOverlay.PORTAL.id(), TipsyClient.VISION_LAYER.getPath(),
				(gui, graphics, partialTick, width, height) -> TipsyClient.renderVision(graphics, partialTick));
			event.registerAboveAll(BlackoutClient.LAYER.getPath(),
				(gui, graphics, partialTick, width, height) -> BlackoutClient.renderBlackout(graphics, partialTick));
		});

		IEventBus game = MinecraftForge.EVENT_BUS;
		game.addListener((TickEvent.ClientTickEvent event) -> {
			if (event.phase != TickEvent.Phase.END) {
				return;
			}
			TipsyClient.onClientTick();
			BlackoutClient.onClientTick();
			WatchClient.onClientTick();
			// A render that never reached its Post event (an exception in a renderer) must not leave its mark behind
			SWAY_PUSHED.clear();
		});
		// "Watch it": the tooltip line, and the key over an item in a screen
		game.addListener((ItemTooltipEvent event) -> WatchClient.onTooltip(event.getItemStack(), event.getToolTip()));
		game.addListener((ScreenEvent.KeyPressed.Pre event) -> {
			if (WatchClient.onScreenKey(event.getScreen(), event.getKeyCode(), event.getScanCode())) {
				event.setCanceled(true);
			}
		});
		game.addListener((ViewportEvent.ComputeCameraAngles event) -> {
			TipsyWobble.Angles angles = TipsyClient.cameraAngles(event.getCamera().getEntity(), (float) event.getPartialTick());
			if (!angles.isNone()) {
				event.setRoll(event.getRoll() + angles.roll());
				event.setPitch(event.getPitch() + angles.pitch());
				event.setYaw(event.getYaw() + angles.yaw());
			}
		});
		// The drift at V; out cold or down after a trip: keys and mouse held still (the mouse: MouseHandlerMixin), no hand.
		// And in a blackout's bed no darkening sleep overlay and no "Leave Bed"
		game.addListener((MovementInputUpdateEvent event) -> TipsyClient.onMovementInput(event.getEntity(), event.getInput()));
		game.addListener((RenderHandEvent event) -> {
			if (TipsyClient.hidesHands()) {
				event.setCanceled(true);
			}
		});
		game.addListener((RenderGuiOverlayEvent.Pre event) -> {
			if (event.getOverlay().id().equals(VanillaGuiOverlay.SLEEP_FADE.id()) && BlackoutClient.hidesSleepOverlay()) {
				event.setCanceled(true);
			}
		});
		game.addListener((ScreenEvent.Opening event) -> {
			if (BlackoutClient.blocksScreen(event.getNewScreen())) {
				event.setCanceled(true);
			}
		});
		// The inventory's effect list: where Forge and the other mods ended up putting it this frame (last, after they
		// have moved it), for Tipsy's tooltip there
		game.addListener(EventPriority.LOWEST, (ScreenEvent.RenderInventoryMobEffects event) -> {
			effectListShown = true;
			effectListLeft = event.getHorizontalOffset();
			effectListCompact = event.isCompact();
		});
		// T1: others see you sway, hiccup, stumble, trip and black out. As late as possible, after other mods have moved
		// the pose; never when another mod cancelled the render (then no Post event comes to pop the pose again)
		game.addListener(EventPriority.LOWEST, true, (RenderLivingEvent.Pre<?, ?> event) -> {
			if (event.isCanceled()) {
				return;
			}
			SWAY_PUSHED.push(TipsyClient.beforeRenderLiving(event.getEntity(), event.getPartialTick(), event.getPoseStack()));
		});
		game.addListener((RenderLivingEvent.Post<?, ?> event) -> {
			boolean pushed = Boolean.TRUE.equals(SWAY_PUSHED.poll());
			TipsyClient.afterRenderLiving(pushed, event.getPoseStack());
		});
		// (Recipes: this Minecraft sends them all to the client itself; JEI and the barrel read the client's recipe manager)
		game.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> BarrelClient.onLoggingOut());
	}

	private static void registerAll(ClientRegistrar registrar) {
		// KettleClient looks the tabs up by name: they exist before the first registration event reaches it
		makeBookTabs();
		KettleClient.register(registrar);
		BarrelClient.register(registrar);
		CauldronClient.register(registrar);
		TipsyClient.register(registrar);
		WatchClient.register(registrar);
	}

	/**
	 * The Brew Kettle's recipe book tabs: Forge 47 extends the client's enum in code and wants each tab's icon stacks at
	 * once, so the tabs are made when the first client registration event fires (all of them fire after the items are
	 * registered), not while the mod is constructed.
	 */
	private static synchronized void makeBookTabs() {
		if (bookTabsMade) {
			return;
		}
		bookTabsMade = true;
		for (String name : new String[] {BrewingRecipeBookCategories.SEARCH, BrewingRecipeBookCategories.JUICES, BrewingRecipeBookCategories.WORTS,
			BrewingRecipeBookCategories.MISC}) {
			RecipeBookCategories.create(name, BrewingRecipeBookCategories.icons(name).toArray(ItemStack[]::new));
		}
	}

	// ------------------------------------------------------------------ for the client mixins

	/** {@code ModelBakeryMixin}: a reload starts baking its models with these sprites. */
	public static void bakingSprites(ModelBakery modelBakery, BiFunction<ResourceLocation, Material, TextureAtlasSprite> sprites) {
		bakery = modelBakery;
		bakingSprites = sprites;
	}

	/** The sprite lookup {@code modelBakery} bakes with, or null if this is not the bakery that is baking. */
	static @Nullable BiFunction<ResourceLocation, Material, TextureAtlasSprite> bakingSprites(ModelBakery modelBakery) {
		return bakery == modelBakery ? bakingSprites : null;
	}

	/** {@code EffectRenderingInventoryScreenMixin}: a new frame of the effect list begins. */
	public static void effectListBegins() {
		effectListShown = false;
	}

	/** Whether the effect list was drawn this frame (it is not when it is empty, has no room, or a mod cancelled it). */
	public static boolean effectListShown() {
		return effectListShown;
	}

	/** The list's left edge, after Forge's event. */
	public static int effectListLeft() {
		return effectListLeft;
	}

	/** Whether the list shows the icons alone (true) or the entries with names (false), after Forge's event. */
	public static boolean effectListCompact() {
		return effectListCompact;
	}
}
