package com.pawconnect.exception;

public class MediaStorageUnavailableException extends RuntimeException {
    public MediaStorageUnavailableException(String message) {
        super(message);
    }

    public MediaStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
