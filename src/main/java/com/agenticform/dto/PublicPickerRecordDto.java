package com.agenticform.dto;

import java.util.List;

public record PublicPickerRecordDto(
        Long submissionId,
        Long formId,
        String label,
        List<PublicPickerFieldValueDto> fields
) {
}
