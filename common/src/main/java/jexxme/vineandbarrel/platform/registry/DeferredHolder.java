package jexxme.vineandbarrel.platform.registry;

import jexxme.vineandbarrel.platform.Services;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * An entry of a {@link DeferredRegister}: its key now, its value once the loader has registered it (looked up in the
 * game's registry and kept). Unlike NeoForge's holder this is not itself a {@link Holder}; {@link #asHolder()} gives
 * the game's own one, which is what anything comparing holders (mob effects!) must use.
 */
public class DeferredHolder<R, T extends R> implements Supplier<T>
{
	private final ResourceKey<R> key;
	private @Nullable T value;
	private @Nullable Holder<R> holder;

	protected DeferredHolder(ResourceKey<R> key) {
		this.key = Objects.requireNonNull(key);
	}

	@Override
	@SuppressWarnings("unchecked")
	public T get() {
		T result = value;
		if (result == null) {
			R found = registry().get(key);
			if (found == null) {
				throw new IllegalStateException("Trying to access unregistered value: " + key);
			}
			result = (T) found;
			value = result;
		}
		return result;
	}

	/** The same as {@link #get()}, for code written against {@link Holder#value()}. */
	public T value() {
		return get();
	}

	public boolean isBound() {
		return value != null || registry().containsKey(key);
	}

	public ResourceLocation getId() {
		return key.location();
	}

	public ResourceKey<R> getKey() {
		return key;
	}

	/**
	 * The game's own holder for this entry, the one the registry hands out everywhere else (so equality checks work).
	 * Safe to call before registration (e.g. in a static field): the loader then gives a holder that the registration
	 * fills in.
	 */
	public Holder<R> asHolder() {
		Holder<R> result = holder;
		if (result == null) {
			result = Services.PLATFORM.holder(key);
			holder = result;
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private Registry<R> registry() {
		Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(key.registry());
		if (registry == null) {
			throw new IllegalStateException("No registry " + key.registry() + " for " + key);
		}
		return registry;
	}

	@Override
	public String toString() {
		return "DeferredHolder{" + key + "}";
	}
}
