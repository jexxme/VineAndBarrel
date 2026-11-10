package jexxme.vineandbarrel.platform;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
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
	private @Nullable T testValue;

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

	/** Every setting declared so far, in declaration order (each loader builds its files from these). */
	public static List<ConfigValue<?>> all() {
		return Collections.unmodifiableList(ALL);
	}

	public T get() {
		if (testValue != null) {
			return testValue;
		}
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

	/**
	 * For GameTests: the value {@link #get()} gives until this is called with null, whatever the loader's file says.
	 * A test must not {@link #set} a value and set it back: on Forge that writes the config file twice, and when
	 * Forge's file watcher reloads the file in between, the test's value comes back and stays for every later test.
	 */
	public void setForTest(@Nullable T value) {
		testValue = value;
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
