package jexxme.vineandbarrel.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A piece of data stored on an entity: NeoForge's data attachments, Fabric's attachment API. The methods mean exactly
 * what NeoForge's {@code hasData}, {@code getData}, {@code getExistingDataOrNull}, {@code setData} and
 * {@code removeData} mean. Made with {@link Platform#attachment}.
 */
public interface EntityAttachment<T>
{
	/** Whether the entity has this data at all. */
	boolean has(Entity entity);

	/** The entity's value; if it has none, the default is attached and returned. */
	T get(Entity entity);

	@Nullable
	T getExisting(Entity entity);

	void set(Entity entity, T value);

	void remove(Entity entity);

	/**
	 * How an attachment behaves: its default, whether it is saved ({@code serialize}, with the field name inside the
	 * entity's attachment data), whether it is sent to the entity's own client and everyone tracking it ({@code sync}),
	 * and whether a player keeps it through death ({@code copyOnDeath}, saved attachments only).
	 */
	record Spec<T>(Supplier<T> defaultValue, @Nullable MapCodec<T> saveCodec, @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec,
		boolean copyOnDeath)
	{
		public static <T> Builder<T> builder(Supplier<T> defaultValue) {
			return new Builder<>(defaultValue);
		}
	}

	final class Builder<T>
	{
		private final Supplier<T> defaultValue;
		private @Nullable MapCodec<T> saveCodec;
		private @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec;
		private boolean copyOnDeath;

		private Builder(Supplier<T> defaultValue) {
			this.defaultValue = Objects.requireNonNull(defaultValue);
		}

		public Builder<T> serialize(MapCodec<T> codec) {
			this.saveCodec = codec;
			return this;
		}

		public Builder<T> sync(StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
			this.syncCodec = codec;
			return this;
		}

		public Builder<T> copyOnDeath() {
			this.copyOnDeath = true;
			return this;
		}

		public Spec<T> build() {
			if (copyOnDeath && saveCodec == null) {
				throw new IllegalStateException("copyOnDeath needs serialize (as on NeoForge)");
			}
			return new Spec<>(defaultValue, saveCodec, syncCodec, copyOnDeath);
		}
	}
}
