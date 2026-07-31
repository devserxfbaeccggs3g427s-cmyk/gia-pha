package com.familya.member.domain.exception;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(String message) { super(message); }
}