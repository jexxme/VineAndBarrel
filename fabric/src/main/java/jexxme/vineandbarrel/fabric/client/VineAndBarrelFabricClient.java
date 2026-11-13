package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.barrel.client.BarrelClient;
import jexxme.vineandbarrel.cauldron.client.CauldronClient;
import jexxme.vineandbarrel.compat.jei.JeiRecipeCache;
import jexxme.vineandbarrel.kettle.client.KettleClient;
import jexxme.vineandbarrel.tipsy.client.BlackoutClient;
import jexxme.vineandbarrel.tipsy.client.TipsyClient;
import jexxme.vineandbarrel.watch.client.WatchClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import jexxme.vineandbarrel.fabric.FabricConfig;
import jexxme.vineandbarrel.fabric.FabricRecipeBook;
import jexxme.vineandbarrel.fabric.ServerConfigPayload;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeMap;

/** The client side on Fabric: the common client classes' registrations and hooks, wired to Fabric API's client events. */
public final class VineAndBarrelFabricClient implements ClientModInitializer
{
	@Override
	public void onInitializeClient() {
		FabricClientRegistrar registrar = new FabricClientRegistrar();
		KettleClient.register(registrar);
		BarrelClient.register(registrar);
		CauldronClient.register(registrar);
		TipsyClient.register(registrar);
		WatchClient.register(registrar);
		// Vines on fences, walls, chains and bars draw the block they hold under their leaves
		SupportedVineModel.register();
		// Trellis rows draw their frame (end posts, top rail) from their neighbours
		TrellisFrameModel.register();

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			TipsyClient.onClientTick();
			BlackoutClient.onClientTick();
			WatchClient.onClientTick();
		});
		// "Watch it": the tooltip line, and the key over an item in a screen
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> WatchClient.onTooltip(stack, lines));
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) ->
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !WatchClient.onScreenKey(s, event)));
		// What the eyes do (TipsyClient.renderVision) above the camera overlays, under the rest of the HUD; the blackout above everything. Both stay with
		// the HUD hidden (F1), as on NeoForge: vanilla skips the camera overlays then but still draws the sleep overlay,
		// so with F1 on they are drawn right after it instead.
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, TipsyClient.VISION_LAYER, unlessHidden(false, TipsyClient::renderVision));
		HudElementRegistry.addLast(BlackoutClient.LAYER, unlessHidden(false, BlackoutClient::renderBlackout));
		HudElementRegistry.attachElementAfter(VanillaHudElements.SLEEP, TipsyClient.VISION_LAYER.withSuffix("_hidden_hud"), unlessHidden(true, TipsyClient::renderVision));
		HudElementRegistry.attachElementAfter(TipsyClient.VISION_LAYER.withSuffix("_hidden_hud"), BlackoutClient.LAYER.withSuffix("_hidden_hud"), unlessHidden(true, BlackoutClient::renderBlackout));
		// In a blackout's bed the eyes are opening: no darkening sleep overlay (NeoForge: the layer's Pre event)
		HudElementRegistry.replaceElement(VanillaHudElements.SLEEP, sleep -> (graphics, delta) -> {
			if (!BlackoutClient.hidesSleepOverlay()) {
				sleep.extractRenderState(graphics, delta);
			}
		});

		ClientRecipeSynchronizedEvent.EVENT.register((minecraft, recipes) -> {
			RecipeMap map = FabricRecipes.map(recipes.recipes());
			JeiRecipeCache.onRecipesReceived(map);
			BarrelClient.onRecipesReceived(map);
		});
		// The server's settings while connected (NeoForge syncs its server config the same way)
		ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.TYPE, (payload, context) -> FabricConfig.useServerValues(payload.values()));
		// The kettle book's settings as the server saved them (vanilla's settings packet can't carry them)
		ClientPlayNetworking.registerGlobalReceiver(FabricRecipeBook.Payload.TYPE, (payload, context) -> payload.applyTo(context.player().getRecipeBook().getBookSettings()));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> {
			BarrelClient.onLoggingOut();
			FabricConfig.useOwnValues();
		});
	}

	/** {@code element}, drawn only while the HUD is hidden with F1 ({@code hidden}) or only while it is shown. */
	private static HudElement unlessHidden(boolean hidden, HudElement element) {
		return (graphics, delta) -> {
			if (Minecraft.getInstance().gui.hud.isHidden() == hidden) {
				element.extractRenderState(graphics, delta);
			}
		};
	}
}
