package com.vnmine.hardcore.managers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads block palettes and packed block data from Litematica's compressed NBT format. */
final class LitematicReader {
    private LitematicReader() { }

    static Schematic read(Path path, int maxBlocks) throws IOException {
        Map<String, Object> root = SpongeSchematicReader.readRoot(path);
        Object metadataValue = root.get("Metadata");
        String name = "Litematic";
        if (metadataValue instanceof Map<?, ?> metadata && metadata.get("Name") instanceof String label && !label.isBlank()) name = label;
        Object regionsValue = root.get("Regions");
        if (!(regionsValue instanceof Map<?, ?> regions) || regions.isEmpty()) throw new IOException("litematic has no regions");

        List<Region> decoded = new ArrayList<>();
        long volume = 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map.Entry<?, ?> entry : regions.entrySet()) {
            if (!(entry.getKey() instanceof String regionName) || !(entry.getValue() instanceof Map<?, ?> region))
                throw new IOException("invalid litematic region");
            int[] position = vector(region.get("Position"), "region Position");
            int[] signedSize = vector(region.get("Size"), "region Size");
            int sx = safeAbs(signedSize[0]), sy = safeAbs(signedSize[1]), sz = safeAbs(signedSize[2]);
            if (sx < 1 || sy < 1 || sz < 1) throw new IOException("invalid dimensions in region " + regionName);
            long regionVolume = (long) sx * sy * sz;
            volume += regionVolume;
            if (volume > Integer.MAX_VALUE || maxBlocks > 0 && volume > maxBlocks)
                throw new IOException("litematic block count exceeds configured limit: " + volume);
            int endX = position[0] + Integer.signum(signedSize[0]) * (sx - 1);
            int endY = position[1] + Integer.signum(signedSize[1]) * (sy - 1);
            int endZ = position[2] + Integer.signum(signedSize[2]) * (sz - 1);
            minX = Math.min(minX, Math.min(position[0], endX)); minY = Math.min(minY, Math.min(position[1], endY));
            minZ = Math.min(minZ, Math.min(position[2], endZ));
            maxX = Math.max(maxX, Math.max(position[0], endX)); maxY = Math.max(maxY, Math.max(position[1], endY));
            maxZ = Math.max(maxZ, Math.max(position[2], endZ));
            List<String> palette = palette(region.get("BlockStatePalette"), regionName);
            Object statesValue = region.get("BlockStates");
            long[] packed = statesValue instanceof long[] values ? values : new long[0];
            decoded.add(new Region(regionName, position, signedSize, sx, sy, sz, palette, packed,
                blockEntities(region.get("TileEntities"), region.get("BlockEntities"), regionName)));
        }
        long boundsVolume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (boundsVolume > Integer.MAX_VALUE || maxBlocks > 0 && boundsVolume > (long) maxBlocks * 8L)
            throw new IOException("litematic regions are too far apart to combine safely: " + boundsVolume + " blocks of bounds");

