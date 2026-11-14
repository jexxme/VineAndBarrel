package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import jexxme.vineandbarrel.world.village.VillageStructures;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.reflect.Field;
import java.util.List;

/**
 * One tavern per village ({@link JigsawPlacerMixin}) with Lithostitched (Terralith and other worldgen mods bring it):
 * it lays out every jigsaw structure, villages too, with a placer of its own instead of the game's, so the game's
 * placer never chose a village's houses and a village got a tavern on every plot one fit. Its placer gets the same
 * rule: the houses a plot chooses from lose the taverns once the village has one (or, in another mod's village, if it
 * may have none). Only when that mod is there (its placer moved to {@code impl} in 2.0; both are named). Its list of
 * placed pieces is looked up by name rather than shadowed, so a version of it without that list (or without the
 * method) only goes without the rule and never fails to load.
 */
@Pseudo
@Mixin(targets = {"dev.worldgen.lithostitched.impl.worldgen.structure.AlternateJigsawGenerator$StructurePoolGenerator",
	"dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawGenerator$StructurePoolGenerator"}, remap = false)
abstract class LithostitchedJigsawMixin
{
	@Unique
	private static volatile Field vineandbarrel$pieces;
	@Unique
	private static volatile boolean vineandbarrel$lookedUp;

	@ModifyReturnValue(method = "getPoolElements", at = @At("RETURN"), require = 0)
	private List<StructurePoolElement> vineandbarrel$oneTavernPerVillage(List<StructurePoolElement> houses) {
		Object pieces = vineandbarrel$placedPieces(this);
		return pieces instanceof List<?> placed ? VillageStructures.oneTavernPerVillage(houses, placed) : houses;
	}

	@Unique
	private static Object vineandbarrel$placedPieces(Object generator) {
		if (!vineandbarrel$lookedUp) {
			try {
				Field field = generator.getClass().getDeclaredField("piecesToPlace");
				field.setAccessible(true);
				vineandbarrel$pieces = field;
			} catch (ReflectiveOperationException | RuntimeException e) {
				vineandbarrel$pieces = null;
			}
			vineandbarrel$lookedUp = true;
		}
		Field field = vineandbarrel$pieces;
		if (field == null) {
			return null;
		}
		try {
			return field.get(generator);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
