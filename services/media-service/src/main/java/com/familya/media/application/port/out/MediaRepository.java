package com.familya.media.application.port.out;

import com.familya.media.domain.model.MediaAsset;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository {

    void insert(MediaAsset asset);

    Optional<MediaAsset> findById(UUID id);

    List<MediaAsset> listByTree(UUID treeId, boolean includeTombstoned);

    List<MediaAsset> listByAlbum(UUID albumId, boolean includeTombstoned);

    void update(MediaAsset asset);

    void tombstone(UUID id, Instant at, long expectedVersion);

    List<MediaAsset> listAfter(Instant watermark, int limit);

    /** Atomically claim a media row for processing; returns true if the row was claimed. */
    boolean claimForProcessing(UUID mediaId, long expectedVersion);

    void markScanning(UUID mediaId, long expectedVersion, Instant at);

    void markReady(UUID mediaId, long expectedVersion, Instant at);

    void markFailed(UUID mediaId, long expectedVersion, Instant at, String reason);

    /** Sets the quarantine path used by scanner/thumbnail adapters. */
    void recordQuarantinePath(UUID mediaId, String path, long expectedVersion);
}
