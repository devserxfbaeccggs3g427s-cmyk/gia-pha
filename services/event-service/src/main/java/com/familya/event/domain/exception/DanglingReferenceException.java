package com.familya.event.domain.exception;

public class DanglingReferenceException extends RuntimeException {
    public DanglingReferenceException(String message) { super(message); }
}