package co.in.thunderingherd.service;


import co.in.thunderingherd.core.SingleFlight;
import co.in.thunderingherd.core.ThunderingHerdCacheManager;
import co.in.thunderingherd.model.Product;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final DatabaseService databaseService;
    private final ThunderingHerdCacheManager cacheManager;
    private final SingleFlight singleFlight;
    private final MeterRegistry meterRegistry;

    private static final String CACHE_KEY_PREFIX = "product:";
    private static final long DEFAULT_TTL = 60;

    public Optional<Product> getProductBaseline(String sku) throws Exception {
        log.debug("Baseline strategy - No cache, direct DB query");
        return databaseService.queryProductBySku(sku);
    }

    public Optional<Product> getProductSingleFlight(String sku) throws Exception {
        log.debug("Single-flight strategy for SKU: {}", sku);

        Product product = singleFlight.execute(
                CACHE_KEY_PREFIX + sku,
                () -> {
                    try {
                        return Objects.requireNonNull(databaseService.queryProductBySku(sku).orElse(null));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        return Optional.of(product);
    }

    public Optional<Product> getProductFull(String sku) throws Exception {
        log.debug("Full solution strategy for SKU: {}", sku);

        Product product = cacheManager.get(
                CACHE_KEY_PREFIX + sku,
                () -> {
                    trackCacheMiss();
                    try {
                        return Objects.requireNonNull(databaseService.queryProductBySku(sku).orElse(null));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                DEFAULT_TTL
        );

        trackCacheHit();

        return Optional.of(product);
    }

    public Optional<Product> getProductWithStale(String sku) throws Exception {
        log.debug("Full solution with stale fallback for SKU: {}", sku);

        Product product = cacheManager.getWithStale(
                CACHE_KEY_PREFIX + sku,
                () -> {
                    try {
                        return Objects.requireNonNull(databaseService.queryProductBySku(sku).orElse(null));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                DEFAULT_TTL
        );

        return Optional.of(product);
    }

    public Product saveProduct(Product product) {
        Product saved = databaseService.save(product);
        cacheManager.invalidate(CACHE_KEY_PREFIX + saved.getSku());
        return saved;
    }

    public void warmCache(String sku) {
        cacheManager.warm(
                CACHE_KEY_PREFIX + sku,
                () -> {
                    try {
                        return Objects.requireNonNull(databaseService.queryProductBySku(sku).orElse(null));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                DEFAULT_TTL
        );
    }

    private void trackCacheHit() {
        Counter.builder("cache.hits")
                .description("Cache hit count")
                .register(meterRegistry)
                .increment();
    }

    private void trackCacheMiss() {
        Counter.builder("cache.misses")
                .description("Cache miss count")
                .register(meterRegistry)
                .increment();
    }
}
