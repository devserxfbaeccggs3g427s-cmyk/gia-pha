package vn.giapha.research.audit.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.audit.application.port.out.AuditWriter;
import vn.giapha.research.audit.domain.model.AuditAction;
import vn.giapha.research.audit.domain.model.AuditEntityType;

/**
 * Legacy change-log migration and runtime audit (Task 28, Req 13.1-13.4).
 *
 * <p>The legacy {@code ChangeLog} entries are pulled from Blob during
 * migration, filtered through {@link AuditRedactor} (which strips hashes,
 * tokens, blobs and PII) and persisted to {@code audit_logs} with the same
 * external-id style as the other entities. Album log entries that are
 * really media-change records get reclassified and deduped so a media
 * mutation doesn't show up twice in the timeline.
 */
@Service
public class LegacyChangeLogService {

    private final AuditWriter writer;
    private final AuditRedactor redactor;

    public LegacyChangeLogService(AuditWriter writer, AuditRedactor redactor) {
        this.writer = writer;
        this.redactor = redactor;
    }

    @Transactional
    public MigrationResult migrate(long treeKey, String treeExternalId,
            List<LegacyChangeLogEntry> entries, Instant now) {
        int accepted = 0;
        int reclassified = 0;
        int rejected = 0;
        for (LegacyChangeLogEntry entry : entries) {
            if (entry == null || entry.action() == null || entry.entityType() == null) {
                rejected++;
                continue;
            }
            String entityType = entry.entityType();
            if ("ALBUM".equals(entityType) && entry.fieldChanged() != null
                    && entry.fieldChanged().startsWith("MEDIA:")) {
                entityType = "MEDIA";
                reclassified++;
            }
            Map<String, Object> previous = redactor.sanitizePayload(entry.previousData());
            Map<String, Object> next = redactor.sanitizePayload(entry.newData());
            if (previous.isEmpty() && next.isEmpty()) {
                // Nothing allowlisted survives: skip silently rather than
                // store an empty row.
                rejected++;
                continue;
            }
            writer.appendBusiness(new vn.giapha.research.audit.domain.model.BusinessAuditEntry(
                    entry.externalId(),
                    treeKey,
                    entry.actorUserExternalId(),
                    AuditEntityType.fromWire(entityType),
                    entry.entityExternalId(),
                    entry.memberExternalId(),
                    AuditAction.fromWire(entry.action()),
                    entry.fieldChanged(),
                    previous, next));
            accepted++;
        }
        return new MigrationResult(accepted, reclassified, rejected);
    }

    public record LegacyChangeLogEntry(
            String externalId,
            String actorUserExternalId,
            String entityType,
            String entityExternalId,
            String memberExternalId,
            String action,
            String fieldChanged,
            Map<String, Object> previousData,
            Map<String, Object> newData) {

        public LegacyChangeLogEntry {
            previousData = previousData == null ? Map.of()
                    : new LinkedHashMap<>(previousData);
            newData = newData == null ? Map.of() : new LinkedHashMap<>(newData);
        }
    }

    public record LegacyChangeLogRow(
            long treeKey,
            String treeExternalId,
            String externalId,
            String actorUserExternalId,
            String entityType,
            String entityExternalId,
            String memberExternalId,
            String action,
            String fieldChanged,
            Map<String, Object> previousData,
            Map<String, Object> newData,
            Instant createdAt) {}

    public record MigrationResult(int accepted, int reclassified, int rejected) {}
}
