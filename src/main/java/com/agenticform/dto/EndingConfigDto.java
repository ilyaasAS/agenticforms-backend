package com.agenticform.dto;

import java.util.List;

/**
 * Configuration de la page de fin (Merci).
 * Persistée dans pages_json.
 */
public record EndingConfigDto(
        /** page | new_form | redirect */
        String type,
        Boolean confetti,
        String redirectUrl,
        String targetFormId,
        Boolean hideIcon,
        Boolean hideBranding,
        Boolean hideMainBlock,
        Boolean showSchedulingDetails,
        Boolean showPaymentDetails,
        List<EndingBlockDto> blocks
) {
}
