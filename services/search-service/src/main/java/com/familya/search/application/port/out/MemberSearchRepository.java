package com.familya.search.application.port.out;

import com.familya.search.application.port.in.SearchMembersCommand;
import com.familya.search.domain.model.MemberSearchDocument;

import java.util.List;
import java.util.UUID;

/**
 * jOOQ / explicit SQL search over the member projection. All
 * string comparisons are performed by the database using
 * {@code utf8mb4_unicode_ci} collation; the application layer
 * never pre-normalises.
 */
public interface MemberSearchRepository {

    List<MemberSearchDocument> search(SearchMembersCommand.MemberFilter filter,
                                      String normalizedQuery,
                                      UUID treeId,
                                      int limit);
}
