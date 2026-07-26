package vn.giapha.research.transfer.application.service;

import java.util.Map;

import vn.giapha.research.transfer.support.Principal;
import vn.giapha.research.transfer.domain.model.ImportDuplicateStrategy;
import vn.giapha.research.transfer.domain.model.ImportFormat;

/**
 * Pluggable import parser (Task 29.1). Implementations parse outside any DB
 * transaction so an oversized or malformed input never opens one; the
 * {@link ImportService} commits the final tree mutation only after the
 * parser reports a clean result.
 */
public interface ImportParser {

    ImportFormat format();

    /** Parse-only — never mutates the tree; returns counts and errors. */
    ImportService.ParseResult parse(byte[] payload);

    /** Parse + apply — runs inside a single MySQL transaction. */
    void parseAndApply(String treeExternalId, byte[] payload,
            ImportDuplicateStrategy strategy, Principal principal);
}
