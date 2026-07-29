package com.familya.media.domain.exception;

public class UploadTooLargeException extends RuntimeException {
    public UploadTooLargeException(String message) { super(message); }
}
