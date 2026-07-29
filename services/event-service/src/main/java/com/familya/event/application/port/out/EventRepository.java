package com.familya.event.application.port.out;

import com.familya.event.domain.model.DomainEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository {

    void insert(DomainEvent event);

    Optional<DomainEvent> findById(UUID id);

    List<DomainEvent> listByTree(UUID treeId, boolean includeTombstoned);

    void update(DomainEvent event);
}