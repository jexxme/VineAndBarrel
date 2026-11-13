package jexxme.vineandbarrel.platform;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One config setting, declared once in common code ({@code TipsyConfig}) and stored by the loader: NeoForge builds its
 * {@code ModConfigSpec} files from these (same keys, comments and ranges as before), Fabric writes its own file.
 * Until the loader has loaded the file, {@link #get()} gives the default, so callers never have to care.
 */
public final class ConfigValue<T>
{
	/** Server settings are per world and sent to every client; client settings are each player's own. */
	public enum Side
	{
		SERVER, CLIENT
	}

	private static final List<ConfigValue<?>> ALL = new ArrayList<>();

	private final Side side;
	private final String section;
	private final String name;
	private final String comment;
	private final T defaultValue;
	private final @Nullable T min;
	private final @Nullable T max;
	private @Nullable Supplier<T> getter;
	private @Nullable Consumer<T> setter;
	private @Nullable T unboundValue;
	/** The key this setting had before it was renamed, and how its old value reads as this one's (null: never renamed). */
	private @Nullable String formerName;
	private @Nullable Function<String, @Nullable String> fromFormer;

	private ConfigValue(Side side, String section, String name, String comment, T defaultValue, @Nullable T min, @Nullable T max) {
		this.side = side;
		this.section = section;
		this.name = name;
		this.comment = comment;
		this.defaultValue = Objects.requireNonNull(defaultValue);
		this.min = min;
		this.max = max;
		ALL.add(this);
	}

	public static ConfigValue<Boolean> define(Side side, String section, String name, String comment, boolean defaultValue) {
		return new ConfigValue<>(side, section, name, comment, defaultValue, null, null);
	}

	public static ConfigValue<Integer> defineInRange(Side side, String section, String name, String comment, int defaultValue, int min, int max) {
		return new ConfigValue<>(side, section, name, comment, defaultValue, min, max);
	}

	public static ConfigValue<Double> defineInRange(Side side, String section, String name, String comment, double defaultValue, double min, double max) {
		return new ConfigValue<>(side, section, name, comment, defaultValue, min, max);
	}

	/**
	 * This setting used to be called {@code formerName}. A file that still has the old key and not this one gets its
	 * value from the old one, once, through {@code convert} (old text in, this setting's text out, null when the old
	 * value says nothing useful); the loader then writes the file with the new key only.
	 */
	public ConfigValue<T> formerly(String formerName, Function<String, @Nullable String> convert) {
		this.formerName = formerName;
		this.fromFormer = convert;
		return this;
	}

	/** {@return the key this setting had before it was renamed, or null} */
	public @Nullable String formerName() {
		return formerName;
	}

	/**
	 * {@return the text of this setting's value in a saved file, by its key ({@code key} turns a name into the file's
	 * key): its own when the file has it, else its old key's value converted, else null (the default applies)}
	 */
	public @Nullable String savedText(Function<String, @Nullable String> savedByName) {
		String own = savedByName.apply(name);
		if (own != null || formerName == null || fromFormer == null) {
			return own;
		}
		String old = savedByName.apply(formerName);
		return old == null ? null : fromFormer.apply(old.trim());
	}

	/** Every setting declared so far, in declaration order (each loader builds its files from these). */
	public static List<ConfigValue<?>> all() {
		return Collections.unmodifiableList(ALL);
	}

	public T get() {
		if (getter != null) {
			return getter.get();
		}
		return unboundValue != null ? unboundValue : defaultValue;
	}

	public void set(T value) {
		if (setter != null) {
			setter.accept(value);
		} else {
			unboundValue = value;
		}
	}

	public T getDefault() {
		return defaultValue;
	}

	/** Called once by the loader: where the value really lives from now on. */
	public void bind(Supplier<T> getter, Consumer<T> setter) {
		this.getter = getter;
		this.setter = setter;
	}

	public Side side() {
		return side;
	}

	public String section() {
		return section;
	}

	public String name() {
		return name;
	}

	public String comment() {
		return comment;
	}

	public @Nullable T min() {
		return min;
	}

	public @Nullable T max() {
		return max;
	}

	@Override
	public String toString() {
		return section + "." + name + "=" + get();
	}
}
