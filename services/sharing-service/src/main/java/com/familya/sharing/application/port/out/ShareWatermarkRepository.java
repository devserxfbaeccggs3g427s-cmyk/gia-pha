package com.familya.sharing.application.port.out;

import java.util.*;

public interface ShareWatermarkRepository {
    long watermark(UUID treeId);

    void update(UUID treeId, long watermark, long version);

    Optional<Map<String, Object>> projection(UUID treeId, com.familya.sharing.domain.model.ShareLink.Scope scope, UUID targetId);

    void saveProjection(UUID treeId, com.familya.sharing.domain.model.ShareLink.Scope scope, UUID targetId, Map<String, Object> value, long watermark);
}
