package ru.library.repo;

import org.springframework.stereotype.Repository;
import ru.library.config.AppProperties;
import ru.library.exception.NotFoundException;
import ru.library.kv.Keys;
import ru.library.kv.KvOp;
import ru.library.kv.KvTemplate;
import ru.library.kv.Versioned;
import ru.library.model.OrderDraft;
import ru.library.web.dto.DraftView;

import java.util.List;
import java.util.Optional;

@Repository
public class DraftRepository {
    private final KvTemplate kv;
    private final Keys keys;
    private final long ttl;

    public DraftRepository(KvTemplate kv, Keys keys, AppProperties props) {
        this.kv = kv; this.keys = keys; this.ttl = props.kv().draftTtlSeconds();
    }

    public DraftView create(OrderDraft d) {
        long leaseId = kv.store().leaseGrant(ttl);
        kv.store().txn(List.of(),
                List.of(KvOp.put(keys.draft(d.id()), kv.toJson(d), leaseId),
                        KvOp.put(keys.draftByEvent(d.eventId(), d.id()), "", leaseId)),
                List.of());
        return new DraftView(d, ttl);
    }

    public Optional<Versioned<OrderDraft>> findVersioned(String id) {
        return kv.get(keys.draft(id), OrderDraft.class);
    }

    public Optional<DraftView> find(String id) {
        return findVersioned(id).map(v -> new DraftView(v.value(), kv.store().leaseTimeToLive(v.leaseId())));
    }

    public List<OrderDraft> findAll() {
        return kv.getPrefix(keys.drafts(), OrderDraft.class).stream().map(Versioned::value).toList();
    }

    public int heldSeats(String eventId) {
        return kv.keysWithPrefix(keys.draftsByEvent(eventId)).stream()
                .map(Keys::lastSegment)
                .map(this::findVersioned)
                .flatMap(Optional::stream)
                .mapToInt(v -> v.value().seats())
                .sum();
    }

    public long extend(String id) {
        Versioned<OrderDraft> v = findVersioned(id)
                .orElseThrow(() -> new NotFoundException("Временная заявка не найдена или истекла: " + id));
        return kv.store().leaseKeepAlive(v.leaseId());
    }

    public void revoke(String id) {
        findVersioned(id).ifPresent(v -> kv.store().leaseRevoke(v.leaseId()));
    }
}