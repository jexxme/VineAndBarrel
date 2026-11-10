package jexxme.vineandbarrel.forge;

import org.jspecify.annotations.Nullable;

/**
 * Every entity's own {@link ForgeAttachments.Holder} (added to {@code Entity} by {@code EntityMixin}): a plain field, so
 * the data can be read whenever NeoForge's attachments could, also on an entity that has been removed (Forge hands out
 * no capability of a removed entity).
 */
public interface AttachmentAccess
{
	ForgeAttachments.@Nullable Holder vineandbarrel$attachments();

	void vineandbarrel$attachments(ForgeAttachments.Holder holder);
}