        List<SchematicBlock> blocks = new ArrayList<>((int) Math.min(volume, Integer.MAX_VALUE));
        List<BlockEntity> blockEntities = new ArrayList<>();
        for (Region region : decoded) {
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(region.palette.size() - 1));
            long mask = (1L << bits) - 1L;
            long expectedLongs = ((long) region.sx * region.sy * region.sz * bits + 63L) / 64L;
            if (region.packed.length < expectedLongs) throw new IOException("truncated BlockStates in region " + region.name);
            for (int index = 0; index < region.sx * region.sy * region.sz; index++) {
                int paletteIndex = packedIndex(region.packed, index, bits, mask);
                if (paletteIndex < 0 || paletteIndex >= region.palette.size())
                    throw new IOException("invalid palette index in region " + region.name);
                String state = region.palette.get(paletteIndex);
                if ("minecraft:air".equals(state) || "minecraft:cave_air".equals(state) || "minecraft:void_air".equals(state)) continue;
                int localX = index % region.sx;
                int localZ = index / region.sx % region.sz;
                int localY = index / (region.sx * region.sz);
                int x = region.position[0] + Integer.signum(region.signedSize[0]) * localX - minX;
                int y = region.position[1] + Integer.signum(region.signedSize[1]) * localY - minY;
                int z = region.position[2] + Integer.signum(region.signedSize[2]) * localZ - minZ;
                blocks.add(new SchematicBlock(x, y, z, state));
            }
            for (BlockEntity entity : region.blockEntities) {
                int x = region.position[0] + Integer.signum(region.signedSize[0]) * entity.x - minX;
                int y = region.position[1] + Integer.signum(region.signedSize[1]) * entity.y - minY;
                int z = region.position[2] + Integer.signum(region.signedSize[2]) * entity.z - minZ;
                blockEntities.add(new BlockEntity(x, y, z, entity.data));
            }
        }
        return new Schematic(name, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1,
            List.copyOf(blocks), List.copyOf(blockEntities));
    }

    private static List<BlockEntity> blockEntities(Object tileValue, Object blockValue, String region) throws IOException {
        Object value = tileValue instanceof List<?> ? tileValue : blockValue;
        if (!(value instanceof List<?> list)) return List.of();
        List<BlockEntity> result = new ArrayList<>();
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> entity)) throw new IOException("invalid block entity in region " + region);
            int[] pos;
            if (entity.get("Pos") instanceof int[] coords && coords.length == 3) pos = coords;
            else pos = new int[]{number(entity.get("x"), "block entity x"), number(entity.get("y"), "block entity y"),
                number(entity.get("z"), "block entity z")};
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            Object nested = entity.get("Data");
            Map<?, ?> payload = nested instanceof Map<?, ?> map ? map : entity;
            for (Map.Entry<?, ?> tag : payload.entrySet()) if (tag.getKey() instanceof String key
                    && !"Pos".equals(key) && !"Data".equals(key)) data.put(key, tag.getValue());
            if (entity.get("Id") instanceof String id) data.put("id", id);
            if (!data.isEmpty()) result.add(new BlockEntity(pos[0], pos[1], pos[2], Map.copyOf(data)));
        }
        return List.copyOf(result);
    }

    private static int packedIndex(long[] data, int index, int bits, long mask) {
        long bitIndex = (long) index * bits;
        int word = (int) (bitIndex >>> 6);
        int offset = (int) (bitIndex & 63L);
        long value = data[word] >>> offset;
        if (offset + bits > 64) value |= data[word + 1] << (64 - offset);
        return (int) (value & mask);
    }

    private static List<String> palette(Object value, String region) throws IOException {
        if (!(value instanceof List<?> entries) || entries.isEmpty()) throw new IOException("missing palette in region " + region);
        List<String> states = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> block) || !(block.get("Name") instanceof String name))
                throw new IOException("invalid palette entry in region " + region);
            Object propsValue = block.get("Properties");
            if (propsValue instanceof Map<?, ?> props && !props.isEmpty()) {
                List<String> properties = new ArrayList<>();
                for (Map.Entry<?, ?> property : props.entrySet()) {
                    if (!(property.getKey() instanceof String key) || !(property.getValue() instanceof String propertyValue))
                        throw new IOException("invalid block property in region " + region);
                    properties.add(key + "=" + propertyValue);
                }
                states.add(name + "[" + String.join(",", properties) + "]");
            } else states.add(name);
        }
        return List.copyOf(states);
    }

    private static int[] vector(Object value, String label) throws IOException {
        if (!(value instanceof Map<?, ?> map)) throw new IOException("missing " + label);
        return new int[]{number(map.get("x"), label), number(map.get("y"), label), number(map.get("z"), label)};
    }

    private static int number(Object value, String label) throws IOException {
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) throw new IOException("invalid " + label);
        return number.intValue();
    }

    private static int safeAbs(int value) throws IOException {
        if (value == Integer.MIN_VALUE) throw new IOException("invalid region size");
        return Math.abs(value);
    }

    record Schematic(String name, int width, int height, int length, List<SchematicBlock> blocks,
                     List<BlockEntity> blockEntities) { }
    record SchematicBlock(int x, int y, int z, String blockData) { }
    record BlockEntity(int x, int y, int z, Map<String, Object> data) { }
    private record Region(String name, int[] position, int[] signedSize, int sx, int sy, int sz,
                          List<String> palette, long[] packed, List<BlockEntity> blockEntities) { }
}
