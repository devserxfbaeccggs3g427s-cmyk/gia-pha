package vn.giapha.research.tree.infrastructure.kernel.web;

public record ApiSuccess<T>(boolean ok, T data) {
    public static <T> ApiSuccess<T> ok(T data) {
        return new ApiSuccess<>(true, data);
    }
}
