package com.agenticform.dto;

public record FormLoginVerifyResponse(
        boolean verified,
        String email) {
}
