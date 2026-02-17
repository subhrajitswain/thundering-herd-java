package co.in.thunderingherd.service;


import co.in.thunderingherd.model.Product;
import co.in.thunderingherd.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class DatabaseService {

    private final ProductRepository productRepository;
    private final Counter queryCounter;
    private final Timer queryTimer;
    private final AtomicLong queryCount = new AtomicLong(0);
    @Getter
    private final long simulatedLatencyMs;

    public DatabaseService(ProductRepository productRepository, MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.simulatedLatencyMs = 100;

        this.queryCounter = Counter.builder("db.queries")
                .description("Total number of database queries")
                .register(meterRegistry);

        this.queryTimer = Timer.builder("db.query.duration")
                .description("Database query duration")
                .register(meterRegistry);
    }

    public Optional<Product> queryProductBySku(String sku) throws Exception {
        return queryTimer.recordCallable(() -> {
            queryCounter.increment();
            queryCount.incrementAndGet();

            log.debug("DB Query for SKU: {}", sku);

            simulateLatency();

            return productRepository.findBySku(sku);
        });
    }

    public Optional<Product> queryProductById(Long id) throws Exception {
        return queryTimer.recordCallable(() -> {
            queryCounter.increment();
            queryCount.incrementAndGet();

            log.debug("DB Query for ID: {}", id);

            simulateLatency();

            return productRepository.findById(id);
        });
    }

    public Product save(Product product) {
        queryCounter.increment();
        queryCount.incrementAndGet();
        return productRepository.save(product);
    }

    public long getQueryCount() {
        return queryCount.get();
    }

    public void resetQueryCount() {
        queryCount.set(0);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(simulatedLatencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during simulated latency", e);
        }
    }
}