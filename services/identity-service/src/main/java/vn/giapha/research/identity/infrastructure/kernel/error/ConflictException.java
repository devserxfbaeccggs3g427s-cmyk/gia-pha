package vn.giapha.research.identity.infrastructure.kernel.error;

import java.util.Map;

public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super("CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }

    public ConflictException(String code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
