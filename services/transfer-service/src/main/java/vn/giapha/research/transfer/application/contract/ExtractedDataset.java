package vn.giapha.research.transfer.application.contract;

import java.nio.file.Path;

public record ExtractedDataset(Path manifestDirectory, Path manifestPath, Path signaturePath,
        int totalEntries, long anomalies) {
}
