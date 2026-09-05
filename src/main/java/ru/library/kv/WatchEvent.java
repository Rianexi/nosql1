package ru.library.kv;

public record WatchEvent(Type type, KvEntry entry, KvEntry previous) {
    public enum Type { PUT, DELETE }
}