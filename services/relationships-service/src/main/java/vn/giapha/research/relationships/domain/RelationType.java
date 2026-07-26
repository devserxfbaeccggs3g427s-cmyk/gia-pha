package vn.giapha.research.relationships.domain;

/**
 * Legacy-frozen relationship types. {@code PARENT_CHILD} edges are directional
 * (source = parent, target = child); the remaining types are symmetric.
 */
public enum RelationType {
    PARENT_CHILD,
    SPOUSE,
    SIBLING,
    ADOPTED,
    CUSTOM;

    public boolean isDirectional() {
        return this == PARENT_CHILD;
    }
}
