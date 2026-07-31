package com.agenticform.dto;

import java.util.List;

public record FormResultsResponse(
        Long formId,
        String title,
        long submissionCount,
        long viewCount,
        Double completionRate,
        List<FormResultsFieldResponse> fields,
        List<FormSubmissionRowResponse> submissions
) {
}
