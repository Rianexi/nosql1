package ru.library.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.library.model.Event;
import ru.library.service.EventService;
import ru.library.web.dto.EventView;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService service;

    public EventController(EventService service) { this.service = service; }

    @GetMapping
    public List<EventView> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventView create(@RequestBody Event e) { return service.create(e); }

    @GetMapping("/{id}")
    public EventView open(@PathVariable String id) { return service.open(id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) { service.delete(id); }
}