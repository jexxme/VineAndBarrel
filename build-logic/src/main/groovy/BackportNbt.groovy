// Structure templates for this older-Minecraft branch: see downgradeStructures in vb-loader.gradle.
/** Just enough NBT (gzip, big-endian) to read a structure template and write it back with every tag type kept. */
class BackportNbt
{
    static class Tag { int type; Object value; Tag(int type, Object value) { this.type = type; this.value = value } }
    static class TagList { int elementType; List<Object> items; TagList(int t, List<Object> i) { elementType = t; items = i } }

    /** Writes {@code source} to {@code target} with each palette's block states renamed from {id, properties} to {Name, Properties}. */
    static void downgrade(File source, File target) {
        def tag = read(source)
        def root = tag.value as CompoundView
        def palettes = []
        if (root.containsKey('palette')) palettes << root.get('palette').value
        if (root.containsKey('palettes')) palettes.addAll((root.get('palettes').value as TagList).items)
        palettes.each { TagList palette ->
            palette.items.each { CompoundView state ->
                if (state.containsKey('id') && !state.containsKey('Name')) state.put('Name', state.remove('id'))
                if (state.containsKey('properties') && !state.containsKey('Properties')) state.put('Properties', state.remove('properties'))
            }
        }
        target.parentFile.mkdirs()
        write(tag, target)
    }

    static Tag read(File file) {
        new DataInputStream(new BufferedInputStream(new java.util.zip.GZIPInputStream(new FileInputStream(file)))).withCloseable { input ->
            int type = input.readUnsignedByte()
            input.readUTF()
            new Tag(type, payload(input, type))
        }
    }

    static void write(Tag tag, File file) {
        new DataOutputStream(new BufferedOutputStream(new java.util.zip.GZIPOutputStream(new FileOutputStream(file)))).withCloseable { output ->
            output.writeByte(tag.type)
            output.writeUTF('')
            writePayload(output, tag.type, tag.value)
        }
    }

    // Compounds are CompoundViews (name -> Tag), lists are TagLists
    private static Object payload(DataInputStream input, int type) {
        switch (type) {
            case 1: return input.readByte()
            case 2: return input.readShort()
            case 3: return input.readInt()
            case 4: return input.readLong()
            case 5: return input.readFloat()
            case 6: return input.readDouble()
            case 7: def bytes = new byte[input.readInt()]; input.readFully(bytes); return bytes
            case 8: return input.readUTF()
            case 9:
                int elementType = input.readUnsignedByte()
                int count = input.readInt()
                def items = []
                count.times { items << payload(input, elementType) }
                return new TagList(elementType, items)
            case 10:
                def map = new LinkedHashMap<String, Tag>()
                while (true) {
                    int childType = input.readUnsignedByte()
                    if (childType == 0) return new CompoundView(map)
                    String name = input.readUTF()
                    map.put(name, new Tag(childType, payload(input, childType)))
                }
            case 11: int n = input.readInt(); def ints = new int[n]; n.times { ints[it] = input.readInt() }; return ints
            case 12: int n = input.readInt(); def longs = new long[n]; n.times { longs[it] = input.readLong() }; return longs
            default: throw new org.gradle.api.GradleException("Unknown NBT tag type ${type}")
        }
    }

    private static void writePayload(DataOutputStream output, int type, Object value) {
        switch (type) {
            case 1: output.writeByte(value as byte); break
            case 2: output.writeShort(value as short); break
            case 3: output.writeInt(value as int); break
            case 4: output.writeLong(value as long); break
            case 5: output.writeFloat(value as float); break
            case 6: output.writeDouble(value as double); break
            case 7: byte[] bytes = value as byte[]; output.writeInt(bytes.length); output.write(bytes); break
            case 8: output.writeUTF(value as String); break
            case 9:
                TagList list = value as TagList
                output.writeByte(list.elementType)
                output.writeInt(list.items.size())
                list.items.each { writePayload(output, list.elementType, it) }
                break
            case 10:
                (value as CompoundView).@tags.each { String name, Tag child ->
                    output.writeByte(child.type)
                    output.writeUTF(name)
                    writePayload(output, child.type, child.value)
                }
                output.writeByte(0)
                break
            case 11: int[] ints = value as int[]; output.writeInt(ints.length); ints.each { output.writeInt(it) }; break
            case 12: long[] longs = value as long[]; output.writeInt(longs.length); longs.each { output.writeLong(it) }; break
            default: throw new org.gradle.api.GradleException("Unknown NBT tag type ${type}")
        }
    }

    /** A compound; renaming a key keeps its tag (type and value). */
    static class CompoundView extends AbstractMap<String, Tag>
    {
        final LinkedHashMap<String, Tag> tags
        CompoundView(LinkedHashMap<String, Tag> tags) { this.tags = tags }
        @Override Set<Map.Entry<String, Tag>> entrySet() { this.@tags.entrySet() }
        @Override Tag put(String key, Tag value) { this.@tags.put(key, value) }
        @Override Tag remove(Object key) { this.@tags.remove(key) }
        @Override boolean containsKey(Object key) { this.@tags.containsKey(key) }
        @Override Tag get(Object key) { this.@tags.get(key) }
    }
}
