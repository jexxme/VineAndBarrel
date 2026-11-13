package jexxme.vineandbarrel.gametest;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.drink.CheersEvent;
import jexxme.vineandbarrel.drink.DrankTogether;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.drink.PlacedDrinksBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The GameTests that need Forge's own API (its event bus, its mod metadata). The common test classes register them
 * by name ({@code loaderTest}), so they run in the same places and with the same names as on NeoForge (one name still
 * says "neoforge": it is the common tests' name for this loader's bus).
 */
public final class ForgeGameTests
{
	private static final Map<String, Consumer<GameTestHelper>> TESTS = Map.of(
		"drinks_cancelled_place_keeps_no_duplicate", ForgeGameTests::cancelledPlaceNoDuplicate,
		"fd_optional_dependency", ForgeGameTests::optionalDependency,
		"compat_optional_dependencies", ForgeGameTests::compatOptionalDependencies,
		"drinks_cheers_reaches_the_neoforge_bus", ForgeGameTests::cheersOnTheBus,
		"tipsy_config_carry_over", ForgeGameTests::tipsyConfigCarryOver,
		"tipsy_chat_on_the_neoforge_bus", ForgeGameTests::tipsyChatOnTheBus);

	private ForgeGameTests() {
	}

	public static @Nullable Consumer<GameTestHelper> get(String name) {
		return TESTS.get(name);
	}

