package vn.giapha.research.tree.infrastructure.kernel.error;

public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super("CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
