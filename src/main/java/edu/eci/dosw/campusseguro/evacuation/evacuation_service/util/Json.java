package edu.eci.dosw.campusseguro.evacuation.evacuation_service.util;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Escritor mínimo de JSON para las respuestas que se arman fuera de Spring MVC (filtros de seguridad) y para los
 * eventos de auditoría. Soporta null, texto, booleanos, números, enums, mapas y colecciones
 */
public final class Json {

    private Json() {
    }

    /**
     * Convierte el valor a JSON
     *
     * @param value null, texto, booleano, número, enum, {@link Map} o {@link Iterable}
     * @return el JSON del valor
     * @throws IllegalArgumentException si el tipo no está soportado
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(value, out);
        return out.toString();
    }

    private static void append(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof CharSequence text) {
            appendString(text.toString(), out);
        } else if (value instanceof Boolean flag) {
            out.append(flag.booleanValue());
        } else if (value instanceof Enum<?> constant) {
            appendString(constant.name(), out);
        } else if (value instanceof Number number) {
            appendNumber(number, out);
        } else if (value instanceof Map<?, ?> map) {
            appendObject(map, out);
        } else if (value instanceof Iterable<?> items) {
            appendArray(items, out);
        } else {
            throw new IllegalArgumentException("Tipo no soportado para JSON: " + value.getClass().getName());
        }
    }

    private static void appendObject(Map<?, ?> map, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            appendString(String.valueOf(entry.getKey()), out);
            out.append(':');
            append(entry.getValue(), out);
        }
        out.append('}');
    }

    private static void appendArray(Iterable<?> items, StringBuilder out) {
        out.append('[');
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            append(item, out);
        }
        out.append(']');
    }

    private static void appendNumber(Number number, StringBuilder out) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("NaN e infinito no son valores JSON válidos");
            }
        }
        out.append(number instanceof BigDecimal decimal ? decimal.toPlainString() : number.toString());
    }

    private static void appendString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
