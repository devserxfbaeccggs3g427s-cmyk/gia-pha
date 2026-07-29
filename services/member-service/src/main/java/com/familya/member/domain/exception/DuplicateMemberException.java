package com.familya.member.domain.exception;

public class DuplicateMemberException extends RuntimeException {
    public DuplicateMemberException(String message) { super(message); }
}