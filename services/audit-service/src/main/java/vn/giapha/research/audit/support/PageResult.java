package vn.giapha.research.audit.support;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long total) {
    public PageResult {
        items = List.copyOf(items);
    }

    public static <T> PageResult<T> of(List<T> items, PageRequest request, long total) {
        return new PageResult<>(items, request.page(), request.size(), total);
    }

    public long totalPages() {
        return size == 0 ? 0 : (total + size - 1) / size;
    }
}
