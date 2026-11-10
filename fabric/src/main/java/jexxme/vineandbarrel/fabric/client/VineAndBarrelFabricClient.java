package jexxme.vineandbarrel.fabric.client;

import jexxme.vineandbarrel.barrel.client.BarrelClient;
import jexxme.vineandbarrel.cauldron.client.CauldronClient;
import jexxme.vineandbarrel.client.VBRenderLayers;
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
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

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
		VBRenderLayers.register(registrar);
		// Vines on fences, walls, chains and bars draw the block they hold under their leaves
		SupportedVineModel.register();
		// Trellis rows draw their frame (end posts, top rail) from their neighbours
		TrellisFrameModel.register();
		// The blocks with glass draw each face in the layer of its own texture
		TextureLayeredModel.register();

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			TipsyClient.onClientTick();
			BlackoutClient.onClientTick();
			WatchClient.onClientTick();
		});
		// "Watch it": the tooltip line, and the key over an item in a screen
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> WatchClient.onTooltip(stack, lines));
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) ->
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scanCode, modifiers) -> !WatchClient.onScreenKey(s, key, scanCode)));
		// Tunnel vision above the camera overlays and under the rest of the HUD, the blackout above everything, and no
		// sleep overlay in a blackout's bed: Fabric API has no HUD layers on this Minecraft, see the mixin (HudMixin).
		// Recipes: this Minecraft sends every recipe to the client by itself, nothing to synchronise.

		// The server's settings while connected (NeoForge syncs its server config the same way)
		// Never sent: taking it tells a server with Vine & Barrel that this client has the mod (ModPresence)
		net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking.registerGlobalReceiver(jexxme.vineandbarrel.fabric.ModPresence.TYPE, (payload, context) -> {
		});
		ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.TYPE, (payload, context) -> FabricConfig.useServerValues(payload.values()));
		// The kettle book's settings as the server saved them (vanilla's settings packet can't carry them)
		ClientPlayNetworking.registerGlobalReceiver(FabricRecipeBook.Payload.TYPE, (payload, context) -> payload.applyTo(context.player().getRecipeBook()));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> {
			BarrelClient.onLoggingOut();
			FabricConfig.useOwnValues();
		});
	}
}
