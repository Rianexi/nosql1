package ru.library.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Order(
        String id,
        String eventId,
        String readerCard,
        String readerName,
        int seats,
        BigDecimal totalPrice,
        OrderStatus status,
        String managerLogin,
        LocalDateTime createdAt
) {
    public Order withStatus(OrderStatus s) {
        return new Order(id, eventId, readerCard, readerName, seats, totalPrice, s, managerLogin, createdAt);
    }
}