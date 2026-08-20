package com.agenticform.dto;

import java.time.Instant;
import java.util.Map;

public record FormLoginResumeStatusResponse(
        boolean showResumePrompt,
        boolean submissionBlocked,
        boolean singleSubmissionLimit,
        boolean allowEditResponses,
        boolean allowNewSubmission,
        String limitTitle,
        String limitSubtitle,
        boolean hasInProgressSession,
        String inProgressSessionId,
        Long inProgressLastFieldId,
        Instant inProgressUpdatedAt,
        Map<Long, String> inProgressAnswers,
        boolean hasCompletedSubmission,
        Long completedSubmissionId,
        Instant completedSubmittedAt,
        Map<Long, String> completedAnswers) {
}
