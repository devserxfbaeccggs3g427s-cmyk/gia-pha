package com.familya.sharing.application.port.out;

import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.outbox.OutboxRecord;

import java.util.List;
import java.util.UUID;

public interface ShareChangePublisher {
    void stage(OutboxRecord record);

    void shareLinkCreated(ShareLink link);

    void shareLinkRevoked(ShareLink link);

    void projectionRebuilt(UUID treeId, long watermark);

    List<OutboxRecord> listPending(int limit);

    void markPublished(UUID id);
}
