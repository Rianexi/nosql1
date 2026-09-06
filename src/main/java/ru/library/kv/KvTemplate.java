package ru.library.kv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class KvTemplate {

    private final KeyValueStore store;
    private final ObjectMapper mapper;

    public KvTemplate(KeyValueStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    public KeyValueStore store() { return store; }

    public String toJson(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new KvException("serialize failed", e); }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JsonProcessingException e) { throw new KvException("deserialize failed", e); }
    }

    public <T> void put(String key, T value)                       { store.put(key, toJson(value)); }
    public <T> void putWithLease(String key, T value, long leaseId) { store.put(key, toJson(value), leaseId); }

    public <T> Optional<Versioned<T>> get(String key, Class<T> type) {
        return store.get(key).map(e -> wrap(e, type));
    }

    public <T> List<Versioned<T>> getPrefix(String prefix, Class<T> type) {
        return store.getPrefix(prefix).stream().map(e -> wrap(e, type)).toList();
    }

    public List<String> keysWithPrefix(String prefix) {
        return store.getPrefix(prefix).stream().map(KvEntry::key).toList();
    }

    public long delete(String key)         { return store.delete(key); }
    public long deletePrefix(String prefix) { return store.deletePrefix(prefix); }

    public <T> boolean compareAndPut(String key, long expectedModRevision, T value) {
        return store.txn(
                List.of(Compare.modRevision(key, Compare.Op.EQUAL, expectedModRevision)),
                List.of(KvOp.put(key, toJson(value))),
                List.of()).succeeded();
    }

    public <T> boolean putIfAbsent(String key, T value) {
        return store.txn(
                List.of(Compare.version(key, Compare.Op.EQUAL, 0)),
                List.of(KvOp.put(key, toJson(value))),
                List.of()).succeeded();
    }

    private <T> Versioned<T> wrap(KvEntry e, Class<T> type) {
        return new Versioned<>(fromJson(e.value(), type), e.modRevision(), e.version(), e.leaseId());
    }
}