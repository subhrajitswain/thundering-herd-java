package co.in.thunderingherd.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Single-Flight Pattern Implementation
 * Ensures only ONE concurrent request executes for a given key,
 * while all other requests wait for the result.
 */

@Slf4j
@Component
public class SingleFlight {

    private final ConcurrentHashMap<String, Call> calls = new ConcurrentHashMap<>();
    private final AtomicInteger dedupCount = new AtomicInteger(0);
    private final AtomicInteger executionCount = new AtomicInteger(0);

    public <T> T execute(String key , Supplier<T> fn , long timeout) throws Exception {
        while(true){
            Call existingCall = calls.get(key);
            if(existingCall != null){
                dedupCount.incrementAndGet();
                log.debug("Request deduplicated for key: {}",key);
                try{
                    return (T) existingCall.await(timeout);
                }catch (Exception e){
                    log.error("Error waiting for deduped request: {}",e.getMessage());
                    throw new RuntimeException("Failed to get result from single-flight",e);
                }
            }
            Call newCall = new Call();
            Call previousCall = calls.putIfAbsent(key, newCall);
            if(previousCall != null){
                continue;
            }
            executionCount.incrementAndGet();
            log.debug("Executing request for key: {}",key);
            try{
                T result = fn.get();
                newCall.complete(result);
                return result;
            }catch (Exception e){
                newCall.completeExceptionally(e);
                throw e;
            }finally {
                calls.remove(key);
            }
        }
    }

    public<T> T execute(String key, Supplier<T> fn) throws Exception {
        return execute(key, fn, 10000);
    }
    public Metrics getMetrics(){
        return new Metrics(dedupCount.get(),
                executionCount.get(),
                calculateDedupRatio());
    }
    public void resetMetrics(){
        dedupCount.set(0);
        executionCount.set(0);
    }

    private double calculateDedupRatio(){
        int total = executionCount.get() + dedupCount.get();
        if(total == 0) return 0.0;
        return (double)dedupCount.get()/total * 100;
    }


    private static class Call {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Object result;
        private volatile Exception exception;

        public void complete(Object result) {
            this.result = result;
            latch.countDown();
        }
        public void completeExceptionally(Exception exception) {
            this.exception = exception;
            latch.countDown();
        }
        public Object await(long timeout) throws Exception {
            boolean completed = latch.await(timeout, TimeUnit.MILLISECONDS);
            if(!completed){
                throw new TimeoutException("Single-flight call timed out");
            }
            if(exception != null){
                throw exception;
            }
            return result;
        }
    }

    public record Metrics(int deduplications, int executions,double dedupRatioPercent){}
}
