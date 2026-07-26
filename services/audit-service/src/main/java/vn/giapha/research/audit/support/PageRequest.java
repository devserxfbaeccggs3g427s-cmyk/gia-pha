package vn.giapha.research.audit.support;

public record PageRequest(int page, int size) {
    public PageRequest {
        if (page < 1 || size < 1) {
            throw new ValidationException("page and size must be >= 1");
        }
    }

    public long offset() {
        return (long) (page - 1) * size;
    }
}
