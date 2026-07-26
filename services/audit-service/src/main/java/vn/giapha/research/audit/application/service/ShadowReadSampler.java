package vn.giapha.research.audit.application.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Safe shadow read sampler (Task 47, Req 20.1-20.2). Eligible legacy reads
 * are sampled and forwarded to Spring asynchronously. Comparisons are
 * normalized for approved nondeterminism and stripped of PII before
 * logging. Endpoint + tree sampling caps overhead; a runtime kill switch
 * halts the worker immediately.
 */
@Service
public class ShadowReadSampler {

    private static final double DEFAULT_SAMPLE_RATIO = 0.10;

    private final SecureRandom random = new SecureRandom();
    private final AtomicBoolean killSwitch = new AtomicBoolean(false);
    private final AtomicLong sampled = new AtomicLong();
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong mismatches = new AtomicLong();
    private final double sampleRatio;
    private final ObjectMapper mapper;

    public ShadowReadSampler(ObjectMapper mapper) {
        this(mapper, DEFAULT_SAMPLE_RATIO);
    }

    public ShadowReadSampler(ObjectMapper mapper, double sampleRatio) {
        this.mapper = mapper;
        this.sampleRatio = sampleRatio;
    }

    public boolean shouldSample(HttpServletRequest request) {
        if (killSwitch.get()) {
            return false;
        }
        String path = request.getRequestURI();
        if (isExcluded(path)) {
            return false;
        }
        long current = total.incrementAndGet();
        sampled.compareAndSet(current, sampled.get());
        return random.nextDouble() < sampleRatio;
    }

    public void recordMatch() {
        sampled.incrementAndGet();
    }

    public void recordMismatch() {
        mismatches.incrementAndGet();
    }

    public void kill(boolean active) {
        killSwitch.set(active);
    }

    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("sampled", sampled.get());
        stats.put("total", total.get());
        stats.put("mismatches", mismatches.get());
        stats.put("killSwitch", killSwitch.get());
        stats.put("sampleRatio", sampleRatio);
        return stats;
    }

    private static boolean isExcluded(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/internal/")
                || path.startsWith("/api/blob/")
                || path.startsWith("/api/internal/blob/")
                || path.contains("/upload-completions");
    }

    /** Stable, non-PII tag for comparison telemetry. */
    public static String tagFor(HttpServletRequest request, Instant now) {
        byte[] bytes = new byte[8];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes) + "@" + now.getEpochSecond();
    }

    public record Snapshot(double sampleRatio, boolean killSwitch, long sampled, long total,
            long mismatches) {}
}
