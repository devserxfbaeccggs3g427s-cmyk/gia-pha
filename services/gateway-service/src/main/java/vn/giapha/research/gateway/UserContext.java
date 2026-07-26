package vn.giapha.research.gateway;

public final class UserContext {

    public static final String HEADER = "X-User-Context-Token";

    private UserContext() {
    }

    public record Token(
            String userKey,
            String roles,
            String csrf,
            String requestId,
            String signature) {
    }
}
