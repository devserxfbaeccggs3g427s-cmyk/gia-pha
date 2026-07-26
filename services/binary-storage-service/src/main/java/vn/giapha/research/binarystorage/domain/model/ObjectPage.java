package vn.giapha.research.binarystorage.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * One page of a prefix listing; {@code cursor} is non-null exactly when
 * {@code hasMore} is true.
 */
public record ObjectPage(List<StoredObjectSummary> objects, boolean hasMore, String cursor) {

    public ObjectPage {
        objects = List.copyOf(Objects.requireNonNull(objects, "objects"));
    }
}
