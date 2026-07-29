package com.familya.sharing.application.port.out;

import com.familya.sharing.domain.model.ShareLink;

import java.util.*;

public interface ShareLinkRepository {
    void insert(ShareLink link);

    void update(ShareLink link);

    Optional<ShareLink> findById(UUID id);

    Optional<ShareLink> findByTokenHash(String hash);

    List<ShareLink> listByTree(UUID treeId);
}
