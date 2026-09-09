package ru.library.web.dto;

import ru.library.model.OrderDraft;

public record DraftView(OrderDraft draft, long ttlSeconds) {}