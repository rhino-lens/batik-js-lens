package io.github.rhinolens;

import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** Minimal JSON writer for maps, lists, strings, numbers, booleans and null (no dependencies). */
final class Json {

    private Json() {
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.toString();
    }

    private static void write(Object v, StringBuilder out, int indent) {
        if (v == null) {
            out.append("null");
        } else if (v instanceof String) {
            string((String) v, out);
        } else if (isNonFinite(v)) {
            string(String.valueOf(v), out); // NaN / Infinity are not valid JSON numbers
        } else if (v instanceof Number || v instanceof Boolean) {
            out.append(v);
        } else if (v instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) v;
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                pad(out, indent + 1);
                string(String.valueOf(e.getKey()), out);
                out.append(": ");
                write(e.getValue(), out, indent + 1);
                out.append(it.hasNext() ? ",\n" : "\n");
            }
            pad(out, indent);
            out.append('}');
        } else if (v instanceof Collection) {
            Collection<?> list = (Collection<?>) v;
            if (list.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            Iterator<?> it = list.iterator();
            while (it.hasNext()) {
                pad(out, indent + 1);
                write(it.next(), out, indent + 1);
                out.append(it.hasNext() ? ",\n" : "\n");
            }
            pad(out, indent);
            out.append(']');
        } else {
            string(String.valueOf(v), out);
        }
    }

    private static boolean isNonFinite(Object v) {
        return (v instanceof Double && !Double.isFinite((Double) v))
                || (v instanceof Float && !Float.isFinite((Float) v));
    }

    private static void pad(StringBuilder out, int indent) {
        for (int i = 0; i < indent; i++) {
            out.append("  ");
        }
    }

    private static void string(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
