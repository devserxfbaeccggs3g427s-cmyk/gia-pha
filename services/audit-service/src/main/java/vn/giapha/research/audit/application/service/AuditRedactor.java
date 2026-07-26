package vn.giapha.research.audit.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import vn.giapha.research.audit.domain.model.AuditEntityType;

/**
 * Field allowlists and value redaction for everything that leaves a business
 * transaction as audit or outbox data (Task 12.2, Requirement 13.2).
 *
 * <p>Policy layers, applied in order:
 * <ol>
 *   <li><b>Allowlist</b> — only fields explicitly listed per entity type
 *       survive; everything else is dropped. Lists are frozen from the legacy
 *       entity shapes in {@code src/data/types.ts}, minus every URL/capability
 *       field ({@code blobUrl}, {@code thumbnailUrl}, {@code contentUrl},
 *       {@code thumbnailContentUrl}, {@code avatarUrl}).</li>
 *   <li><b>Forbidden-key guard</b> — defense in depth: any surviving key whose
 *       name matches the secret pattern (password, hash, token, secret,
 *       cookie, session, credential, capability, signature, authorization)
 *       is dropped even if a future allowlist edit mistakenly admits it.</li>
 *   <li><b>Value scrub</b> — string values that look like credentials (JWTs,
 *       bearer values, URLs carrying query credentials, data URIs) are
 *       replaced with {@code [REDACTED]} so prohibited values can never enter
 *       audit even inside free-text fields.</li>
 * </ol>
 *
 * <p>Nested maps and lists are scrubbed recursively; map depth is bounded to
 * keep adversarial payloads from recursing unboundedly.
 */
@Component
public class AuditRedactor {

    public static final String REDACTED = "[REDACTED]";

    private static final int MAX_DEPTH = 6;

    private static final Pattern FORBIDDEN_KEY = Pattern.compile(
            "(?i).*(password|passhash|hash|token|secret|cookie|session|credential"
                    + "|capability|signature|authorization|apikey|api_key).*");

    /** JWT (three base64url segments), bearer prefixes, credentialed URLs, data URIs. */
    private static final Pattern FORBIDDEN_VALUE = Pattern.compile(
            "(?is).*(eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}"
                    + "|\\bbearer\\s+\\S+"
                    + "|[?&](token|sig|signature|se|sv|X-Amz-[A-Za-z-]+)="
                    + "|\\bdata:[a-z]+/[a-z0-9.+-]+;base64,).*");

    private static final Map<AuditEntityType, Set<String>> ALLOWLIST = Map.of(
            AuditEntityType.MEMBER, Set.of(
                    "id", "treeId", "firstName", "lastName", "fullName", "nickname",
                    "gender", "dateOfBirth", "dateOfDeath", "placeOfBirth",
                    "currentAddress", "phone", "email", "occupation", "education",
                    "biography", "achievements", "notes", "avatarMediaId",
                    "generation", "isAlive", "createdAt", "updatedAt"),
            AuditEntityType.RELATIONSHIP, Set.of(
                    "id", "treeId", "sourceMemberId", "targetMemberId", "type",
                    "customType", "marriageDate", "divorceDate", "marriageStatus",
                    "createdAt"),
            AuditEntityType.EVENT, Set.of(
                    "id", "treeId", "type", "customType", "title", "eventDate",
                    "location", "description", "memberIds", "mediaIds",
                    "createdAt", "updatedAt"),
            // Covers media metadata and albums (legacy kind: "ALBUM" rows).
            AuditEntityType.MEDIA, Set.of(
                    "kind", "id", "treeId", "memberIds", "eventIds", "albumId",
                    "filename", "originalName", "mimeType", "fileSize", "caption",
                    "takenAt", "uploadedAt", "title", "description",
                    "createdAt", "updatedAt"));

    /**
     * Reduces a legacy-shaped entity map to its allowlisted, scrubbed audit
     * representation. Returns {@code null} for {@code null} input so CREATE
     * and DELETE rows keep their one-sided payloads.
     */
    public Map<String, Object> redact(AuditEntityType entityType, Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Set<String> allowed = ALLOWLIST.get(entityType);
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (!allowed.contains(key) || FORBIDDEN_KEY.matcher(key).matches()) {
                continue;
            }
            safe.put(key, scrubValue(entry.getValue(), 1));
        }
        return safe;
    }

    /**
     * Sanitizes an outbox payload built by our own services: forbidden keys
     * are dropped at every depth and suspicious values are scrubbed. Outbox
     * payloads carry references (ids, paths, checksums), never secrets.
     */
    public Map<String, Object> sanitizePayload(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        return scrubMap(payload, 1);
    }

    private Map<String, Object> scrubMap(Map<String, Object> map, int depth) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key == null || FORBIDDEN_KEY.matcher(key).matches()) {
                continue;
            }
            safe.put(key, scrubValue(entry.getValue(), depth));
        }
        return safe;
    }

    @SuppressWarnings("unchecked")
    private Object scrubValue(Object value, int depth) {
        if (value == null || depth > MAX_DEPTH) {
            return depth > MAX_DEPTH ? REDACTED : null;
        }
        return switch (value) {
            case String s -> FORBIDDEN_VALUE.matcher(s).matches() ? REDACTED : s;
            case Map<?, ?> m -> scrubMap((Map<String, Object>) m, depth + 1);
            case List<?> list -> list.stream().map(item -> scrubValue(item, depth + 1)).toList();
            // Raw bytes (file content, hashes) never belong in audit payloads.
            case byte[] ignored -> REDACTED;
            default -> value;
        };
    }
}
