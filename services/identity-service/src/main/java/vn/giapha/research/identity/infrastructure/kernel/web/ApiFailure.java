package vn.giapha.research.identity.infrastructure.kernel.web;

public record ApiFailure(boolean ok, Error error) {
    public static ApiFailure of(String code, String message) {
        return new ApiFailure(false, new Error(code, message));
    }

    public record Error(String code, String message) {}
}
