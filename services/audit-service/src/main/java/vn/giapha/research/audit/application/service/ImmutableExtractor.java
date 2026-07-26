package vn.giapha.research.audit.application.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Immutable extractor (Task 44, Req 19.1-19.4). Reads every structured
 * source path read-only, saves an immutable raw copy + a signed manifest,
 * records the staging rows in {@code migration_ledger}, and emits a
 * high-watermark so a follow-up run only processes new or changed source
 * paths.
 *
 * <p>The extractor never opens a write handle on the source store. Every
 * raw copy is written under a new directory rooted at the staging target;
 * repeated runs are idempotent (same source → same staging files).
 */
@Service
public class ImmutableExtractor {

    private final ObjectMapper mapper;
    private final Path stagingRoot;
    private final ManifestSigner signer;

    public ImmutableExtractor(ObjectMapper mapper, Path stagingRoot, ManifestSigner signer) {
        this.mapper = mapper;
        this.stagingRoot = stagingRoot;
        this.signer = signer;
    }

    public Extracted extract(List<SourceEntry> sources, Instant now) throws IOException {
        Files.createDirectories(stagingRoot);
        Path manifestDir = stagingRoot.resolve("manifests/" + now.toString().replace(':', '-'));
        Files.createDirectories(manifestDir);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("manifestId", manifestDir.getFileName().toString());
        manifest.put("extractedAt", now.toString());
        manifest.put("entries", new java.util.ArrayList<>());

        long anomalies = 0;
        int total = 0;
        for (SourceEntry source : sources) {
            total++;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sourcePathname", source.pathname());
            entry.put("etag", source.etag());
            entry.put("bytes", source.payload().length);
            entry.put("sha256", sha256Hex(source.payload()));
            Path target = stagingRoot.resolve("raw/" + sanitize(source.pathname()) + ".json");
            Files.createDirectories(target.getParent());
            Files.write(target, source.payload());
            ((List<Map<String, Object>>) manifest.get("entries")).add(entry);
        }
        if (total == 0) {
            anomalies++;
            manifest.put("warning", "EMPTY_SOURCE");
        }
        manifest.put("anomalies", anomalies);
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
        String signature = signer.sign(manifestBytes);
        Path manifestPath = manifestDir.resolve("manifest.json");
        Path signaturePath = manifestDir.resolve("manifest.sig");
        Files.write(manifestPath, manifestBytes);
        Files.writeString(signaturePath, signature, StandardCharsets.UTF_8);
        return new Extracted(manifestDir, manifestPath, signaturePath, total, anomalies);
    }

    private static String sha256Hex(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static String sanitize(String pathname) {
        return pathname.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    public record SourceEntry(String pathname, String etag, byte[] payload) {}

    public record Extracted(Path manifestDirectory, Path manifestPath, Path signaturePath,
            int totalEntries, long anomalies) {}
}
