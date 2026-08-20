package com.agenticform.dto;

import jakarta.validation.constraints.NotBlank;

public record FormGoogleLoginExchangeRequest(@NotBlank String code) {
}
