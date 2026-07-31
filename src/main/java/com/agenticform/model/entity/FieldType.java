package com.agenticform.model.entity;

/**
 * Types de champs dynamiques supportés par un formulaire.
 * Les types DISPLAY_* / layout sont des stubs UI (aperçu builder + rendu public minimal).
 */
public enum FieldType {

    TEXT,
    EMAIL,
    NUMBER,
    TEXTAREA,
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    CHECKBOX,
    RATING,
    DATE,

    PHONE,
    DROPDOWN,
    MULTISELECT,
    SWITCH,
    DATE_TIME,
    TIME,
    DATE_RANGE,
    RANKING,
    SLIDER,
    OPINION_SCALE,
    RICH_TEXT,
    CURRENCY,
    URL,
    COLOR,
    PASSWORD,
    FILE,
    SIGNATURE,
    VOICE,
    CAPTCHA,
    LOCATION,
    TABLE,
    ADDRESS,
    PICTURE_CHOICE,
    CHOICE_MATRIX,

    DISPLAY_H1,
    DISPLAY_HEADING,
    DISPLAY_PARAGRAPH,
    DISPLAY_BANNER,
    DISPLAY_IMAGE,
    DISPLAY_VIDEO,
    DISPLAY_PDF,
    DISPLAY_HTML,
    DISPLAY_DIVIDER,
    DISPLAY_SOCIAL,

    SECTION_COLLAPSE,
    PROGRESS_BAR,
    SUBMISSION_PICKER,
    SUBFORM
}
