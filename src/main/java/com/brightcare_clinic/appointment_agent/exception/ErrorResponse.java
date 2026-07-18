package com.brightcare_clinic.appointment_agent.exception;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, ApiError error, String message, String path) {
}
