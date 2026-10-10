package io.github.andreanicte02.concurrency;

import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Map;

/** Minimal JSON writer for records, maps, collections, strings, numbers and booleans. */
final class Json {

    private Json() {}

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case String s -> string(s, out);
            case Number n -> out.append(n);
            case Boolean b -> out.append(b);
            case Enum<?> e -> string(e.name(), out);
            case Map<?, ?> map -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) out.append(',');
                    first = false;
                    string(String.valueOf(entry.getKey()), out);
                    out.append(':');
                    write(entry.getValue(), out);
                }
                out.append('}');
            }
            case Collection<?> list -> {
                out.append('[');
                boolean first = true;
                for (Object item : list) {
                    if (!first) out.append(',');
                    first = false;
                    write(item, out);
                }
                out.append(']');
            }
            case Record record -> {
                out.append('{');
                RecordComponent[] components = record.getClass().getRecordComponents();
                for (int i = 0; i < components.length; i++) {
                    if (i > 0) out.append(',');
                    string(components[i].getName(), out);
                    out.append(':');
                    try {
                        write(components[i].getAccessor().invoke(record), out);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
                out.append('}');
            }
            default -> string(value.toString(), out);
        }
    }

    private static void string(String s, StringBuilder out) {
        out.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
