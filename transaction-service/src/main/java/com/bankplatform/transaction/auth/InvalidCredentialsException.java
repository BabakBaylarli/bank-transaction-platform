package com.bankplatform.transaction.auth;

import com.bankplatform.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Invalid credentials",
                "Username or password is incorrect");
    }
}
