// Structure templates for this older-Minecraft branch: see downgradeStructures in vb-loader.gradle.
/** Just enough NBT (gzip, big-endian) to read a structure template and write it back with every tag type kept. */
class BackportNbt
{
    static class Tag { int type; Object value; Tag(int type, Object value) { this.type = type; this.value = value } }
    static class TagList { int elementType; List<Object> items; TagList(int t, List<Object> i) { elementType = t; items = i } }

    /**
     * Writes {@code source} to {@code target} with each palette's block states renamed from {id, properties} to
     * {Name, Properties}, sign text as JSON strings, shelves as item frames or shutters and item stacks in this
     * Minecraft's own form.
     */
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
        // Sign text: 26.3 saves each line as a text component compound, this Minecraft reads a JSON string
        if (root.containsKey('blocks')) {
            (root.get('blocks').value as TagList).items.each { CompoundView block ->
                if (!block.containsKey('nbt')) return
                def nbt = block.get('nbt').value as CompoundView
                ['front_text', 'back_text'].each { side ->
                    if (!nbt.containsKey(side)) return
                    def text = nbt.get(side).value as CompoundView
                    if (!text.containsKey('messages')) return
                    def messages = text.get('messages').value as TagList
                    if (messages.elementType == 10) {
                        messages.items = messages.items.collect { groovy.json.JsonOutput.toJson(plain(it)) }
                        messages.elementType = 8
                    }
                }
            }
        }
        shelvesToItemFrames(root)
        // Item stacks anywhere in block entity and entity data: 26.3 saves {id, count, components}, this Minecraft
        // reads {id, Count (a byte), tag}. The templates' stacks carry no components.
        ['blocks', 'entities'].each { key ->
            if (root.containsKey(key)) {
                (root.get(key).value as TagList).items.each { Object entry ->
                    CompoundView holder = entry as CompoundView
                    if (holder.containsKey('nbt')) oldItemStacks(holder.get('nbt').value)
                }
            }
        }
        target.parentFile.mkdirs()
        write(tag, target)
    }

    /** The marker on the item frames that stand in for shelves; {@code StructureEntityTileMixin} gives them their block position. */
    static final String SHELF_FRAME = 'vineandbarrel_shelf'
    private static final Map<String, Integer> FACING = [down: 0, up: 1, north: 2, south: 3, west: 4, east: 5]
    private static final Map<String, List<Integer>> BEHIND = [north: [0, 0, 1], south: [0, 0, -1], west: [1, 0, 0], east: [-1, 0, 0]]

    /**
     * Shelves (Minecraft 1.21.9) don't exist on this Minecraft. Each one becomes air with an item frame on the wall
     * behind it that shows the shelf's first item. A shelf in front of an opening or a window pane (the taverns' bar
     * walls have windows behind their shelf rows) becomes an open trapdoor of the same wood instead, a closed shutter
     * in the same place, without an item (both the tech lead's call, from pictures).
     */
    private static void shelvesToItemFrames(CompoundView root) {
        if (!root.containsKey('palette') || !root.containsKey('blocks')) return
        TagList palette = root.get('palette').value as TagList
        TagList blocks = root.get('blocks').value as TagList
        List<String> names = palette.items.collect { ((it as CompoundView).get('Name').value) as String }
        if (!names.any { it.endsWith('_shelf') }) return
        int air = names.indexOf('minecraft:air')
        if (air < 0) {
            def state = new LinkedHashMap<String, Tag>()
            state.put('Name', new Tag(8, 'minecraft:air'))
            palette.items << new CompoundView(state)
            names << 'minecraft:air'
            air = names.size() - 1
        }
        def position = { CompoundView block -> (block.get('pos').value as TagList).items.collect { it as int } }
        def solidAt = new HashSet<List<Integer>>()
        blocks.items.each { Object entry ->
            CompoundView block = entry as CompoundView
            String id = names[block.get('state').value as int]
            // glass counts as an opening: a shelf in front of a window covered it, and a frame on a pane looks stuck on
            if (id != 'minecraft:air' && !id.endsWith('_shelf') && !id.contains('glass')) solidAt << position(block)
        }
        List<Object> frames = []
        Map<String, Integer> shutters = [:]
        blocks.items.each { Object entry ->
            CompoundView block = entry as CompoundView
            int stateIndex = block.get('state').value as int
            if (!names[stateIndex].endsWith('_shelf')) return
            CompoundView state = palette.items[stateIndex] as CompoundView
            String facing = ((state.get('Properties').value as CompoundView).get('facing').value) as String
            List<Integer> pos = position(block)
            CompoundView nbt = block.containsKey('nbt') ? block.get('nbt').value as CompoundView : null
            block.put('state', new Tag(3, air))
            block.remove('nbt')
            List<Integer> behind = BEHIND[facing]
            if (behind == null) return
            if (!solidAt.contains([pos[0] + behind[0], pos[1] + behind[1], pos[2] + behind[2]])) {
                // No wall behind it: the shelf itself closed a window opening or covered a window pane. An open
                // trapdoor of the same wood stands where the shelf's back board was, as a shutter.
                String shutter = names[stateIndex].replace('_shelf', '_trapdoor')
                String key = shutter + '/' + facing
                Integer index = shutters.get(key)
                if (index == null) {
                    def properties = new LinkedHashMap<String, Tag>()
                    properties.put('facing', new Tag(8, facing))
                    properties.put('half', new Tag(8, 'bottom'))
                    properties.put('open', new Tag(8, 'true'))
                    properties.put('powered', new Tag(8, 'false'))
                    properties.put('waterlogged', new Tag(8, 'false'))
                    def shutterState = new LinkedHashMap<String, Tag>()
                    shutterState.put('Name', new Tag(8, shutter))
                    shutterState.put('Properties', new Tag(10, new CompoundView(properties)))
                    palette.items << new CompoundView(shutterState)
                    names << shutter
                    index = names.size() - 1
                    shutters.put(key, index)
                }
                block.put('state', new Tag(3, index))
                return
            }
            if (nbt == null || !nbt.containsKey('Items')) return
            List<Object> items = (nbt.get('Items').value as TagList).items.sort(false) { ((it as CompoundView).get('Slot').value) as int }
            if (items.isEmpty()) return
            def item = new LinkedHashMap<String, Tag>()
            item.put('id', (items[0] as CompoundView).get('id'))
            item.put('count', new Tag(3, 1)) // made a Count byte with every other stack below
            def frame = new LinkedHashMap<String, Tag>()
            frame.put('id', new Tag(8, 'minecraft:item_frame'))
            frame.put('Facing', new Tag(1, FACING[facing]))
            frame.put('Item', new Tag(10, new CompoundView(item)))
            frame.put('ItemRotation', new Tag(1, 0))
            frame.put(SHELF_FRAME, new Tag(1, 1))
            def entity = new LinkedHashMap<String, Tag>()
            entity.put('pos', new Tag(9, new TagList(6, pos.collect { (it + 0.5d) as Object })))
            entity.put('blockPos', new Tag(9, new TagList(3, pos.collect { it as Object })))
            entity.put('nbt', new Tag(10, new CompoundView(frame)))
            frames << new CompoundView(entity)
        }
        // No block uses the shelf states any more: they become air too, so the palette names only blocks this Minecraft has
        palette.items.eachWithIndex { Object entry, int i ->
            if (names[i].endsWith('_shelf')) {
                CompoundView state = entry as CompoundView
                state.put('Name', new Tag(8, 'minecraft:air'))
                state.remove('Properties')
            }
        }
        TagList entities = root.containsKey('entities') ? root.get('entities').value as TagList : null
        if (entities == null) {
            entities = new TagList(10, [])
            root.put('entities', new Tag(9, entities))
        }
        entities.elementType = 10
        entities.items.addAll(frames)
    }

    private static void oldItemStacks(Object value) {
        if (value instanceof CompoundView) {
            CompoundView compound = value as CompoundView
            if (compound.containsKey('id') && compound.get('id').type == 8 && compound.containsKey('count') && compound.get('count').type == 3) {
                int count = compound.remove('count').value as int
                compound.put('Count', new Tag(1, count as byte))
            }
            if (compound.containsKey('components') && compound.get('components').type == 10
                    && (compound.get('components').value as CompoundView).@tags.isEmpty()) {
                compound.remove('components')
            }
            compound.@tags.values().each { Tag child -> oldItemStacks(child.value) }
        } else if (value instanceof TagList) {
            (value as TagList).items.each { oldItemStacks(it) }
        }
    }

    /** A tag's value as plain maps, lists and values (for JSON). */
    private static Object plain(Object value) {
        if (value instanceof CompoundView) {
            def map = new LinkedHashMap<String, Object>()
            (value as CompoundView).@tags.each { String name, Tag child -> map.put(name, plain(child.value)) }
            return map
        }
        if (value instanceof TagList) return (value as TagList).items.collect { plain(it) }
        return value
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
