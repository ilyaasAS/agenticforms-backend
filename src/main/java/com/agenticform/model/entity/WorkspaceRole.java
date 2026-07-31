package com.agenticform.model.entity;

/**
 * Rôle au sein d'un workspace — distinct des rôles plateforme ({@link Role}).
 */
public enum WorkspaceRole {

    MEMBER(0),
    ADMIN(1),
    OWNER(2);

    private final int level;

    WorkspaceRole(int level) {
        this.level = level;
    }

    /** {@code true} si ce rôle est au moins aussi élevé que {@code required}. */
    public boolean isAtLeast(WorkspaceRole required) {
        return this.level >= required.level;
    }
}
