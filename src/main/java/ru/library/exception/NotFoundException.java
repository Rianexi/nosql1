package ru.library.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String m) { super(m); }
}