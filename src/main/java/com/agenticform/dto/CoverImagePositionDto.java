package com.agenticform.dto;

/**
 * Point focal de l’image de couverture (object-position / background-position en % + zoom).
 */
public record CoverImagePositionDto(Double x, Double y, Double scale) {
}
