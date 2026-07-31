package com.agenticform.dto;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(
        @NotNull(message = "New owner user id is required")
        Long newOwnerUserId
) {
}
