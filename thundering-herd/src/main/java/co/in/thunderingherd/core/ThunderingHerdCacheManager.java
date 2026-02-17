package co.in.thunderingherd.core;

import co.in.thunderingherd.config.ThunderingHerdProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Component("thunderingHerdCacheManager")
@RequiredArgsConstructor
public class ThunderingHerdCacheManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SingleFlight singleFlight;
    private final ThunderingHerdProperties properties;
    private final Random random = new Random();

    public <T> T get(String key, Supplier<T> loader) throws Exception {
        return get(key, loader, properties.getCache().getDefaultTtl());
    }

    public <T> T get(String key, Supplier<T> loader, long ttlSeconds) throws Exception {
        Optional<CachedItem<T>> cachedItem = getFromCache(key);

        if (cachedItem.isPresent()) {
            CachedItem<T> item = cachedItem.get();

            if (item.isNegative()) {
                log.debug("Negative cache hit for key: {}", key);
                return null;
            }

            if (shouldRefreshEarly(item, ttlSeconds)) {
                log.debug("Triggering probabilistic refresh for key: {}", key);
                refreshAsync(key, loader, ttlSeconds);
            }

            return item.getValue();
        }

        return singleFlight.execute(key, () -> {
            T value = loader.get();

            if (value == null) {
                setNegativeCache(key);
                return null;
            }

            long jitteredTtl = addJitter(ttlSeconds);
            setCache(key, value, jitteredTtl);

            return value;
        });
    }

    public <T> T getWithStale(String key, Supplier<T> loader, long ttlSeconds) throws Exception {
        try {
            return get(key, loader, ttlSeconds);
        } catch (Exception e) {
            log.warn("Error loading value for key: {}. Attempting stale cache.", key, e);

            Optional<CachedItem<T>> stale = getFromCache(key);
            if (stale.isPresent() && !stale.get().isNegative()) {
                log.info("Serving stale cache for key: {}", key);
                return stale.get().getValue();
            }

            throw e;
        }
    }

    private <T> boolean shouldRefreshEarly(CachedItem<T> item, long ttl) {
        long age = Duration.between(item.getCreatedAt(), Instant.now()).getSeconds();
        double probability = properties.getCache().getBeta() * ((double) age / ttl);
        return random.nextDouble() < probability;
    }

    private long addJitter(long ttl) {
        double jitterPercent = properties.getCache().getJitterPercentage() / 100.0;
        long jitterRange = (long) (ttl * jitterPercent);
        long jitter = random.nextLong(jitterRange * 2) - jitterRange;
        return Math.max(1, ttl + jitter);
    }

    private <T> void refreshAsync(String key, Supplier<T> loader, long ttl) {
        CompletableFuture.runAsync(() -> {
            try {
                T value = loader.get();
                if (value != null) {
                    long jitteredTtl = addJitter(ttl);
                    setCache(key, value, jitteredTtl);
                    log.debug("Background refresh completed for key: {}", key);
                }
            } catch (Exception e) {
                log.warn("Background refresh failed for key: {}", key, e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<CachedItem<T>> getFromCache(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }

            if (value instanceof CachedItem) {
                return Optional.of((CachedItem<T>) value);
            }

            return Optional.of(new CachedItem<>((T) value, Instant.now()));
        } catch (Exception e) {
            log.error("Error getting from cache: {}", key, e);
            return Optional.empty();
        }
    }

    private <T> void setCache(String key, T value, long ttl) {
        try {
            CachedItem<T> item = new CachedItem<>(value, Instant.now());
            redisTemplate.opsForValue().set(key, item, ttl, TimeUnit.SECONDS);
            log.debug("Cached key: {} with TTL: {}s", key, ttl);
        } catch (Exception e) {
            log.error("Error setting cache: {}", key, e);
        }
    }

    private void setNegativeCache(String key) {
        try {
            CachedItem<Object> item = CachedItem.negative();
            long ttl = properties.getCache().getNegativeCacheTtl();
            redisTemplate.opsForValue().set(key, item, ttl, TimeUnit.SECONDS);
            log.debug("Negative cached key: {} with TTL: {}s", key, ttl);
        } catch (Exception e) {
            log.error("Error setting negative cache: {}", key, e);
        }
    }

    public void invalidate(String key) {
        redisTemplate.delete(key);
        log.debug("Invalidated cache key: {}", key);
    }

    public <T> void warm(String key, Supplier<T> loader, long ttl) {
        T value = loader.get();
        if (value != null) {
            setCache(key, value, ttl);
        }
    }
}