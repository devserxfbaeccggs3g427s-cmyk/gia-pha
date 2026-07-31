package com.familya.event.application.usecase;

import com.familya.event.application.port.out.EventRepository;
import com.familya.event.application.port.out.ReferenceAvailability;
import com.familya.event.domain.model.DomainEvent;

import java.util.*;

@org.springframework.stereotype.Service
public class QueryDomainEventUseCase {

    private final EventRepository repo;
    private final ReferenceAvailability refs;

    public QueryDomainEventUseCase(EventRepository repo, ReferenceAvailability refs) {
        this.repo = repo;
        this.refs = refs;
    }

    public Optional<DomainEvent> findById(UUID id) {
        return repo.findById(id);
    }

    public List<DomainEvent> listByTree(UUID treeId, boolean includeTombstoned) {
        return repo.listByTree(treeId, includeTombstoned);
    }

    /**
     * Returns the set of dangling references in the event payload.
     * Used by the reconciliation endpoint to surface projection lag.
     */
    public Set<UUID> danglingReferences(DomainEvent ev) {
        Set<UUID> all = new HashSet<>();
        if (ev.primaryMemberId() != null) all.add(ev.primaryMemberId());
        all.addAll(ev.additionalMemberIds());
        return refs.danglingMembers(ev.treeId(), all);
    }
}