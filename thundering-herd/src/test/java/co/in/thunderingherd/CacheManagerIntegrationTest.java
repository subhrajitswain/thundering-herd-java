package co.in.thunderingherd;


import co.in.thunderingherd.config.ThunderingHerdProperties;

import co.in.thunderingherd.core.ThunderingHerdCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;


@SpringBootTest
@Testcontainers
@DisplayName("CacheManager Integration Tests")
class CacheManagerIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private ThunderingHerdCacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ThunderingHerdProperties properties;

    private AtomicInteger loaderCallCount;

    @BeforeEach
    void setUp() {

        assert redisTemplate.getConnectionFactory() != null;
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        loaderCallCount = new AtomicInteger(0);
    }

    @Test
    @DisplayName("Should cache value on first access")
    void testCaching() throws Exception {
        String key = "test:cache";
        String value = cacheManager.get(key, () -> {
            loaderCallCount.incrementAndGet();
            return "cached-value";
        });

        assertEquals("cached-value", value);
        assertEquals(1, loaderCallCount.get());
        String cachedValue = cacheManager.get(key, () -> {
            loaderCallCount.incrementAndGet();
            return "new-value";
        });

        assertEquals("cached-value", cachedValue);
        assertEquals(1, loaderCallCount.get(), "Loader should not be called again");
    }

    @Test
    @DisplayName("Should handle null values with negative caching")
    void testNegativeCaching() throws Exception {
        String key = "test:negative";
        String value = cacheManager.get(key, () -> {
            loaderCallCount.incrementAndGet();
            return null;
        });

        assertNull(value);
        assertEquals(1, loaderCallCount.get());
        String cachedNull = cacheManager.get(key, () -> {
            loaderCallCount.incrementAndGet();
            return "should-not-load";
        });

        assertNull(cachedNull);
        assertEquals(1, loaderCallCount.get(), "Loader should not be called for negative cache");
    }

    @Test
    @DisplayName("Should apply jitter to TTL")
    void testJitteredTTL() throws Exception {
        long baseTtl = 60;
        for (int i = 0; i < 10; i++) {
            String key = "test:jitter:" + i;
            int finalI = i;
            cacheManager.get(key, () -> "value" + finalI, baseTtl);

            Long ttl = redisTemplate.getExpire(key);
            assertNotNull(ttl);
            assertTrue(ttl >= 45 && ttl <= 75,
                    "TTL should be jittered: " + ttl);
        }
    }

    @Test
    @DisplayName("Should support probabilistic early refresh")
    void testProbabilisticRefresh() throws Exception {
        String key = "test:probabilistic";
        long shortTtl = 2; // 2 seconds

        // Initial load
        String value = cacheManager.get(key, () -> {
            loaderCallCount.incrementAndGet();
            return "initial-value";
        }, shortTtl);

        assertEquals("initial-value", value);
        assertEquals(1, loaderCallCount.get());

        // Wait for cache to age
        await().pollDelay(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(3))
                .until(() -> {
                    // Keep accessing, should trigger probabilistic refresh
                    for (int i = 0; i < 50; i++) {
                        cacheManager.get(key, () -> {
                            loaderCallCount.incrementAndGet();
                            return "refreshed-value";
                        }, shortTtl);
                    }
                    return loaderCallCount.get() > 1;
                });

        assertTrue(loaderCallCount.get() > 1, "Should have triggered refresh");
    }

    @Test
    @DisplayName("Should invalidate cache correctly")
    void testInvalidation() throws Exception {
        String key = "test:invalidate";

        cacheManager.get(key, () -> {
            loaderCallCount.incrementAndGet();
            return "value";
        });

        assertEquals(1, loaderCallCount.get());

        // Invalidate
        cacheManager.invalidate(key);

        // Should reload
        cacheManager.get(key, () -> {
            loaderCallCount.incrementAndGet();
            return "new-value";
        });

        assertEquals(2, loaderCallCount.get());
    }

    @Test
    @DisplayName("Should warm cache correctly")
    void testCacheWarming() throws Exception {
        String key = "test:warm";

        cacheManager.warm(key, () -> {
            loaderCallCount.incrementAndGet();
            return "warmed-value";
        }, 60);

        assertEquals(1, loaderCallCount.get());

        // Should use warmed cache
        String value = cacheManager.get(key, () -> {
            loaderCallCount.incrementAndGet();
            return "should-not-load";
        });

        assertEquals("warmed-value", value);
        assertEquals(1, loaderCallCount.get());
    }

    @Test
    @DisplayName("Should serve stale cache on error")
    void testStaleOnError() throws Exception {
        String key = "test:stale";

        // Initial cache
        String value = cacheManager.get(key, () -> "initial-value");
        assertEquals("initial-value", value);

        await().pollDelay(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(3))
                .until(() -> redisTemplate.hasKey(key) == Boolean.FALSE);

        assertThrows(RuntimeException.class, () -> {
            cacheManager.getWithStale(key, () -> {
                throw new RuntimeException("Loader failed");
            }, 1);
        });
    }

    @Test
    @DisplayName("Should handle high concurrency")
    void testHighConcurrency() throws InterruptedException {
        String key = "test:concurrency";
        int threads = 100;

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try {
                    cacheManager.get(key, () -> {
                        loaderCallCount.incrementAndGet();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "concurrent-value";
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        // Should have very few loader calls due to single-flight
        assertTrue(loaderCallCount.get() < 5,
                "Loader should be called very few times: " + loaderCallCount.get());
    }

    @Test
    @DisplayName("Should handle multiple different keys")
    void testMultipleKeys() throws Exception {
        for (int i = 0; i < 10; i++) {
            String key = "test:multi:" + i;
            int finalI = i;
            String value = cacheManager.get(key, () -> {
                loaderCallCount.incrementAndGet();
                return "value-" + finalI;
            });

            assertEquals("value-" + i, value);
        }

        assertEquals(10, loaderCallCount.get());

        // Access all again - should use cache
        for (int i = 0; i < 10; i++) {
            String key = "test:multi:" + i;
            int finalI = i;
            String value = cacheManager.get(key, () -> {
                loaderCallCount.incrementAndGet();
                return "new-value-" + finalI;
            });

            assertEquals("value-" + i, value);
        }

        assertEquals(10, loaderCallCount.get(), "Should not call loader again");
    }

    @Test
    @DisplayName("Should respect custom TTL")
    void testCustomTTL() throws Exception {
        String key = "test:custom-ttl";
        long customTtl = 120;

        cacheManager.get(key, () -> "value", customTtl);

        Long ttl = redisTemplate.getExpire(key);
        assertNotNull(ttl);
        assertTrue(ttl >= 90 && ttl <= 150,
                "TTL should be around custom value: " + ttl);
    }
}