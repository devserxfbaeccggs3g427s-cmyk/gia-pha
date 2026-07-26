package vn.giapha.research.audit.support;

import java.util.List;

public record PageEnvelope<T>(List<T> items, int page, int pageSize,
        long totalItems, long totalPages) {
    public PageEnvelope {
        items = List.copyOf(items);
    }
}
