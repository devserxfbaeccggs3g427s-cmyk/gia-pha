package com.familya.media.application.port.out;

import com.familya.media.domain.model.MediaAsset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository {

    void insert(MediaAsset asset);

    Optional<MediaAsset> findById(UUID id);

    List<MediaAsset> listByTree(UUID treeId, boolean includeTombstoned);

    List<MediaAsset> listByAlbum(UUID albumId, boolean includeTombstoned);

    void update(MediaAsset asset);

    void tombstone(UUID id, java.time.Instant at, long expectedVersion);

    /** Watermark-based delta read used by the reconciliation endpoint. */
    List<MediaAsset> listAfter(java.time.Instant watermark, int limit);
}
