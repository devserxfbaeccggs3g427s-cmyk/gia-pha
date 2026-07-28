package com.familya.relationship.domain.exception;

public class DuplicateRelationshipException extends RuntimeException {
    public DuplicateRelationshipException(String message) { super(message); }
}