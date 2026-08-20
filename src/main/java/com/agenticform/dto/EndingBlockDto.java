package com.agenticform.dto;

import java.util.List;

/**
 * Bloc éditable de la page de fin (ordre / type catalogue).
 * Persisté dans pages_json via EndingConfigDto.
 */
public record EndingBlockDto(
        String id,
        String type,
        String label,
        String text,
        String description,
        String url,
        /** left | center | right | justify | stretch */
        String alignment,
        /** h1 | h2 | h3 | h4 | h5 | body */
        String level,
        String src,
        String alt,
        String html,
        String css,
        Boolean allowScripts,
        Boolean renderInIframe,
        String buttonText,
        String buttonColor,
        Boolean openInNewTab,
        /** fill_again : recommencer depuis une page choisie */
        Boolean restartFromPage,
        /** fill_again : id de page cible */
        String restartPageId,
        /** solid | dashed | dotted */
        String borderStyle,
        /** info | warning | error | success */
        String alertType,
        Boolean halfWidth,
        /** Hauteur max (px) pour video / image */
        Integer maxHeight,
        Boolean hideAlways,
        /** show_when | hide_when */
        String hideConditionMode,
        /** Liens du bloc réseaux sociaux */
        List<EndingSocialLinkDto> socialLinks,
        /** solid | outline | clear */
        String iconStyle,
        VisibilityNodeDto visibilityLogic
) {
}
