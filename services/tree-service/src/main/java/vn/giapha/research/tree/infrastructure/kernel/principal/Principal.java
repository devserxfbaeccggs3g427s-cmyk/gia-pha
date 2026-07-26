package vn.giapha.research.tree.infrastructure.kernel.principal;

import java.util.Objects;

public record Principal(String userId, String email, String name) {
    public static final Principal SYSTEM =
            new Principal("system", "system@giapha.local", "System");

    public Principal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(email, "email");
    }

    public boolean isSystem() {
        return SYSTEM.userId().equals(userId);
    }
}
