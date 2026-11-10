import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Adds {@code "refmap": "<name>"} to a mixin config while it is copied into the Forge jar:
 * {@code filter(MixinRefmapFilter, refmap: 'vineandbarrel.refmap.json')}. Forge's Mixin (0.8.5) only reads a refmap its
 * config names; the source file stays without the key, because the shared config is also Fabric's, where Loom adds its own.
 */
class MixinRefmapFilter extends FilterReader {
	/** The refmap's file name in the jar (set by Gradle from the filter's properties). */
	String refmap

	private final Reader source
	private Reader result

	MixinRefmapFilter(Reader source) {
		super(source)
		this.source = source
	}

	private Reader result() {
		if (result == null) {
			if (!refmap) {
				throw new IllegalStateException('MixinRefmapFilter needs a refmap name')
			}
			def json = new JsonSlurper().parseText(source.text)
			if (!(json instanceof Map)) {
				throw new IllegalStateException('Not a mixin config: ' + json)
			}
			def withRefmap = new LinkedHashMap<String, Object>()
			json.each { key, value ->
				withRefmap.put(key as String, value)
				if (key == 'required') {
					withRefmap.put('refmap', refmap)
				}
			}
			withRefmap.put('refmap', refmap)
			result = new StringReader(JsonOutput.prettyPrint(JsonOutput.toJson(withRefmap)) + '\n')
		}
		return result
	}

	@Override
	int read() throws IOException {
		return result().read()
	}

	@Override
	int read(char[] buffer, int offset, int length) throws IOException {
		return result().read(buffer, offset, length)
	}

	@Override
	void close() throws IOException {
		source.close()
	}
}
