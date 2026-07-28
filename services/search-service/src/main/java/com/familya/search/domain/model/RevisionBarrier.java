package com.familya.search.domain.model;

import java.util.Map;
import java.util.UUID;

public record RevisionBarrier(
        UUID treeId,
        Map<Watermark.Domain, Long> values
) { }
