package ru.library.experiment;

import org.springframework.web.bind.annotation.*;
import ru.library.exception.ConflictException;
import ru.library.exception.ValidationException;
import ru.library.kv.KeyValueStore;
import ru.library.kv.KvTemplate;
import ru.library.model.*;
import ru.library.service.EventService;
import ru.library.service.OrderService;
import ru.library.service.ViewCounterService;
import ru.library.web.dto.EventView;
import ru.library.web.dto.OrderRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {
    private final ViewCounterService counter;
    private final OrderService orders;
    private final EventService events;
    private final KeyValueStore store;

    public ExperimentController(ViewCounterService counter, OrderService orders,
                                EventService events, KvTemplate kv) {
        this.counter = counter; this.orders = orders; this.events = events; this.store = kv.store();
    }

    public record CounterResult(String mode, int threads, int perThread, long expected, long actual,
                                long lost, double lostPercent, long casRetries, long millis) {}

    @PostMapping("/counter")
    public CounterResult counter(@RequestParam(defaultValue = "cas") String mode,
                                 @RequestParam(defaultValue = "20") int threads,
                                 @RequestParam(defaultValue = "50") int perThread) throws Exception {
        String key = "/library/experiments/counter-" + mode;
        store.delete(key);
        counter.resetRetries();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < perThread; j++) {
                    if ("cas".equals(mode)) counter.incrementKey(key);
                    else counter.incrementNaive(key);
                }
                return null;
            }));
        }
        long t0 = System.currentTimeMillis();
        start.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();
        long millis = System.currentTimeMillis() - t0;

        long actual = counter.getKey(key);
        long expected = (long) threads * perThread;
        long lost = expected - actual;
        return new CounterResult(mode, threads, perThread, expected, actual, lost,
                100.0 * lost / expected, counter.retries(), millis);
    }

    public record OrdersResult(String eventId, int seats, int attempts, long confirmed, long rejected,
                               int freeSeatsAfter, long soldByOrders, boolean overbooking,
                               long casConflicts, long millis) {}

    @PostMapping("/orders")
    public OrdersResult orders(@RequestParam(defaultValue = "10") int seats,
                               @RequestParam(defaultValue = "40") int attempts) throws Exception {
        EventView ev = events.create(new Event(null, "Стресс-тест " + LocalDateTime.now(), EventType.LECTURE,
                LocalDateTime.now().plusDays(1), "Малый зал", seats, seats, BigDecimal.TEN, EventStatus.OPEN, ""));
        String eventId = ev.event().id();
        orders.resetConflicts();

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong ok = new AtomicLong(), rejected = new AtomicLong();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            int n = i;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    orders.placeOrder(new OrderRequest(eventId, "Ч-" + n, "Читатель " + n, 1, null), "m" + n);
                    ok.incrementAndGet();
                } catch (ValidationException | ConflictException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        long t0 = System.currentTimeMillis();
        start.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();
        long millis = System.currentTimeMillis() - t0;

        Event after = events.peek(eventId).event();
        long sold = orders.byEvent(eventId).stream()
                .filter(o -> o.status() == OrderStatus.CONFIRMED).mapToInt(Order::seats).sum();
        return new OrdersResult(eventId, seats, attempts, ok.get(), rejected.get(), after.freeSeats(),
                sold, sold > seats || after.freeSeats() < 0, orders.conflicts(), millis);
    }
}