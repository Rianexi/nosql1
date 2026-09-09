package ru.library.web.dto;

public record DraftRequest(String eventId, String readerCard, String readerName, int seats) {

}