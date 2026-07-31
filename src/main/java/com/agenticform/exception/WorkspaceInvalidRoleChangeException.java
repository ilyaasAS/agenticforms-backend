package com.agenticform.exception;

public class WorkspaceInvalidRoleChangeException extends RuntimeException {

    public WorkspaceInvalidRoleChangeException() {
        super("Invalid workspace role change");
    }
}
