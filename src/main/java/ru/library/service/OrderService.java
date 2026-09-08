package ru.library.service;

import org.springframework.stereotype.Service;
import ru.library.exception.ConflictException;
import ru.library.exception.NotFoundException;
import ru.library.exception.ValidationException;
import ru.library.kv.*;
import ru.library.model.*;
import ru.library.repo.EventRepository;
import ru.library.repo.OrderRepository;
import ru.library.web.dto.OrderRequest;
import ru.library.web.dto.PlaceOrderResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {
    private static final int MAX_ATTEMPTS = 20;

    private final KvTemplate kv;
    private final Keys keys;
    private final EventRepository events;
    private final OrderRepository orders;
    private final AtomicLong conflicts = new AtomicLong();

    public OrderService(KvTemplate kv, Keys keys, EventRepository events, OrderRepository orders) {
        this.kv = kv; this.keys = keys; this.events = events; this.orders = orders;
    }

    public PlaceOrderResult placeOrder(OrderRequest req, String managerLogin) {
        if (req.seats() <= 0) throw new ValidationException("Количество мест должно быть > 0");

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Versioned<Event> v = events.find(req.eventId())
                    .orElseThrow(() -> new NotFoundException("Событие не найдено: " + req.eventId()));
            Event e = v.value();

            if (e.status() != EventStatus.OPEN)
                throw new ValidationException("Регистрация на событие закрыта");
            if (e.freeSeats() < req.seats())
                throw new ValidationException("Недостаточно мест: доступно " + e.freeSeats()
                        + ", запрошено " + req.seats());

            Event updated = e.withFreeSeats(e.freeSeats() - req.seats());
            Order order = new Order(UUID.randomUUID().toString(), e.id(), req.readerCard(), req.readerName(),
                    req.seats(), e.price().multiply(BigDecimal.valueOf(req.seats())),
                    OrderStatus.CONFIRMED, managerLogin, LocalDateTime.now());

            TxnResult r = kv.store().txn(
                    List.of(Compare.modRevision(keys.event(e.id()), Compare.Op.EQUAL, v.modRevision())),
                    List.of(KvOp.put(keys.event(e.id()), kv.toJson(updated)),
                            KvOp.put(keys.order(order.id()), kv.toJson(order)),
                            KvOp.put(keys.orderByEvent(e.id(), order.id()), "")),
                    List.of());

            if (r.succeeded()) return new PlaceOrderResult(order, updated);
            conflicts.incrementAndGet();
        }
        throw new ConflictException("Не удалось оформить заказ из-за высокой конкуренции, повторите попытку");
    }

    public Order cancel(String orderId) {
        Order o = orders.find(orderId).orElseThrow(() -> new NotFoundException("Заказ не найден: " + orderId));
        if (o.status() == OrderStatus.CANCELLED) return o;
        Order cancelled = o.withStatus(OrderStatus.CANCELLED);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Versioned<Event> v = events.find(o.eventId())
                    .orElseThrow(() -> new NotFoundException("Событие не найдено: " + o.eventId()));
            Event upd = v.value().withFreeSeats(v.value().freeSeats() + o.seats());
            TxnResult r = kv.store().txn(
                    List.of(Compare.modRevision(keys.event(o.eventId()), Compare.Op.EQUAL, v.modRevision())),
                    List.of(KvOp.put(keys.event(o.eventId()), kv.toJson(upd)),
                            KvOp.put(keys.order(o.id()), kv.toJson(cancelled))),
                    List.of());
            if (r.succeeded()) return cancelled;
            conflicts.incrementAndGet();
        }
        throw new ConflictException("Не удалось отменить заказ, повторите попытку");
    }

    public Optional<Order> find(String id)     { return orders.find(id); }
    public List<Order> all()                   { return orders.findAll(); }
    public List<Order> byEvent(String eventId) { return orders.findByEvent(eventId); }
    public long conflicts()                    { return conflicts.get(); }
    public void resetConflicts()               { conflicts.set(0); }
}