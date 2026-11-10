package jexxme.vineandbarrel.forge.client;

import jexxme.vineandbarrel.forge.ForgeAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/** The client's end of {@link ForgeAttachments}' sync packet (its own class: a dedicated server never loads it). */
public final class ClientAttachments
{
	private ClientAttachments() {
	}

	/** An entity the client does not know (it left sight meanwhile) is skipped. */
	public static void receive(ForgeAttachments.Sync sync) {
		ClientLevel level = Minecraft.getInstance().level;
		Entity entity = level == null ? null : level.getEntity(sync.entityId());
		if (entity != null) {
			ForgeAttachments.apply(entity, sync);
		}
	}
}
