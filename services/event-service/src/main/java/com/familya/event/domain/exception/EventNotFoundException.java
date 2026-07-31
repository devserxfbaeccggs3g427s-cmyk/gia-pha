package com.familya.event.domain.exception;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(String message) { super(message); }
}