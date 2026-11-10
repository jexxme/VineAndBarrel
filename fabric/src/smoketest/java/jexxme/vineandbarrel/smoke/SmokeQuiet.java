package jexxme.vineandbarrel.smoke;

import jexxme.vineandbarrel.smoke.mixin.ChunkMapAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;

/**
 * Whether a server still generates chunks. Minecraft 1.21.1 can hang for good in "Saving worlds" when it is stopped
 * while chunks generate (the village step leaves the player in fresh terrain): its stop loop unloads chunks without a
 * time limit (MinecraftServer#stopServer). This Minecraft has the same stop loop but no generation tasks that hold
 * chunks, so what is asked here is plainer: whether any loaded chunk still waits for a status it was scheduled for.
 * The smoke test's servers stop only once generation is done.
 */
final class SmokeQuiet
{
	/** The longest a stop waits for generation to end, in ticks. */
	static final int LIMIT = 20 * 60;

	private SmokeQuiet() {
	}

	/** On the server thread: whether any level has a chunk with a scheduled status that isn't reached yet. */
	static boolean generating(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			ChunkMapAccessor chunks = (ChunkMapAccessor) level.getChunkSource().chunkMap;
			for (ChunkHolder holder : chunks.vineandbarrel_smoke$updatingChunkMap().values()) {
				if (holder.getAllFutures().stream().anyMatch(status -> status.getSecond() != null && !status.getSecond().isDone())) {
					return true;
				}
			}
		}
		return false;
	}
}
