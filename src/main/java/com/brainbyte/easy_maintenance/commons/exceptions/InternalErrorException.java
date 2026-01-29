package com.brainbyte.easy_maintenance.commons.exceptions;

public class InternalErrorException extends RuntimeException {

    public InternalErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    public InternalErrorException(String message) {
        super(message);
    }

}
