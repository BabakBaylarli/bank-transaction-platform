package com.bankplatform.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected, business-level failures. The global handler converts it to a
 * {@code ProblemDetail} with {@code type = https://bank-platform.dev/problems/<errorCode>}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final String title;

    protected ApiException(HttpStatus status, String errorCode, String title, String detail) {
        super(detail);
        this.status = status;
        this.errorCode = errorCode;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public String title() {
        return title;
    }
}
