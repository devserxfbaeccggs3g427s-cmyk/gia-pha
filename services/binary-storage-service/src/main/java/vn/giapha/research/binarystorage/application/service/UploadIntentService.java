package vn.giapha.research.binarystorage.application.service;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import vn.giapha.research.binarystorage.application.port.in.CompleteUploadUseCase;
import vn.giapha.research.binarystorage.application.port.in.RequestUploadUseCase;
import vn.giapha.research.binarystorage.application.port.out.BinaryObjectStore;
import vn.giapha.research.binarystorage.application.port.out.FileCleanupJobRepository;
import vn.giapha.research.binarystorage.application.port.out.MalwareScanner;
import vn.giapha.research.binarystorage.application.port.out.UploadActivationHandler;
import vn.giapha.research.binarystorage.application.port.out.UploadIntentRepository;
import vn.giapha.research.binarystorage.config.BinaryStorageSettings;
import vn.giapha.research.binarystorage.domain.error.BinaryStorageException;
import vn.giapha.research.binarystorage.domain.model.BinaryContent;
import vn.giapha.research.binarystorage.domain.model.CleanupReasons;
import vn.giapha.research.binarystorage.domain.model.NewUploadIntent;
import vn.giapha.research.binarystorage.domain.model.ObjectHead;
import vn.giapha.research.binarystorage.domain.model.ScanOutcome;
import vn.giapha.research.binarystorage.domain.model.SignedUrl;
import vn.giapha.research.binarystorage.domain.model.SignedUrlRequest;
import vn.giapha.research.binarystorage.domain.model.UploadGrant;
import vn.giapha.research.binarystorage.domain.model.UploadIntent;
import vn.giapha.research.binarystorage.domain.model.UploadIntentStatus;
import vn.giapha.research.binarystorage.shared.crypto.Hashes;
import vn.giapha.research.binarystorage.shared.error.ValidationException;
import vn.giapha.research.binarystorage.shared.time.TimeProvider;

/**
 * The upload-intent lifecycle engine (Task 15, design.md §Upload Flow).
 *
 * <p>Every step is idempotent and every failure leaves recoverable state:
 * <ul>
 *   <li>capability issuance failure → intent {@code FAILED}, nothing uploaded;</li>
 *   <li>verification/validation/scan rejection → intent {@code FAILED} plus a
 *       durable cleanup job for the quarantine object;</li>
 *   <li>scanner or store outage → a retryable exception propagates, the
 *       intent stays {@code PENDING_UPLOAD}/{@code UPLOADED} and the event is
 *       redelivered (callback retry, then reconciliation re-drive) —
 *       fail-closed: nothing is ever activated on an unknown verdict;</li>
 *   <li>crash after promotion copy but before finalize → the next attempt's
 *       copy hits {@code CONFLICT}, which is treated as "already promoted",
 *       and finalization proceeds.</li>
 * </ul>
 *
 * <p>Activation is a <em>second transaction</em> that runs strictly after the
 * binary is verified, scanned and present at its final path, so no activated
 * record can point at absent or unscanned content (Task 15.6 / DoD).
 */
@Service
public class UploadIntentService implements RequestUploadUseCase, CompleteUploadUseCase {

    private static final Logger log = LoggerFactory.getLogger(UploadIntentService.class);

    private final UploadIntentRepository intents;
    private final FileCleanupJobRepository cleanupJobs;
    private final BinaryObjectStore objectStore;
    private final MalwareScanner malwareScanner;
    private final ContentValidator contentValidator;
    private final UploadActivationHandler activationHandler;
    private final TransactionTemplate transaction;
    private final BinaryStorageSettings settings;
    private final TimeProvider time;

