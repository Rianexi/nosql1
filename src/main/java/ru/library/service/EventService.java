package ru.library.service;

import org.springframework.stereotype.Service;
import ru.library.exception.NotFoundException;
import ru.library.exception.ValidationException;
import ru.library.kv.Versioned;
import ru.library.model.Event;
import ru.library.model.EventStatus;
import ru.library.repo.EventRepository;
import ru.library.web.dto.EventView;
import ru.library.exception.ConflictException;
import ru.library.repo.DraftRepository;

import java.util.List;
import java.util.UUID;

@Service
public class EventService {
    private final EventRepository repo;
    private final ViewCounterService views;

    private final DraftRepository drafts;

    public EventService(EventRepository repo, ViewCounterService views, DraftRepository drafts) {
        this.repo = repo; this.views = views; this.drafts = drafts;
    }

    public int heldSeats(String eventId) { return drafts.heldSeats(eventId); }

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

    public EventView update(String id, Event patch, long expectedRevision) {
        Versioned<Event> cur = find(id);
        Event c = cur.value();
        int sold = c.totalSeats() - c.freeSeats();
        if (patch.totalSeats() < sold)
            throw new ValidationException("totalSeats меньше уже проданных мест (" + sold + ")");

        Event upd = new Event(id, patch.title(), patch.type(), patch.dateTime(), patch.hall(),
                patch.totalSeats(), patch.totalSeats() - sold, patch.price(),
                patch.status() == null ? c.status() : patch.status(), patch.description());

        if (!repo.updateIfUnchanged(upd, expectedRevision))
            throw new ConflictException("Событие изменено параллельно (ваша ревизия " + expectedRevision
                    + ", текущая " + cur.modRevision() + "). Обновите данные и повторите.");
        return peek(id);
    }

    public void delete(String id) { repo.delete(id); }

    private Versioned<Event> find(String id) {
        return repo.find(id).orElseThrow(() -> new NotFoundException("Событие не найдено: " + id));
    }
}