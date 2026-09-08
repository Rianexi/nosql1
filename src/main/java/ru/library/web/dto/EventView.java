package ru.library.web.dto;

import ru.library.model.Event;

public record EventView(Event event, long revision, long views) {}