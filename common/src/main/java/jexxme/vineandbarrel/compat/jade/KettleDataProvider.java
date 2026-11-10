package jexxme.vineandbarrel.compat.jade;

import io.netty.buffer.ByteBuf;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.StreamServerDataProvider;

/** Brew Kettle in Jade (server side): sends how far the current batch is to {@link KettleProvider}. */
public enum KettleDataProvider implements StreamServerDataProvider<BlockAccessor, KettleDataProvider.Progress>
{
	INSTANCE;

	@Override
	public ResourceLocation getUid() {
		return KettleProvider.UID;
	}

	@Override
	public Progress streamData(BlockAccessor accessor) {
		BrewKettleBlockEntity kettle = (BrewKettleBlockEntity) accessor.getBlockEntity();
		return new Progress(kettle.getBrewTime(), kettle.getBrewTimeTotal());
	}

	@Override
	public StreamCodec<RegistryFriendlyByteBuf, Progress> streamCodec() {
		return Progress.STREAM_CODEC.cast();
	}

	public record Progress(int time, int total)
	{
		static final StreamCodec<ByteBuf, Progress> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Progress::time,
			ByteBufCodecs.VAR_INT, Progress::total,
			Progress::new);
	}
}
