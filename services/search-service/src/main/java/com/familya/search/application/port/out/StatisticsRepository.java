package com.familya.search.application.port.out;

import com.familya.search.domain.model.StatisticsSnapshot;

import java.util.Optional;
import java.util.UUID;

public interface StatisticsRepository {

    StatisticsSnapshot compute(UUID treeId, long watermark);

    Optional<StatisticsSnapshot> latest(UUID treeId);

    default long deleteByTree(UUID treeId) { return 0L; }
}