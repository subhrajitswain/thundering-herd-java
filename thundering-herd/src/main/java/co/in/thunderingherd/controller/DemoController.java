package co.in.thunderingherd.controller;


import co.in.thunderingherd.core.SingleFlight;
import co.in.thunderingherd.model.Product;
import co.in.thunderingherd.service.DatabaseService;
import co.in.thunderingherd.service.LoadTestService;
import co.in.thunderingherd.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final ProductService productService;
    private final DatabaseService databaseService;
    private final LoadTestService loadTestService;
    private final SingleFlight singleFlight;

    @GetMapping("/baseline")
    public ResponseEntity<Map<String, Object>> runBaseline(
            @RequestParam(defaultValue = "DEMO-001") String sku,
            @RequestParam(defaultValue = "100") int concurrency
    ) {
        log.info("Running BASELINE demo: {} concurrent requests for SKU: {}", concurrency, sku);

        databaseService.resetQueryCount();
        long startTime = System.currentTimeMillis();

        var results = loadTestService.simulateConcurrentRequests(
                concurrency,
                () -> {
                    try {
                        return productService.getProductBaseline(sku);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        long endTime = System.currentTimeMillis();
        Map<String, Object> response = getObjectMap(concurrency, endTime, startTime);

        return ResponseEntity.ok(response);
    }

    private @NonNull Map<String, Object> getObjectMap(int concurrency, long endTime, long startTime) {
        long dbQueries = databaseService.getQueryCount();

        Map<String, Object> response = new HashMap<>();
        response.put("scenario", "Baseline (No Mitigation)");
        response.put("concurrency", concurrency);
        response.put("totalRequests", concurrency);
        response.put("databaseQueries", dbQueries);
        response.put("totalTimeMs", endTime - startTime);
        response.put("avgLatencyMs", (endTime - startTime) / concurrency);
        response.put("problem", "Every request hits the database!");
        response.put("impact", String.format("%dx database load", dbQueries));
        return response;
    }

    @GetMapping("/singleflight")
    public ResponseEntity<Map<String, Object>> runSingleFlight(
            @RequestParam(defaultValue = "DEMO-001") String sku,
            @RequestParam(defaultValue = "100") int concurrency
    ) {
        log.info("Running SINGLE-FLIGHT demo: {} concurrent requests for SKU: {}", concurrency, sku);

        databaseService.resetQueryCount();
        singleFlight.resetMetrics();
        long startTime = System.currentTimeMillis();

        var results = loadTestService.simulateConcurrentRequests(
                concurrency,
                () -> {
                    try {
                        return productService.getProductSingleFlight(sku);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        long endTime = System.currentTimeMillis();
        Map<String, Object> response = getStringObjectMap(concurrency, endTime, startTime);

        return ResponseEntity.ok(response);
    }

    private @NonNull Map<String, Object> getStringObjectMap(int concurrency, long endTime, long startTime) {
        long dbQueries = databaseService.getQueryCount();
        var metrics = singleFlight.getMetrics();

        Map<String, Object> response = new HashMap<>();
        response.put("scenario", "Single-Flight Pattern");
        response.put("concurrency", concurrency);
        response.put("totalRequests", concurrency);
        response.put("databaseQueries", dbQueries);
        response.put("deduplications", metrics.deduplications());
        response.put("executions", metrics.executions());
        response.put("dedupRatio", String.format("%.2f%%", metrics.dedupRatioPercent()));
        response.put("totalTimeMs", endTime - startTime);
        response.put("avgLatencyMs", (endTime - startTime) / concurrency);
        response.put("improvement", String.format("%dx reduction in DB load", concurrency / Math.max(1, dbQueries)));
        return response;
    }

    @GetMapping("/full")
    public ResponseEntity<Map<String, Object>> runFullSolution(
            @RequestParam(defaultValue = "DEMO-001") String sku,
            @RequestParam(defaultValue = "100") int concurrency
    ) throws Exception {
        log.info("Running FULL SOLUTION demo: {} concurrent requests for SKU: {}", concurrency, sku);

        databaseService.resetQueryCount();
        singleFlight.resetMetrics();
        long startTime = System.currentTimeMillis();

        productService.getProductFull(sku);
        long initialDbQueries = databaseService.getQueryCount();

        var results = loadTestService.simulateConcurrentRequests(
                concurrency,
                () -> {
                    try {
                        return productService.getProductFull(sku);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        long endTime = System.currentTimeMillis();
        long totalDbQueries = databaseService.getQueryCount();
        long additionalQueries = totalDbQueries - initialDbQueries;

        long cacheHits = concurrency - additionalQueries;
        double hitRate = ((double)(concurrency - additionalQueries) / concurrency) * 100;

        Map<String, Object> response = new HashMap<>();
        response.put("scenario", "Full Solution (All Strategies)");
        response.put("concurrency", concurrency);
        response.put("totalRequests", concurrency);
        response.put("databaseQueries", additionalQueries);
        response.put("cacheHits", cacheHits);
        response.put("cacheHitRate", String.format("%.2f%%", hitRate));
        response.put("totalTimeMs", endTime - startTime);
        response.put("avgLatencyMs", (endTime - startTime) / concurrency);
        response.put("strategies", new String[]{
                "Single-Flight",
                "Probabilistic Early Expiration",
                "Jittered TTL",
                "Negative Caching"
        });
        response.put("improvement", additionalQueries == 0 ?
                "Perfect! No database queries needed" :
                String.format("%dx reduction in DB load", concurrency / Math.max(1, additionalQueries)));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stampede")
    public ResponseEntity<Map<String, Object>> simulateStampede(
            @RequestParam(defaultValue = "DEMO-001") String sku,
            @RequestParam(defaultValue = "1000") int concurrency
    ) {
        log.info("Running CACHE STAMPEDE simulation: {} concurrent requests", concurrency);

        Map<String, Object> response = new HashMap<>();
        response.put("scenario", "Cache Stampede Simulation");
        response.put("concurrency", concurrency);
        try {
            log.info("Starting BASELINE test...");
            databaseService.resetQueryCount();
            long baselineStart = System.currentTimeMillis();

            var baselineResults = loadTestService.simulateConcurrentRequests(
                    concurrency,
                    () -> {
                        try {
                            return productService.getProductBaseline(sku);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
            );

            long baselineQueries = databaseService.getQueryCount();
            long baselineTime = System.currentTimeMillis() - baselineStart;

            log.info("BASELINE completed: {} queries in {}ms", baselineQueries, baselineTime);
            Thread.sleep(1000);
            log.info("Starting FULL SOLUTION test...");
            productService.getProductFull(sku);
            Thread.sleep(100);

            databaseService.resetQueryCount();
            long fullStart = System.currentTimeMillis();

            var fullResults = loadTestService.simulateConcurrentRequests(
                    concurrency,
                    () -> {
                        try {
                            return productService.getProductFull(sku);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
            );

            long fullQueries = databaseService.getQueryCount();
            long fullTime = System.currentTimeMillis() - fullStart;

            log.info("FULL SOLUTION completed: {} queries in {}ms", fullQueries, fullTime);

            Map<String, Object> baseline = new HashMap<>();
            baseline.put("databaseQueries", baselineQueries);
            baseline.put("timeMs", baselineTime);
            baseline.put("avgLatencyMs", baselineTime / concurrency);

            Map<String, Object> full = new HashMap<>();
            full.put("databaseQueries", fullQueries);
            full.put("timeMs", fullTime);
            full.put("avgLatencyMs", fullTime / concurrency);

            response.put("baseline", baseline);
            response.put("fullSolution", full);

            long queryReduction = baselineQueries / Math.max(1, fullQueries);
            long timeReduction = baselineTime / Math.max(1, fullTime);

            Map<String, String> improvement = new HashMap<>();
            improvement.put("queriesReduction", queryReduction + "x");
            improvement.put("timeReduction", timeReduction + "x");
            improvement.put("summary", String.format(
                    "Reduced queries by %dx and improved speed by %dx",
                    queryReduction,
                    timeReduction
            ));

            response.put("improvement", improvement);
            response.put("status", "success");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in stampede simulation", e);

            response.put("status", "error");
            response.put("error", e.getMessage());
            response.put("message", "Stampede test failed. Try reducing concurrency or check logs.");

            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/product/{sku}")
    public ResponseEntity<Product> getProduct(@PathVariable String sku) throws Exception {
        Optional<Product> product = productService.getProductFull(sku);
        return product.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        var sfMetrics = singleFlight.getMetrics();

        Map<String, Object> response = new HashMap<>();
        response.put("databaseQueries", databaseService.getQueryCount());
        response.put("singleFlight", Map.of(
                "deduplications", sfMetrics.deduplications(),
                "executions", sfMetrics.executions(),
                "dedupRatio", String.format("%.2f%%", sfMetrics.dedupRatioPercent())
        ));

        return ResponseEntity.ok(response);
    }
}