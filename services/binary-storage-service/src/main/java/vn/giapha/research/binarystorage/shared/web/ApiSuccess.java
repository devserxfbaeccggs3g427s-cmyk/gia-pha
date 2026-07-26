package vn.giapha.research.binarystorage.shared.web;

public record ApiSuccess<T>(boolean ok, T data) {

    public static <T> ApiSuccess<T> of(T data) {
        return new ApiSuccess<>(true, data);
    }
}
