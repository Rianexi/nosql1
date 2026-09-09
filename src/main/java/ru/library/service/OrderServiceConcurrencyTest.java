package ru.library.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.library.exception.ValidationException;
import ru.library.model.Event;
import ru.library.model.EventStatus;
import ru.library.model.EventType;
import ru.library.model.Order;
import ru.library.web.dto.EventView;
import ru.library.web.dto.OrderRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {"library.demo-data=false", "library.kv.restore-on-start=false"})
class OrderServiceConcurrencyTest {

    @Autowired EventService events;
    @Autowired OrderService orders;

    @Test
    void noOverbookingUnderConcurrentOrders() throws Exception {
        int seats = 10, attempts = 50;
        EventView ev = events.create(new Event(null, "test", EventType.LECTURE, LocalDateTime.now().plusDays(1),
                "hall", seats, seats, BigDecimal.ONE, EventStatus.OPEN, ""));

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            int n = i;
            fs.add(pool.submit(() -> {
                try {
                    orders.placeOrder(new OrderRequest(ev.event().id(), "c" + n, "r" + n, 1, null), "m");
                    ok.incrementAndGet();
                } catch (ValidationException ignored) { }
            }));
        }
        for (Future<?> f : fs) f.get();
        pool.shutdown();

        assertEquals(seats, ok.get());
        assertEquals(0, events.peek(ev.event().id()).event().freeSeats());
        assertEquals(seats, orders.byEvent(ev.event().id()).stream().mapToInt(Order::seats).sum());
    }
}