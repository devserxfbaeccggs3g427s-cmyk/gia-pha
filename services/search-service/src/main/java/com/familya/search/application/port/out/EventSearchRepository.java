package com.familya.search.application.port.out;

import com.familya.search.application.port.in.SearchEventsCommand;
import com.familya.search.domain.model.EventSearchDocument;

import java.util.List;
import java.util.UUID;

public interface EventSearchRepository {

    List<EventSearchDocument> search(SearchEventsCommand.EventFilter filter,
                                     String normalizedQuery,
                                     UUID treeId,
                                     int limit);

    default long deleteByTree(UUID treeId) { return 0L; }
}