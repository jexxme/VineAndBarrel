package jexxme.vineandbarrel.gametest;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * What {@link ModCompatGameTests} asks the other mods and the loader, per loader (each loader project names its class in
 * {@code META-INF/services/jexxme.vineandbarrel.gametest.CompatProbes}). The mods' own APIs differ a little between
 * their NeoForge and Fabric builds, so each loader compiles its probes against its own builds. A probe into a mod is only
 * called when that mod is loaded; the implementations keep each mod's classes in a nested class, so none of them load
 * without the mod.
 */
public interface CompatProbes
{
	/** The compat run's mods on this loader (with the libraries they need): the ones that have a build for it. */
	List<String> compatRunMods();

	/** Makes a GameTest player's connection look like a real client's, so mods can sync their data to it. */
	void prepareMockConnection(Connection connection);

	/** AppleSkin's {default, modified} food values for the stack, as its tooltip and HUD ask for them. */
	FoodProperties[] appleSkinFoodValues(Player player, ItemStack stack);

	boolean toughAsNailsThirstOn();

	void toughAsNailsSetThirst(Player player, int thirst, float hydration);

	/** {thirst, hydration} */
	float[] toughAsNailsThirst(Player player);

	/** Homeostatic's {amount, saturation, sickness chance} for the stack, or null if it has nothing for it. */
	float @Nullable [] homeostaticValues(ItemStack stack);

	/** Thirst Was Taken 2's {thirst, quenched} for the stack (empty if none). */
	int[] thirstWasTakenValues(ItemStack stack);

	boolean thirstWasTakenRestoresThirst(ItemStack stack);

	/** The food group names Nutritional Balance gives the item. */
	Set<String> nutritionalBalanceGroups(Item item, ServerLevel level);

	/** How much variety Spice of Life gives for eating the stack. */
	double spiceOfLifeDiversity(Player player, ItemStack stack);
}
