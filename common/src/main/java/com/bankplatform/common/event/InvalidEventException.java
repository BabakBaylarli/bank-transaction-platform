package com.bankplatform.common.event;

/** Thrown for payloads that can never be processed. Not retried; goes straight to the DLT. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
