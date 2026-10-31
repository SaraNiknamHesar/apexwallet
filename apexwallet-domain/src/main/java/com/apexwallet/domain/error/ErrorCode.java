package com.apexwallet.domain.error;

/**
 * Stable codes. The HTTP layer maps them; callers and tests depend on the name, not on message text.
 */
public enum ErrorCode {
    CURRENCY_MISMATCH,
    UNBALANCED_TRANSACTION,
    INSUFFICIENT_FUNDS,
    NEGATIVE_AMOUNT,
    OVERFLOW,
    ACCOUNT_NOT_FOUND,
    ACCOUNT_NOT_ACTIVE,
    ILLEGAL_TRANSITION,
    IDEMPOTENCY_KEY_REQUIRED,
    IDEMPOTENCY_CONFLICT,
    IDEMPOTENCY_IN_PROGRESS,
    OPTIMISTIC_LOCK,
    SELF_APPROVAL,
    APPROVAL_REQUIRED,
    FORBIDDEN,
    NOT_FOUND,
    UNAUTHORIZED,
    VALIDATION_FAILED,
    DAILY_LIMIT,
    RATE_LIMITED,
    DUPLICATE
}
