package com.vnmine.hardcore.managers;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Bukkit;

/** Writes Sponge schematic v3 files, including native block entity compounds. */
final class SpongeSchematicWriter {
    private SpongeSchematicWriter() { }

    static void write(Path path, String name, int width, int height, int length, List<Block> blocks) throws IOException {
        if (width < 1 || height < 1 || length < 1 || width > 65535 || height > 65535 || length > 65535)
            throw new IOException("schematic dimensions must be between 1 and 65535");
        long volume = (long) width * height * length;
        if (volume > Integer.MAX_VALUE || blocks.size() != volume) throw new IOException("schematic block array does not match its dimensions");
        Map<String, Integer> palette = new LinkedHashMap<>();
        for (Block block : blocks) palette.computeIfAbsent(block.blockData(), ignored -> palette.size());
        byte[] encoded = encode(blocks, palette);
        List<Object> blockEntities = new ArrayList<>();
        for (Block block : blocks) {
            if (block.blockEntityData() == null || block.blockEntityData().isEmpty()) continue;
            Object id = block.blockEntityData().get("id");
            if (!(id instanceof String identifier)) throw new IOException("tile entity NBT is missing id at " + block.x() + "," + block.y() + "," + block.z());
            Map<String, Object> data = new LinkedHashMap<>(block.blockEntityData());
            data.remove("id"); data.remove("x"); data.remove("y"); data.remove("z");
            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("Id", identifier);
            entity.put("Pos", new int[]{block.x(), block.y(), block.z()});
            entity.put("Data", data);
            blockEntities.add(entity);
        }
        Map<String, Object> paletteNbt = new LinkedHashMap<>();
        palette.forEach(paletteNbt::put);
        Map<String, Object> blocksNbt = new LinkedHashMap<>();
        blocksNbt.put("Palette", paletteNbt);
        blocksNbt.put("Data", encoded);
        blocksNbt.put("BlockEntities", blockEntities);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("Name", name);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 3);
        root.put("DataVersion", Bukkit.getUnsafe().getDataVersion());
        root.put("Width", (short) width); root.put("Height", (short) height); root.put("Length", (short) length);
        root.put("Offset", new int[]{0, 0, 0});
        root.put("Metadata", metadata);
        root.put("Blocks", blocksNbt);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temporary)))) {
            output.writeByte(10); output.writeUTF("Schematic"); writeCompound(output, root);
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        try {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path);
        }
    }

    private static byte[] encode(List<Block> blocks, Map<String, Integer> palette) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (Block block : blocks) {
            int value = palette.get(block.blockData());
            while ((value & ~0x7f) != 0) { bytes.write((value & 0x7f) | 0x80); value >>>= 7; }
            bytes.write(value);
        }
        return bytes.toByteArray();
    }

    private static void writeCompound(DataOutput output, Map<String, ?> values) throws IOException {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            output.writeByte(tagType(value)); output.writeUTF(entry.getKey()); writePayload(output, value);
        }
        output.writeByte(0);
    }

    private static byte tagType(Object value) throws IOException {
        if (value instanceof Byte) return 1;
        if (value instanceof Short) return 2;
        if (value instanceof Integer) return 3;
        if (value instanceof Long) return 4;
        if (value instanceof Float) return 5;
        if (value instanceof Double) return 6;
        if (value instanceof byte[]) return 7;
        if (value instanceof String) return 8;
        if (value instanceof List<?>) return 9;
        if (value instanceof Map<?, ?>) return 10;
        if (value instanceof int[]) return 11;
        if (value instanceof long[]) return 12;
        throw new IOException("unsupported NBT value: " + (value == null ? "null" : value.getClass().getName()));
    }

    private static void writePayload(DataOutput output, Object value) throws IOException {
        if (value instanceof Byte number) output.writeByte(number);
        else if (value instanceof Short number) output.writeShort(number);
        else if (value instanceof Integer number) output.writeInt(number);
        else if (value instanceof Long number) output.writeLong(number);
        else if (value instanceof Float number) output.writeFloat(number);
        else if (value instanceof Double number) output.writeDouble(number);
        else if (value instanceof byte[] array) { output.writeInt(array.length); output.write(array); }
        else if (value instanceof String string) output.writeUTF(string);
        else if (value instanceof int[] array) { output.writeInt(array.length); for (int item : array) output.writeInt(item); }
        else if (value instanceof long[] array) { output.writeInt(array.length); for (long item : array) output.writeLong(item); }
        else if (value instanceof Map<?, ?> map) {
            Map<String, Object> compound = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) compound.put(String.valueOf(entry.getKey()), entry.getValue());
            writeCompound(output, compound);
        } else if (value instanceof List<?> list) {
            int elementType = list.isEmpty() ? 0 : tagType(list.getFirst());
            for (Object item : list) if (tagType(item) != elementType) throw new IOException("NBT list entries must have a consistent type");
            output.writeByte(elementType); output.writeInt(list.size());
            for (Object item : list) writePayload(output, item);
        } else throw new IOException("unsupported NBT payload");
    }

    record Block(int x, int y, int z, String blockData, Map<String, Object> blockEntityData) { }
}
