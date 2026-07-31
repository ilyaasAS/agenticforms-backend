package com.agenticform.dto;

public record OAuth2LoginResponse(
        AuthResponse.UserInfo user
) {
}
