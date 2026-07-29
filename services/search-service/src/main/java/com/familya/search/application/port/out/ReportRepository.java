package com.familya.search.application.port.out;

import com.familya.search.domain.model.ReportSnapshot;

import java.util.Optional;
import java.util.UUID;

public interface ReportRepository {

    void save(ReportSnapshot snapshot);

    Optional<ReportSnapshot> findById(UUID reportId);

    boolean exists(UUID treeId, ReportSnapshot.Kind kind, long watermark);
}
