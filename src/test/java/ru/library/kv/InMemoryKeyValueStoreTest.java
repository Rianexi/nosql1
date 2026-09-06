package ru.library.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryKeyValueStoreTest {
    private InMemoryKeyValueStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore(new ObjectMapper());
        store.start();
    }

    @Test
    void revisionsAndVersions() {
        store.put("/a", "1");
        KvEntry e1 = store.get("/a").orElseThrow();
        store.put("/a", "2");
        KvEntry e2 = store.get("/a").orElseThrow();
        assertEquals(e1.createRevision(), e2.createRevision());
        assertTrue(e2.modRevision() > e1.modRevision());
        assertEquals(2, e2.version());
    }

    @Test
    void compareAndSwapRejectsStaleRevision() {
        store.put("/a", "1");
        long rev = store.get("/a").orElseThrow().modRevision();
        store.put("/a", "2");   // кто-то изменил
        TxnResult r = store.txn(List.of(Compare.modRevision("/a", Compare.Op.EQUAL, rev)),
                List.of(KvOp.put("/a", "3")), List.of());
        assertFalse(r.succeeded());
        assertEquals("2", store.get("/a").orElseThrow().value());
    }

    @Test
    void leaseExpiresAndRemovesKeys() throws Exception {
        long lease = store.leaseGrant(1);
        store.put("/tmp/1", "x", lease);
        store.put("/tmp/2", "y", lease);
        assertEquals(2, store.getPrefix("/tmp/").size());
        Thread.sleep(2500);
        assertEquals(0, store.getPrefix("/tmp/").size());
        assertEquals(-1, store.leaseTimeToLive(lease));
    }

    @Test
    void naiveIncrementLosesUpdates_casDoesNot() throws Exception {
        int threads = 16, per = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) pool.submit(() -> {
            for (int j = 0; j < per; j++) {
                String key = "/cas";
                while (true) {
                    var cur = store.get(key);
                    long next = cur.map(e -> Long.parseLong(e.value())).orElse(0L) + 1;
                    Compare cmp = cur.map(e -> Compare.modRevision(key, Compare.Op.EQUAL, e.modRevision()))
                            .orElse(Compare.version(key, Compare.Op.EQUAL, 0));
                    if (store.txn(List.of(cmp), List.of(KvOp.put(key, Long.toString(next))), List.of()).succeeded()) break;
                }
            }
        });
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(threads * per, Long.parseLong(store.get("/cas").orElseThrow().value()));
    }
}