    UploadIntentService(
            UploadIntentRepository intents,
            FileCleanupJobRepository cleanupJobs,
            BinaryObjectStore objectStore,
            MalwareScanner malwareScanner,
            ContentValidator contentValidator,
            UploadActivationHandler activationHandler,
            TransactionTemplate transaction,
            BinaryStorageSettings settings,
            TimeProvider time) {
        this.intents = intents;
        this.cleanupJobs = cleanupJobs;
        this.objectStore = objectStore;
        this.malwareScanner = malwareScanner;
        this.contentValidator = contentValidator;
        this.activationHandler = activationHandler;
        this.transaction = transaction;
        this.settings = settings;
        this.time = time;
    }

    @Override
    public UploadGrant requestUpload(NewUploadIntent intent) {
        if (!ContentValidator.ALLOWED_MIME_TYPES
                .contains(intent.expectedMimeType().toLowerCase(Locale.ROOT))) {
            throw new ValidationException("Unsupported content type");
        }
        Instant now = time.now();
        if (!intent.expiresAt().isAfter(now)) {
            throw new ValidationException("expiresAt must be in the future");
        }
        long id = intents.insert(intent, now);
        // The capability never outlives the intent: reconciliation can rely on
        // no new object appearing in quarantine after expires_at + skew.
        Duration ttl = min(settings.browserSignedUrlTtl(), Duration.between(now, intent.expiresAt()));
        try {
            SignedUrl url = objectStore.issueSignedUrl(SignedUrlRequest.forPut(
                    intent.quarantineObjectPath(), ttl, intent.expectedMimeType(),
                    intent.expectedMaxBytes(), intent.externalId()));
            return new UploadGrant(intent.externalId(), url, intent.expiresAt());
        } catch (RuntimeException e) {
            // No capability was handed out, so no object can appear: FAILED
            // without a cleanup job is fully recovered state.
            intents.transition(id, UploadIntentStatus.PENDING_UPLOAD, UploadIntentStatus.FAILED, time.now());
            throw e;
        }
    }

