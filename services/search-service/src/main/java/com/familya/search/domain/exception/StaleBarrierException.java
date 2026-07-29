package com.familya.search.domain.exception;

public class StaleBarrierException extends RuntimeException {
    public StaleBarrierException(String message) { super(message); }
}
