package ru.library.repo;

import org.springframework.stereotype.Repository;
import ru.library.kv.Keys;
import ru.library.kv.KvTemplate;
import ru.library.kv.Versioned;
import ru.library.model.Event;

import java.util.List;
import java.util.Optional;

@Repository
public class EventRepository {
    private final KvTemplate kv;
    private final Keys keys;

    public EventRepository(KvTemplate kv, Keys keys) { this.kv = kv; this.keys = keys; }

    public void save(Event e) { kv.put(keys.event(e.id()), e); }

    public Optional<Versioned<Event>> find(String id) { return kv.get(keys.event(id), Event.class); }

    public List<Versioned<Event>> findAll() {
        return kv.store().getPrefix(keys.events()).stream()
                .filter(e -> !e.key().endsWith("/views"))
                .map(e -> new Versioned<>(kv.fromJson(e.value(), Event.class),
                        e.modRevision(), e.version(), e.leaseId()))
                .toList();
    }

    public void delete(String id) { kv.deletePrefix(keys.event(id)); }

    public boolean updateIfUnchanged(Event e, long expectedRevision) {
        return kv.compareAndPut(keys.event(e.id()), expectedRevision, e);
    }
}