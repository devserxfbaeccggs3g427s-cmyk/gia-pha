package vn.giapha.research.identity.application.cutover;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import vn.giapha.research.identity.application.port.out.OAuthAccountRepository;
import vn.giapha.research.identity.application.port.out.UserRepository;
import vn.giapha.research.identity.domain.model.AuthProvider;
import vn.giapha.research.identity.domain.model.NewUser;
import vn.giapha.research.identity.domain.model.OAuthAccountLink;
import vn.giapha.research.identity.domain.model.User;

/**
 * Rollback projection (Task 20.4). Reconstructs a legacy-compatible
 * {@code data/users.json} from MySQL so a failed cutover can flip authority
 * back to {@link vn.giapha.research.identity.domain.cutover.IdentityWriter#LEGACY}
 * with the source store in sync. The projection is deterministic and
 * re-runnable.
 */
@Service
public class IdentityRollbackProjector {

    private final UserRepository users;
    private final OAuthAccountRepository oauthAccounts;
    private final JsonMapper json;

    public IdentityRollbackProjector(UserRepository users, OAuthAccountRepository oauthAccounts,
            ObjectMapper mapper) {
        this.users = users;
        this.oauthAccounts = oauthAccounts;
        this.json = mapper instanceof JsonMapper jm ? jm : JsonMapper.builder().build();
    }

    /** Project MySQL → JSON bytes ready to overwrite {@code data/users.json}. */
    public String projectJson() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User user : users.findAllActiveOrderedByExternalId()) {
            rows.add(rowFor(user));
        }
        return json.writeValueAsString(rows);
    }

    /** Convenience helper used by the cutover rehearsal scripts. */
    public Path writeTo(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        String body = projectJson();
        Files.writeString(target, body, StandardCharsets.UTF_8);
        return target;
    }

    private Map<String, Object> rowFor(User user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.externalId());
        row.put("email", user.email());
        row.put("name", user.name());
        row.put("passwordHash", user.passwordHash() == null ? "" : user.passwordHash());
        row.put("image", user.imageUrl());
        row.put("provider", user.provider().dbValue());
        if (user.emailVerifiedAt() != null) {
            row.put("emailVerified", user.emailVerifiedAt().toString());
        } else {
            row.put("emailVerified", null);
        }
        row.put("failedLoginAttempts", user.failedLoginAttempts());
        if (user.lockedUntil() != null) {
            row.put("lockedUntil", user.lockedUntil().toString());
        } else {
            row.put("lockedUntil", null);
        }
        row.put("createdAt", isoOrNow(user.createdAt()));
        row.put("updatedAt", isoOrNow(user.updatedAt()));
        List<Map<String, Object>> oauth = new ArrayList<>();
        for (OAuthAccountLink link : oauthAccounts.listByUser(user.userKey())) {
            if (!link.provider().isOauth()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("provider", link.provider().dbValue());
            entry.put("providerAccountId", link.providerAccountId());
            oauth.add(entry);
        }
        if (!oauth.isEmpty()) {
            row.put("oauthAccounts", oauth);
        }
        if (user.provider() == AuthProvider.CREDENTIALS) {
            row.remove("provider");
            row.put("provider", "credentials");
        }
        return row;
    }

    private static String isoOrNow(Instant instant) {
        return instant == null ? Instant.now().toString() : instant.toString();
    }
}
