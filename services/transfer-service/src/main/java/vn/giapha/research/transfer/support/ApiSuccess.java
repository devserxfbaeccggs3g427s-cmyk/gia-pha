package vn.giapha.research.transfer.support;

public record ApiSuccess<T>(boolean ok, T data) {
    public static <T> ApiSuccess<T> of(T data) {
        return new ApiSuccess<>(true, data);
    }

    public static <T> ApiSuccess<T> ok(T data) {
        return of(data);
    }
}
