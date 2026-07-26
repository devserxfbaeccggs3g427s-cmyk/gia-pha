package vn.giapha.research.media.shared.principal;

import java.util.Objects;

public record Principal(String userId, String email, String name) {

    public Principal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(email, "email");
    }
}
