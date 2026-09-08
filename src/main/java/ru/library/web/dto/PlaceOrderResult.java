package ru.library.web.dto;

import ru.library.model.Event;
import ru.library.model.Order;

public record PlaceOrderResult(Order order, Event event) {}