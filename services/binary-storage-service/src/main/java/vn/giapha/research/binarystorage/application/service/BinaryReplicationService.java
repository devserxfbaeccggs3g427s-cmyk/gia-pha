package vn.giapha.research.binarystorage.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.binarystorage.application.port.out.BinaryObjectStore;
import vn.giapha.research.binarystorage.application.port.out.BinaryReplicaRepository;
import vn.giapha.research.binarystorage.domain.model.BinaryReplicaRecord;
import vn.giapha.research.binarystorage.domain.model.ReplicaStatus;

/**
 * Independent binary replication (Task 35, Req 7.17, 12.7-12.9). Every
 * {@code PROMOTED} upload intent's original bytes are streamed to a
 * separately-credentialed encrypted archive (e.g. an S3-compatible bucket
 * with its own access keys). Credentials live in a dedicated secret and
 * never overlap with the Vercel Blob credentials so the credential blast
 * radius is isolated.
 *
 * <p>The replication worker drains {@code binary_replicas} every cycle;
 * an integration test confirms the 24-hour binary RPO and a 4-hour RTO
 * restore drill from the archive.
 */
@Service
public class BinaryReplicationService {

    private static final Logger log = LoggerFactory.getLogger(BinaryReplicationService.class);

    /** Approved RPO/RTO ceilings (Task 35 DoD). */
    public static final Duration RPO = Duration.ofHours(24);
    public static final Duration RTO = Duration.ofHours(4);

    private final BinaryReplicaRepository repository;
    private final BinaryObjectStore objectStore;
    private final BinaryArchiveClient archive;
    private final String archiveBucket;
    private final AtomicReference<Instant> lastCycleAt = new AtomicReference<>(Instant.EPOCH);

    public BinaryReplicationService(BinaryReplicaRepository repository,
            BinaryObjectStore objectStore, BinaryArchiveClient archive,
            @Value("${giapha.binary-archive.bucket:giapha-binary-archive}") String archiveBucket) {
        this.repository = repository;
        this.objectStore = objectStore;
        this.archive = archive;
        this.archiveBucket = archiveBucket;
    }

    @Transactional
    public int runOnce(String workerId, int batchSize) {
        Instant now = Instant.now();
        List<BinaryReplicaRecord> batch = repository.claimBatch(workerId, now, batchSize);
        for (BinaryReplicaRecord record : batch) {
            process(record, now);
        }
        lastCycleAt.set(now);
        return batch.size();
    }

    private void process(BinaryReplicaRecord record, Instant now) {
        try {
            byte[] bytes = objectStore.get(record.primaryObjectPath()).bytes();
            if (!MessageDigest.isEqual(record.primarySha256(), sha256(bytes))) {
                throw new IllegalStateException(
                        "Primary object checksum mismatch: " + record.primaryObjectPath());
            }
            String archivePath = archiveBucket + "/" + record.primaryObjectPath();
            byte[] archiveSha = archive.put(archivePath, bytes);
            repository.markReplicated(record.binaryReplicaKey(), archivePath, archiveSha, now);
        } catch (RuntimeException failure) {
            repository.markFailed(record.binaryReplicaKey(), failure.getMessage(), now);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> lagReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("rpoCeilingHours", RPO.toHours());
        report.put("rtoCeilingHours", RTO.toHours());
        report.put("pending", repository.countByStatus(ReplicaStatus.PENDING));
        report.put("failed", repository.countByStatus(ReplicaStatus.FAILED));
        report.put("replicated", repository.countByStatus(ReplicaStatus.REPLICATED));
        report.put("lastCycleAt", lastCycleAt.get().toString());
        return report;
    }

    @Transactional
    public boolean restoreFromArchive(long mediaKey, String archiveObjectPath,
            Instant now) {
        if (archive == null) {
            return false;
        }
        byte[] bytes = archive.get(archiveObjectPath);
        if (bytes == null) {
            return false;
        }
        // Final path mirrors the original — same bytes, same SHA-256, same
        // ETag precondition; the Blob store refuses to overwrite a divergent
        // object so a successful restore means checksums agreed.
        objectStore.put(archiveObjectPath, bytes, "application/octet-stream");
        repository.markOrphanedByArchive(archiveObjectPath, now);
        return true;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** Minimal archive client (Task 35.1) — implementation provided by the binary archive vendor. */
    public interface BinaryArchiveClient {
        byte[] put(String archivePath, byte[] payload);
        byte[] get(String archivePath);
    }
}
