package ru.library.kv;

public class KvException extends RuntimeException {
    public KvException(String message) { super(message); }
    public KvException(String message, Throwable cause) { super(message, cause); }
}