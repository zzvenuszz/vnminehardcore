package com.vnmine.hardcore.managers;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;

/** Bridges Bukkit tile snapshots and the server's NBT tags without a compile-time NMS dependency. */
final class NativeBlockEntityNbt {
    private NativeBlockEntityNbt() { }

    static Map<String, Object> capture(BlockState state) {
        if (!(state instanceof TileState)) return Map.of();
        try {
            Object tag = state.getClass().getMethod("getSnapshotNBT").invoke(state);
            Object value = toJava(tag);
            if (!(value instanceof Map<?, ?> map)) throw new IllegalStateException("Tile snapshot NBT is not a compound.");
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, nested) -> result.put(String.valueOf(key), nested));
            return Map.copyOf(result);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Paper could not export tile entity NBT.", exception);
        }
    }

    static void apply(BlockState state, Map<String, Object> data, int x, int y, int z) {
        if (!(state instanceof TileState) || data == null || data.isEmpty()) return;
        Map<String, Object> relocated = new LinkedHashMap<>(data);
        relocated.put("x", x); relocated.put("y", y); relocated.put("z", z);
        try {
            Class<?> compoundType = Class.forName("net.minecraft.nbt.CompoundTag");
            Class<?> parserType = Class.forName("net.minecraft.nbt.TagParser");
            Object compound = invokeStaticFirst(parserType, new String[] {"parseCompoundFully", "parseTag"}, toSnbt(relocated));
            Method loadData = state.getClass().getMethod("loadData", compoundType);
            loadData.invoke(state, compound);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Paper could not apply tile entity NBT.", exception);
        }
    }

    static String canonical(Map<String, Object> data) { return toSnbt(data == null ? Map.of() : data); }

    private static Object toJava(Object tag) throws ReflectiveOperationException {
        int id = ((Number) tag.getClass().getMethod("getId").invoke(tag)).intValue();
        return switch (id) {
            case 1 -> numeric(tag, "getAsByte", "byteValue");
            case 2 -> numeric(tag, "getAsShort", "shortValue");
            case 3 -> numeric(tag, "getAsInt", "intValue");
            case 4 -> numeric(tag, "getAsLong", "longValue");
            case 5 -> numeric(tag, "getAsFloat", "floatValue");
            case 6 -> numeric(tag, "getAsDouble", "doubleValue");
            case 7 -> invokeFirst(tag, new String[] {"getAsByteArray", "asByteArray"});
            case 8 -> invokeFirst(tag, new String[] {"getAsString", "asString"});
            case 9 -> {
                int size = ((Number) tag.getClass().getMethod("size").invoke(tag)).intValue();
                List<Object> list = new ArrayList<>(size);
                Method get = tag.getClass().getMethod("get", int.class);
                for (int index = 0; index < size; index++) list.add(toJava(get.invoke(tag, index)));
                yield List.copyOf(list);
            }
            case 10 -> {
                @SuppressWarnings("unchecked") Set<String> keys = (Set<String>) invokeFirst(tag, new String[] {"getAllKeys", "keySet"});
                Method get = tag.getClass().getMethod("get", String.class);
                Map<String, Object> compound = new LinkedHashMap<>();
                for (String key : keys) compound.put(key, toJava(get.invoke(tag, key)));
                yield Map.copyOf(compound);
            }
            case 11 -> invokeFirst(tag, new String[] {"getAsIntArray", "asIntArray"});
            case 12 -> invokeFirst(tag, new String[] {"getAsLongArray", "asLongArray"});
            default -> throw new IllegalArgumentException("Unsupported NBT tag type " + id);
        };
    }

    private static Object numeric(Object tag, String legacyMethod, String modernMethod) throws ReflectiveOperationException {
        try {
            return invokeAndUnwrap(tag.getClass().getMethod(legacyMethod), tag);
        } catch (NoSuchMethodException ignored) {
            Object value = invokeAndUnwrap(tag.getClass().getMethod(modernMethod), tag);
            return value instanceof Number number ? number : value;
        }
    }

    private static Object invokeStaticFirst(Class<?> type, String[] names, Object... arguments) throws ReflectiveOperationException {
        for (String name : names) {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
                try {
                    return unwrapOptional(method.invoke(null, arguments));
                } catch (IllegalArgumentException ignored) {
                    // Try another overload with a compatible signature.
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + String.join("/", names));
    }

    private static Object invokeFirst(Object target, String[] names) throws ReflectiveOperationException {
        for (String name : names) {
            try {
                return invokeAndUnwrap(target.getClass().getMethod(name), target);
            } catch (NoSuchMethodException ignored) {
                // Try the next API name used by supported server versions.
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + String.join("/", names));
    }

    private static Object invokeAndUnwrap(Method method, Object target, Object... arguments) throws ReflectiveOperationException {
        return unwrapOptional(method.invoke(target, arguments));
    }

    private static Object unwrapOptional(Object value) {
        return value instanceof java.util.Optional<?> optional ? optional.orElseThrow() : value;
    }

    private static String toSnbt(Object value) {
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, nested) -> entries.add(quoteKey(String.valueOf(key)) + ":" + toSnbt(nested)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof byte[] array) return "[B;" + joinArray(array, item -> item + "b") + "]";
        if (value instanceof int[] array) return "[I;" + joinArray(array, Object::toString) + "]";
        if (value instanceof long[] array) return "[L;" + joinArray(array, item -> item + "L") + "]";
        if (value instanceof List<?> list) return "[" + String.join(",", list.stream().map(NativeBlockEntityNbt::toSnbt).toList()) + "]";
        if (value instanceof String string) return quote(string);
        if (value instanceof Byte number) return number + "b";
        if (value instanceof Short number) return number + "s";
        if (value instanceof Long number) return number + "L";
        if (value instanceof Float number) return number + "f";
        if (value instanceof Double number) return number + "d";
        if (value instanceof Number number) return number.toString();
        throw new IllegalArgumentException("Unsupported NBT value " + value);
    }

    private static String quoteKey(String key) {
        return key.matches("[A-Za-z0-9._+-]+") ? key : quote(key);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static <T> String joinArray(T array, java.util.function.Function<Object, String> formatter) {
        int size = Array.getLength(array);
        List<String> parts = new ArrayList<>(size);
        for (int index = 0; index < size; index++) parts.add(formatter.apply(Array.get(array, index)));
        return String.join(",", parts);
    }
}
