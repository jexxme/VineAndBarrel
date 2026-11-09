package jexxme.vineandbarrel.kettle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An unwaxed part of a big kettle. The kettle weathers as one vessel: only its controller takes random ticks, with
 * vanilla's once-a-day chance for one copper block, so a big kettle ages no faster than a single one, and every block
 * turns at once (its chimney extension too).
 */
public class WeatheringBrewKettlePartBlock extends BrewKettlePartBlock implements WeatheringCopper
{
	/** Vanilla's chance for one copper block to try weathering on a random tick ({@code ChangeOverTimeBlock}). */
	private static final float CHANCE = 0.05688889F;

	public WeatheringBrewKettlePartBlock(WeatherState weatherState, BlockBehaviour.Properties properties) {
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
		return part(state).isController() && getWeatherState() != WeatherState.OXIDIZED;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		KettleMultiblock.Kettle kettle = KettleMultiblock.kettleOf(state, pos);
		if (kettle == null || random.nextFloat() >= CHANCE) {
			return;
		}
		Block next = KettleRegistry.nextStage(KettleRegistry.singleOf(this));
		if (next != null) {
			KettleMultiblock.restage(level, kettle, next);
		}
	}
}
