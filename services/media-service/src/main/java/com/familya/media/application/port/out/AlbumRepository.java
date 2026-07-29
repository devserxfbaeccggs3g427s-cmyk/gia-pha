package com.familya.media.application.port.out;

import com.familya.media.domain.model.Album;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlbumRepository {

    void insert(Album album);

    Optional<Album> findById(UUID id);

    List<Album> listByTree(UUID treeId, boolean includeTombstoned);

    void update(Album album);

    void tombstone(java.util.UUID id, java.time.Instant at, long expectedVersion);
}
