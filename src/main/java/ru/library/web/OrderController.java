package ru.library.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.library.exception.NotFoundException;
import ru.library.model.Order;
import ru.library.service.OrderService;
import ru.library.web.dto.OrderRequest;
import ru.library.web.dto.PlaceOrderResult;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceOrderResult place(@RequestBody OrderRequest r, Principal p) {
        return service.placeOrder(r, p.getName());
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable String id) {
        return service.find(id).orElseThrow(() -> new NotFoundException("Заказ не найден: " + id));
    }

    @PostMapping("/{id}/cancel")
    public Order cancel(@PathVariable String id) { return service.cancel(id); }

    @GetMapping
    public List<Order> all(@RequestParam(required = false) String eventId) {
        return eventId == null ? service.all() : service.byEvent(eventId);
    }
}