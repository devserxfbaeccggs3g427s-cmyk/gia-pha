package com.familya.sharing.application.port.out;

import com.familya.sharing.domain.model.ShareLink;

public interface ShareChangePublisher {
    void created(ShareLink link);

    void revoked(ShareLink link);

    void projectionRebuilt(java.util.UUID treeId, long watermark, long version);
}
