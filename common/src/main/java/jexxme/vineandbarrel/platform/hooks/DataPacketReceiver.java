package jexxme.vineandbarrel.platform.hooks;

import net.minecraft.network.Connection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * A block entity that reads its update packet on the client itself (instead of a full {@code loadWithComponents}).
 * The same method as NeoForge's {@code IBlockEntityExtension#onDataPacket}, so on NeoForge it simply overrides that;
 * on Fabric a client mixin calls it. On this Minecraft the packet's data is its tag (26.x: a ValueInput).
 */
public interface DataPacketReceiver
{
	void onDataPacket(Connection connection, CompoundTag tag, HolderLookup.Provider registries);
}
