package com.agenticform.dto;

import java.util.List;

/**
 * Configuration de la page Révision (relecture avant envoi).
 * Persistée dans pages_json.
 */
public record ReviewConfigDto(
        /** Afficher les liens « Modifier » à côté des réponses. */
        Boolean showEditButtons,
        /** Inclure les champs masqués par la logique de visibilité. */
        Boolean showHiddenFields,
        /**
         * IDs des champs à afficher.
         * null = tous les champs du formulaire (hors display).
         */
        List<Long> visibleFieldIds,
        /** Libellé du bouton d’envoi (défaut : Soumettre). */
        String submitButtonText,
        /** Alignement du bloc bouton : left | center | right | stretch. */
        String submitButtonAlign,
        /** Couleur de fond du bouton (null = thème). */
        String submitButtonBg,
        /** Afficher le lien « Passer ». */
        Boolean showSkipButton,
        /** Libellé du lien Passer. */
        String skipButtonText,
        /** Liste Revoir : toujours masquée. */
        Boolean summaryHideAlways,
        /** Liste Revoir : show_when | hide_when. */
        String summaryHideMode,
        /** Liste Revoir : conditions de visibilité. */
        VisibilityNodeDto summaryVisibilityLogic,
        /** Bouton : conditions de désactivation. */
        VisibilityNodeDto buttonDisableLogic,
        /** Bouton : show_when | hide_when. */
        String buttonHideMode,
        /** Bouton : conditions de visibilité. */
        VisibilityNodeDto buttonVisibilityLogic
) {
}
