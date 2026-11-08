package jexxme.vineandbarrel.kettle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An unwaxed Brew Kettle: it oxidizes over time like vanilla copper (K8). The next stage comes from NeoForge's
 * {@code oxidizables} data map, so waxing, scraping and lightning all use vanilla's rules. Like vanilla's copper chest,
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
	protected boolean isRandomlyTicking(BlockState state) {
		return WeatheringCopper.getNext(state.getBlock()).isPresent();
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(OPEN)) {
			this.changeOverTime(state, level, pos, random);
		}
	}
}
