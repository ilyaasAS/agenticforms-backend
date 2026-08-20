package com.agenticform.dto;

import java.util.List;

public record CalendlyStatusResponse(boolean configured, boolean connected, String email) {
}
