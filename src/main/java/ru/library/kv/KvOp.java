package ru.library.kv;

public sealed interface KvOp permits KvOp.Put, KvOp.Delete {
    record Put(String key, String value, long leaseId) implements KvOp {}
    record Delete(String key, boolean prefix) implements KvOp {}

    static Put put(String key, String value)               { return new Put(key, value, 0); }
    static Put put(String key, String value, long leaseId) { return new Put(key, value, leaseId); }
    static Delete delete(String key)                       { return new Delete(key, false); }
    static Delete deletePrefix(String prefix)              { return new Delete(prefix, true); }
}