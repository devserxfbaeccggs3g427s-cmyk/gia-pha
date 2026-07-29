package com.familya.search.application.port.out;

import com.familya.search.domain.model.AutocompleteEntry;

import java.util.List;
import java.util.UUID;

public interface AutocompleteRepository {

    List<AutocompleteEntry> suggestions(String normalizedPrefix, UUID treeId, int limit);
}
