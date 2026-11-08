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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The GameTests that need NeoForge's own API (its event bus, its mod metadata). The common test classes register them
 * by name ({@code loaderTest}), so they run in the same places and with the same names as before the split.
 */
public final class NeoForgeGameTests
{
	private static final Map<String, Consumer<GameTestHelper>> TESTS = Map.of(
		"drinks_cancelled_place_keeps_no_duplicate", NeoForgeGameTests::cancelledPlaceNoDuplicate,
		"fd_optional_dependency", NeoForgeGameTests::optionalDependency,
		"compat_optional_dependencies", NeoForgeGameTests::compatOptionalDependencies,
		"drinks_cheers_reaches_the_neoforge_bus", NeoForgeGameTests::cheersOnTheBus);

	private NeoForgeGameTests() {
	}

	public static @Nullable Consumer<GameTestHelper> get(String name) {
		return TESTS.get(name);
	}

	/** A protection mod cancelling the place event: the item comes back and the table has no copy of it. */
	static void cancelledPlaceNoDuplicate(GameTestHelper helper) {
		BlockPos table = new BlockPos(2, 1, 2);
		BlockPos spot = table.above();
		helper.setBlock(table, Blocks.OAK_PLANKS);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DrinkItems.MUG.get()));
		helper.assertTrue(player.getMainHandItem().useOn(BarrelGameTests.sneakUse(helper, player, table, new Vec3(0.25, 1.0, 0.25))).consumesAction(), "first mug placed");
		BlockPos absolute = helper.absolutePos(spot);
		Consumer<BlockEvent.EntityPlaceEvent> deny = event -> {
			if (event.getPos().equals(absolute)) {
				event.setCanceled(true);
			}
		};
		NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, deny);
		try {
			ItemStack ale = new ItemStack(DrinkItems.BARLEY_ALE.get());
			player.setItemInHand(InteractionHand.MAIN_HAND, ale);
			player.getMainHandItem().useOn(BarrelGameTests.sneakUse(helper, player, spot, new Vec3(0.75, 0.1, 0.75)));
		} finally {
			NeoForge.EVENT_BUS.unregister(deny);
		}
		helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "the ale is refunded");
		PlacedDrinksBlockEntity drinks = helper.getBlockEntity(spot, PlacedDrinksBlockEntity.class);
		int held = 0;
		for (int i = 0; i < 4; i++) {
			held += drinks.getItem(i).isEmpty() ? 0 : 1;
		}
		helper.assertValueEqual(held, 1, "only the first mug is on the table");
		helper.succeed();
	}

	/** Every Cheers! is also a CheersEvent on NeoForge's bus, with the same players, for mods listening there. */
	static void cheersOnTheBus(GameTestHelper helper) {
		Player a = helper.makeMockPlayer(GameType.SURVIVAL);
		Player b = helper.makeMockPlayer(GameType.SURVIVAL);
		List<List<Player>> heard = new ArrayList<>();
		Consumer<CheersEvent> listener = event -> {
			if (event.players().contains(a)) {
				heard.add(event.players());
			}
		};
		NeoForge.EVENT_BUS.addListener(CheersEvent.class, listener);
		try {
			DrankTogether.post(new DrankTogether(helper.getLevel(), List.of(a, b)));
		} finally {
			NeoForge.EVENT_BUS.unregister(listener);
		}
		helper.assertValueEqual(heard.size(), 1, "one CheersEvent on the bus");
		helper.assertValueEqual(heard.getFirst(), List.of(a, b), "with both drinkers");
		helper.succeed();
	}

	/** Each integration's mod is an optional dependency in neoforge.mods.toml; an installed one is in range. */
	static void compatOptionalDependencies(GameTestHelper helper) {
		IModInfo info = ModList.get().getModContainerById(VineAndBarrel.MODID).orElseThrow().getModInfo();
		for (String mod : ModCompatGameTests.OPTIONAL) {
			IModInfo.ModVersion dependency = info.getDependencies().stream().filter(d -> d.getModId().equals(mod)).findFirst()
				.orElseThrow(() -> new IllegalStateException("no " + mod + " dependency in neoforge.mods.toml"));
			helper.assertValueEqual(dependency.getType(), IModInfo.DependencyType.OPTIONAL, mod + " dependency type");
			ModList.get().getModContainerById(mod).ifPresent(installed -> helper.assertTrue(
				dependency.getVersionRange().containsVersion(installed.getModInfo().getVersion()), "the installed " + mod + " is in range"));
		}
		helper.succeed();
	}

	/** neoforge.mods.toml declares FD as an optional dependency, loaded before us when present. */
	static void optionalDependency(GameTestHelper helper) {
		IModInfo info = ModList.get().getModContainerById(VineAndBarrel.MODID).orElseThrow().getModInfo();
		IModInfo.ModVersion dependency = info.getDependencies().stream().filter(d -> d.getModId().equals(FDCompat.MODID)).findFirst()
			.orElseThrow(() -> new IllegalStateException("no farmersdelight dependency in neoforge.mods.toml"));
		helper.assertValueEqual(dependency.getType(), IModInfo.DependencyType.OPTIONAL, "FD dependency type");
		helper.assertValueEqual(dependency.getOrdering(), IModInfo.Ordering.AFTER, "we load after FD");
		ModList.get().getModContainerById(FDCompat.MODID).ifPresent(fd -> helper.assertTrue(
			dependency.getVersionRange().containsVersion(fd.getModInfo().getVersion()), "the installed FD " + fd.getModInfo().getVersion() + " is in range " + dependency.getVersionRange()));
		helper.succeed();
	}
}
