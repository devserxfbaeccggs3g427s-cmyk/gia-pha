package vn.giapha.research.media.shared.web;

public record ApiSuccess<T>(boolean ok, T data) {

    public static <T> ApiSuccess<T> ok(T data) {
        return new ApiSuccess<>(true, data);
    }
}
