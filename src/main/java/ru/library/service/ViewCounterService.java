package ru.library.service;

import org.springframework.stereotype.Service;
import ru.library.exception.ConflictException;
import ru.library.kv.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ViewCounterService {
    private static final int MAX_ATTEMPTS = 100;

    private final KeyValueStore store;
    private final Keys keys;

    public ViewCounterService(KvTemplate kv, Keys keys) {
        this.store = kv.store();
        this.keys = keys;
    }

    public long increment(String eventId) { return incrementKey(keys.eventViews(eventId)); }

    public long get(String eventId) { return getKey(keys.eventViews(eventId)); }

    public long getKey(String key) {
        return store.get(key).map(e -> Long.parseLong(e.value())).orElse(0L);
    }

    public long incrementKey(String key) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Optional<KvEntry> cur = store.get(key);
            long next;
            Compare cmp;
            if (cur.isEmpty()) {
                next = 1;
                cmp = Compare.version(key, Compare.Op.EQUAL, 0);          // ключа ещё нет
            } else {
                next = Long.parseLong(cur.get().value()) + 1;
                cmp = Compare.modRevision(key, Compare.Op.EQUAL, cur.get().modRevision());
            }
            TxnResult r = store.txn(List.of(cmp), List.of(KvOp.put(key, Long.toString(next))), List.of());
            if (r.succeeded()) return next;
            backoff(attempt);
        }
        throw new ConflictException("Слишком высокая конкуренция за счётчик " + key);
    }

    private static void backoff(int attempt) {
        try { Thread.sleep(ThreadLocalRandom.current().nextLong(0, 1L + Math.min(attempt, 10))); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}