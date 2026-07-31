package com.agenticform.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FormPageDto(
        @NotBlank
        @Size(max = 64)
        String id,

        @NotBlank
        @Size(max = 32)
        String type,

        @Size(max = 255)
        String title,

        @Size(max = 5000)
        String description,

        List<Long> fieldIds,

        @Size(max = 255)
        String buttonText,

        String imageAboveTitle,

        String coverMediaUrl,

        @Size(max = 32)
        String customCoverLayout
) {
}
