package dev.learning.delivery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, item) -> entries.add(quote(key.toString()) + ":" + write(item)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof Iterable<?> items) {
            List<String> values = new ArrayList<>();
            items.forEach(item -> values.add(write(item)));
            return "[" + String.join(",", values) + "]";
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    static Map<String, Object> object(String source) {
        Object value = new Parser(source).parse();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 32 ? String.format("\\u%04x", (int) c) : c);
            }
        }
        return out.append('\"').toString();
    }

    private static final class Parser {
        private final String source;
        private int position;

        Parser(String source) { this.source = source; }

        Object parse() {
            Object value = value();
            whitespace();
            if (position != source.length()) throw error("Trailing JSON content");
            return value;
        }

        private Object value() {
            whitespace();
            if (position >= source.length()) throw error("Missing JSON value");
            return switch (source.charAt(position)) {
                case '{' -> object();
                case '[' -> array();
                case '\"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            position++;
            whitespace();
            if (take('}')) return values;
            do {
                whitespace();
                String key = string();
                whitespace();
                expect(':');
                values.put(key, value());
                whitespace();
            } while (take(','));
            expect('}');
            return values;
        }

        private List<Object> array() {
            List<Object> values = new ArrayList<>();
            position++;
            whitespace();
            if (take(']')) return values;
            do {
                values.add(value());
                whitespace();
            } while (take(','));
            expect(']');
            return values;
        }

        private String string() {
            expect('\"');
            StringBuilder out = new StringBuilder();
            while (position < source.length()) {
                char c = source.charAt(position++);
                if (c == '\"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (position >= source.length()) throw error("Unfinished escape");
                char escaped = source.charAt(position++);
                switch (escaped) {
                    case '\"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (position + 4 > source.length()) throw error("Short unicode escape");
                        out.append((char) Integer.parseInt(source.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw error("Unknown escape");
                }
            }
            throw error("Unclosed string");
        }

        private Object number() {
            int start = position;
            while (position < source.length() && "-+0123456789.eE".indexOf(source.charAt(position)) >= 0) position++;
            if (start == position) throw error("Expected value");
            String token = source.substring(start, position);
            return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token);
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, position)) throw error("Expected " + token);
            position += token.length();
            return value;
        }

        private void whitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        }

        private boolean take(char expected) {
            if (position < source.length() && source.charAt(position) == expected) { position++; return true; }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw error("Expected " + expected);
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + position);
        }
    }
}
