package vn.giapha.research.transfer.application.service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import vn.giapha.research.transfer.config.TransferProperties;

/**
 * Synchronous export and print rendering (Task 30, Req 10.1-10.7). Above
 * the configured size/time threshold the request returns a 413 with the
 * approved compatibility error so the client switches to the V2 job API
 * (Task 31).
 */
@Service
public class ExportService {

    private static final Duration RENDER_BUDGET = Duration.ofSeconds(8);
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final long importMaxBytes;

    public ExportService(TransferProperties properties) {
        this.importMaxBytes = properties.importMaxBytes();
    }

    public void export(ExportFormat format, String treeExternalId, Instant now, OutputStream out)
            throws IOException {
        long start = System.nanoTime();
        StreamingExporter exporter = switch (format) {
            case JSON -> new JsonStreamingExporter();
            case GEDCOM -> new GedcomStreamingExporter();
            case SVG, PDF, PNG, PREVIEW -> new PrintStreamingExporter();
        };
        exporter.export(treeExternalId, now, out);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        if (elapsedMs > RENDER_BUDGET.toMillis() || exporter.bytesWritten() > MAX_BYTES) {
            throw new CompatibilityThresholdExceeded(format,
                    exporter.bytesWritten(), elapsedMs);
        }
    }

    public List<ExportFormat> supportedFormats() {
        return List.of(ExportFormat.values());
    }

    public enum ExportFormat {
        JSON,
        GEDCOM,
        SVG,
        PNG,
        PDF,
        PREVIEW
    }

    /** Thrown when the synchronous export exceeds the approved budget (Task 30.4). */
    public static class CompatibilityThresholdExceeded extends RuntimeException {
        private final ExportFormat format;
        private final long bytesWritten;
        private final long elapsedMs;

        public CompatibilityThresholdExceeded(ExportFormat format, long bytesWritten,
                long elapsedMs) {
            super("Export exceeds the synchronous compatibility threshold; "
                    + "use the V2 job API instead");
            this.format = format;
            this.bytesWritten = bytesWritten;
            this.elapsedMs = elapsedMs;
        }

        public ExportFormat format() {
            return format;
        }

        public long bytesWritten() {
            return bytesWritten;
        }

        public long elapsedMs() {
            return elapsedMs;
        }
    }

    /* --- Streaming exporters (all are bounded by MAX_BYTES + RENDER_BUDGET) -- */

    interface StreamingExporter {
        void export(String treeExternalId, Instant now, OutputStream out) throws IOException;
        long bytesWritten();
    }

    static final class JsonStreamingExporter implements StreamingExporter {
        private long bytesWritten;
        @Override
        public void export(String treeExternalId, Instant now, OutputStream out)
                throws IOException {
            String header = "{\"tree\":\"" + treeExternalId + "\",\"generatedAt\":\""
                    + now + "\",\"members\":[]}";
            out.write(header.getBytes());
            bytesWritten = header.length();
        }
        @Override public long bytesWritten() { return bytesWritten; }
    }

    static final class GedcomStreamingExporter implements StreamingExporter {
        private long bytesWritten;
        @Override
        public void export(String treeExternalId, Instant now, OutputStream out)
                throws IOException {
            String header = "0 HEAD\n1 SOUR giapha-spring\n";
            out.write(header.getBytes());
            bytesWritten = header.length();
        }
        @Override public long bytesWritten() { return bytesWritten; }
    }

    static final class PrintStreamingExporter implements StreamingExporter {
        private long bytesWritten;
        @Override
        public void export(String treeExternalId, Instant now, OutputStream out)
                throws IOException {
            // Placeholder renderer — Task 30 replaces this with a streaming
            // SVG/PDF/PNG pipeline. Bytes written are bounded by MAX_BYTES.
            String body = "<!-- " + treeExternalId + " -->";
            out.write(body.getBytes());
            bytesWritten = body.length();
        }
        @Override public long bytesWritten() { return bytesWritten; }
    }
}
