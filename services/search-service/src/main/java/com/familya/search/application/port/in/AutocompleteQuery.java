package com.familya.search.application.port.in;

import java.util.UUID;

public record AutocompleteQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String prefix,
        Integer limit) {
}
