package jexxme.vineandbarrel.kettle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * An unwaxed Brew Kettle: it oxidizes over time like vanilla copper (K8), by vanilla's rules. The next stage is the
 * kettle's own ({@link KettleRegistry#nextStage}): vanilla's table of copper blocks is fixed on this Minecraft, and Forge
 * has no way to add to it (newer NeoForge: its {@code oxidizables} data map). Like vanilla's copper chest,
 * it does not weather while someone has it open.
 */
public class WeatheringBrewKettleBlock extends BrewKettleBlock implements WeatheringCopper
{
	public WeatheringBrewKettleBlock(WeatherState weatherState, BlockBehaviour.Properties properties) {
		super(weatherState, properties);
	}

	@Override
	public boolean isWaxed() {
		return false;
	}

	@Override
	public WeatherState getAge() {
		return getWeatherState();
	}

	@Override
	public Optional<BlockState> getNext(BlockState state) {
		Block next = KettleRegistry.nextStage(this);
		return next == null ? Optional.empty() : Optional.of(next.withPropertiesOf(state));
	}

	@Override
	public boolean isRandomlyTicking(BlockState state) {
		return KettleRegistry.nextStage(this) != null;
	}

	@Override
	public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(OPEN)) {
			this.onRandomTick(state, level, pos, random);
		}
	}
}
