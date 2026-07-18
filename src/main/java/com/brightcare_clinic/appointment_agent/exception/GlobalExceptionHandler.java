package com.brightcare_clinic.appointment_agent.exception;

import com.brightcare_clinic.appointment_agent.ai.exception.GeminiException;
import com.brightcare_clinic.appointment_agent.calendar.exception.CalendarException;
import com.brightcare_clinic.appointment_agent.email.exception.EmailException;
import com.brightcare_clinic.appointment_agent.faq.exception.FaqException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CalendarException.class)
    public ResponseEntity<ErrorResponse> handleCalendar(CalendarException e, HttpServletRequest request) {
        log.error("Calendar error", e);
        return build(HttpStatus.SERVICE_UNAVAILABLE, ApiError.CALENDAR_ERROR, "The calendar service is currently unavailable.", request);
    }

    @ExceptionHandler(GeminiException.class)
    public ResponseEntity<ErrorResponse> handleGemini(GeminiException e, HttpServletRequest request) {
        log.error("Gemini error", e);
        return build(HttpStatus.SERVICE_UNAVAILABLE, ApiError.GEMINI_ERROR, "The AI service is currently unavailable.", request);
    }

    @ExceptionHandler(EmailException.class)
    public ResponseEntity<ErrorResponse> handleEmail(EmailException e, HttpServletRequest request) {
        log.error("Email error", e);
        return build(HttpStatus.SERVICE_UNAVAILABLE, ApiError.EMAIL_ERROR, "The email service is currently unavailable.", request);
    }

    @ExceptionHandler(FaqException.class)
    public ResponseEntity<ErrorResponse> handleFaq(FaqException e, HttpServletRequest request) {
        log.error("FAQ error", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.FAQ_ERROR, "The FAQ service encountered an error.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed.");
        return build(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_ERROR, message, request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleGeneric(RuntimeException e, HttpServletRequest request) {
        log.error("Unhandled error", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.INTERNAL_ERROR, "An unexpected error occurred.", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, ApiError error, String message, HttpServletRequest request) {
        ErrorResponse response = new ErrorResponse(Instant.now(), status.value(), error, message, request.getRequestURI());
        return ResponseEntity.status(status).body(response);
    }

}
