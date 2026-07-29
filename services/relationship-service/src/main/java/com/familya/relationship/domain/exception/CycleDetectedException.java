package com.familya.relationship.domain.exception;

public class CycleDetectedException extends RuntimeException {
    public CycleDetectedException(String message) { super(message); }
}