    @Override
    public void handleCompletion(String intentExternalId) {
        UploadIntent intent = intents.findByExternalId(intentExternalId).orElse(null);
        if (intent == null) {
            // Unknown correlation id: nothing to drive. The orphan sweep reaps
            // whatever landed in quarantine, so swallowing is safe and stops
            // poison redelivery loops.
            log.warn("Upload completion for unknown intent (idempotent no-op)");
            return;
        }
        switch (intent.status()) {
            case PROMOTED, FAILED, EXPIRED, CANCELLED -> {
                log.info("Upload completion redelivered for settled intent {} ({}); ignoring",
                        intent.id(), intent.status());
                return;
            }
            case PENDING_UPLOAD, UPLOADED -> { /* proceed */ }
        }

        // ---- Task 15.3: never trust the callback/client claim. Observe the
        // object ourselves and recompute the hash from the exact bytes.
        ObjectHead head;
        try {
            head = objectStore.head(intent.quarantineObjectPath());
        } catch (BinaryStorageException e) {
            if (e.reason() == BinaryStorageException.Reason.NOT_FOUND) {
                // Completion claimed but no object present: leave the intent
                // alone — either the event raced deletion or it was spoofed;
                // reconciliation expires it on schedule.
                log.warn("Upload completion for intent {} but quarantine object is absent", intent.id());
                return;
            }
            throw e; // retryable → caller returns 5xx → redelivery
        }
        if (head.sizeBytes() > intent.expectedMaxBytes()) {
            reject(intent, CleanupReasons.VALIDATION_REJECTED, "observed size exceeds the intent cap");
            return;
        }
        BinaryContent content = objectStore.get(intent.quarantineObjectPath());
        if (content.sizeBytes() > intent.expectedMaxBytes()) {
            reject(intent, CleanupReasons.VALIDATION_REJECTED, "fetched size exceeds the intent cap");
            return;
        }
        byte[] sha256 = Hashes.sha256(content.bytes());
        if (intent.expectedSha256() != null
                && !MessageDigest.isEqual(intent.expectedSha256(), sha256)) {
            reject(intent, CleanupReasons.VALIDATION_REJECTED, "content hash does not match the declared hash");
            return;
        }

        if (intent.status() == UploadIntentStatus.PENDING_UPLOAD
                && !intents.transition(intent.id(), UploadIntentStatus.PENDING_UPLOAD,
                        UploadIntentStatus.UPLOADED, null)) {
            // Reconciliation expired/cancelled the intent concurrently; its
            // cleanup job reaps the object. Nothing more to do here.
            log.info("Intent {} was settled concurrently; skipping pipeline", intent.id());
            return;
        }

        // ---- Task 15.4: declared type, magic bytes and parser must agree.
        try {
            contentValidator.validate(intent.expectedMimeType(), content.bytes());
        } catch (ValidationException e) {
            reject(intent, CleanupReasons.VALIDATION_REJECTED, e.getMessage());
            return;
        }

        // ---- Task 15.5: mandatory scan, fail closed on anything but CLEAN.
        ScanOutcome outcome;
        try {
            outcome = malwareScanner.scan(
                    intent.quarantineObjectPath(), intent.expectedMimeType(), content.bytes());
        } catch (RuntimeException e) {
            throw new BinaryStorageException(BinaryStorageException.Reason.UPSTREAM_ERROR,
                    "Malware scanner unavailable", e);
        }
        if (outcome.result() == ScanOutcome.Result.ERROR) {
            // Scanner outage: stay UPLOADED and retry later — never activate.
            throw new BinaryStorageException(BinaryStorageException.Reason.UPSTREAM_ERROR,
                    "Malware scanner returned an ERROR verdict");
        }
        if (outcome.result() == ScanOutcome.Result.INFECTED) {
            log.warn("SECURITY intent {} rejected by malware scan (engine={}, signatures={})",
                    intent.id(), outcome.engine(), outcome.signatureVersion());
            reject(intent, CleanupReasons.MALWARE_REJECTED, "malware scan rejected the content");
            return;
        }

        // ---- Task 15.6: promote (no-overwrite copy), then activate in a
        // second transaction. CONFLICT means a previous attempt already
        // copied — idempotent redelivery proceeds straight to finalize.
        try {
            objectStore.copy(intent.quarantineObjectPath(), intent.finalObjectPath());
        } catch (BinaryStorageException e) {
            if (e.reason() != BinaryStorageException.Reason.CONFLICT) {
                throw e;
            }
        }
        ObjectHead finalHead = objectStore.head(intent.finalObjectPath());

        Instant now = time.now();
        transaction.executeWithoutResult(status -> {
            if (!intents.transition(intent.id(), UploadIntentStatus.UPLOADED,
                    UploadIntentStatus.PROMOTED, now)) {
                log.info("Intent {} finalized concurrently; skipping activation", intent.id());
                return;
            }
            // Quarantine copy becomes garbage the instant promotion commits;
            // enqueue in the same transaction so the two cannot diverge.
            cleanupJobs.enqueue(intent.quarantineObjectPath(), content.etag(),
                    CleanupReasons.PROMOTED_QUARANTINE, now);
            activationHandler.onPromoted(intent, finalHead);
        });
        log.info("Intent {} promoted (path hash {})", intent.id(),
                pathHash(intent.finalObjectPath()));
    }

    /**
     * Terminal rejection: FAILED plus a durable cleanup job, atomically. The
     * transition is status-guarded, so a concurrent settle wins cleanly.
     */
    private void reject(UploadIntent intent, String reason, String detail) {
        Instant now = time.now();
        transaction.executeWithoutResult(status -> {
            if (intents.transition(intent.id(), intent.status(), UploadIntentStatus.FAILED, now)
                    || intents.transition(intent.id(), UploadIntentStatus.UPLOADED,
                            UploadIntentStatus.FAILED, now)) {
                cleanupJobs.enqueue(intent.quarantineObjectPath(), null, reason, now);
            }
        });
        log.warn("Intent {} rejected ({}): {}", intent.id(), reason, detail);
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String pathHash(String pathname) {
        return Hashes.hex(Hashes.sha256(pathname)).substring(0, 16);
    }
}
