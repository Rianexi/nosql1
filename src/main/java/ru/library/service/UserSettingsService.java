package ru.library.service;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;
import ru.library.config.CacheConfig;
import ru.library.kv.Keys;
import ru.library.kv.KvTemplate;
import ru.library.kv.Versioned;
import ru.library.model.UserSettings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;


@Service
public class UserSettingsService {
    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private final KvTemplate kv;
    private final Keys keys;
    private final CacheManager cacheManager;
    private final AtomicLong storeReads = new AtomicLong();
    private AutoCloseable watcher;

    public UserSettingsService(KvTemplate kv, Keys keys, CacheManager cacheManager) {
        this.kv = kv; this.keys = keys; this.cacheManager = cacheManager;
    }

    @Cacheable(cacheNames = CacheConfig.USER_SETTINGS, key = "#login")
    public UserSettings get(String login) {
        storeReads.incrementAndGet();
        log.debug("cache miss -> store: settings of {}", login);
        return kv.get(keys.userSettings(login), UserSettings.class)
                .map(Versioned::value)
                .orElseGet(() -> UserSettings.defaults(login));
    }

    @CachePut(cacheNames = CacheConfig.USER_SETTINGS, key = "#settings.login")
    public UserSettings save(UserSettings settings) {
        kv.put(keys.userSettings(settings.login()), settings);
        return settings;
    }

    @CacheEvict(cacheNames = CacheConfig.USER_SETTINGS, key = "#login")
    public void reset(String login) {
        kv.delete(keys.userSettings(login));
    }

    @PostConstruct
    void watchSettings() {
        String prefix = keys.usersPrefix();
        watcher = kv.store().watch(prefix, ev -> {
            String key = ev.entry().key();
            String login = key.substring(prefix.length(), key.lastIndexOf('/'));
            Objects.requireNonNull(cacheManager.getCache(CacheConfig.USER_SETTINGS)).evict(login);
            log.debug("watch: settings of {} changed ({}), cache evicted", login, ev.type());
        });
    }

    @PreDestroy
    void stopWatch() throws Exception {
        if (watcher != null) watcher.close();
    }

    public Map<String, Object> stats() {
        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(CacheConfig.USER_SETTINGS);
        CacheStats s = Objects.requireNonNull(cache).getNativeCache().stats();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hitCount", s.hitCount());
        m.put("missCount", s.missCount());
        m.put("hitRate", s.hitRate());
        m.put("storeReads", storeReads.get());
        m.put("size", cache.getNativeCache().estimatedSize());
        return m;
    }
}