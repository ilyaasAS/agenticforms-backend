package com.agenticform.exception;

public class WorkspaceNotFoundException extends RuntimeException {

    public WorkspaceNotFoundException() {
        super("Workspace not found");
    }

    public WorkspaceNotFoundException(Long workspaceId) {
        super("Workspace not found: id=" + workspaceId);
    }
}
