package com.familya.search.domain.model;

import java.util.UUID;

public record AutocompleteEntry(
        UUID treeId,
        UUID ownerId,
        String surface,
        String normalizedPrefix,
        int weight
) { }
