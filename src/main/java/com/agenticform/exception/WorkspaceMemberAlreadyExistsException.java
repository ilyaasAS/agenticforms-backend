package com.agenticform.exception;

public class WorkspaceMemberAlreadyExistsException extends RuntimeException {

    public WorkspaceMemberAlreadyExistsException() {
        super("User is already a member of this workspace");
    }
}
