package com.vnmine.hardcore.managers;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Minimal Sponge Schematic v2/v3 reader for vanilla block states. */
final class SpongeSchematicReader {
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    private static final int MAX_NBT_ENTRIES = 2_000_000;

    private SpongeSchematicReader() { }

    static Schematic read(Path path, int maxBlocks) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_BYTES) throw new IOException("schem file size must be 1..64 MiB");
        Map<String, Object> root;
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            int rootType = input.readUnsignedByte();
            if (rootType != 10) throw new IOException("root NBT tag must be a compound");
            input.readUTF();
            root = compound(input, 0);
        } catch (RuntimeException exception) {
            throw new IOException("invalid Sponge schematic NBT", exception);
        }
        Object schematicValue = root.getOrDefault("Schematic", root);
        if (!(schematicValue instanceof Map<?, ?> schematic)) throw new IOException("missing Schematic compound");
        int version = number(schematic.get("Version"), "Version");
        if (version < 2 || version > 3) throw new IOException("only Sponge schematic versions 2 and 3 are supported");
        int width = unsignedShort(schematic.get("Width"), "Width");
        int height = unsignedShort(schematic.get("Height"), "Height");
        int length = unsignedShort(schematic.get("Length"), "Length");
        long volume = (long) width * height * length;
        if (width < 1 || height < 1 || length < 1)
            throw new IOException("invalid schematic dimensions " + width + "x" + height + "x" + length);
        if (volume > maxBlocks)
            throw new IOException("dimensions " + width + "x" + height + "x" + length + " contain "
                + volume + " blocks; configured limit is " + maxBlocks);

        Object blocksValue = schematic.get("Blocks");
        Map<?, ?> blocks = blocksValue instanceof Map<?, ?> map ? map : schematic;
        Object paletteValue = blocks.get("Palette");
        Object dataValue = blocks.containsKey("Data") ? blocks.get("Data") : blocks.get("BlockData");
        if (!(paletteValue instanceof Map<?, ?> palette) || !(dataValue instanceof byte[] encodedData))
            throw new IOException("Blocks.Palette and Blocks.Data are required");
        List<String> paletteStates = new ArrayList<>();
        int paletteSize = 0;
        for (Object value : palette.values()) {
            int paletteIndex = number(value, "palette index");
            if (paletteIndex < 0 || paletteIndex >= maxBlocks) throw new IOException("palette index is out of bounds");
            paletteSize = Math.max(paletteSize, paletteIndex + 1);
        }
        if (paletteSize < 1 || paletteSize > maxBlocks) throw new IOException("invalid palette size");
        for (int i = 0; i < paletteSize; i++) paletteStates.add(null);
        for (Map.Entry<?, ?> entry : palette.entrySet()) {
            if (!(entry.getKey() instanceof String state)) throw new IOException("palette state must be a string");
            int index = number(entry.getValue(), "palette index");
            if (index < 0 || index >= paletteSize || paletteStates.get(index) != null)
                throw new IOException("palette indexes must be unique and non-negative");
            paletteStates.set(index, state);
        }
        List<String> states = new ArrayList<>((int) volume);
        int[] cursor = {0};
        for (int i = 0; i < volume; i++) {
            int index = readVarInt(encodedData, cursor);
            if (index < 0 || index >= paletteStates.size()) throw new IOException("block data references invalid palette index " + index);
            String state = paletteStates.get(index);
            if (state == null) throw new IOException("block data references an undefined palette index " + index);
            states.add(state);
        }
        if (cursor[0] != encodedData.length) throw new IOException("unexpected trailing bytes in block data");
        String name = "Schematic";
        Object metadataValue = schematic.get("Metadata");
        if (metadataValue instanceof Map<?, ?> metadata && metadata.get("Name") instanceof String metadataName
                && !metadataName.isBlank()) name = metadataName;
        return new Schematic(name, width, height, length, List.copyOf(states));
    }

    private static int readVarInt(byte[] bytes, int[] cursor) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            if (cursor[0] >= bytes.length) throw new IOException("truncated block data varint");
            int part = bytes[cursor[0]++] & 0xff;
            value |= (part & 0x7f) << shift;
            if ((part & 0x80) == 0) return value;
        }
        throw new IOException("block data varint is too long");
    }

    private static int unsignedShort(Object value, String name) throws IOException {
        if (!(value instanceof Number number)) throw new IOException(name + " must be an unsigned short");
        return number.intValue() & 0xffff;
    }

    private static int number(Object value, String name) throws IOException {
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue())
            throw new IOException(name + " must be an integer");
        return number.intValue();
    }

    private static Map<String, Object> compound(DataInputStream input, int depth) throws IOException {
        if (depth > 32) throw new IOException("NBT nesting exceeds limit");
        Map<String, Object> values = new LinkedHashMap<>();
        int entries = 0;
        while (true) {
            int type = input.readUnsignedByte();
            if (type == 0) return values;
            if (++entries > MAX_NBT_ENTRIES) throw new IOException("NBT compound contains too many entries");
            String name = input.readUTF();
            values.put(name, payload(input, type, depth + 1));
        }
    }

    private static Object payload(DataInputStream input, int type, int depth) throws IOException {
        return switch (type) {
            case 1 -> input.readByte();
            case 2 -> input.readShort();
            case 3 -> input.readInt();
            case 4 -> input.readLong();
            case 5 -> input.readFloat();
            case 6 -> input.readDouble();
            case 7 -> byteArray(input);
            case 8 -> input.readUTF();
            case 9 -> list(input, depth);
            case 10 -> compound(input, depth);
            case 11 -> intArray(input);
            case 12 -> longArray(input);
            default -> throw new IOException("unsupported NBT tag type " + type);
        };
    }

    private static List<Object> list(DataInputStream input, int depth) throws IOException {
        int elementType = input.readUnsignedByte();
        int length = arrayLength(input);
        List<Object> values = new ArrayList<>(length);
        for (int i = 0; i < length; i++) values.add(payload(input, elementType, depth + 1));
        return values;
    }

    private static int[] intArray(DataInputStream input) throws IOException {
        int[] values = new int[arrayLength(input)];
        for (int i = 0; i < values.length; i++) values[i] = input.readInt();
        return values;
    }

    private static byte[] byteArray(DataInputStream input) throws IOException {
        int length = arrayLength(input);
        byte[] values = input.readNBytes(length);
        if (values.length != length) throw new IOException("truncated NBT byte array");
        return values;
    }

    private static long[] longArray(DataInputStream input) throws IOException {
        long[] values = new long[arrayLength(input)];
        for (int i = 0; i < values.length; i++) values[i] = input.readLong();
        return values;
    }

    private static int arrayLength(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_NBT_ENTRIES) throw new IOException("NBT array/list length is out of bounds");
        return length;
    }

    record Schematic(String name, int width, int height, int length, List<String> blockStates) { }
}
