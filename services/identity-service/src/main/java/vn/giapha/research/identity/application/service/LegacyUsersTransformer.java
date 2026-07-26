package vn.giapha.research.identity.application.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import vn.giapha.research.identity.domain.model.AuthProvider;
import vn.giapha.research.identity.domain.model.LegacyUserImport;
import vn.giapha.research.identity.domain.model.NewUser;

/**
 * Deterministic {@code data/users.json} → relational transformation
 * (Task 17.3). The same input bytes always produce the same batch: users are
 * sorted by external ID, OAuth links are de-duplicated and sorted by
 * {@code provider:providerAccountId}, and every rejected record carries a
 * stable reason. Frozen legacy normalizations applied here:
 *
 * <ul>
 *   <li>emails are trimmed + lowercased ({@code user-store.ts});</li>
 *   <li>{@code passwordHash: ''} (OAuth adapter artifact) becomes NULL;</li>
 *   <li>{@code emailVerificationTokenHash} (SHA-256 hex of the raw token) is
 *       carried over verbatim as a pending verification-token row;</li>
 *   <li>lockout counter/timestamp survive unchanged.</li>
 * </ul>
 */
@Component
public class LegacyUsersTransformer {

    /** Deterministic transformation output; {@code accepted} sorted by external ID. */
    public record Batch(List<LegacyUserImport> accepted, List<Rejected> rejected,
            int sourceOauthLinkCount) {}

    public record Rejected(String externalId, String reason) {}

    private final JsonMapper json;

    public LegacyUsersTransformer(JsonMapper json) {
        this.json = json;
    }

    public Batch transform(String usersJson) {
        JsonNode root = json.readTree(usersJson);
        if (!root.isArray()) {
            throw new IllegalArgumentException("users.json root must be an array");
        }

        // LinkedHashMap keyed by externalId: first occurrence wins so duplicate
        // external IDs are itemized as rejected (Task 17.3 DoD — exact counts
        // and stable conflict reporting). Legacy findIndex semantics used to
        // overwrite silently, which prevented reconciliation from detecting
        // a source-side anomaly.
        Map<String, LegacyUserImport> byExternalId = new LinkedHashMap<>();
        List<Rejected> rejected = new ArrayList<>();
        int index = 0;
        for (JsonNode node : root) {
            String externalId = text(node, "id");
            try {
                LegacyUserImport imported = transformOne(node);
                LegacyUserImport prior = byExternalId.putIfAbsent(imported.user().externalId(),
                        imported);
                if (prior != null) {
                    rejected.add(new Rejected(imported.user().externalId(),
                            "DUPLICATE_EXTERNAL_ID"));
                }
            } catch (RuntimeException invalid) {
                rejected.add(new Rejected(externalId != null ? externalId : "#" + index,
                        invalid.getMessage()));
            }
            index++;
        }

        List<LegacyUserImport> accepted = new ArrayList<>(byExternalId.values());
        accepted.sort(Comparator.comparing(imported -> imported.user().externalId()));
        int oauthLinks = accepted.stream().mapToInt(u -> u.oauthAccounts().size()).sum();
        return new Batch(List.copyOf(accepted), List.copyOf(rejected), oauthLinks);
    }

    private LegacyUserImport transformOne(JsonNode node) {
        String externalId = requireText(node, "id");
        String email = requireText(node, "email");
        String name = requireText(node, "name");
        Instant createdAt = requireInstant(node, "createdAt");
        Instant updatedAt = requireInstant(node, "updatedAt");

        String providerValue = text(node, "provider");
        AuthProvider provider = providerValue == null
                ? AuthProvider.CREDENTIALS
                : AuthProvider.fromDbValue(providerValue);

        NewUser user = new NewUser(
                externalId,
                email,
                name,
                text(node, "passwordHash"),
                text(node, "image"),
                provider,
                optionalInstant(node, "emailVerified"),
                node.path("failedLoginAttempts").asInt(0),
                optionalInstant(node, "lockedUntil"),
                createdAt,
                updatedAt);

        Map<String, LegacyUserImport.OAuthLinkImport> links = new LinkedHashMap<>();
        for (JsonNode account : node.path("oauthAccounts")) {
            AuthProvider linkProvider = AuthProvider.fromDbValue(
                    requireText(account, "provider"));
            if (!linkProvider.isOauth()) {
                throw new IllegalArgumentException("oauthAccounts.provider cannot be credentials");
            }
            String providerAccountId = requireText(account, "providerAccountId");
            links.putIfAbsent(IdentityDigest.oauthKey(linkProvider, providerAccountId),
                    new LegacyUserImport.OAuthLinkImport(linkProvider, providerAccountId));
        }
        List<LegacyUserImport.OAuthLinkImport> oauthAccounts = links.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();

        LegacyUserImport.PendingVerification verification = null;
        String tokenHashHex = text(node, "emailVerificationTokenHash");
        Instant tokenExpiresAt = optionalInstant(node, "emailVerificationExpiresAt");
        if (tokenHashHex != null && tokenExpiresAt != null) {
            byte[] tokenHash = HexFormat.of().parseHex(tokenHashHex);
            if (tokenHash.length != 32) {
                throw new IllegalArgumentException("emailVerificationTokenHash must be SHA-256");
            }
            verification = new LegacyUserImport.PendingVerification(tokenHash, tokenExpiresAt);
        }

        return new LegacyUserImport(user, oauthAccounts, verification);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.asString() : null;
    }

    private static String requireText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static Instant requireInstant(JsonNode node, String field) {
        Instant value = optionalInstant(node, field);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static Instant optionalInstant(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Invalid instant in field: " + field);
        }
    }
}
