package ru.library.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.library.model.OrderDraft;
import ru.library.service.DraftService;
import ru.library.web.dto.DraftRequest;
import ru.library.web.dto.DraftView;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {
    private final DraftService service;

    public DraftController(DraftService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DraftView create(@RequestBody DraftRequest r, Principal p) { return service.create(r, p.getName()); }

    @GetMapping
    public List<OrderDraft> all() { return service.all(); }

    @GetMapping("/{id}")
    public DraftView get(@PathVariable String id) { return service.get(id); }

    @PostMapping("/{id}/extend")
    public Map<String, Long> extend(@PathVariable String id) { return Map.of("ttl", service.extend(id)); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String id) { service.cancel(id); }
}