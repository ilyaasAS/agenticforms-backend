package com.agenticform.exception;

public class WorkspaceLastOwnerException extends RuntimeException {

    public WorkspaceLastOwnerException() {
        super("Cannot remove or demote the last owner of the workspace");
    }
}
