package ru.library.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {
    public static final String USER_SETTINGS = "userSettings";

    @Bean
    public CacheManager cacheManager(AppProperties props) {
        CaffeineCacheManager manager = new CaffeineCacheManager(USER_SETTINGS);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(props.cache().userSettingsTtlSeconds()))
                .maximumSize(props.cache().userSettingsMaxSize())
                .recordStats());
        return manager;
    }
}