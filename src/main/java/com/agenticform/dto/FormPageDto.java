package com.agenticform.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

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

        /** Nom d’onglet (indépendant du titre de contenu). */
        @Size(max = 255)
        String navLabel,

        @Size(max = 5000)
        String description,

        List<Long> fieldIds,

        @Size(max = 255)
        String buttonText,

        /** Image / logo au-dessus du titre (legacy alias: imageAboveTitle). */
        @JsonAlias("imageAboveTitle")
        String headerImage,

        /** Média des dispositions split / full (legacy alias: coverMediaUrl). */
        @JsonAlias("coverMediaUrl")
        String coverLayoutMedia,

        /** Point focal du média de disposition (x/y en %). */
        CoverImagePositionDto coverImagePosition,

        @Size(max = 32)
        String customCoverLayout,

        /** Configuration page de fin (Merci). */
        EndingConfigDto endingConfig,

        /** Configuration page Révision (relecture avant envoi). */
        ReviewConfigDto reviewConfig,

        /** Configuration page Connexion. */
        LoginConfigDto loginConfig,

        /** Configuration page Paiement. */
        PaymentConfigDto paymentConfig,

        /** Blocs partagés (Titre, Image…) — même modèle que la page de fin. */
        List<EndingBlockDto> contentBlocks,

        /** Ordre unifié canvas : "block:<id>" | "field:<id>". */
        List<String> canvasOrder,

        /** Id d’étape de la barre de progression pour cette page. */
        @Size(max = 64)
        String progressStepId
) {
}
