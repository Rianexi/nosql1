package ru.library.service;

import org.springframework.stereotype.Service;
import ru.library.exception.NotFoundException;
import ru.library.exception.ValidationException;
import ru.library.kv.Versioned;
import ru.library.model.Event;
import ru.library.model.EventStatus;
import ru.library.repo.EventRepository;
import ru.library.web.dto.EventView;

import java.util.List;
import java.util.UUID;

@Service
public class EventService {
    private final EventRepository repo;
    private final ViewCounterService views;

    public EventService(EventRepository repo, ViewCounterService views) {
        this.repo = repo;
        this.views = views;
    }

    public EventView create(Event in) {
        if (in.totalSeats() <= 0) throw new ValidationException("totalSeats должно быть > 0");
        Event e = new Event(UUID.randomUUID().toString(), in.title(), in.type(), in.dateTime(), in.hall(),
                in.totalSeats(), in.totalSeats(), in.price(),
                in.status() == null ? EventStatus.OPEN : in.status(), in.description());
        repo.save(e);
        return new EventView(e, repo.find(e.id()).orElseThrow().modRevision(), 0);
    }

    public EventView open(String id) {
        Versioned<Event> v = find(id);
        long n = views.increment(id);
        return new EventView(v.value(), v.modRevision(), n);
    }

    public EventView peek(String id) {
        Versioned<Event> v = find(id);
        return new EventView(v.value(), v.modRevision(), views.get(id));
    }

    public List<EventView> list() {
        return repo.findAll().stream()
                .map(v -> new EventView(v.value(), v.modRevision(), views.get(v.value().id())))
                .toList();
    }

    public void delete(String id) { repo.delete(id); }

    private Versioned<Event> find(String id) {
        return repo.find(id).orElseThrow(() -> new NotFoundException("Событие не найдено: " + id));
    }
}