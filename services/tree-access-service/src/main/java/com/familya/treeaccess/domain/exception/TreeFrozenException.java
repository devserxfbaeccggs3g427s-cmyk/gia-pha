package com.familya.treeaccess.domain.exception;

public class TreeFrozenException extends RuntimeException {
    public TreeFrozenException(String message) { super(message); }
}