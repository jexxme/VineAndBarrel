package jexxme.vineandbarrel.compat.jade;

import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.Optional;

/**
 * Brew Kettle in Jade (server side): sends how far the current batch is to {@link KettleProvider}. Jade 11 (Minecraft
 * 1.20.1) sends server data as one NBT tag shared by all providers, so the progress is a compound under our id.
 */
public enum KettleDataProvider implements IServerDataProvider<BlockAccessor>
{
	INSTANCE;

	private static final String KEY = KettleProvider.UID.toString();

	@Override
	public ResourceLocation getUid() {
		return KettleProvider.UID;
	}

	@Override
	public void appendServerData(CompoundTag data, BlockAccessor accessor) {
		BrewKettleBlockEntity kettle = (BrewKettleBlockEntity) accessor.getBlockEntity();
		CompoundTag progress = new CompoundTag();
		progress.putInt("time", kettle.getBrewTime());
		progress.putInt("total", kettle.getBrewTimeTotal());
		data.put(KEY, progress);
	}

	/** The progress the server sent for the block looked at, if it has sent any. */
	static Optional<Progress> decodeFromData(BlockAccessor accessor) {
		CompoundTag data = accessor.getServerData();
		if (data == null || !data.contains(KEY, Tag.TAG_COMPOUND)) {
			return Optional.empty();
		}
		CompoundTag progress = data.getCompound(KEY);
		return Optional.of(new Progress(progress.getInt("time"), progress.getInt("total")));
	}

	public record Progress(int time, int total)
	{
	}
}
