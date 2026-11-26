package com.bankplatform.common.error;

import org.springframework.http.HttpStatus;

/** The request is well-formed but violates a business rule (HTTP 422). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String errorCode, String title, String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, title, detail);
    }
}
