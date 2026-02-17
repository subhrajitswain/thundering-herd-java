package co.in.thunderingherd;

import co.in.thunderingherd.core.SingleFlight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SingleFlight Tests")
class SingleFlightTest {

    private SingleFlight singleFlight;
    private AtomicInteger executionCount;

    @BeforeEach
    void setUp() {
        singleFlight = new SingleFlight();
        executionCount = new AtomicInteger(0);
        singleFlight.resetMetrics();
    }

    @Test
    @DisplayName("Should execute function only once for concurrent requests")
    void testConcurrentDeduplication() throws InterruptedException, ExecutionException {
        int concurrency = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrency);

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            Future<String> future = executor.submit(() -> {
                startLatch.await();
                String result = singleFlight.execute("test-key", () -> {
                    executionCount.incrementAndGet();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return "result";
                });
                endLatch.countDown();
                return result;
            });
            futures.add(future);
        }

        startLatch.countDown();
        endLatch.await(5, TimeUnit.SECONDS);

        for (Future<String> future : futures) {
            assertEquals("result", future.get());
        }

        assertEquals(1, executionCount.get());

        SingleFlight.Metrics metrics = singleFlight.getMetrics();
        assertEquals(1, metrics.executions());
        assertEquals(99, metrics.deduplications());
        assertTrue(metrics.dedupRatioPercent() > 98);

        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle different keys independently")
    void testDifferentKeys() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<String> future1 = executor.submit(() ->
                singleFlight.execute("key1", () -> {
                    executionCount.incrementAndGet();
                    return "result1";
                })
        );

        Future<String> future2 = executor.submit(() ->
                singleFlight.execute("key2", () -> {
                    executionCount.incrementAndGet();
                    return "result2";
                })
        );

        assertEquals("result1", future1.get());
        assertEquals("result2", future2.get());
        assertEquals(2, executionCount.get());

        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle exceptions correctly")
    void testExceptionHandling() {
        assertThrows(RuntimeException.class, () -> {
            singleFlight.execute("error-key", () -> {
                throw new RuntimeException("Test error");
            });
        });
    }
}