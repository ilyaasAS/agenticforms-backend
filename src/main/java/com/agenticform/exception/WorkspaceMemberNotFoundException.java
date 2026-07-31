package com.agenticform.exception;

public class WorkspaceMemberNotFoundException extends RuntimeException {

    public WorkspaceMemberNotFoundException() {
        super("Workspace member not found");
    }

    public WorkspaceMemberNotFoundException(Long workspaceId, Long userId) {
        super("Workspace member not found: workspaceId=" + workspaceId + ", userId=" + userId);
    }
}
