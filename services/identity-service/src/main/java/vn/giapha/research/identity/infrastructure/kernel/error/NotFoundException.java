package vn.giapha.research.identity.infrastructure.kernel.error;

public class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
