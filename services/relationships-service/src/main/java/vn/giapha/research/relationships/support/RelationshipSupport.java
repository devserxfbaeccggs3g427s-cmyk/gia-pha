package vn.giapha.research.relationships.support;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class RelationshipSupport {

    private static final char[] ALPHABET =
            "useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private RelationshipSupport() {}

    public static String newId() {
        byte[] bytes = new byte[21];
        RANDOM.nextBytes(bytes);
        StringBuilder id = new StringBuilder(bytes.length);
        for (byte value : bytes) {
            id.append(ALPHABET[value & 0x3F]);
        }
        return id.toString();
    }

    public static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet resultSet, String column) throws SQLException {
        LocalDateTime value = resultSet.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static long requiredKey(GeneratedKeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL did not return a generated key");
        }
        return key.longValue();
    }

    public record Principal(String userId, String email, String name) {
        public Principal {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(email, "email");
        }
    }

    public record ApiSuccess<T>(boolean ok, T data) {
        public static <T> ApiSuccess<T> ok(T data) {
            return new ApiSuccess<>(true, data);
        }
    }

    public abstract static class DomainException extends RuntimeException {
        private final String code;
        private final transient Map<String, Object> details;

        protected DomainException(String code, String message) {
            super(message);
            this.code = code;
            this.details = Map.of();
        }

        public String code() {
            return code;
        }

        public Map<String, Object> details() {
            return details;
        }
    }

    public static final class ConflictException extends DomainException {
        public ConflictException(String message) {
            super("CONFLICT", message);
        }

        public ConflictException(String code, String message) {
            super(code, message);
        }
    }

    public static final class NotFoundException extends DomainException {
        public NotFoundException(String message) {
            super("NOT_FOUND", message);
        }

        public NotFoundException(String code, String message) {
            super(code, message);
        }
    }

    public static final class UnauthorizedException extends DomainException {
        public UnauthorizedException(String code, String message) {
            super(code, message);
        }
    }

    public static final class ForbiddenException extends DomainException {
        public ForbiddenException(String message) {
            super("FORBIDDEN", message);
        }
    }

    public static final class ValidationException extends DomainException {
        public ValidationException(String message) {
            super("VALIDATION_ERROR", message);
        }
    }
}
