package com.familya.relationship.domain.exception;

public class DanglingMemberReferenceException extends RuntimeException {
    public DanglingMemberReferenceException(String message) { super(message); }
}