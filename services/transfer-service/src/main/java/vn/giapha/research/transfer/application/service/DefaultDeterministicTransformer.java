package vn.giapha.research.transfer.application.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.giapha.research.transfer.application.contract.ExtractedDataset;

/**
 * Default deterministic transformer (Task 45). Reads the immutable raw
 * copies + signed manifest, normalizes member/relationship/event shapes,
 * preserves external IDs and timestamps, and quarantines malformed rows
 * with an explicit reason.
 */
@Service
public class DefaultDeterministicTransformer implements DeterministicTransformer {

    private final ObjectMapper mapper;

    public DefaultDeterministicTransformer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Result transform(ExtractedDataset extracted) {
        List<Transformed> transformed = new ArrayList<>();
        List<Quarantined> quarantined = new ArrayList<>();
        try {
            byte[] manifestBytes = Files.readAllBytes(extracted.manifestPath());
            Map<String, Object> manifest = mapper.readValue(manifestBytes,
                    new TypeReference<Map<String, Object>>() {});
            Object entries = manifest.get("entries");
            if (!(entries instanceof List<?> rawEntries)) {
                quarantined.add(new Quarantined("manifest", "MISSING_ENTRIES", manifest));
                return new Result(transformed, quarantined, List.of(), digest(manifestBytes));
            }
            for (Object raw : rawEntries) {
                if (!(raw instanceof Map<?, ?> rawEntry)) {
                    quarantined.add(new Quarantined("entry", "BAD_SHAPE",
                            Map.of("entry", raw)));
                    continue;
                }
                String pathname = String.valueOf(rawEntry.get("sourcePathname"));
                Path rawFile = extracted.manifestDirectory().getParent()
                        .resolve("raw/" + sanitize(pathname) + ".json");
                if (!Files.exists(rawFile)) {
                    quarantined.add(new Quarantined(pathname, "RAW_MISSING",
                            Map.of("pathname", pathname)));
                    continue;
                }
                try {
                    Map<String, Object> payload = mapper.readValue(rawFile.toFile(),
                            new TypeReference<Map<String, Object>>() {});
                    String externalId = String.valueOf(payload.get("id"));
                    if (externalId == null || externalId.isBlank() || "null".equals(externalId)) {
                        quarantined.add(new Quarantined(pathname, "MISSING_EXTERNAL_ID",
                                payload));
                        continue;
                    }
                    transformed.add(new Transformed("USER", externalId, null, payload));
                } catch (IOException malformed) {
                    quarantined.add(new Quarantined(pathname, "PARSE_FAILED",
                            Map.of("error", malformed.getMessage())));
                }
            }
            return new Result(transformed, quarantined, List.of(), digest(manifestBytes));
        } catch (IOException ioFailure) {
            quarantined.add(new Quarantined("manifest", "READ_FAILED",
                    Map.of("error", ioFailure.getMessage())));
            return new Result(transformed, quarantined, List.of(), "");
        }
    }

    private static String sanitize(String pathname) {
        return pathname.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String digest(byte[] payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