	/**
	 * An old client settings file (as 1.1.1 wrote it) on Forge, read from a real file the way the client reads it before
	 * Forge loads it: wobbleIntensity and visionEffects under [tipsy] carry over as drunkScreen and doubleVision; a file
	 * that already has the new key, a key in another section and a missing file carry nothing. Then the whole carry-over on
	 * that file (set and saved through a client spec of its own, never the game's settings).
	 */
	static void tipsyConfigCarryOver(GameTestHelper helper) {
		try {
			java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("vineandbarrel-config");
			java.nio.file.Path old = dir.resolve("old.toml");
			java.nio.file.Files.writeString(old, String.join("\n", "#Tipsy visuals on this client", "[tipsy]",
				"\t#How much Tipsy moves your screen", "\t#Range: 0.0 ~ 2.0", "\twobbleIntensity = 0.4",
				"\t#Show tipsy players swaying", "\totherPlayersSway = true", "\t#Darker screen edges and slow, heavy-eyed blinks",
				"\tvisionEffects = false", "\t#Range: 0.0 ~ 1.0", "\theartbeatVolume = 1.0", ""));
			var carried = jexxme.vineandbarrel.forge.ForgeConfig.carriedOver(old);
			VBGameTests.assertValueEqual(helper, carried.get(jexxme.vineandbarrel.tipsy.TipsyConfig.DRUNK_SCREEN), "40", "wobbleIntensity 0.4 carries over as 40 %");
			VBGameTests.assertValueEqual(helper, carried.get(jexxme.vineandbarrel.tipsy.TipsyConfig.DOUBLE_VISION), "false", "visionEffects false carries over");
			VBGameTests.assertValueEqual(helper, carried.size(), 2, "nothing else carries over: " + carried);
			java.nio.file.Path both = dir.resolve("both.toml");
			java.nio.file.Files.writeString(both, "[tipsy]\n\tdrunkScreen = 70\n\twobbleIntensity = 0.0\n");
			helper.assertTrue(jexxme.vineandbarrel.forge.ForgeConfig.carriedOver(both).isEmpty(), "the new key is there: nothing carries over");
			java.nio.file.Path other = dir.resolve("other.toml");
			java.nio.file.Files.writeString(other, "[somethingElse]\n\twobbleIntensity = 0.0\n");
			helper.assertTrue(jexxme.vineandbarrel.forge.ForgeConfig.carriedOver(other).isEmpty(), "only the [tipsy] section counts");
			helper.assertTrue(jexxme.vineandbarrel.forge.ForgeConfig.carriedOver(dir.resolve("missing.toml")).isEmpty(), "no file: nothing");
			// The whole carry-over, as the client does it once the file is loaded: a client spec of its own, given the old file
			// the way Forge's own config loading opens it and hands it to the spec (Forge 47 has one config tracker, the
			// game's, so the file is opened here as it does), the carried values set and saved, and the file then has the new
			// keys with the old values
			var detached = jexxme.vineandbarrel.forge.ForgeConfig.detached(jexxme.vineandbarrel.platform.ConfigValue.Side.CLIENT,
				jexxme.vineandbarrel.tipsy.TipsyConfig.CLIENT_COMMENT);
			com.electronwill.nightconfig.core.file.CommentedFileConfig file = com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(old)
				.sync().preserveInsertionOrder().writingMode(com.electronwill.nightconfig.core.io.WritingMode.REPLACE).build();
			file.load();
			detached.spec().setConfig(file);
			helper.assertTrue(detached.spec().isLoaded(), "the old file is loaded");
			jexxme.vineandbarrel.forge.ForgeConfig.carryOver(carried, detached.values(), detached.spec());
			file.close();
			String saved = java.nio.file.Files.readString(old);
			helper.assertTrue(saved.contains("drunkScreen = 40"), "saved as drunkScreen = 40: " + saved);
			helper.assertTrue(saved.contains("doubleVision = false"), "saved as doubleVision = false");
			helper.assertTrue(jexxme.vineandbarrel.forge.ForgeConfig.carriedOver(old).isEmpty(), "and next time nothing carries over");
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
		helper.succeed();
	}

	/**
	 * Drunk chat through Forge's real client chat event, wired the way the client wires it ({@code ForgeChat}): at Tipsy V
	 * a message goes out slurred, a command as typed; sober, as typed.
	 */
	static void tipsyChatOnTheBus(GameTestHelper helper) {
		Player player = VBGameTests.makeMockPlayer(helper, GameType.SURVIVAL);
		net.minecraftforge.eventbus.api.IEventBus bus = net.minecraftforge.eventbus.api.BusBuilder.builder().build();
		jexxme.vineandbarrel.forge.ForgeChat.register(bus, line -> jexxme.vineandbarrel.tipsy.Tipsy.ownChat(player, line, 9L));
		jexxme.vineandbarrel.tipsy.Tipsy.setLevel(player, 5);
		VBGameTests.assertValueEqual(helper, said(bus, "where is my horse"), jexxme.vineandbarrel.tipsy.TipsySlur.slur("where is my horse", 9L, 4),
			"at V the message goes out slurred");
		VBGameTests.assertValueEqual(helper, said(bus, "/home"), "/home", "a command as typed");
		jexxme.vineandbarrel.tipsy.Tipsy.clear(player);
		VBGameTests.assertValueEqual(helper, said(bus, "hello"), "hello", "sober as typed");
		helper.succeed();
	}

	/** {@return what a chat line goes out as, once the client chat event on {@code bus} has had it} */
	private static String said(net.minecraftforge.eventbus.api.IEventBus bus, String line) {
		net.minecraftforge.client.event.ClientChatEvent event = new net.minecraftforge.client.event.ClientChatEvent(line);
		bus.post(event);
		return event.getMessage();
	}

	/** A protection mod cancelling the place event: the item comes back and the table has no copy of it. */
	static void cancelledPlaceNoDuplicate(GameTestHelper helper) {
		BlockPos table = new BlockPos(2, 1, 2);
		BlockPos spot = table.above();
		helper.setBlock(table, Blocks.OAK_PLANKS);
		Player player = VBGameTests.makeMockPlayer(helper, GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.MUG.get()));
		helper.assertTrue(player.getMainHandItem().useOn(BarrelGameTests.sneakUse(helper, player, table, new Vec3(0.25, 1.0, 0.25))).consumesAction(), "first mug placed");
		BlockPos absolute = helper.absolutePos(spot);
		Consumer<BlockEvent.EntityPlaceEvent> deny = event -> {
			if (event.getPos().equals(absolute)) {
				event.setCanceled(true);
			}
		};
		MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, deny);
		try {
			ItemStack ale = new ItemStack(DrinkItems.BARLEY_ALE.get());
			player.setItemInHand(InteractionHand.MAIN_HAND, ale);
			player.getMainHandItem().useOn(BarrelGameTests.sneakUse(helper, player, spot, new Vec3(0.75, 0.1, 0.75)));
		} finally {
			MinecraftForge.EVENT_BUS.unregister(deny);
		}
		VBGameTests.assertValueEqual(helper, player.getMainHandItem().getCount(), 1, "the ale is refunded");
		PlacedDrinksBlockEntity drinks = VBGameTests.blockEntity(helper, spot, PlacedDrinksBlockEntity.class);
		int held = 0;
		for (int i = 0; i < 4; i++) {
			held += drinks.getItem(i).isEmpty() ? 0 : 1;
		}
		VBGameTests.assertValueEqual(helper, held, 1, "only the first mug is on the table");
		helper.succeed();
	}

	/** Every Cheers! is also a CheersEvent on Forge's bus, with the same players, for mods listening there. */
	static void cheersOnTheBus(GameTestHelper helper) {
		Player a = VBGameTests.makeMockPlayer(helper, GameType.SURVIVAL);
		Player b = VBGameTests.makeMockPlayer(helper, GameType.SURVIVAL);
		List<List<Player>> heard = new ArrayList<>();
		Consumer<CheersEvent> listener = event -> {
			if (event.players().contains(a)) {
				heard.add(event.players());
			}
		};
		MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, CheersEvent.class, listener);
		try {
			DrankTogether.post(new DrankTogether(helper.getLevel(), List.of(a, b)));
		} finally {
			MinecraftForge.EVENT_BUS.unregister(listener);
		}
		VBGameTests.assertValueEqual(helper, heard.size(), 1, "one CheersEvent on the bus");
		VBGameTests.assertValueEqual(helper, heard.get(0), List.of(a, b), "with both drinkers");
		helper.succeed();
	}

	/**
	 * Each integration's mod is an optional dependency in mods.toml; an installed one is in range. Nutritional Balance
	 * has no build for this Minecraft, so mods.toml does not name it.
	 */
	static void compatOptionalDependencies(GameTestHelper helper) {
		IModInfo info = ModList.get().getModContainerById(VineAndBarrel.MODID).orElseThrow().getModInfo();
		for (String mod : ModCompatGameTests.OPTIONAL) {
			if (mod.equals(ModCompatGameTests.NUTRITIONAL_BALANCE)) {
				continue;
			}
			IModInfo.ModVersion dependency = info.getDependencies().stream().filter(d -> d.getModId().equals(mod)).findFirst()
				.orElseThrow(() -> new IllegalStateException("no " + mod + " dependency in mods.toml"));
			VBGameTests.assertValueEqual(helper, dependency.isMandatory(), false, mod + " dependency is mandatory");
			ModList.get().getModContainerById(mod).ifPresent(installed -> helper.assertTrue(
				dependency.getVersionRange().containsVersion(installed.getModInfo().getVersion()), "the installed " + mod + " is in range"));
		}
		helper.succeed();
	}

	/** mods.toml declares FD as an optional dependency, loaded before us when present. */
	static void optionalDependency(GameTestHelper helper) {
		IModInfo info = ModList.get().getModContainerById(VineAndBarrel.MODID).orElseThrow().getModInfo();
		IModInfo.ModVersion dependency = info.getDependencies().stream().filter(d -> d.getModId().equals(FDCompat.MODID)).findFirst()
			.orElseThrow(() -> new IllegalStateException("no farmersdelight dependency in mods.toml"));
		VBGameTests.assertValueEqual(helper, dependency.isMandatory(), false, "FD dependency is mandatory");
		VBGameTests.assertValueEqual(helper, dependency.getOrdering(), IModInfo.Ordering.AFTER, "we load after FD");
		ModList.get().getModContainerById(FDCompat.MODID).ifPresent(fd -> helper.assertTrue(
			dependency.getVersionRange().containsVersion(fd.getModInfo().getVersion()), "the installed FD " + fd.getModInfo().getVersion() + " is in range " + dependency.getVersionRange()));
		helper.succeed();
	}
}
