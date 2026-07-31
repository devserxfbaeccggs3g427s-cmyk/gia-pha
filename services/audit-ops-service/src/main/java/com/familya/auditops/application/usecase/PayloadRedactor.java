/**
 * Centralized payload redactor used by every DLQ and audit-evidence write path.
 *
 * <p>The allowlist follows ADR-004 (no secrets, no raw Blob URLs, no signed
 * capabilities, no PII beyond what the event catalog declares). The redactor
 * is applied <em>before</em> any payload is persisted, so DLQ inspection and
 * audit timelines can never leak credentials or non-allowlisted PII.</p>
 */
package com.familya.auditops.application.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PayloadRedactor {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Set<String> ALLOWED_TOP_LEVEL = Set.of(
            "operationId", "ownerService", "sagaType", "treeId", "initiatingUserId",
            "state", "occurredAt", "startedAt", "ackAggregateVersion", "ackEpoch",
            "targetAggregateVersion", "targetEpoch", "deadlineAtEpochMs",
            "failureCode", "failureMessage", "failureRouting", "schemaVersion",
            "schema_version", "eventId", "event_id", "correlationId", "correlation_id",
            "causationId", "causation_id", "operation_id", "stepCode", "stepName",
            "sequenceNo", "isCompensation", "traceparent", "trace_id"
    );

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "password", "token", "accessToken", "refreshToken", "idToken",
            "authorization", "cookie", "secret", "bcrypt", "hash",
            "blobUrl", "signedUrl", "signedCapability", "privateKey", "private_key"
    );

    private static final Pattern STACK_TRACE_LINE = Pattern.compile("^\\s*at\\s+.*$");
    private static final String REDACTED_MARKER = "[REDACTED]";

    public Optional<JsonNode> redact(JsonNode raw) {
        if (raw == null || raw.isNull()) return Optional.empty();
        if (raw.isObject()) {
            return Optional.of(redactObject((ObjectNode) raw.deepCopy()));
        }
        if (raw.isArray()) {
            ArrayNode arr = JSON.createArrayNode();
            for (JsonNode child : raw) {
                redact(child).ifPresent(arr::add);
            }
            return arr.isEmpty() ? Optional.empty() : Optional.of(arr);
        }
        if (raw.isTextual() && STACK_TRACE_LINE.matcher(raw.asText()).find()) {
            return Optional.of(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode(REDACTED_MARKER));
        }
        return Optional.of(raw);
    }

    private ObjectNode redactObject(ObjectNode obj) {
        ObjectNode out = JSON.createObjectNode();
        Set<String> keys = new LinkedHashSet<>();
        Iterator<String> it = obj.fieldNames();
        while (it.hasNext()) keys.add(it.next());
        for (String key : keys) {
            JsonNode value = obj.get(key);
            if (FORBIDDEN_KEYS.contains(key)) {
                out.put(key, REDACTED_MARKER);
                continue;
            }
            if (!ALLOWED_TOP_LEVEL.contains(key) && value != null && value.isContainerNode()) {
                continue;
            }
            if (value != null && value.isTextual() && STACK_TRACE_LINE.matcher(value.asText()).find()) {
                out.put(key, REDACTED_MARKER);
                continue;
            }
            redact(value).ifPresent(v -> out.set(key, v));
        }
        return out;
    }
}
