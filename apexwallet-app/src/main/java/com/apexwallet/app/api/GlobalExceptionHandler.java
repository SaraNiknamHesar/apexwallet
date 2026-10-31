package com.apexwallet.app.api;

import com.apexwallet.domain.error.DomainException;
import com.apexwallet.domain.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException exception) {
        HttpStatus status = status(exception.code());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(exception.code().name());
        problem.setType(URI.create("https://apexwallet.local/errors/" + exception.code().name()));
        problem.setProperty("code", exception.code().name());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "validation failed");
        problem.setTitle("VALIDATION_FAILED");
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("fields", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "unexpected error");
        problem.setProperty("code", "INTERNAL");
        return problem;
    }

    private static HttpStatus status(ErrorCode code) {
        return switch (code) {
            case CURRENCY_MISMATCH, INSUFFICIENT_FUNDS, NEGATIVE_AMOUNT, UNBALANCED_TRANSACTION,
                    ACCOUNT_NOT_ACTIVE, VALIDATION_FAILED, DAILY_LIMIT -> HttpStatus.UNPROCESSABLE_CONTENT;
            case IDEMPOTENCY_KEY_REQUIRED -> HttpStatus.BAD_REQUEST;
            case IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS, ILLEGAL_TRANSITION, DUPLICATE, OPTIMISTIC_LOCK ->
                    HttpStatus.CONFLICT;
            case NOT_FOUND, ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN, SELF_APPROVAL -> HttpStatus.FORBIDDEN;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case OVERFLOW, APPROVAL_REQUIRED -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }
}
