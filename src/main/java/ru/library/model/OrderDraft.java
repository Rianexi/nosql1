package ru.library.model;

import java.time.LocalDateTime;

public record OrderDraft(
        String id,
        String eventId,
        String readerCard,
        String readerName,
        int seats,
        LocalDateTime createdAt,
        String managerLogin
) {}