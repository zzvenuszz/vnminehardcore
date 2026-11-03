package com.vnmine.hardcore.managers;

import java.io.DataInputStream;
import java.io.EOFException;
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
    private static final int MAX_NBT_ARRAY_LENGTH = 16_000_000;

    private SpongeSchematicReader() { }

    static Schematic read(Path path, int maxBlocks) throws IOException {
        validateDimensionsBeforePayload(path, maxBlocks);
        Map<String, Object> root = readRoot(path);
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
        if (volume > Integer.MAX_VALUE)
            throw new IOException("schematic contains too many blocks for this server to load safely: " + volume);
        if (maxBlocks > 0 && volume > maxBlocks)
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
            if (paletteIndex < 0 || maxBlocks > 0 && paletteIndex >= maxBlocks)
                throw new IOException("palette index is out of bounds");
            paletteSize = Math.max(paletteSize, paletteIndex + 1);
        }
        if (paletteSize < 1 || maxBlocks > 0 && paletteSize > maxBlocks)
            throw new IOException("invalid palette size");
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
        List<BlockEntity> blockEntities = readBlockEntities(schematic, blocks);
        return new Schematic(name, width, height, length, List.copyOf(states), blockEntities);
    }

    private static List<BlockEntity> readBlockEntities(Map<?, ?> schematic, Map<?, ?> blocks) throws IOException {
        Object entitiesValue = blocks.get("BlockEntities");
        if (!(entitiesValue instanceof List<?>)) entitiesValue = schematic.get("TileEntities");
        if (!(entitiesValue instanceof List<?> entities)) return List.of();
        List<BlockEntity> result = new ArrayList<>(entities.size());
        for (Object value : entities) {
            if (!(value instanceof Map<?, ?> entity)) throw new IOException("block entity must be a compound");
            int[] position;
            Object posValue = entity.get("Pos");
            if (posValue instanceof int[] coords && coords.length == 3) position = coords;
            else position = new int[]{number(entity.get("x"), "block entity x"), number(entity.get("y"), "block entity y"),
                number(entity.get("z"), "block entity z")};
            Map<String, Object> data = new LinkedHashMap<>();
            Object dataValue = entity.get("Data");
            Map<?, ?> payload = dataValue instanceof Map<?, ?> map ? map : entity;
            for (Map.Entry<?, ?> entry : payload.entrySet()) {
                if (entry.getKey() instanceof String key && !"Pos".equals(key) && !"Data".equals(key))
                    data.put(key, entry.getValue());
            }
            if (entity.get("Id") instanceof String id) data.put("id", id);
            if (data.isEmpty()) throw new IOException("block entity has no data");
            result.add(new BlockEntity(position[0], position[1], position[2], Map.copyOf(data)));
        }
        return List.copyOf(result);
    }

    static Map<String, Object> readRoot(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_BYTES) throw new IOException("schematic file size must be 1..64 MiB");
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            int rootType = input.readUnsignedByte();
            if (rootType != 10) throw new IOException("root NBT tag must be a compound");
            input.readUTF();
            return compound(input, 0);
        } catch (RuntimeException exception) {
            throw new IOException("invalid schematic NBT", exception);
        }
    }

    private static void validateDimensionsBeforePayload(Path path, int maxBlocks) throws IOException {
        if (maxBlocks <= 0) return;
        long fileSize = Files.size(path);
        if (fileSize <= 0 || fileSize > MAX_FILE_BYTES) throw new IOException("schematic file size must be 1..64 MiB");
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            if (input.readUnsignedByte() != 10) return;
            input.readUTF();
            int[] dimensions = new int[3];
            scanDimensionCompound(input, dimensions, maxBlocks, true, 0);
        } catch (EOFException ignored) {
            // The regular reader reports the exact malformed-NBT error.
        }
    }

    private static void scanDimensionCompound(DataInputStream input, int[] dimensions, int maxBlocks,
                                               boolean isSchematicRoot, int depth) throws IOException {
        if (depth > 32) throw new IOException("NBT nesting exceeds limit");
        while (true) {
            int type = input.readUnsignedByte();
            if (type == 0) return;
            String name = input.readUTF();
            int dimension = dimensionIndex(name);
            if (isSchematicRoot && dimension >= 0 && (type == 2 || type == 3)) {
                dimensions[dimension] = type == 2 ? input.readShort() & 0xffff : input.readInt();
                if (dimensions[0] > 0 && dimensions[1] > 0 && dimensions[2] > 0) {
                    long volume = (long) dimensions[0] * dimensions[1] * dimensions[2];
                    if (volume > maxBlocks) throw new IOException("dimensions " + dimensions[0] + "x" + dimensions[1]
                        + "x" + dimensions[2] + " contain " + volume + " blocks; configured limit is " + maxBlocks);
                    return;
                }
            } else if (type == 10 && "Schematic".equalsIgnoreCase(name)) {
                scanDimensionCompound(input, dimensions, maxBlocks, true, depth + 1);
                if (dimensions[0] > 0 && dimensions[1] > 0 && dimensions[2] > 0) return;
            } else {
                skipPayload(input, type, depth + 1);
            }
        }
    }

    private static int dimensionIndex(String name) {
        return switch (name) {
            case "Width" -> 0;
            case "Height" -> 1;
            case "Length" -> 2;
            default -> -1;
        };
    }

    private static void skipPayload(DataInputStream input, int type, int depth) throws IOException {
        if (depth > 32) throw new IOException("NBT nesting exceeds limit");
        switch (type) {
            case 1 -> input.skipNBytes(1);
            case 2 -> input.skipNBytes(2);
            case 3, 5 -> input.skipNBytes(4);
            case 4, 6 -> input.skipNBytes(8);
            case 7 -> input.skipNBytes(arrayLength(input, MAX_NBT_ARRAY_LENGTH));
            case 8 -> input.readUTF();
            case 9 -> {
                int elementType = input.readUnsignedByte();
                int length = collectionLength(input);
                for (int i = 0; i < length; i++) skipPayload(input, elementType, depth + 1);
            }
            case 10 -> {
                while (true) {
                    int childType = input.readUnsignedByte();
                    if (childType == 0) break;
                    input.readUTF();
                    skipPayload(input, childType, depth + 1);
                }
            }
            case 11 -> input.skipNBytes(Math.multiplyExact((long) arrayLength(input, MAX_NBT_ARRAY_LENGTH), 4L));
            case 12 -> input.skipNBytes(Math.multiplyExact((long) arrayLength(input, MAX_NBT_ARRAY_LENGTH), 8L));
            default -> throw new IOException("unsupported NBT tag type " + type);
        }
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
        int length = collectionLength(input);
        List<Object> values = new ArrayList<>(length);
        for (int i = 0; i < length; i++) values.add(payload(input, elementType, depth + 1));
        return values;
    }

    private static int[] intArray(DataInputStream input) throws IOException {
        int[] values = new int[arrayLength(input, MAX_NBT_ARRAY_LENGTH)];
        for (int i = 0; i < values.length; i++) values[i] = input.readInt();
        return values;
    }

    private static byte[] byteArray(DataInputStream input) throws IOException {
        int length = arrayLength(input, MAX_NBT_ARRAY_LENGTH);
        byte[] values = input.readNBytes(length);
        if (values.length != length) throw new IOException("truncated NBT byte array");
        return values;
    }

    private static long[] longArray(DataInputStream input) throws IOException {
        long[] values = new long[arrayLength(input, MAX_NBT_ARRAY_LENGTH)];
        for (int i = 0; i < values.length; i++) values[i] = input.readLong();
        return values;
    }

    private static int collectionLength(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_NBT_ENTRIES) throw new IOException("NBT array/list length is out of bounds");
        return length;
    }

    private static int arrayLength(DataInputStream input, int maximum) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maximum)
            throw new IOException("NBT primitive array length " + length + " exceeds safety limit " + maximum);
        return length;
    }

    record Schematic(String name, int width, int height, int length, List<String> blockStates,
                     List<BlockEntity> blockEntities) { }
    record BlockEntity(int x, int y, int z, Map<String, Object> data) { }
}
