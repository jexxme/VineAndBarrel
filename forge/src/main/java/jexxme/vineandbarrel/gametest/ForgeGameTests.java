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
		"drinks_cheers_reaches_the_neoforge_bus", ForgeGameTests::cheersOnTheBus);

	private ForgeGameTests() {
	}

	public static @Nullable Consumer<GameTestHelper> get(String name) {
		return TESTS.get(name);
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
