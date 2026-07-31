package com.agenticform.dto;

import java.util.List;

public record FormResultsFieldResponse(
        Long id,
        String label,
        String fieldType,
        int displayOrder,
        List<String> options
) {
}
