package ru.library.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Event(
        String id,
        String title,
        EventType type,
        LocalDateTime dateTime,
        String hall,
        int totalSeats,
        int freeSeats,
        BigDecimal price,
        EventStatus status,
        String description
) {
    public Event withFreeSeats(int newFreeSeats) {
        return new Event(id, title, type, dateTime, hall, totalSeats, newFreeSeats, price, status, description);
    }
}