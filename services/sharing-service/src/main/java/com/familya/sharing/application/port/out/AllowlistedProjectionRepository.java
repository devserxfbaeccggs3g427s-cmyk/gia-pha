package com.familya.sharing.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface AllowlistedProjectionRepository {

    long readWatermark(UUID treeId, String domain);

    long advanceWatermark(UUID treeId, String domain, long watermark, Instant at);

    void saveMember(UUID treeId, UUID memberId, Map<String, Object> allowlisted, boolean tombstoned, Instant at);

    void saveMedia(UUID treeId, UUID mediaId, Map<String, Object> allowlisted, boolean tombstoned, Instant at);

    void saveEvent(UUID treeId, UUID eventId, Map<String, Object> allowlisted, boolean tombstoned, Instant at);

    void saveRelationship(UUID treeId, UUID relId, Map<String, Object> allowlisted, boolean tombstoned, Instant at);

    void saveTree(UUID treeId, Map<String, Object> allowlisted, boolean tombstoned, Instant at);

    void savePublicProjection(UUID treeId, ShareScope scope, UUID targetId, Map<String, Object> value, long watermark, Instant at);

    Map<String, Object> readPublicProjection(UUID treeId, ShareScope scope, UUID targetId);

    enum ShareScope { TREE, MEMBER, MEDIA, EVENT }
}
