package com.familya.member.domain.exception;

public class MemberTombstonedException extends RuntimeException {
    public MemberTombstonedException(String message) { super(message); }
}