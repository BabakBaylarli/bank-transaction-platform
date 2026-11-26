package com.bankplatform.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String errorCode, String title, String detail) {
        super(HttpStatus.CONFLICT, errorCode, title, detail);
    }
}
