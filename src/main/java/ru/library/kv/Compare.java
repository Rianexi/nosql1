package ru.library.kv;

public record Compare(String key, Target target, Op op, long number, String text) {
    public enum Target { VERSION, MOD_REVISION, VALUE }
    public enum Op { EQUAL, NOT_EQUAL, GREATER, LESS }

    public static Compare version(String key, Op op, long v)      { return new Compare(key, Target.VERSION, op, v, null); }
    public static Compare modRevision(String key, Op op, long v)  { return new Compare(key, Target.MOD_REVISION, op, v, null); }
    public static Compare value(String key, Op op, String v)      { return new Compare(key, Target.VALUE, op, 0, v); }
}