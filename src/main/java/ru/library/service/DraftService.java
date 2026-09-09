package ru.library.service;

import org.springframework.stereotype.Service;
import ru.library.exception.NotFoundException;
import ru.library.exception.ValidationException;
import ru.library.model.Event;
import ru.library.model.EventStatus;
import ru.library.model.OrderDraft;
import ru.library.repo.DraftRepository;
import ru.library.repo.EventRepository;
import ru.library.web.dto.DraftRequest;
import ru.library.web.dto.DraftView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DraftService {
    private final DraftRepository drafts;
    private final EventRepository events;

    public DraftService(DraftRepository drafts, EventRepository events) {
        this.drafts = drafts; this.events = events;
    }

    public DraftView create(DraftRequest r, String managerLogin) {
        if (r.seats() <= 0) throw new ValidationException("Количество мест должно быть > 0");
        Event e = events.find(r.eventId())
                .orElseThrow(() -> new NotFoundException("Событие не найдено: " + r.eventId())).value();
        if (e.status() != EventStatus.OPEN) throw new ValidationException("Регистрация на событие закрыта");
        int available = e.freeSeats() - drafts.heldSeats(e.id());
        if (available < r.seats())
            throw new ValidationException("Недостаточно мест для удержания: доступно " + available);
        return drafts.create(new OrderDraft(UUID.randomUUID().toString(), e.id(), r.readerCard(),
                r.readerName(), r.seats(), LocalDateTime.now(), managerLogin));
    }

    public DraftView get(String id) {
        return drafts.find(id).orElseThrow(() -> new NotFoundException("Временная заявка истекла или не найдена"));
    }

    public List<OrderDraft> all()  { return drafts.findAll(); }
    public long extend(String id)   { return drafts.extend(id); }
    public void cancel(String id)   { drafts.revoke(id); }
}