package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.graph.GraphAlgorithms;
import com.familya.relationship.domain.model.Relationship;

import java.util.*;

/**
 * Read-only access to the genealogy algorithms. Used by REST/GraphQL
 * adapters and by the migration reconciliation endpoint.
 */
@org.springframework.stereotype.Service
public class QueryGraphUseCase {

    private final RelationshipRepository repo;

    public QueryGraphUseCase(RelationshipRepository repo) {
        this.repo = repo;
    }

    public Map<UUID, Integer> generations(UUID treeId, UUID root) {
        return GraphAlgorithms.generations(repo.listByTree(treeId, false), root);
    }

    public Set<UUID> ancestors(UUID treeId, UUID member) {
        return GraphAlgorithms.ancestors(repo.listByTree(treeId, true), member);
    }

    public Set<UUID> spouses(UUID treeId, UUID member) {
        return GraphAlgorithms.spouses(repo.listByTree(treeId, false), member);
    }

    public Set<UUID> adoptions(UUID treeId, UUID member) {
        return GraphAlgorithms.adoptions(repo.listByTree(treeId, false), member);
    }

    public List<Relationship> list(UUID treeId, boolean includeTombstoned) {
        return repo.listByTree(treeId, includeTombstoned);
    }
}