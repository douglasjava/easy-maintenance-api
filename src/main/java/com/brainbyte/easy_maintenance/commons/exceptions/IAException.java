package com.brainbyte.easy_maintenance.commons.exceptions;

public class IAException extends RuntimeException {

    public IAException(String message) {
        super(message);
    }

    public IAException(String message, Throwable cause) {
        super(message, cause);
    }

}
