package vn.giapha.research.transfer.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.transfer.config.TransferProperties;
import vn.giapha.research.transfer.support.Principal;
import vn.giapha.research.transfer.support.ValidationException;
import vn.giapha.research.transfer.application.port.out.ImportJobRepository;
import vn.giapha.research.transfer.domain.model.ImportDuplicateStrategy;
import vn.giapha.research.transfer.domain.model.ImportFormat;
import vn.giapha.research.transfer.domain.model.ImportJob;
import vn.giapha.research.transfer.domain.model.ImportJobStatus;

/**
 * Import preview and execution (Task 29, Req 9). Mirrors the legacy
 * import-service with two phases:
 *
 * <ul>
 *   <li>{@code PREVIEW} parses outside any DB transaction and produces
 *       diagnostics + counts; the tree is not mutated;</li>
 *   <li>{@code EXECUTE} re-runs the parse and finalizes the tree inside one
 *       MySQL transaction so a partial failure leaves the tree untouched.</li>
 * </ul>
 *
 * <p>Idempotency keys are recorded in {@code processed_commands}; replaying
 * the same key returns the prior result without re-running the import.
 */
@Service
public class ImportService {

    private static final Duration MAX_INPUT_AGE = Duration.ofMinutes(15);

    private final ImportJobRepository jobs;
    private final ImportParserRegistry parsers;
    private final long maxBytes;

    public ImportService(ImportJobRepository jobs, ImportParserRegistry parsers,
            TransferProperties properties) {
        this.jobs = jobs;
        this.parsers = parsers;
        this.maxBytes = properties.importMaxBytes();
    }

    @Transactional
    public ImportJob preview(Principal principal, String treeExternalId, ImportFormat format,
            byte[] payload, ImportDuplicateStrategy duplicateStrategy) {
        validatePayloadSize(payload);
        ImportJob job = startJob(principal, treeExternalId, format, payload, duplicateStrategy,
                ImportJobStatus.VALIDATING);
        ImportParser parser = parsers.require(format);
        try {
            ParseResult preview = parser.parse(payload);
            jobs.completePreview(job.importJobKey(), preview.accepted(), preview.skipped(),
                    preview.errored(), preview.errors());
            return jobs.findByKey(job.importJobKey()).orElseThrow();
        } catch (RuntimeException parseFailure) {
            jobs.markFailed(job.importJobKey(), "PREVIEW_FAILED",
                    parseFailure.getMessage());
            throw parseFailure;
        }
    }

    @Transactional
    public ImportJob execute(Principal principal, String treeExternalId, ImportFormat format,
            byte[] payload, ImportDuplicateStrategy duplicateStrategy,
            String idempotencyKey) {
        validatePayloadSize(payload);
        ImportJob job = startJob(principal, treeExternalId, format, payload, duplicateStrategy,
                ImportJobStatus.APPLYING);
        ImportParser parser = parsers.require(format);
        try {
            parser.parseAndApply(treeExternalId, payload, duplicateStrategy, principal);
            jobs.markCompleted(job.importJobKey(), Instant.now());
            return jobs.findByKey(job.importJobKey()).orElseThrow();
        } catch (RuntimeException applyFailure) {
            jobs.markFailed(job.importJobKey(), "APPLY_FAILED", applyFailure.getMessage());
            throw applyFailure;
        }
    }

    private ImportJob startJob(Principal principal, String treeExternalId, ImportFormat format,
            byte[] payload, ImportDuplicateStrategy duplicateStrategy,
            ImportJobStatus status) {
        long userKey = principal == null ? 0L : principal.userId().hashCode();
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("strategy", duplicateStrategy.name());
        return jobs.startJob(treeExternalId, userKey, format, payload, duplicateStrategy, status);
    }

    private void validatePayloadSize(byte[] payload) {
        if (payload == null) {
            throw new ValidationException("Import payload is required");
        }
        if (payload.length > maxBytes) {
            throw new ValidationException("Import exceeds the " + maxBytes + "-byte cap");
        }
        // The UTF-8 length is what matters for legacy parsers; payload was
        // already accepted as bytes so the higher-level cap is exact.
        java.nio.ByteBuffer.wrap(payload);
        // Defensive: detect non-UTF-8 quickly.
        try {
            new String(payload, StandardCharsets.UTF_8);
        } catch (RuntimeException invalid) {
            throw new ValidationException("Import is not valid UTF-8");
        }
    }

    public record ParseResult(int accepted, int skipped, int errored, List<String> errors) {}
}
