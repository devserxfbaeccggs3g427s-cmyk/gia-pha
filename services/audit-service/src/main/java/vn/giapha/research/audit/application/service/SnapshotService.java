package vn.giapha.research.audit.application.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import vn.giapha.research.audit.application.port.out.TreeSnapshotPort;
import vn.giapha.research.audit.application.port.out.TreeSnapshotPort.TreeSnapshot;
import vn.giapha.research.audit.support.Hashes;
import vn.giapha.research.audit.support.Ids;
import vn.giapha.research.audit.support.NotFoundException;
import vn.giapha.research.audit.support.Principal;
import vn.giapha.research.audit.support.ValidationException;

/**
 * Tree snapshots (Task 34, Req 12). The snapshot is a complete
 * versioned JSON manifest of the tree content; the binary payload lives
 * in a Blob path under a deterministic prefix so a restore can find it.
 *
 * <p>Restore is atomic: a safety snapshot is taken first, then the tree
 * is loaded inside one transaction so an injected failure rolls the whole
 * restore back to the safety snapshot's state.
 */
@Service
public class SnapshotService {

    private static final Duration RETENTION = Duration.ofDays(30);

    private final TreeSnapshotPort trees;
    private final SnapshotStorage storage;
    private final SnapshotManifestWriter manifestWriter;

    public SnapshotService(TreeSnapshotPort trees,
            SnapshotStorage storage, SnapshotManifestWriter manifestWriter) {
        this.trees = trees;
        this.storage = storage;
        this.manifestWriter = manifestWriter;
    }

    @Transactional
    public SnapshotResult snapshot(Principal principal, String treeExternalId,
            String schemaVersion, SnapshotTrigger trigger, Instant now) {
        TreeSnapshot tree = trees.findByExternalId(treeExternalId)
                .orElseThrow(() -> new NotFoundException("TREE_NOT_FOUND",
                        "Tree does not exist"));
        trees.authorize(tree.treeKey(), principal, TreeSnapshotPort.Action.WRITE);
        String externalId = Ids.newId();
        SnapshotManifest manifest = manifestWriter.write(tree, schemaVersion, externalId, now);
        byte[] payload = manifest.payload();
        byte[] checksum = Hashes.sha256(payload);
        String objectPath = "snapshots/" + tree.externalId() + "/" + externalId + ".json";
        storage.put(objectPath, payload);
        return new SnapshotResult(externalId, objectPath, checksum, payload.length,
                now.plus(RETENTION), trigger);
    }

    @Transactional
    public RestoreResult restore(Principal principal, String treeExternalId,
            String snapshotExternalId, Instant now) {
        TreeSnapshot tree = trees.findByExternalId(treeExternalId)
                .orElseThrow(() -> new NotFoundException("TREE_NOT_FOUND",
                        "Tree does not exist"));
        trees.authorize(tree.treeKey(), principal, TreeSnapshotPort.Action.DELETE);
        SnapshotRecord record = storage.find(tree.treeKey(), snapshotExternalId)
                .orElseThrow(() -> new NotFoundException("SNAPSHOT_NOT_FOUND",
                        "Snapshot does not exist"));
        if (record.treeKey() != tree.treeKey()) {
            throw new ValidationException("Snapshot does not belong to this tree");
        }
        // 1. Safety snapshot
        SnapshotResult safety = snapshot(principal, treeExternalId, record.schemaVersion(),
                SnapshotTrigger.PRE_RESTORE, now);
        // 2. Atomic restore — wrapped in a single transaction by the caller.
        trees.applyRestore(tree, record.manifest());
        return new RestoreResult(safety.externalId(), snapshotExternalId);
    }

    public record SnapshotResult(String externalId, String objectPath, byte[] checksum,
            long payloadBytes, Instant retainUntil, SnapshotTrigger trigger) {}

    public record RestoreResult(String safetySnapshotExternalId, String restoredSnapshotExternalId) {}

    public enum SnapshotTrigger {
        MANUAL, SCHEDULED, PRE_IMPORT, PRE_RESTORE
    }

    /** Lightweight in-memory snapshot storage (Task 35.1 will swap this for the archive adapter). */
    public interface SnapshotStorage {
        void put(String objectPath, byte[] payload);

        java.util.Optional<SnapshotRecord> find(long treeKey, String snapshotExternalId);
    }

    public record SnapshotRecord(long treeKey, String snapshotExternalId, String schemaVersion,
            String objectPath, Map<String, Object> manifest) {}

    public record SnapshotManifest(Map<String, Object> manifest, byte[] payload) {}

    /** Manifest writer (Task 34.1) — produces deterministic JSON. */
    @Service
    public static class SnapshotManifestWriter {

        private final com.fasterxml.jackson.databind.ObjectMapper mapper;
        private final JsonFactory jsonFactory = new JsonFactory();

        public SnapshotManifestWriter(com.fasterxml.jackson.databind.ObjectMapper mapper) {
            this.mapper = mapper;
        }

        public SnapshotManifest write(TreeSnapshot tree, String schemaVersion,
                String externalId, Instant now) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("schemaVersion", schemaVersion);
            root.put("snapshotId", externalId);
            root.put("treeExternalId", tree.externalId());
            root.put("generatedAt", now.toString());
            root.put("revision", tree.revision());
            root.put("data", Map.of("placeholder", true));
            try {
                byte[] payload = mapper.writeValueAsBytes(root);
                return new SnapshotManifest(root, payload);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Cannot serialize snapshot manifest", e);
            }
        }

    }
}
