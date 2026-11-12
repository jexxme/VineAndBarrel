package jexxme.vineandbarrel.neoforge.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.barrel.client.BarrelClient;
import jexxme.vineandbarrel.cauldron.client.CauldronClient;
import jexxme.vineandbarrel.compat.jei.JeiRecipeCache;
import jexxme.vineandbarrel.kettle.client.KettleClient;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import jexxme.vineandbarrel.tipsy.TipsyWobble;
import jexxme.vineandbarrel.tipsy.client.BlackoutClient;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import jexxme.vineandbarrel.watch.client.WatchClient;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookSearchCategoriesEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client side on NeoForge: the common client classes' registrations and hooks, wired to NeoForge's client events. */
public final class NeoForgeClient
{
	/** How a tipsy (or blacked-out) player's body is posed (TipsyClient#bodyPose), and whether that pose was pushed. */
	private static final ContextKey<TipsyClient.BodyPose> BODY_POSE = new ContextKey<>(VineAndBarrel.id("tipsy_body_pose"));
	private static final ContextKey<Boolean> SWAY_PUSHED = new ContextKey<>(VineAndBarrel.id("tipsy_sway_pushed"));

	private NeoForgeClient() {
	}

	public static void init(IEventBus modBus, ModContainer container) {
		// The mod list's Config button: NeoForge's own screen, built from our config files (names from the lang file)
		container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
		// Registrations: each event runs every class's register(...) and keeps its own kind (see NeoForgeClientRegistrar)
		modBus.addListener(RegisterMenuScreensEvent.class, event -> registerAll(NeoForgeClientRegistrar.screens(event)));
		modBus.addListener(RegisterRecipeBookSearchCategoriesEvent.class, event -> registerAll(NeoForgeClientRegistrar.recipeBook(event)));
		modBus.addListener(RegisterParticleProvidersEvent.class, event -> registerAll(NeoForgeClientRegistrar.particles(event)));
		modBus.addListener(RegisterClientTooltipComponentFactoriesEvent.class, event -> registerAll(NeoForgeClientRegistrar.tooltips(event)));
		modBus.addListener(RegisterColorHandlersEvent.BlockTintSources.class, event -> registerAll(NeoForgeClientRegistrar.tints(event)));
		modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> registerAll(NeoForgeClientRegistrar.renderers(event)));
		modBus.addListener(RegisterPictureInPictureRenderersEvent.class, event -> registerAll(NeoForgeClientRegistrar.pictures(event)));
		modBus.addListener(RegisterKeyMappingsEvent.class, event -> registerAll(NeoForgeClientRegistrar.keys(event)));
		modBus.addListener(AddClientReloadListenersEvent.class, event -> registerAll(NeoForgeClientRegistrar.reloads(event)));

