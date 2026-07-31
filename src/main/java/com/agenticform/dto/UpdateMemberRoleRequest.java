package com.agenticform.dto;

import jakarta.validation.constraints.NotNull;

import com.agenticform.model.entity.WorkspaceRole;

public record UpdateMemberRoleRequest(
        @NotNull(message = "Role is required")
        WorkspaceRole role
) {
}
