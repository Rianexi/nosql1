package ru.library.kv;

public record TxnResult(boolean succeeded, long revision) {}