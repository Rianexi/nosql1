package ru.library.kv;

public record KvEntry(String key, String value, long createRevision, long modRevision, long version, long leaseId) {}