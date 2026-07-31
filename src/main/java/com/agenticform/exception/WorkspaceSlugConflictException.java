package com.agenticform.exception;

public class WorkspaceSlugConflictException extends RuntimeException {

    public WorkspaceSlugConflictException(String slug) {
        super("Workspace slug already exists: " + slug);
    }
}
