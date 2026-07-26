package vn.giapha.research.audit.support;

import java.util.Objects;

public record Principal(String userId, String email, String name) {
    public static final Principal SYSTEM = new Principal("system", "system@giapha.local", "System");

    public Principal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(email, "email");
    }
}
