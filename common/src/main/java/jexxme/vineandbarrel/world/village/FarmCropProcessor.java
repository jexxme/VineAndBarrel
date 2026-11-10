package jexxme.vineandbarrel.world.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Turns some of a village farm's wheat into our crops: each rule in order takes {@code chance} of the wheat still left.
 * <p>
 * Not a vanilla {@code RuleProcessor} on purpose (as FD's is): a rule processor seeds its random from the block
 * position alone, so a second one on the same farm replays the numbers vanilla's farm processor already drew. Wheat
 * that survived vanilla's rules drew at least 0.3, 0.2 and 0.1, so lower chances after it never hit at all. This one
 * draws from its own salted seed: independent of vanilla's (and FD's) rules, and still the same for the same block.
 */
public final class FarmCropProcessor extends StructureProcessor
{
	public record Rule(BlockState output, float chance) {
		static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockState.CODEC.fieldOf("output_state").forGetter(Rule::output),
			Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(Rule::chance)
		).apply(i, Rule::new));
	}

	public static final Codec<FarmCropProcessor> CODEC = RecordCodecBuilder.create(i -> i.group(
		Rule.CODEC.listOf().fieldOf("rules").forGetter(FarmCropProcessor::rules)
	).apply(i, FarmCropProcessor::new));

	private static final long SALT = 0x5649_4E45_4241_524CL; // "VINEBARL"

	private final List<Rule> rules;

	public FarmCropProcessor(List<Rule> rules) {
		this.rules = List.copyOf(rules);
	}

	public List<Rule> rules() {
		return rules;
	}

	@Override
	public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level, BlockPos targetPosition, BlockPos referencePos,
		StructureTemplate.StructureBlockInfo originalBlockInfo, StructureTemplate.StructureBlockInfo processedBlockInfo, StructurePlaceSettings settings) {
		if (!processedBlockInfo.state().is(Blocks.WHEAT)) {
			return processedBlockInfo;
		}
		BlockState crop = pick(processedBlockInfo.pos());
		return crop == null ? processedBlockInfo : new StructureTemplate.StructureBlockInfo(processedBlockInfo.pos(), crop, null);
	}

	/** What the wheat at this spot of the world turns into, or null when it stays wheat: always the same for the same spot. */
	public @Nullable BlockState pick(BlockPos pos) {
		RandomSource random = RandomSource.create(Mth.getSeed(pos) ^ SALT);
		for (Rule rule : rules) {
			if (random.nextFloat() < rule.chance()) {
				return rule.output();
			}
		}
		return null;
	}

	@Override
	protected StructureProcessorType<?> getType() {
		return VillageStructures.FARM_CROP_PROCESSOR.get();
	}
}
