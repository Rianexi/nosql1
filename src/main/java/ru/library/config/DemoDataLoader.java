package ru.library.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.library.model.Event;
import ru.library.model.EventStatus;
import ru.library.model.EventType;
import ru.library.service.EventService;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class DemoDataLoader {
    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private final EventService events;
    private final AppProperties props;

    public DemoDataLoader(EventService events, AppProperties props) { this.events = events; this.props = props; }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void load() {
        if (!props.demoData() || !events.list().isEmpty()) return;
        events.create(new Event(null, "Встреча с автором: Евгений Водолазкин", EventType.AUTHOR_MEETING,
                LocalDateTime.now().plusDays(10).withHour(18).withMinute(0), "Большой зал",
                50, 50, new BigDecimal("300"), EventStatus.OPEN, "Презентация нового романа"));
        events.create(new Event(null, "Лекция «История книгопечатания»", EventType.LECTURE,
                LocalDateTime.now().plusDays(3).withHour(17).withMinute(30), "Малый зал",
                25, 25, BigDecimal.ZERO, EventStatus.OPEN, "Бесплатно, по записи"));
        events.create(new Event(null, "Читательский клуб: Достоевский", EventType.READING_CLUB,
                LocalDateTime.now().plusDays(7).withHour(19).withMinute(0), "Читальный зал",
                12, 12, new BigDecimal("150"), EventStatus.OPEN, "Обсуждение «Идиота»"));
        log.info("demo events created");
    }
}