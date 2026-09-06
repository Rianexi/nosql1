package ru.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "library")
public record AppProperties(Kv kv, Cache cache, boolean demoData) {
    public record Kv(String mode, String keyPrefix, long draftTtlSeconds,
                     String snapshotFile, boolean restoreOnStart) {}
    public record Cache(long userSettingsTtlSeconds, long userSettingsMaxSize) {}
}