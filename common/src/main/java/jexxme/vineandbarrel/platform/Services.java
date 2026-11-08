package jexxme.vineandbarrel.platform;

import java.util.ServiceLoader;

/**
 * The loader's implementation of {@link Platform}, found with Java's service loader: each loader project names its
 * class in {@code META-INF/services/jexxme.vineandbarrel.platform.Platform}.
 */
public final class Services
{
	public static final Platform PLATFORM = load(Platform.class);

	private Services() {
	}

	public static <T> T load(Class<T> type) {
		return ServiceLoader.load(type, Services.class.getClassLoader()).findFirst()
			.orElseThrow(() -> new IllegalStateException("No " + type.getName() + " for this loader"));
	}
}
