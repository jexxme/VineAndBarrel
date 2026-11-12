package jexxme.vineandbarrel.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.tipsy.TipsyInfo;
import jexxme.vineandbarrel.smoke.mixin.KeyboardHandlerInvoker;
import jexxme.vineandbarrel.smoke.mixin.MouseHandlerAccessor;
import jexxme.vineandbarrel.smoke.mixin.AbstractContainerScreenAccessor;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.barrel.BarrelStage;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.client.BlackoutClient;
import jexxme.vineandbarrel.watch.client.ScenePlayer;
import jexxme.vineandbarrel.watch.client.WatchClient;
import jexxme.vineandbarrel.watch.client.WatchIndex;
import jexxme.vineandbarrel.watch.client.WatchPathScreen;
import jexxme.vineandbarrel.watch.client.WatchScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import jexxme.vineandbarrel.compat.jei.JeiRecipeCache;
import jexxme.vineandbarrel.compat.jei.VBJeiPlugin;
import jexxme.vineandbarrel.fabric.client.VBConfigScreen;
import jexxme.vineandbarrel.drink.PlacedDrinksBlockEntity;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.platform.ConfigValue;
import jexxme.vineandbarrel.kettle.BrewKettleMenu;
import jexxme.vineandbarrel.kettle.client.BrewKettleScreen;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The Fabric smoke test (dev only, never in the jar): a real client in a hidden window plays through V&B's main
 * interactions and checks what it sees, the way a player would meet them, and saves a screenshot of each.
 * <ul>
 * <li>{@code -Dvineandbarrel.smoke=client}: a fresh singleplayer world (Gradle: {@code runSmokeClient}, {@code runSmokeClientFd}).</li>
 * <li>{@code host}: a fresh world opened to LAN on 127.0.0.1 ({@code vineandbarrel.smokePort}); quits once a joined player has left.</li>
 * <li>{@code join}: joins that host, or a dedicated server running {@link SmokeServer}, over a real connection and runs
 * the same checks (Gradle: {@code runSmokeLan(Fd)}, {@code runSmokeServer(Fd)}).</li>
 * </ul>
 * Results go to {@code <run dir>/smoke/results.json}, screenshots to {@code <run dir>/smoke/*.png}; the Gradle check
 * task fails on any failed check and on any error or exception in the log.
 */
public final class SmokeClient implements ClientModInitializer
{
	static final Logger LOGGER = LogManager.getLogger("vineandbarrel_smoke");
	static final int PORT = Integer.getInteger("vineandbarrel.smokePort", 25599);
	private static final String WORLD = "smoke";
	private static final String MODE = System.getProperty("vineandbarrel.smoke", "client");
	/**
	 * Join mode: the address to join, once the file {@code vineandbarrel.smokeReady} exists (the host or server writes it
	 * when it is up; Gradle writes {@code <that file>.failed} if it stopped before). So the client starts at the same time
	 * as the host and joins as soon as both are up.
	 */
	private static final @Nullable String CONNECT = System.getProperty("vineandbarrel.smokeConnect");
	private static final @Nullable String READY = System.getProperty("vineandbarrel.smokeReady");
	/** Give up after this long: the run fails (no results file) rather than hanging. */
	private static final int TIMEOUT = 20 * 60 * 8;

	/** How long a polled step's screenshot waits after its check first passed, for the frame to catch up. */
	private static final int SHOT_SETTLE = 20;

	/**
	 * One step: do something, wait, check what the client sees, maybe save a screenshot. A polled step's check runs
	 * every tick and the step ends as soon as it passes; {@code delay} is then only the most it waits. Only for checks
	 * without side effects whose state cannot already hold before the step's action.
	 */
	private record Step(String name, int delay, Consumer<Minecraft> action, @Nullable Function<Minecraft, String> check, @Nullable String shot, boolean hud,
		boolean poll)
	{
		Step(String name, int delay, Consumer<Minecraft> action, @Nullable Function<Minecraft, String> check, @Nullable String shot, boolean hud) {
			this(name, delay, action, check, shot, hud, false);
		}

		Step polled() {
			return new Step(name, delay, action, check, shot, hud, true);
		}
	}

	private enum Phase { TITLE, LOADING, SETTLING, STEPS, HOSTING, DONE }

	private final Path out = FabricLoader.getInstance().getGameDir().resolve("smoke");
	/** The furthest the arms were seen swung forward (radians) while getting up off the ground. */
	private static float getUpArms;
	private final List<Step> steps = new ArrayList<>();
	private final JsonArray results = new JsonArray();
	private Phase phase = Phase.TITLE;
	private int step = -1;
	private int ticks;
	/** The tick of the current step at which its polled check first passed, or -1. */
	private int passedAt = -1;
	private int total;
	private boolean joinedSeen;
	/** Whether the join client has started connecting. */
	private boolean connecting;
	/** The tick the joined player left the host, or -1. */
	private int leftAt = -1;
	/** Whether the table of drinks was still there right after the client's own guess of a break. */
	private boolean keptAtOnce;
	/** Chat lines this client received (for the slurred-chat step). */
	private final List<String> chat = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static final String TYPED = "cheers to the cellar";
	private static final String EMOTE = "raises a mug";
	/** The effect tooltip last drawn (EffectTooltipCaptureMixin). */
	private static volatile List<Component> effectTooltip = List.of();
	/** How far down the inventory's effect list the pointer is, or -1 when the smoke test isn't hovering. */
	private int hoverY = -1;

	/** The most lines a drawn tooltip had since the hovering began (DrawnTooltipMixin). */
	private static volatile int drawnTooltipLines;

	public static void effectTooltip(List<Component> tooltip) {
		effectTooltip = List.copyOf(tooltip);
	}

	public static void drawnTooltip(int lines) {
		drawnTooltipLines = Math.max(drawnTooltipLines, lines);
	}

	/** Moves the pointer down the open inventory's effect list until an effect tooltip was drawn. */
	private void hover(Minecraft mc) {
		if (hoverY < 0 || !effectTooltip.isEmpty() || !(mc.gui.screen() instanceof InventoryScreen screen)) {
			return;
		}
		AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) screen;
		double x = panel.vineandbarrel_smoke$leftPos() + panel.vineandbarrel_smoke$imageWidth() + 12;
		double y = panel.vineandbarrel_smoke$topPos() + 4 + hoverY;
		hoverY = (hoverY + 3) % 160;
		MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
		mouse.vineandbarrel_smoke$setXpos(x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth());
		mouse.vineandbarrel_smoke$setYpos(y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
	}

	@Override
	public void onInitializeClient() {
		if (MODE.equals("off")) {
			return;
		}
		try {
			deleteTree(out);
			deleteTree(FabricLoader.getInstance().getGameDir().resolve("saves").resolve(WORLD));
			Files.createDirectories(out);
		} catch (IOException e) {
			throw new IllegalStateException("Could not clear the smoke test's folders", e);
		}
		if (MODE.equals("visit")) {
			defineVisit();
		} else {
			defineSteps();
		}
		LOGGER.info("Smoke test ({} mode): {} steps", MODE, steps.size());
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, received) -> chat.add(message.getString()));
	}

	// ------------------------------------------------------------------ the script

	/**
	 * {@code visit}: this client (with Vine & Barrel) on a Fabric server without it (Gradle: runSmokeForeign). It must
	 * get in and stay connected: nothing of ours may change what vanilla's packets look like on the wire.
	 */
	private void defineVisit() {
		steps.add(new Step("stays_on_a_server_without_vb", 100, mc -> {
		}, mc -> mc.getConnection() != null && mc.player != null ? null : "not connected any more", "visit", true));
	}

	private void defineSteps() {
		steps.add(new Step("scene_builds", 100, mc -> {
			command(mc, "gamemode creative");
			command(mc, "gamerule advance_time false");
			command(mc, "gamerule spawn_mobs false");
			command(mc, "weather clear 1000000");
			command(mc, "time set 6000");
			command(mc, "vbsmoke setup");
		}, mc -> {
			BlockPos o = origin(mc);
			StringBuilder problems = new StringBuilder();
			if (!(mc.level.getBlockEntity(o.offset(SmokeCommands.KETTLE)) instanceof BrewKettleBlockEntity kettle)) {
				problems.append("no kettle on the client; ");
			} else if (kettle.getBrew().getCount() != 6) {
				problems.append("the client's kettle shows ").append(kettle.getBrew()).append(" instead of 6 juice; ");
			}
			if (!(mc.level.getBlockEntity(o.offset(SmokeCommands.BARREL)) instanceof AgingBarrelBlockEntity barrel)) {
				problems.append("no barrel on the client; ");
			} else if (barrel.servings() != 4) {
				problems.append("the client's barrel has ").append(barrel.servings()).append(" servings instead of 4; ");
			}
			if (!(mc.level.getBlockEntity(o.offset(SmokeCommands.TABLE).above()) instanceof PlacedDrinksBlockEntity drinks)) {
				problems.append("no drinks on the table on the client; ");
			} else if (drinks.count() != 4) {
				problems.append("the client's table has ").append(drinks.count()).append(" drinks instead of 4; ");
			}
			return problems.isEmpty() ? null : problems.toString();
		}, null, false).polled());
		steps.add(new Step("scene_renders", 1, mc -> {
		}, mc -> null, "scene", false));
		steps.add(new Step("recipes_reach_the_client", 1, mc -> {
		}, mc -> {
			int brewing = JeiRecipeCache.brewing().size();
			int aging = AgingRecipes.all(mc.level).size();
			return brewing > 0 && aging > 0 ? null : "brewing recipes " + brewing + ", aging recipes " + aging;
		}, null, false));
		String serverSetting = System.getProperty("vineandbarrel.smokeServerSetting");
		if (serverSetting != null) {
			// On a dedicated server whose settings differ from this client's own files: the client uses the server's
			steps.add(new Step("server_settings_reach_the_client", 1, mc -> {
			}, mc -> {
				String[] keyValue = serverSetting.split("=", 2);
				for (ConfigValue<?> value : ConfigValue.all()) {
					if ((value.section() + "." + value.name()).equals(keyValue[0])) {
						return String.valueOf(value.get()).equals(keyValue[1]) ? null : keyValue[0] + " is " + value.get() + " on the client, the server has " + keyValue[1];
					}
				}
				return "no setting " + keyValue[0];
			}, null, false));
		}
		// Trellis 2.0: vines on a fence and a wall keep those blocks (the client knows them, and draws them), a trellis row's frame
		steps.add(new Step("vines_on_fences_walls_and_trellises", 60, mc -> command(mc, "vbsmoke vines"), mc -> {
			BlockPos o = origin(mc);
			StringBuilder problems = new StringBuilder();
			for (BlockPos pos : new BlockPos[] {o.offset(SmokeCommands.VINE_FENCE), o.offset(SmokeCommands.VINE_WALL)}) {
				if (!(mc.level.getBlockEntity(pos) instanceof jexxme.vineandbarrel.block.VineSupportBlockEntity vine) || vine.support() == null) {
					problems.append("no vine on a support with its block on the client at ").append(pos).append("; ");
				}
			}
			return problems.isEmpty() ? null : problems.toString();
		}, "vines", false).polled());
		steps.add(new Step("jade_on_the_kettle", 60, mc -> command(mc, "vbsmoke look kettle"),
			mc -> FabricLoader.getInstance().isModLoaded("jade") ? null : "Jade is not loaded", "jade_kettle", true));
		steps.add(new Step("jade_on_the_drinks", 60, mc -> command(mc, "vbsmoke look drinks"), null, "jade_drinks", true));
		steps.add(new Step("jade_on_the_barrel", 60, mc -> command(mc, "vbsmoke look barrel"), null, "jade_barrel", true));
		// Breaking a table of drinks knocks off only the one aimed at; the client's own guess agrees (no flicker)
		steps.add(new Step("aim_at_the_drinks", 15, mc -> command(mc, "vbsmoke look drinks"), null, null, true));
		steps.add(new Step("a_break_knocks_off_one_drink", 15, mc -> {
			BlockPos spot = origin(mc).offset(SmokeCommands.TABLE).above();
			mc.gameMode.startDestroyBlock(spot, Direction.UP);
			keptAtOnce = mc.level.getBlockState(spot).is(BarrelRegistry.PLACED_DRINKS.get());
		}, mc -> {
			BlockPos spot = origin(mc).offset(SmokeCommands.TABLE).above();
			if (!keptAtOnce) {
				return "the client's own guess took the whole table away";
			}
			if (!(mc.level.getBlockEntity(spot) instanceof PlacedDrinksBlockEntity drinks)) {
				return "the table of drinks is gone: " + mc.level.getBlockState(spot);
			}
			return drinks.count() == 3 && drinks.getItem(0).isEmpty() ? null : "the table holds " + drinks.count() + " drinks, spot 0: " + drinks.getItem(0);
		}, "drinks_after_a_break", true));
		// Real input from here on where a player would act: clicks and held keys, sent to the server like a player's
		steps.add(new Step("kettle_opens_by_hand", 40, mc -> {
			command(mc, "give @s minecraft:sugar 3");
			BlockPos kettle = origin(mc).offset(SmokeCommands.KETTLE);
			useOn(mc, kettle, Vec3.atCenterOf(kettle).add(0.0, 0.3, -0.4), Direction.NORTH);
		}, mc -> {
			if (!(mc.gui.screen() instanceof BrewKettleScreen screen)) {
				return "the screen is " + mc.gui.screen();
			}
			BrewKettleMenu menu = screen.getMenu();
			return menu.getServings() == 6 ? null : "the kettle screen shows " + menu.getServings() + " servings instead of 6";
		}, "kettle_screen", true));
		steps.add(new Step("kettle_takes_an_ingredient_by_shift_click", 20, mc -> {
			if (mc.gui.screen() instanceof BrewKettleScreen screen) {
				// The hotbar's first slot, right after the 6 ingredient slots and the 27 inventory slots
				int hotbar0 = BrewKettleMenu.INDEX_INVENTORY + 27;
				mc.gameMode.handleContainerInput(screen.getMenu().containerId, hotbar0, 0, ContainerInput.QUICK_MOVE, mc.player);
			}
		}, mc -> {
			if (!(mc.gui.screen() instanceof BrewKettleScreen screen)) {
				return "the kettle screen closed";
			}
			ItemStack grid = screen.getMenu().getSlot(0).getItem();
			return grid.is(Items.SUGAR) && grid.getCount() == 3 ? null : "the kettle's first slot holds " + grid + " instead of 3 sugar";
		}, "kettle_with_sugar", true));
		steps.add(new Step("jei_shows_our_recipes", 40, mc -> {
			mc.player.closeContainer();
			if (!VBJeiPlugin.showOurCategories()) {
				LOGGER.warn("JEI did not open our categories");
			}
		}, mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("jei")
			? null : "the screen is " + mc.gui.screen(), "jei", true));
		// Mod Menu's Config button opens this screen (Mod Menu isn't in the run): the settings as vanilla options rows
		steps.add(new Step("the_settings_screen_opens", 20, mc -> mc.gui.setScreen(new VBConfigScreen(null)),
			mc -> mc.gui.screen() instanceof VBConfigScreen ? null : "the screen is " + mc.gui.screen(), "settings", true));
		steps.add(new Step("place_a_drink_by_sneak_click", 30, mc -> {
			mc.gui.setScreen(null);
			command(mc, "vbsmoke look empty_table");
			command(mc, "item replace entity @s weapon.mainhand with vineandbarrel:aged_red_wine");
			mc.options.keyShift.setDown(true);
		}, null, null, true));
		steps.add(new Step("the_drink_stands_on_the_table", 20, mc -> {
			BlockPos table = origin(mc).offset(SmokeCommands.EMPTY_TABLE);
			useOn(mc, table, Vec3.atLowerCornerOf(table).add(0.25, 1.0, 0.25), Direction.UP);
		}, mc -> {
			mc.options.keyShift.setDown(false);
			BlockPos spot = origin(mc).offset(SmokeCommands.EMPTY_TABLE).above();
			if (!(mc.level.getBlockEntity(spot) instanceof PlacedDrinksBlockEntity drinks)) {
				return "no drink on the empty table (" + mc.level.getBlockState(spot) + ")";
			}
			return drinks.count() == 1 && drinks.getItem(0).is(jexxme.vineandbarrel.drink.DrinkItems.AGED_RED_WINE.get()) ? null
				: "the table holds " + drinks.count() + " drinks, spot 0: " + drinks.getItem(0);
		}, null, true));
		steps.add(new Step("pour_into_the_barrel_by_hand", 20, mc -> {
			command(mc, "vbsmoke look barrel");
			command(mc, "item replace entity @s weapon.mainhand with vineandbarrel:grape_juice");
		}, null, null, true));
		steps.add(new Step("the_barrel_takes_it", 20, mc -> {
			BlockPos barrel = origin(mc).offset(SmokeCommands.BARREL);
			useOn(mc, barrel, Vec3.atCenterOf(barrel).add(0.0, 0.5, 0.0), Direction.UP);
		}, mc -> mc.level.getBlockEntity(origin(mc).offset(SmokeCommands.BARREL)) instanceof AgingBarrelBlockEntity barrel && barrel.servings() == 5
			? null : "the barrel did not take the juice (" + mc.level.getBlockEntity(origin(mc).offset(SmokeCommands.BARREL)) + ")", null, true).polled());
		// The new drinks age like the others: four days on, the honey must is aged mead, tapped by hand
		steps.add(new Step("mead_ages_in_the_barrel", 40, mc -> {
			command(mc, "time add 96000");
			command(mc, "vbsmoke look mead_barrel");
		}, mc -> {
			if (!(mc.level.getBlockEntity(origin(mc).offset(SmokeCommands.MEAD_BARREL)) instanceof AgingBarrelBlockEntity mead)) {
				return "no mead barrel on the client";
			}
			return mead.stage() == BarrelStage.AGED && mead.drink().is(DrinkItems.AGED_MEAD.get()) ? null
				: "the mead barrel is " + mead.stage() + " with " + mead.drink();
		}, "jade_mead", true));
		steps.add(new Step("a_vessel_for_the_mead", 20, mc -> {
			AgingBarrelBlockEntity mead = (AgingBarrelBlockEntity) mc.level.getBlockEntity(origin(mc).offset(SmokeCommands.MEAD_BARREL));
			Item vessel = AgingBarrelBlockEntity.vesselOf(mead.drink());
			command(mc, "item replace entity @s weapon.mainhand with " + BuiltInRegistries.ITEM.getKey(vessel));
		}, null, null, true));
		steps.add(new Step("the_aged_mead_is_tapped", 20, mc -> {
			BlockPos barrel = origin(mc).offset(SmokeCommands.MEAD_BARREL);
			useOn(mc, barrel, Vec3.atCenterOf(barrel).add(0.0, 0.0, -0.5), Direction.NORTH);
		}, mc -> {
			AgingBarrelBlockEntity mead = (AgingBarrelBlockEntity) mc.level.getBlockEntity(origin(mc).offset(SmokeCommands.MEAD_BARREL));
			boolean got = mc.player.getMainHandItem().is(DrinkItems.AGED_MEAD.get()) || mc.player.getInventory().contains(new ItemStack(DrinkItems.AGED_MEAD.get()));
			if (!got) {
				return "no aged mead tapped (hand: " + mc.player.getMainHandItem() + ")";
			}
			return mead.servings() == 3 ? null : "the mead barrel has " + mead.servings() + " servings instead of 3";
		}, null, true));
		steps.add(new Step("drink_by_holding_use", 60, mc -> {
			// Away from the barrels: the held key must only drink, not tap again with the mug that comes back
			command(mc, "vbsmoke look sky");
			command(mc, "gamemode survival");
			command(mc, "item replace entity @s weapon.mainhand with vineandbarrel:barley_ale");
		}, null, null, true));
		steps.add(new Step("the_ale_is_drunk", 70, mc -> {
			mc.options.keyUse.setDown(true);
			mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
		}, mc -> {
			mc.options.keyUse.setDown(false);
			Integer level = TipsyRegistry.SYNCED_LEVEL.getExisting(mc.player);
			ItemStack hand = mc.player.getMainHandItem();
			if (level == null || level != 1) {
				return "Tipsy level " + level + " after one ale instead of 1";
			}
			return hand.is(jexxme.vineandbarrel.drink.DrinkItems.MUG.get()) ? null : "the hand holds " + hand + " instead of the empty mug";
		}, "after_one_ale", true));
		steps.add(new Step("tipsy_reaches_the_client", 100, mc -> {
			command(mc, "gamemode creative");
			mc.gui.setScreen(null);
			command(mc, "vbsmoke view");
			command(mc, "vbsmoke tipsy 4");
		}, mc -> {
			Integer level = TipsyRegistry.SYNCED_LEVEL.getExisting(mc.player);
			if (!mc.player.hasEffect(TipsyRegistry.TIPSY)) {
				return "no Tipsy effect on the client";
			}
			return level != null && level == 4 ? null : "synced Tipsy level " + level + " instead of 4";
		}, "tipsy_4", true).polled());
		// Slurred chat at Tipsy IV, through the server's real chat path (Fabric: ServerGamePacketListenerImplMixin): a typed
		// message comes back slurred, a /me line as typed, as on NeoForge (ServerChatEvent is typed chat only)
		steps.add(new Step("typed_chat_is_slurred_and_me_is_not", 40, mc -> {
			chat.clear();
			mc.player.connection.sendChat(TYPED);
			mc.player.connection.sendCommand("me " + EMOTE);
		}, mc -> {
			// Exact lines, no chance involved: the slur always changes a message (when nothing else changed it adds a
			// *hic*), so the typed line differs from what was typed, and the /me line is exactly what was typed
			List<String> lines = List.copyOf(chat);
			String name = mc.player.getName().getString();
			String emote = "* " + name + " " + EMOTE;
			String unslurred = "<" + name + "> " + TYPED;
			if (!lines.contains(emote)) {
				return "no /me line came back exactly as typed (\"" + emote + "\"): " + lines;
			}
			List<String> typed = lines.stream().filter(line -> line.startsWith("<" + name + "> ")).toList();
			if (typed.isEmpty()) {
				return "the typed message never came back: " + lines;
			}
			return typed.contains(unslurred) ? "the typed message came back unslurred: " + typed : null;
		}, "slurred_chat", true));
		// The survival inventory, which lists the effects (Tipsy's own entry, its side effects hidden when icon-less)
		steps.add(new Step("survival_for_the_inventory", 20, mc -> command(mc, "gamemode survival"),
			mc -> mc.player.isCreative() ? "still in creative" : null, null, true).polled());
		steps.add(new Step("inventory_with_tipsy", 30, mc -> mc.gui.setScreen(new InventoryScreen(mc.player)),
			mc -> mc.gui.screen() instanceof InventoryScreen ? null : "the screen is " + mc.gui.screen(), "inventory_tipsy", true));
		// Hovering Tipsy in that list explains the level (Fabric: EffectsInInventoryMixin; the pointer moves down the
		// list a little each tick until something shows a tooltip)
		steps.add(new Step("hovering_tipsy_explains_the_level", 80, mc -> {
			effectTooltip = List.of();
			drawnTooltipLines = 0;
			hoverY = 0;
		}, mc -> {
			String expected = TipsyInfo.lines(4).getFirst().getString();
			String shown = String.join(" | ", effectTooltip.stream().map(Component::getString).toList());
			if (!shown.contains(expected)) {
				return "the Tipsy tooltip shows [" + shown + "], expected the line " + expected;
			}
			// What is drawn, not only what was built: name, time and every line of the level
			int lines = 2 + TipsyInfo.lines(4).size();
			return drawnTooltipLines >= lines ? null : "the drawn tooltip has " + drawnTooltipLines + " lines, expected " + lines;
		}, "inventory_tipsy_tooltip", true).polled());
		steps.add(new Step("sway_in_third_person", 60, mc -> {
			hoverY = -1;
			mc.gui.setScreen(null);
			command(mc, "gamemode creative");
			command(mc, "vbsmoke view");
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		}, null, "sway_third_person", true));
		// The blackout, ending in the player's own bed (the smoke command picks that ending): out cold on the ground with no
		// hand in view, then waking up in bed without a "Leave Bed" screen, then up again at Tipsy I
		steps.add(new Step("blackout_reaches_the_client", 40, mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			command(mc, "vbsmoke blackout home");
		}, mc -> {
			Integer left = TipsyRegistry.BLACKOUT.getExisting(mc.player);
			if (left == null || left <= 0) {
				return "no blackout on the client (" + left + ")";
			}
			if (BlackoutClient.body(mc.player, 0.0F) == null) {
				return "not lying on the ground (" + left + " ticks left, pose " + Blackout.pose(mc.player) + ")";
			}
			return BlackoutClient.hidesHands() ? null : "the hand still shows";
		}, "blackout", true));
		steps.add(new Step("wakes_up_in_own_bed", 95, mc -> {
		}, mc -> {
			BlockPos head = origin(mc).offset(SmokeCommands.BED_FOOT).east();
			if (!mc.player.isSleeping()) {
				return "not in bed (at " + mc.player.blockPosition() + ", pose " + Blackout.pose(mc.player) + ")";
			}
			if (!mc.player.getSleepingPos().map(head::equals).orElse(false)) {
				return "in another bed: " + mc.player.getSleepingPos() + " instead of " + head;
			}
			return mc.gui.screen() instanceof InBedChatScreen ? "the Leave Bed screen opened" : null;
		}, "blackout_home_bed", true).polled());
		steps.add(new Step("wakes_up_after_the_blackout", 300, mc -> {
		}, mc -> {
			Integer left = TipsyRegistry.BLACKOUT.getExisting(mc.player);
			if (left != null && left > 0) {
				return "still out (" + left + " ticks left)";
			}
			if (mc.player.isSleeping()) {
				return "still in bed";
			}
			Integer level = TipsyRegistry.SYNCED_LEVEL.getExisting(mc.player);
			return level != null && level == 1 ? null : "Tipsy " + level + " after waking up instead of 1";
		}, "after_blackout", true).polled());
		// Getting up off the ground moves the limbs, not only the body: on Fabric too (the common HumanoidModelMixin, fed
		// through AvatarRendererMixin); seen from the front in third person, the arms swing in under the body as they push
		steps.add(new Step("getting_up_moves_the_limbs", 60, mc -> {
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			getUpArms = 0.0F;
			command(mc, "vbsmoke blackout getup");
		}, mc -> {
			// The player model as last drawn: vanilla leaves a still player's arms hanging
			if (BlackoutClient.body(mc.player, 0.0F) != null
				&& mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> renderer) {
				var model = renderer.getModel();
				getUpArms = Math.max(getUpArms, Math.max(-model.rightArm.xRot, -model.leftArm.xRot));
			}
			return getUpArms > 1.0F ? null : "the arms don't swing in under the body (" + getUpArms + " rad forward at most; " + Blackout.ticksLeft(mc.player)
				+ " ticks left, pose " + Blackout.pose(mc.player) + ")";
		}, "blackout_getting_up", true).polled());
		steps.add(new Step("up_off_the_ground", 60, mc -> {
		}, mc -> Blackout.isOut(mc.player) ? "still out (" + Blackout.ticksLeft(mc.player) + " ticks left)" : null, null, true).polled());
		steps.add(new Step("sober_again", 40, mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			command(mc, "vbsmoke sober");
		}, mc -> mc.player.hasEffect(TipsyRegistry.TIPSY) ? "still tipsy" : null, null, true).polled());
		// "Watch it": the tooltip offers the scene; at the scene's barrel in the world the key opens it where that barrel is
		steps.add(new Step("watch_it_on_the_tooltip", 1, mc -> {
		}, mc -> {
			ItemStack barrel = new ItemStack(BarrelRegistry.AGING_BARREL_ITEM.get());
			List<Component> lines = barrel.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
			String watch = Component.translatable("watch.vineandbarrel.press", WatchClient.KEY.getTranslatedKeyMessage()).getString();
			return lines.stream().anyMatch(line -> line.getString().equals(watch)) ? null : "no \"" + watch + "\" in " + lines;
		}, null, false));
		// As a player does it: the pointer over the barrel in the inventory, then G (through the game's own key handling)
		steps.add(new Step("watch_it_hover_the_barrel", 10, mc -> {
			// survival: a creative player gets the creative inventory instead
			command(mc, "gamemode survival");
			command(mc, "item replace entity @s hotbar.8 with vineandbarrel:aging_barrel");
		}, null, null, false));
		steps.add(new Step("watch_it_open_the_inventory", 5, mc -> {
			mc.gui.setScreen(new InventoryScreen(mc.player));
		}, null, null, false));
		steps.add(new Step("watch_it_key_over_the_barrel", 10, mc -> {
			if (mc.gui.screen() instanceof InventoryScreen screen) {
				AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) screen;
				Slot slot = screen.getMenu().slots.stream().filter(s -> s.container == mc.player.getInventory() && s.getContainerSlot() == 8).findFirst().orElseThrow();
				MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
				double x = panel.vineandbarrel_smoke$leftPos() + slot.x + 8, y = panel.vineandbarrel_smoke$topPos() + slot.y + 8;
				mouse.vineandbarrel_smoke$setXpos(x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth());
				mouse.vineandbarrel_smoke$setYpos(y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
			}
		}, null, "watch_tooltip", false));
		steps.add(new Step("watch_it_opens_from_the_inventory", 20, mc ->
			((KeyboardHandlerInvoker) mc.keyboardHandler).vineandbarrel_smoke$keyPress(mc.getWindow().handle(), InputConstants.PRESS,
				new KeyEvent(InputConstants.KEY_G, 0, 0)),
			mc -> mc.gui.screen() instanceof WatchScreen ? null : "G over the barrel: the screen is " + mc.gui.screen(), null, false));
		steps.add(new Step("watch_it_goes_back_to_the_inventory", 5, mc -> {
			if (mc.gui.screen() != null) {
				mc.gui.screen().onClose();
			}
		}, mc -> mc.gui.screen() instanceof InventoryScreen ? null : "after Escape the screen is " + mc.gui.screen(), null, false));
		// A wort bucket asks for its own brew: the barrel scene pours that wort and taps it into a mug
		steps.add(new Step("watch_it_hover_a_wort_bucket", 10, mc -> {
			command(mc, "item replace entity @s hotbar.8 with vineandbarrel:barley_wort_bucket");
			mc.gui.setScreen(new InventoryScreen(mc.player));
		}, null, null, false));
		steps.add(new Step("watch_it_point_at_the_wort", 10, mc -> {
			if (mc.gui.screen() instanceof InventoryScreen screen) {
				AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) screen;
				Slot slot = screen.getMenu().slots.stream().filter(s -> s.container == mc.player.getInventory() && s.getContainerSlot() == 8).findFirst().orElseThrow();
				MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
				double x = panel.vineandbarrel_smoke$leftPos() + slot.x + 8, y = panel.vineandbarrel_smoke$topPos() + slot.y + 8;
				mouse.vineandbarrel_smoke$setXpos(x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth());
				mouse.vineandbarrel_smoke$setYpos(y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
			}
		}, null, null, false));
		steps.add(new Step("watch_it_opens_for_the_wort", 20, mc ->
			((KeyboardHandlerInvoker) mc.keyboardHandler).vineandbarrel_smoke$keyPress(mc.getWindow().handle(), InputConstants.PRESS,
				new KeyEvent(InputConstants.KEY_G, 0, 0)), mc -> {
			if (!(mc.gui.screen() instanceof WatchScreen screen)) {
				return "G over the wort bucket: the screen is " + mc.gui.screen();
			}
			ScenePlayer player = screen.player();
			if (!player.askedRecipe().equals(java.util.Optional.of(VineAndBarrel.id("aging/barley_ale")))) {
				return "the wort bucket opened the barrel scene for " + player.askedRecipe();
			}
			String caption = player.caption(0).getString();
			String wort = Component.translatable("watch.vineandbarrel.aging_barrel.1.wort").getString();
			return caption.equals(wort) ? null : "the wort's first step says \"" + caption + "\" instead of \"" + wort + "\"";
		}, "watch_wort", false));
		steps.add(new Step("watch_it_done", 2, mc -> {
			mc.gui.setScreen(null);
			command(mc, "gamemode creative");
		}, null, null, true));
		steps.add(new Step("watch_it_opens_at_the_barrel", 40, mc -> {
			mc.gui.setScreen(null);
			BlockPos barrel = origin(mc).offset(SmokeCommands.BARREL);
			WatchIndex.INSTANCE.forBlock(mc.level, barrel, mc.level.getBlockState(barrel)).ifPresent(entry ->
				WatchScreen.open(entry.scene(), entry.step(), entry.now().map(Component::translatable).orElse(null), null));
		}, mc -> {
			BlockPos barrel = origin(mc).offset(SmokeCommands.BARREL);
			BlockState state = mc.level.getBlockState(barrel);
			var entry = WatchIndex.INSTANCE.forBlock(mc.level, barrel, state);
			if (entry.isEmpty()) {
				return "no scene for the barrel " + state;
			}
			if (!(mc.gui.screen() instanceof WatchScreen screen)) {
				return "the screen is " + mc.gui.screen();
			}
			return screen.player().step() == entry.get().step() ? null : "the scene opened at step " + screen.player().step() + " for " + state;
		}, "watch_barrel_from_the_world", false));
		// It plays: the brewer, the real blocks, particles and sounds in a 3D view
		steps.add(new Step("watch_it_plays", 60, mc -> {
			mc.gui.setScreen(null);
			if (WatchScreen.open(VineAndBarrel.id("aging_barrel")) && mc.gui.screen() instanceof WatchScreen screen) {
				screen.player().jumpTo(1);
			}
		}, mc -> {
			if (!(mc.gui.screen() instanceof WatchScreen screen)) {
				return "the screen is " + mc.gui.screen();
			}
			ScenePlayer player = screen.player();
			if (player.scene().steps().size() != 6) {
				return "the barrel scene has " + player.scene().steps().size() + " steps instead of 6";
			}
			return player.step() == 1 && player.tick() > 30 ? null : "the scene is at step " + player.step() + ", tick " + player.tick();
		}, "watch_barrel", false));
		// The kettle's scene (its steam step), and with Farmer's Delight its own scene (the vine on the ropes)
		steps.add(watchScene("watch_kettle_plays", "brew_kettle", 3, "watch_kettle"));
		if (FabricLoader.getInstance().isModLoaded("farmersdelight")) {
			steps.add(watchScene("watch_farmers_delight_plays", "farmers_delight", 1, "watch_farmers_delight"));
		}
		// The Brewer's path: every station of the journey, with what the player has done ticked off
		steps.add(new Step("watch_path_opens", 40, mc -> mc.gui.setScreen(new WatchPathScreen(null)),
			mc -> mc.gui.screen() instanceof WatchPathScreen ? null : "the path didn't open: the screen is " + mc.gui.screen(), "watch_path", false));
		steps.add(new Step("watch_it_closes", 5, mc -> mc.gui.setScreen(null), mc -> mc.gui.screen() == null ? null : "still open: " + mc.gui.screen(), null, true));
		// A real plains village grown by the game with our tavern in it; its casks reach the client
		// Growing villages until one has a tavern can keep the server busy for a while (with FD, up to ten tries)
		steps.add(new Step("a_village_with_a_tavern", 600, mc -> {
			command(mc, "gamemode creative");
			command(mc, "vbsmoke village");
		}, mc -> {
			BlockPos at = mc.player.blockPosition();
			if (at.distManhattan(origin(mc)) < 150) {
				return "still at the scene (" + at + "): the village was not placed in time";
			}
			int barrels = 0;
			for (BlockPos pos : BlockPos.betweenClosed(at.offset(-24, -36, -4), at.offset(24, 6, 48))) {
				barrels += mc.level.getBlockState(pos).is(BarrelRegistry.AGING_BARREL.get()) ? 1 : 0;
			}
			return barrels >= 2 ? null : "only " + barrels + " barrels near the tavern at " + at;
		}, "tavern_village", true).polled());
	}

	/** A scene opens at a step and plays there for a few seconds, its screenshot taken. */
	private static Step watchScene(String name, String scene, int step, String shot) {
		return new Step(name, 60, mc -> {
			mc.gui.setScreen(null);
			if (WatchScreen.open(VineAndBarrel.id(scene)) && mc.gui.screen() instanceof WatchScreen screen) {
				screen.player().jumpTo(step);
			}
		}, mc -> {
			if (!(mc.gui.screen() instanceof WatchScreen screen)) {
				return "the " + scene + " scene didn't open: the screen is " + mc.gui.screen();
			}
			return screen.player().step() == step && screen.player().tick() > 30 ? null
				: "the scene is at step " + screen.player().step() + ", tick " + screen.player().tick();
		}, shot, false);
	}

	// ------------------------------------------------------------------ running it

	private void tick(Minecraft mc) {
		if (phase == Phase.DONE) {
			return;
		}
		// The hidden window never has focus (HiddenWindowMixin): without this the game would pause itself
		mc.options.pauseOnLostFocus = false;
		hover(mc);
		if (++total > TIMEOUT) {
			LOGGER.error("Smoke test timed out in phase {} at step {}", phase, step);
			finish(mc, false);
			return;
		}
		switch (phase) {
			case TITLE -> {
				if (MODE.equals("join") || MODE.equals("visit")) {
					if (mc.player != null && mc.level != null) {
						LOGGER.info("Joined the host");
						ticks = 0;
						phase = Phase.SETTLING;
					} else if (mc.gui.screen() instanceof DisconnectedScreen screen) {
						// Refused or kicked: fail now instead of waiting for the timeout
						record("joins_the_server", "could not join: " + screen.getNarrationMessage().getString());
						finish(mc, false);
					} else if (!connecting && CONNECT != null && READY != null && mc.gui.screen() instanceof TitleScreen) {
						if (Files.exists(Path.of(READY + ".failed"))) {
							record("joins_the_server", "the host stopped before it was ready");
							finish(mc, false);
						} else if (Files.exists(Path.of(READY))) {
							connecting = true;
							LOGGER.info("The host is up; joining {}", CONNECT);
							ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(CONNECT),
								new ServerData("V&B smoke test", CONNECT, ServerData.Type.OTHER), false, null);
						}
					}
				} else if (mc.gui.screen() instanceof TitleScreen) {
					LOGGER.info("Creating the smoke test world");
					LevelSettings settings = new LevelSettings("V&B smoke test", GameType.CREATIVE,
						new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
					mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261107L, false, false),
						WorldPresets::createNormalWorldDimensions, new TitleScreen());
					phase = Phase.LOADING;
				}
			}
			case LOADING -> {
				MinecraftServer server = mc.getSingleplayerServer();
				if (mc.player != null && mc.level != null && server != null && server.isReady()) {
					ticks = 0;
					if (MODE.equals("host")) {
						host(mc, (IntegratedServer) server);
						phase = Phase.HOSTING;
					} else {
						phase = Phase.SETTLING;
					}
				}
			}
			case SETTLING -> {
				if (++ticks >= 60) {
					nextStep(mc);
					phase = Phase.STEPS;
				}
			}
			case STEPS -> runStep(mc);
			case HOSTING -> {
				MinecraftServer server = mc.getSingleplayerServer();
				int players = server == null ? 0 : server.getPlayerList().getPlayerCount();
				if (players > 1 && !joinedSeen) {
					joinedSeen = true;
					LOGGER.info("A player joined over the network");
				}
				if (joinedSeen && players <= 1) {
					if (leftAt < 0) {
						leftAt = total;
						record("a_player_joins_and_leaves", null);
					} else if (server == null || !remoteConnectionOpen(server) || total - leftAt >= 40) {
						// Once the joined player's connection is closed and gone (two seconds at most): quitting while it is
						// still open makes vanilla log a closed channel
						finish(mc, true);
					}
				}
			}
			case DONE -> {
			}
		}
	}

	/** Whether the host still has a connection to another client (not its own player's in-memory one). */
	private static boolean remoteConnectionOpen(MinecraftServer server) {
		List<Connection> connections = server.getConnection().getConnections();
		synchronized (connections) {
			return connections.stream().anyMatch(connection -> !connection.isMemoryConnection());
		}
	}

	private void host(Minecraft mc, IntegratedServer server) {
		command(mc, "gamerule advance_time false");
		command(mc, "gamerule spawn_mobs false");
		server.setUsesAuthentication(false);
		boolean published = server.publishServer(MinecraftServer.MultiplayerScope.LAN, null, true, PORT);
		record("host_opens_to_lan", published ? null : "could not open to LAN on port " + PORT);
		try {
			Files.writeString(out.resolve("host-ready"), Integer.toString(PORT), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write the ready file", e);
		}
		LOGGER.info("Hosting on 127.0.0.1:{}", PORT);
	}

	/** Whether a polled step's check passes now; the result that counts is still the one at the step's end. */
	private static boolean passes(Minecraft mc, Step current) {
		@Nullable Function<Minecraft, String> check = current.check();
		try {
			return check != null && check.apply(mc) == null;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private void nextStep(Minecraft mc) {
		step++;
		ticks = 0;
		passedAt = -1;
		if (step >= steps.size()) {
			finish(mc, true);
			return;
		}
		Step current = steps.get(step);
		LOGGER.info("Step {}: {}", step + 1, current.name());
		current.action().accept(mc);
	}

	private void runStep(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			if (mc.gui.screen() instanceof DisconnectedScreen screen) {
				record(steps.get(step).name(), "disconnected: " + screen.getNarrationMessage().getString());
			}
			LOGGER.error("Lost the world during step {}", steps.get(step).name());
			finish(mc, false);
			return;
		}
		Step current = steps.get(step);
		if (mc.gui.hud.isHidden() == current.hud()) {
			mc.gui.hud.toggle();
		}
		ticks++;
		if (current.poll() && passedAt < 0 && ticks < current.delay() && passes(mc, current)) {
			passedAt = ticks;
		}
		// A polled step ends once its check passed (and its screenshot had a moment to catch up), the others after their delay
		boolean waiting = passedAt < 0 ? ticks < current.delay() : current.shot() != null && ticks < passedAt + SHOT_SETTLE;
		// A view without the HUD waits for its chunks to render, and so does a polled step's screenshot (it may be taken
		// somewhere new, like the village), for a few seconds at most
		boolean chunksMatter = current.shot() != null && (current.poll() ? passedAt >= 0 : !current.hud());
		int chunkLimit = current.poll() ? passedAt + SHOT_SETTLE + 100 : current.delay() + 400;
		boolean waitForChunks = chunksMatter && !mc.levelRenderer.hasRenderedAllSections() && ticks < chunkLimit;
		if (waiting || waitForChunks) {
			return;
		}
		if (current.check() != null) {
			String problem;
			try {
				problem = current.check().apply(mc);
			} catch (RuntimeException e) {
				LOGGER.error("Step {} threw", current.name(), e);
				problem = e.toString();
			}
			record(current.name(), problem);
		}
		if (current.shot() != null) {
			shoot(mc, current.shot());
		}
		nextStep(mc);
	}

	private void record(String name, @Nullable String problem) {
		JsonObject result = new JsonObject();
		result.addProperty("check", name);
		result.addProperty("mode", MODE);
		result.addProperty("loader", "fabric");
		result.addProperty("farmersdelight", FabricLoader.getInstance().isModLoaded("farmersdelight"));
		result.addProperty("ok", problem == null);
		if (problem != null) {
			result.addProperty("problem", problem);
			LOGGER.warn("Smoke check {} FAILED: {}", name, problem);
		} else {
			LOGGER.info("Smoke check {} passed", name);
		}
		results.add(result);
	}

	private void shoot(Minecraft mc, String name) {
		String file = name + ".png";
		Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1, message -> {
			Path saved = mc.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR).resolve(file);
			try {
				Files.copy(saved, out.resolve(file), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				LOGGER.error("Could not copy the screenshot {}", saved, e);
			}
		});
	}

	private void finish(Minecraft mc, boolean completed) {
		phase = Phase.DONE;
		JsonObject report = new JsonObject();
		report.addProperty("mode", MODE);
		report.addProperty("completed", completed);
		report.add("checks", results);
		try {
			Files.writeString(out.resolve("results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write the smoke test results", e);
		}
		long failed = results.asList().stream().filter(r -> !r.getAsJsonObject().get("ok").getAsBoolean()).count();
		LOGGER.info("Smoke test {}: {} checks, {} failed{}", MODE, results.size(), failed, completed ? "" : ", NOT COMPLETED");
		mc.execute(mc::stop);
	}

	/** A right-click on a block, as the player's client sends it (sneaking when the sneak key is down). */
	private static void useOn(Minecraft mc, BlockPos pos, Vec3 hit, Direction face) {
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
	}

	private static BlockPos origin(Minecraft mc) {
		BlockPos spawn = mc.level.getLevelData().getRespawnData().pos();
		return new BlockPos(spawn.getX(), SmokeCommands.HEIGHT, spawn.getZ());
	}

	private static void command(Minecraft mc, String command) {
		mc.player.connection.sendCommand(command);
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}
}
