package ru.library.web.dto;

public record OrderRequest(String eventId, String readerCard, String readerName, int seats) {}
