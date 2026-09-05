package ru.library.kv;

public record Versioned<T>(T value, long modRevision, long version, long leaseId) {}