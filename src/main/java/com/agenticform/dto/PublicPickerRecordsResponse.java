package com.agenticform.dto;

import java.util.List;

public record PublicPickerRecordsResponse(
        List<PublicPickerRecordDto> records
) {
}
