package vn.giapha.research.identity.application.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser used by the bridge codec (Task 18). The JOSE/JWT
 * payloads are tiny and well-formed (issuer, audience, sub, iat, exp, jti)
 * so we avoid pulling Jackson into the codec path. Only objects, strings,
 * numbers, booleans, null and arrays are recognized — exactly what JOSE
 * specifies.
 */
final class JsonMap {

    private final Map<String, Object> entries;

    private JsonMap(Map<String, Object> entries) {
        this.entries = entries;
    }

    static JsonMap parse(String json) {
        Parser parser = new Parser(json);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos < parser.src.length()) {
            throw new IllegalArgumentException("Trailing characters at " + parser.pos);
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Bridge JWT header must be an object");
        }
        Map<String, Object> entries = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            entries.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return new JsonMap(entries);
    }

    public String requireString(String field) {
        Object value = entries.get(field);
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException("Missing or non-string field: " + field);
        }
        return s;
    }

    public String string(String field) {
        Object value = entries.get(field);
        return value instanceof String s ? s : null;
    }

    public long longValue(String field) {
        Object value = entries.get(field);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Field " + field + " is not numeric");
            }
        }
        throw new IllegalArgumentException("Field " + field + " missing");
    }

    public long longValueOrDefault(String field, long fallback) {
        Object value = entries.get(field);
        if (value instanceof Number n) {
            return n.longValue();
        }
        return fallback;
    }

    public List<String> stringList(String field) {
        Object value = entries.get(field);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Field " + field + " must be an array");
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof String s)) {
                throw new IllegalArgumentException("Field " + field + " must contain strings");
            }
            result.add(s);
        }
        return List.copyOf(result);
    }

    private static final class Parser {

        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        Object readValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                result.put(key, value);
                skipWhitespace();
                char c = expectOr('}', ',');
                if (c == '}') {
                    return result;
                }
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                result.add(readValue());
                skipWhitespace();
                char c = expectOr(']', ',');
                if (c == ']') {
                    return result;
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c == '\\' && pos < src.length()) {
                    char esc = src.charAt(pos++);
                    builder.append(switch (esc) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case '/' -> '/';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new IllegalArgumentException("Truncated unicode escape");
                            }
                            yield (char) Integer.parseInt(src.substring(pos, pos + 4), 16);
                        }
                        default -> throw new IllegalArgumentException("Bad escape: " + esc);
                    });
                } else {
                    builder.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Boolean readBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Bad literal at " + pos);
        }

        private Object readNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Bad null literal at " + pos);
        }

        private Number readNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (pos < src.length() && (Character.isDigit(src.charAt(pos))
                    || src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E'
                    || src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            String literal = src.substring(start, pos);
            if (literal.contains(".") || literal.contains("e") || literal.contains("E")) {
                return Double.parseDouble(literal);
            }
            try {
                return Long.parseLong(literal);
            } catch (NumberFormatException overflow) {
                return Double.parseDouble(literal);
            }
        }

        private char peek() {
            return src.charAt(pos);
        }

        private void expect(char expected) {
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new IllegalArgumentException(
                        "Expected '" + expected + "' at " + pos);
            }
            pos++;
        }

        private char expectOr(char a, char b) {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }
            char c = src.charAt(pos);
            if (c != a && c != b) {
                throw new IllegalArgumentException("Expected '" + a + "' or '" + b + "' at " + pos);
            }
            pos++;
            return c;
        }
    }
}
