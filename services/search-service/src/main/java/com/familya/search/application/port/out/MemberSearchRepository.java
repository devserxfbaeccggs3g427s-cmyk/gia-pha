package com.familya.search.application.port.out;

import com.familya.search.application.port.in.SearchMembersCommand;
import com.familya.search.domain.model.MemberSearchDocument;

import java.util.List;
import java.util.UUID;

public interface MemberSearchRepository {

    List<MemberSearchDocument> search(SearchMembersCommand.MemberFilter filter,
                                      String normalizedQuery,
                                      UUID treeId,
                                      int limit);

    /**
     * Delete every member document for the tree. Default returns 0 so existing
     * services stay binary-compatible; JdbcMemberSearchRepository overrides
     * this with a real DELETE statement.
     */
    default long deleteByTree(UUID treeId) { return 0L; }
}