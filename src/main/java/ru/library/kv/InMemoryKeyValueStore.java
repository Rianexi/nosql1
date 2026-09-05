package ru.library.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "library.kv.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryKeyValueStore implements KeyValueStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryKeyValueStore.class);

    private final ConcurrentSkipListMap<String, KvEntry> data = new ConcurrentSkipListMap<>();
    private final Map<Long, Lease> leases = new ConcurrentHashMap<>();
    private final List<Watcher> watchers = new CopyOnWriteArrayList<>();
    private final AtomicLong revision = new AtomicLong();
    private final AtomicLong leaseSeq = new AtomicLong(0x7000_0000L);
    private final Object lock = new Object();
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kv-lease-expirer");
        t.setDaemon(true);
        return t;
    });

    public InMemoryKeyValueStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static final class Lease {
        final long id;
        final long ttlSeconds;
        volatile long expiresAt;
        final Set<String> keys = ConcurrentHashMap.newKeySet();

        Lease(long id, long ttlSeconds) { this.id = id; this.ttlSeconds = ttlSeconds; touch(); }
        void touch() { expiresAt = System.currentTimeMillis() + ttlSeconds * 1000; }
    }

    private record Watcher(String prefix, Consumer<WatchEvent> listener) {}

    public record Snapshot(long revision, List<KvEntry> entries) {}

    @PostConstruct
    void start() {
        scheduler.scheduleAtFixedRate(this::expireLeases, 1, 1, TimeUnit.SECONDS);
        log.info("In-memory key-value store started (etcd emulation)");
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    @Override
    public Optional<KvEntry> get(String key) {
        return Optional.ofNullable(data.get(key));
    }

    @Override
    public List<KvEntry> getPrefix(String prefix) {
        List<KvEntry> out = new ArrayList<>();
        for (Map.Entry<String, KvEntry> e : data.tailMap(prefix, true).entrySet()) {
            if (!e.getKey().startsWith(prefix)) break;
            out.add(e.getValue());
        }
        return out;
    }

    @Override
    public long put(String key, String value) {
        return put(key, value, 0);
    }

    @Override
    public long put(String key, String value, long leaseId) {
        synchronized (lock) {
            long rev = revision.incrementAndGet();
            doPut(rev, key, value, leaseId);
            return rev;
        }
    }

    @Override
    public long delete(String key) {
        synchronized (lock) {
            if (!data.containsKey(key)) return 0;
            revision.incrementAndGet();
            return doDelete(key);
        }
    }

    @Override
    public long deletePrefix(String prefix) {
        synchronized (lock) {
            List<String> keys = getPrefix(prefix).stream().map(KvEntry::key).toList();
            if (keys.isEmpty()) return 0;
            revision.incrementAndGet();
            long n = 0;
            for (String k : keys) n += doDelete(k);
            return n;
        }
    }

    @Override
    public TxnResult txn(List<Compare> conditions, List<KvOp> thenOps, List<KvOp> elseOps) {
        synchronized (lock) {
            boolean ok = conditions.stream().allMatch(this::check);
            List<KvOp> ops = ok ? thenOps : elseOps;
            if (ops.isEmpty()) return new TxnResult(ok, revision.get());
            long rev = revision.incrementAndGet();      // одна ревизия на транзакцию, как в etcd
            for (KvOp op : ops) {
                if (op instanceof KvOp.Put p) {
                    doPut(rev, p.key(), p.value(), p.leaseId());
                } else if (op instanceof KvOp.Delete d) {
                    if (d.prefix()) getPrefix(d.key()).forEach(e -> doDelete(e.key()));
                    else doDelete(d.key());
                }
            }
            return new TxnResult(ok, rev);
        }
    }

    @Override
    public long leaseGrant(long ttlSeconds) {
        long id = leaseSeq.incrementAndGet();
        leases.put(id, new Lease(id, ttlSeconds));
        return id;
    }

    @Override
    public void leaseRevoke(long leaseId) {
        synchronized (lock) {
            Lease lease = leases.remove(leaseId);
            if (lease == null) return;
            if (!lease.keys.isEmpty()) {
                revision.incrementAndGet();
                for (String k : new ArrayList<>(lease.keys)) doDelete(k);
            }
        }
    }

    @Override
    public long leaseKeepAlive(long leaseId) {
        Lease lease = leases.get(leaseId);
        if (lease == null) throw new KvException("lease not found: " + leaseId);
        lease.touch();
        return lease.ttlSeconds;
    }

    @Override
    public long leaseTimeToLive(long leaseId) {
        Lease lease = leases.get(leaseId);
        if (lease == null) return -1;
        return Math.max(0, (lease.expiresAt - System.currentTimeMillis()) / 1000);
    }

    private void expireLeases() {
        long now = System.currentTimeMillis();
        for (Lease lease : leases.values()) {
            if (lease.expiresAt <= now) {
                log.debug("lease {} expired, removing {} key(s)", lease.id, lease.keys.size());
                leaseRevoke(lease.id);
            }
        }
    }

    @Override
    public AutoCloseable watch(String prefix, Consumer<WatchEvent> listener) {
        Watcher w = new Watcher(prefix, listener);
        watchers.add(w);
        return () -> watchers.remove(w);
    }

    @Override
    public void snapshotSave(String path) {
        synchronized (lock) {
            try {
                File f = new File(path);
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValue(f, new Snapshot(revision.get(), new ArrayList<>(data.values())));
                log.info("snapshot saved to {} ({} keys, revision {})", path, data.size(), revision.get());
            } catch (Exception e) {
                throw new KvException("snapshot save failed", e);
            }
        }
    }

    @Override
    public void snapshotRestore(String path) {
        synchronized (lock) {
            try {
                Snapshot s = mapper.readValue(new File(path), Snapshot.class);
                data.clear();
                leases.clear();
                revision.set(s.revision());
                for (KvEntry e : s.entries()) {
                    data.put(e.key(), new KvEntry(e.key(), e.value(), e.createRevision(),
                            e.modRevision(), e.version(), 0));
                }
                log.info("snapshot restored from {} ({} keys, revision {})", path, data.size(), revision.get());
            } catch (Exception e) {
                throw new KvException("snapshot restore failed", e);
            }
        }
    }

    @Override
    public long currentRevision() {
        return revision.get();
    }

    public int activeLeases() {
        return leases.size();
    }

    public int size() {
        return data.size();
    }

    private boolean check(Compare c) {
        KvEntry e = data.get(c.key());
        return switch (c.target()) {
            case VERSION -> cmp(e == null ? 0 : e.version(), c.op(), c.number());
            case MOD_REVISION -> cmp(e == null ? 0 : e.modRevision(), c.op(), c.number());
            case VALUE -> {
                if (e == null) yield c.op() == Compare.Op.NOT_EQUAL;
                yield cmp(e.value().compareTo(c.text()), c.op(), 0);
            }
        };
    }

    private static boolean cmp(long a, Compare.Op op, long b) {
        return switch (op) {
            case EQUAL -> a == b;
            case NOT_EQUAL -> a != b;
            case GREATER -> a > b;
            case LESS -> a < b;
        };
    }

    private void doPut(long rev, String key, String value, long leaseId) {
        if (leaseId != 0 && !leases.containsKey(leaseId))
            throw new KvException("lease not found: " + leaseId);
        KvEntry old = data.get(key);
        if (old != null && old.leaseId() != 0 && old.leaseId() != leaseId) detach(old);
        KvEntry e = new KvEntry(key, value,
                old == null ? rev : old.createRevision(), rev,
                old == null ? 1 : old.version() + 1, leaseId);
        data.put(key, e);
        if (leaseId != 0) leases.get(leaseId).keys.add(key);
        notify(WatchEvent.Type.PUT, e, old);
    }

    private long doDelete(String key) {
        KvEntry old = data.remove(key);
        if (old == null) return 0;
        detach(old);
        notify(WatchEvent.Type.DELETE, new KvEntry(key, null, 0, revision.get(), 0, 0), old);
        return 1;
    }

    private void detach(KvEntry e) {
        Lease lease = leases.get(e.leaseId());
        if (lease != null) lease.keys.remove(e.key());
    }

    private void notify(WatchEvent.Type type, KvEntry entry, KvEntry previous) {
        for (Watcher w : watchers) {
            if (entry.key().startsWith(w.prefix())) {
                try { w.listener().accept(new WatchEvent(type, entry, previous)); }
                catch (Exception ex) { log.warn("watch listener failed: {}", ex.toString()); }
            }
        }
    }
}