package jexxme.vineandbarrel.forge.mixin;

import jexxme.vineandbarrel.forge.AttachmentAccess;
import jexxme.vineandbarrel.forge.ForgeAttachments;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Every entity carries its {@link ForgeAttachments.Holder} in a field of its own (see {@link AttachmentAccess}). */
@Mixin(Entity.class)
abstract class EntityMixin implements AttachmentAccess
{
	@Unique
	private ForgeAttachments.@Nullable Holder vineandbarrel$attachmentHolder;

	@Override
	public ForgeAttachments.@Nullable Holder vineandbarrel$attachments() {
		return vineandbarrel$attachmentHolder;
	}

	@Override
	public void vineandbarrel$attachments(ForgeAttachments.Holder holder) {
		vineandbarrel$attachmentHolder = holder;
	}
}
