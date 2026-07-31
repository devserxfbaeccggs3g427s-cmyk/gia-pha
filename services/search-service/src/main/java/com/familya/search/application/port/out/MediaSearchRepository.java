package com.familya.search.application.port.out;

import com.familya.search.application.port.in.SearchMediaCommand;
import com.familya.search.domain.model.MediaSearchDocument;

import java.util.List;
import java.util.UUID;

public interface MediaSearchRepository {

    List<MediaSearchDocument> search(SearchMediaCommand.MediaFilter filter,
                                     String normalizedQuery,
                                     UUID treeId,
                                     int limit);

    default long deleteByTree(UUID treeId) { return 0L; }
}