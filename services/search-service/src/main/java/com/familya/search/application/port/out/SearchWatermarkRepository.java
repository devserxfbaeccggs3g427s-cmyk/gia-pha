package com.familya.search.application.port.out;

import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.model.Watermark;

import java.util.Optional;
import java.util.UUID;

public interface SearchWatermarkRepository {

    Optional<Watermark> find(UUID treeId, Watermark.Domain domain);

    RevisionBarrier barrierFor(UUID treeId);

    void advance(UUID treeId, Watermark.Domain domain, long value);
}
