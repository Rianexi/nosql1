package ru.library.kv;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface KeyValueStore {
    Optional<KvEntry> get(String key);
    List<KvEntry> getPrefix(String prefix);
    long put(String key, String value);
    long put(String key, String value, long leaseId);
    long delete(String key);
    long deletePrefix(String prefix);

    TxnResult txn(List<Compare> conditions, List<KvOp> thenOps, List<KvOp> elseOps);

    long leaseGrant(long ttlSeconds);
    void leaseRevoke(long leaseId);
    long leaseKeepAlive(long leaseId);
    long leaseTimeToLive(long leaseId);

    AutoCloseable watch(String prefix, Consumer<WatchEvent> listener);

    void snapshotSave(String path);
    void snapshotRestore(String path);

    long currentRevision();
}