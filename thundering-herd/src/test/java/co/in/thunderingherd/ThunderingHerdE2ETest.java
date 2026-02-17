package co.in.thunderingherd;


import co.in.thunderingherd.core.SingleFlight;
import co.in.thunderingherd.service.DatabaseService;
import co.in.thunderingherd.service.LoadTestService;
import co.in.thunderingherd.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests demonstrating the Thundering Herd problem and solutions
 */
@SpringBootTest
@Testcontainers
@DisplayName("Thundering Herd End-to-End Tests")
class ThunderingHerdE2ETest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private LoadTestService loadTestService;

    @Autowired
    private SingleFlight singleFlight;

    private static final String TEST_SKU = "DEMO-001";
    private static final int CONCURRENCY = 100;

    @BeforeEach
    void setUp() {
        databaseService.resetQueryCount();
        singleFlight.resetMetrics();
    }

    @Test
    @DisplayName("Scenario 1: Baseline - Demonstrates the Thundering Herd problem")
    void testBaselineProblem() {
        System.out.println("""
                
                ═══════════════════════════════════════════════════════════
                  SCENARIO 1: BASELINE (NO MITIGATION)
                  Demonstrating the Thundering Herd Problem
                ═══════════════════════════════════════════════════════════""");

        long startTime = System.currentTimeMillis();

        // Simulate concurrent requests without any mitigation
        var results = loadTestService.simulateConcurrentRequests(
                CONCURRENCY,
                () -> {
                    try {
                        return productService.getProductBaseline(TEST_SKU);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        long endTime = System.currentTimeMillis();
        long dbQueries = databaseService.getQueryCount();

        System.out.println("\nRESULTS:");
        System.out.println("  Concurrent Requests: " + CONCURRENCY);
        System.out.println("  Database Queries:    " + dbQueries);
        System.out.println("  Total Time:          " + (endTime - startTime) + "ms");
        System.out.println("  Avg Latency:         " + ((endTime - startTime) / CONCURRENCY) + "ms");
        System.out.println("\n❌ PROBLEM: Every request hit the database!");
        System.out.println("   Impact: " + dbQueries + "x database load\n");

        // Verify the problem
        assertEquals(CONCURRENCY, dbQueries,
                "Without mitigation, every request should hit the database");
        assertEquals(CONCURRENCY, results.size());
    }

    @Test
    @DisplayName("Scenario 2: Single-Flight - Request deduplication")
    void testSingleFlightSolution() {
        System.out.println("""
                
                ═══════════════════════════════════════════════════════════
                  SCENARIO 2: SINGLE-FLIGHT PATTERN
                  Request Deduplication
                ═══════════════════════════════════════════════════════════""");

        long startTime = System.currentTimeMillis();

        // Simulate concurrent requests with single-flight
        var results = loadTestService.simulateConcurrentRequests(
                CONCURRENCY,
                () -> {
                    try {
                        return productService.getProductSingleFlight(TEST_SKU);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        long endTime = System.currentTimeMillis();
        long dbQueries = databaseService.getQueryCount();
        var metrics = singleFlight.getMetrics();

        System.out.println("\nRESULTS:");
        System.out.println("  Concurrent Requests: " + CONCURRENCY);
        System.out.println("  Database Queries:    " + dbQueries);
        System.out.println("  Deduplications:      " + metrics.deduplications());
        System.out.println("  Executions:          " + metrics.executions());
        System.out.println("  Dedup Ratio:         " + String.format("%.2f%%", metrics.dedupRatioPercent()));
        System.out.println("  Total Time:          " + (endTime - startTime) + "ms");
        System.out.println("  Avg Latency:         " + ((endTime - startTime) / CONCURRENCY) + "ms");
        System.out.println("\n✅ SOLUTION: Only 1 request hit the database!");
        System.out.println("   Improvement: " + (CONCURRENCY / Math.max(1, dbQueries)) + "x reduction\n");

        // Verify single-flight worked
        assertTrue(dbQueries <= 2,
                "Single-flight should reduce queries to 1-2: " + dbQueries);
        assertTrue(metrics.dedupRatioPercent() > 95,
                "Dedup ratio should be >95%: " + metrics.dedupRatioPercent());
    }

    @Test
    @DisplayName("Scenario 3: Full Solution - All mitigation strategies")
    void testFullSolution() throws Exception {
        System.out.println("""
                
                ═══════════════════════════════════════════════════════════
                  SCENARIO 3: FULL SOLUTION
                  All Mitigation Strategies Combined
                ═══════════════════════════════════════════════════════════""");
        productService.getProductFull(TEST_SKU);
        long initialQueries = databaseService.getQueryCount();

        long startTime = System.currentTimeMillis();
        var results = loadTestService.simulateConcurrentRequests(
                CONCURRENCY,
                () -> {
                    try {
                        return productService.getProductFull(TEST_SKU);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        long endTime = System.currentTimeMillis();
        long totalQueries = databaseService.getQueryCount();
        long additionalQueries = totalQueries - initialQueries;

        long cacheHits = CONCURRENCY - additionalQueries;
        double hitRate = ((double) cacheHits / CONCURRENCY) * 100;

        System.out.println("\nRESULTS:");
        System.out.println("  Concurrent Requests: " + CONCURRENCY);
        System.out.println("  Database Queries:    " + additionalQueries);
        System.out.println("  Cache Hits:          " + cacheHits);
        System.out.println("  Cache Hit Rate:      " + String.format("%.2f%%", hitRate));
        System.out.println("  Total Time:          " + (endTime - startTime) + "ms");
        System.out.println("  Avg Latency:         " + ((endTime - startTime) / CONCURRENCY) + "ms");
        System.out.println("\n✅ PERFECT: Cache served all requests!");
        System.out.println("   Strategies Applied:");
        System.out.println("   - Single-Flight (Request Deduplication)");
        System.out.println("   - Probabilistic Early Expiration");
        System.out.println("   - Jittered TTL");
        System.out.println("   - Negative Caching\n");

        // Verify full solution
        assertTrue(additionalQueries <= 1,
                "Should have 0-1 queries with warm cache: " + additionalQueries);
        assertTrue(hitRate > 95,
                "Cache hit rate should be >95%: " + hitRate);
    }

    @Test
    @DisplayName("Scenario 4: Cache Stampede - Comparison of all strategies")
    void testCacheStampede() throws Exception {
        System.out.println("""
                
                ═══════════════════════════════════════════════════════════
                  SCENARIO 4: CACHE STAMPEDE SIMULATION
                  Comparing All Strategies
                ═══════════════════════════════════════════════════════════""");

        int highConcurrency = 1000;

        // Test 1: Baseline (Problem)
        System.out.println("\n[Test 1: Baseline - No Mitigation]");
        databaseService.resetQueryCount();
        long baselineStart = System.currentTimeMillis();

        loadTestService.simulateConcurrentRequests(
                highConcurrency,
                () -> {
                    try {
                        return productService.getProductBaseline(TEST_SKU);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        long baselineTime = System.currentTimeMillis() - baselineStart;
        long baselineQueries = databaseService.getQueryCount();

        System.out.println("  Queries: " + baselineQueries);
        System.out.println("  Time:    " + baselineTime + "ms");
        System.out.println("  Avg:     " + (baselineTime / highConcurrency) + "ms per request");

        // Test 2: Single-Flight
        System.out.println("\n[Test 2: Single-Flight Only]");
        databaseService.resetQueryCount();
        singleFlight.resetMetrics();
        long sfStart = System.currentTimeMillis();

        loadTestService.simulateConcurrentRequests(
                highConcurrency,
                () -> {
                    try {
                        return productService.getProductSingleFlight(TEST_SKU);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        long sfTime = System.currentTimeMillis() - sfStart;
        long sfQueries = databaseService.getQueryCount();

        System.out.println("  Queries: " + sfQueries);
        System.out.println("  Time:    " + sfTime + "ms");
        System.out.println("  Avg:     " + (sfTime / highConcurrency) + "ms per request");

        // Test 3: Full Solution
        System.out.println("\n[Test 3: Full Solution]");

        // Warm cache first
        productService.getProductFull(TEST_SKU);
        Thread.sleep(100); // Ensure cache is set

        databaseService.resetQueryCount();
        long fullStart = System.currentTimeMillis();

        loadTestService.simulateConcurrentRequests(
                highConcurrency,
                () -> {
                    try {
                        return productService.getProductFull(TEST_SKU);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        long fullTime = System.currentTimeMillis() - fullStart;
        long fullQueries = databaseService.getQueryCount();

        System.out.println("  Queries: " + fullQueries);
        System.out.println("  Time:    " + fullTime + "ms");
        System.out.println("  Avg:     " + (fullTime / highConcurrency) + "ms per request");

        // Print comparison
        System.out.println("""
                
                ═══════════════════════════════════════════════════════════
                  COMPARISON SUMMARY
                ═══════════════════════════════════════════════════════════""");
        System.out.println("\n  Metric              | Baseline    | Single-Flight | Full Solution");
        System.out.println("  ─────────────────────────────────────────────────────────────────");
        System.out.printf("  DB Queries          | %-11d | %-13d | %d%n",
                baselineQueries, sfQueries, fullQueries);
        System.out.printf("  Total Time (ms)     | %-11d | %-13d | %d%n",
                baselineTime, sfTime, fullTime);
        System.out.printf("  Avg Latency (ms)    | %-11d | %-13d | %d%n",
                baselineTime/highConcurrency, sfTime/highConcurrency, fullTime/highConcurrency);
        System.out.println("\n  Improvements:");
        System.out.printf("  Query Reduction     | 1x          | %dx           | %dx%n",
                baselineQueries/Math.max(1, sfQueries),
                baselineQueries/Math.max(1, fullQueries));
        System.out.printf("  Speed Improvement   | 1x          | %.1fx          | %.1fx%n",
                (double)baselineTime/sfTime,
                (double)baselineTime/fullTime);
        System.out.println("\n═══════════════════════════════════════════════════════════\n");

        assertTrue(sfQueries < baselineQueries / 10,
                "Single-flight should reduce queries by >10x");
        assertTrue(fullQueries < sfQueries,
                "Full solution should have fewer queries than single-flight");
        assertTrue(fullTime < baselineTime / 2,
                "Full solution should be >2x faster");
    }

    @Test
    @DisplayName("Scenario 5: Negative caching protects against invalid keys")
    void testNegativeCaching() throws Exception {
        System.out.println("""
                
                ═══════════════════════════════════════════════════════════
                  SCENARIO 5: NEGATIVE CACHING
                  Protection Against Invalid Keys
                ═══════════════════════════════════════════════════════════""");

        String invalidSku = "INVALID-SKU-999";

        databaseService.resetQueryCount();

        // First request - cache miss
        productService.getProductFull(invalidSku);
        long firstQuery = databaseService.getQueryCount();

        // Subsequent requests - should use negative cache
        for (int i = 0; i < 10; i++) {
            productService.getProductFull(invalidSku);
        }

        long totalQueries = databaseService.getQueryCount();

        System.out.println("\nRESULTS:");
        System.out.println("  Invalid SKU:         " + invalidSku);
        System.out.println("  Total Requests:      11");
        System.out.println("  Database Queries:    " + totalQueries);
        System.out.println("  Negative Cache Hits: " + (11 - totalQueries));
        System.out.println("\n✅ PROTECTED: Negative cache prevented repeated DB queries!\n");

        assertEquals(1, totalQueries,
                "Should only query database once for invalid key");
    }

    @Test
    @DisplayName("Scenario 6: Performance under sustained load")
    void testSustainedLoad() throws Exception {
        System.out.println("""
                
                ═══════════════════════════════════════════════════════════
                  SCENARIO 6: SUSTAINED LOAD TEST
                  Multiple rounds of requests
                ═══════════════════════════════════════════════════════════""");

        int rounds = 5;
        int requestsPerRound = 100;

        // Warm cache
        productService.getProductFull(TEST_SKU);

        databaseService.resetQueryCount();
        long totalStartTime = System.currentTimeMillis();

        for (int round = 0; round < rounds; round++) {
            System.out.println("\nRound " + (round + 1) + ":");
            long roundStart = System.currentTimeMillis();

            loadTestService.simulateConcurrentRequests(
                    requestsPerRound,
                    () -> {
                        try {
                            return productService.getProductFull(TEST_SKU);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
            );

            long roundTime = System.currentTimeMillis() - roundStart;
            System.out.println("  Time: " + roundTime + "ms");
            System.out.println("  Avg:  " + (roundTime / requestsPerRound) + "ms per request");
        }

        long totalTime = System.currentTimeMillis() - totalStartTime;
        long totalQueries = databaseService.getQueryCount();
        int totalRequests = rounds * requestsPerRound;

        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("OVERALL RESULTS:");
        System.out.println("  Total Requests:      " + totalRequests);
        System.out.println("  Total DB Queries:    " + totalQueries);
        System.out.println("  Cache Hit Rate:      " +
                String.format("%.2f%%", ((double)(totalRequests - totalQueries) / totalRequests * 100)));
        System.out.println("  Total Time:          " + totalTime + "ms");
        System.out.println("  Throughput:          " +
                String.format("%.0f req/s", (double)totalRequests / (totalTime / 1000.0)));
        System.out.println("═══════════════════════════════════════════════════════════\n");

        // Should have very few queries compared to total requests
        assertTrue(totalQueries < totalRequests / 10,
                "Queries should be <10% of total requests");
    }
}