package vn.giapha.research.media.shared.paging;

import vn.giapha.research.media.shared.error.ValidationException;

public record PageRequest(int page, int size) {

    public PageRequest {
        if (page < 1) {
            throw new ValidationException("page must be >= 1");
        }
        if (size < 1) {
            throw new ValidationException("size must be >= 1");
        }
    }

    public long offset() {
        return (long) (page - 1) * size;
    }
}
