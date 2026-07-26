package vn.giapha.research.media.shared.paging;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long total) {

    public PageResult {
        items = List.copyOf(items);
    }

    public static <T> PageResult<T> of(List<T> items, PageRequest request, long total) {
        return new PageResult<>(items, request.page(), request.size(), total);
    }
}
