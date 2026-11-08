package jexxme.vineandbarrel.platform.hooks;

import net.minecraft.network.Connection;
import net.minecraft.world.level.storage.ValueInput;

/**
 * A block entity that reads its update packet on the client itself (instead of a full {@code loadWithComponents}).
 * The same method as NeoForge's {@code IBlockEntityExtension#onDataPacket}, so on NeoForge it simply overrides that;
 * on Fabric a client mixin calls it.
 */
public interface DataPacketReceiver
{
	void onDataPacket(Connection connection, ValueInput input);
}