		modBus.addListener(RegisterClientExtensionsEvent.class, NeoForgeClient::registerClientExtensions);
		// Vines on fences, walls, chains and bars draw the block they hold under their leaves
		modBus.addListener(ModelEvent.ModifyBakingResult.class, SupportedVineModel::wrap);
		// Trellis rows draw their frame (end posts, top rail) from their neighbours
		modBus.addListener(ModelEvent.RegisterStandalone.class, TrellisFrameModel::registerParts);
		modBus.addListener(ModelEvent.ModifyBakingResult.class, TrellisFrameModel::wrap);
		modBus.addListener(RegisterGuiLayersEvent.class, event -> {
			event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, TipsyClient.VISION_LAYER, TipsyClient::renderVision);
			event.registerAboveAll(BlackoutClient.LAYER, BlackoutClient::renderBlackout);
		});
		modBus.addListener(RegisterRenderStateModifiersEvent.class, NeoForgeClient::registerAvatarModifier);

		IEventBus game = NeoForge.EVENT_BUS;
		game.addListener(GatherEffectScreenTooltipsEvent.class, event -> TipsyClient.onEffectTooltip(event.getEffectInstance(), event.getTooltip()));
		game.addListener(ClientTickEvent.Post.class, event -> {
			TipsyClient.onClientTick();
			BlackoutClient.onClientTick();
			WatchClient.onClientTick();
		});
		// "Watch it": the tooltip line, and the key over an item in a screen
		game.addListener(ItemTooltipEvent.class, event -> WatchClient.onTooltip(event.getItemStack(), event.getToolTip()));
		game.addListener(ScreenEvent.KeyPressed.Pre.class, event -> {
			if (WatchClient.onScreenKey(event.getScreen(), event.getKeyEvent())) {
				event.setCanceled(true);
			}
		});
		game.addListener(ViewportEvent.ComputeCameraAngles.class, event -> {
			TipsyWobble.Angles angles = TipsyClient.cameraAngles(event.getCamera().entity(), (float) event.getPartialTick());
			if (!angles.isNone()) {
				event.setRoll(event.getRoll() + angles.roll());
				event.setPitch(event.getPitch() + angles.pitch());
				event.setYaw(event.getYaw() + angles.yaw());
			}
		});
		// The drift at V; out cold or down after a trip: keys and mouse held still, no hand. And in a blackout's bed no
		// darkening sleep overlay and no "Leave Bed"
		game.addListener(MovementInputUpdateEvent.class, event -> TipsyClient.onMovementInput(event.getEntity(), event.getInput()));
		game.addListener(CalculatePlayerTurnEvent.class, event -> event.setMouseSensitivity(TipsyClient.mouseSensitivity(event.getMouseSensitivity())));
		game.addListener(RenderHandEvent.class, event -> {
			if (TipsyClient.hidesHands()) {
				event.setCanceled(true);
			}
		});
		game.addListener(RenderGuiLayerEvent.Pre.class, event -> {
			if (event.getName().equals(VanillaGuiLayers.SLEEP_OVERLAY) && BlackoutClient.hidesSleepOverlay()) {
				event.setCanceled(true);
			}
		});
		game.addListener(ScreenEvent.Opening.class, event -> {
			if (BlackoutClient.blocksScreen(event.getNewScreen())) {
				event.setCanceled(true);
			}
		});
		// T1: others see you sway, hiccup, stumble, trip and black out. As late as possible, after other mods have moved
		// the pose; never when another mod cancelled the render (then no Post event comes to pop the pose again)
		game.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Pre.class, event -> {
			if (event.isCanceled()) {
				return;
			}
			LivingEntityRenderState state = event.getRenderState();
			if (TipsyClient.beforeRenderLiving(state, state.getRenderData(BODY_POSE), event.getPoseStack())) {
				state.setRenderData(SWAY_PUSHED, Boolean.TRUE);
			}
		});
		game.addListener(RenderLivingEvent.Post.class, event -> {
			LivingEntityRenderState state = event.getRenderState();
			boolean pushed = Boolean.TRUE.equals(state.getRenderData(SWAY_PUSHED));
			state.setRenderData(SWAY_PUSHED, null);
			TipsyClient.afterRenderLiving(pushed, event.getPoseStack());
		});
		game.addListener(RecipesReceivedEvent.class, event -> {
			JeiRecipeCache.onRecipesReceived(event.getRecipeMap());
			if (event.getRecipeTypes().contains(BarrelRegistry.AGING.get())) {
				BarrelClient.onRecipesReceived(event.getRecipeMap());
			}
		});
		game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> BarrelClient.onLoggingOut());
	}

	private static void registerAll(ClientRegistrar registrar) {
		KettleClient.register(registrar);
		BarrelClient.register(registrar);
		CauldronClient.register(registrar);
		TipsyClient.register(registrar);
		WatchClient.register(registrar);
	}

	/**
	 * Tipsy's side effects (and the blackout's) come without an icon, but the inventory lists every effect anyway: for
	 * these vanilla effects it shows an instance only when it has its icon. If another mod already claimed them,
	 * vanilla behaviour stays.
	 */
	/** 26.1: NeoForge 26.1.0 has no registerAvatarEntityModifier; this is what it does in 26.1.1 and later. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerAvatarModifier(RegisterRenderStateModifiersEvent event) {
		event.<Avatar, AvatarRenderState>registerEntityModifier((Class) AvatarRenderer.class,
			(avatar, state) -> state.setRenderData(BODY_POSE, TipsyClient.bodyPose(avatar, state)));
	}

	private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
		try {
			event.registerMobEffect(new IClientMobEffectExtensions()
			{
				@Override
				public boolean isVisibleInInventory(MobEffectInstance instance) {
					return TipsyClient.isVisibleInInventory(instance);
				}
			}, TipsyClient.SIDE_EFFECTS.stream().map(Holder::value).toArray(MobEffect[]::new));
		} catch (IllegalStateException alreadyRegistered) {
			VineAndBarrel.LOGGER.debug("Effect visibility for Tipsy's side effects is already handled by another mod", alreadyRegistered);
		}
	}
}
