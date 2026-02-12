package com.brainbyte.easy_maintenance.commons.exceptions;

public class S3Exception extends RuntimeException {

    public S3Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public S3Exception(String message) {
        super(message);
    }

}
