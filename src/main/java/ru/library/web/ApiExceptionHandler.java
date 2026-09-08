package ru.library.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.library.exception.ConflictException;
import ru.library.exception.NotFoundException;
import ru.library.exception.ValidationException;
import ru.library.kv.KvException;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record Err(String error, String message) {}

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Err notFound(RuntimeException e) { return new Err("not_found", e.getMessage()); }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Err validation(RuntimeException e) { return new Err("validation", e.getMessage()); }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Err conflict(RuntimeException e) { return new Err("conflict", e.getMessage()); }

    @ExceptionHandler(KvException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Err storage(RuntimeException e) { return new Err("storage_error", e.getMessage()); }
}