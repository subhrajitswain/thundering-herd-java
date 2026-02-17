package co.in.thunderingherd.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
@Service
public class LoadTestService {

    private final ExecutorService executorService;

    public LoadTestService() {
        this.executorService = Executors.newFixedThreadPool(1000);
    }

    public <T> List<T> simulateConcurrentRequests(int concurrency, Supplier<T> task) {
        CountDownLatch latch = new CountDownLatch(1);
        List<CompletableFuture<T>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                try {
                    latch.await();
                    return task.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
            }, executorService);

            futures.add(future);
        }

        latch.countDown();

        List<T> results = new ArrayList<>();
        for (CompletableFuture<T> future : futures) {
            try {
                results.add(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("Error in concurrent execution", e);
            }
        }

        return results;
    }
}
