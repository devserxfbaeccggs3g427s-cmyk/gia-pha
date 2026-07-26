package vn.giapha.research.tree.infrastructure.kernel.principal;

public enum TreeRole {
    ADMIN,
    EDITOR,
    VIEWER;

    public boolean atLeast(TreeRole required) {
        return ordinal() <= required.ordinal();
    }

    public static TreeRole fromWire(String value) {
        return TreeRole.valueOf(value);
    }
}
