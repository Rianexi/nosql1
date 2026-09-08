package ru.library.repo;

import org.springframework.stereotype.Repository;
import ru.library.kv.Keys;
import ru.library.kv.KvOp;
import ru.library.kv.KvTemplate;
import ru.library.kv.Versioned;
import ru.library.model.Order;

import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {
    private final KvTemplate kv;
    private final Keys keys;

    public OrderRepository(KvTemplate kv, Keys keys) { this.kv = kv; this.keys = keys; }

    public Optional<Order> find(String id) {
        return kv.get(keys.order(id), Order.class).map(Versioned::value);
    }

    public List<Order> findAll() {
        return kv.getPrefix(keys.orders(), Order.class).stream().map(Versioned::value).toList();
    }

    public List<Order> findByEvent(String eventId) {
        return kv.keysWithPrefix(keys.ordersByEvent(eventId)).stream()
                .map(Keys::lastSegment)
                .map(this::find)
                .flatMap(Optional::stream)
                .toList();
    }

    public void save(Order o) {
        kv.store().txn(List.of(),
                List.of(KvOp.put(keys.order(o.id()), kv.toJson(o)),
                        KvOp.put(keys.orderByEvent(o.eventId(), o.id()), "")),
                List.of());
    }
}