package com.apexwallet.domain.error;

/**
 * Business failure. The message is for logs and humans; {@link #code()} is the contract.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
