package com.familya.treeaccess.application.port.out;

import com.familya.treeaccess.domain.model.Tree;
import com.familya.treeaccess.domain.model.TreeMembership;
import com.familya.treeaccess.domain.model.AuthorizationProjection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreeRepository {

    void insertTree(Tree tree, TreeMembership ownerMembership);

    Optional<Tree> findTree(UUID treeId);

    List<Tree> findTreesByOwner(UUID ownerUserId);

    void updateTree(Tree tree);

    void insertMembership(TreeMembership membership);

    Optional<TreeMembership> findMembership(UUID treeId, UUID userId);

    List<TreeMembership> listMemberships(UUID treeId);

    void updateMembership(TreeMembership membership);

    void upsertProjection(AuthorizationProjection projection);

    Optional<AuthorizationProjection> findProjection(UUID treeId, UUID userId);

    long currentRevision(UUID treeId);
}