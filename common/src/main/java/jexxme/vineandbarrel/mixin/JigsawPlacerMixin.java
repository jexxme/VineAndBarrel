package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import jexxme.vineandbarrel.world.village.VillageStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * One tavern per village. A village is laid out plot by plot, each plot drawing from the house pool on its own, so
 * nothing in the game stops a second tavern: a village with many roomy plots (a mod's sprawling town, or a lucky vanilla
 * one) got several. Once a village has its tavern, the taverns leave the houses its other plots choose from.
 *
 * <p>The value is modified, not the call redirected, so mods that hook the same call chain with it. Not required: if
 * another mod replaces the village layout altogether, taverns go uncapped there instead of the game failing to start.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class JigsawPlacerMixin
{
	@Shadow
	@Final
	private List<? super PoolElementStructurePiece> pieces;

	@ModifyExpressionValue(method = "tryPlacingChildren", require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;getShuffledTemplates(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
	private List<StructurePoolElement> vineandbarrel$oneTavernPerVillage(List<StructurePoolElement> houses) {
		return VillageStructures.oneTavernPerVillage(houses, this.pieces);
	}
}
