package jexxme.vineandbarrel.smoke;

import jexxme.vineandbarrel.smoke.mixin.ChunkMapAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;

/**
 * Whether a server still generates chunks. Minecraft 1.21.1 can hang for good in "Saving worlds" when it is stopped
 * while chunks generate (the village step leaves the player in fresh terrain): its stop loop unloads chunks without a
 * time limit, and a chunk that a running generation task still holds is put back in that queue forever, so the task
 * never gets the server thread to finish (MinecraftServer#stopServer, ChunkMap#processUnloads and #scheduleUnload).
 * Later versions don't have this. So the smoke test's servers stop only once generation is done.
 */
final class SmokeQuiet
{
	/** The longest a stop waits for generation to end, in ticks. */
	static final int LIMIT = 20 * 60;

	private SmokeQuiet() {
	}

	/** On the server thread: whether any level has generation queued or a chunk held by a generation task. */
	static boolean generating(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			ChunkMapAccessor chunks = (ChunkMapAccessor) level.getChunkSource().chunkMap;
			if (!chunks.vineandbarrel_smoke$pendingGenerationTasks().isEmpty()) {
				return true;
			}
			for (ChunkHolder holder : chunks.vineandbarrel_smoke$updatingChunkMap().values()) {
				if (holder.getGenerationRefCount() != 0) {
					return true;
				}
			}
		}
		return false;
	}
}
