package co.in.thunderingherd.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedItem<T> implements Serializable {

    private T value;
    private Instant createdAt;
    private boolean negative;

    public CachedItem(T value, Instant createdAt) {
        this.value = value;
        this.createdAt = createdAt;
        this.negative = false;
    }

    public static <T> CachedItem<T> negative() {
        CachedItem<T> item = new CachedItem<>();
        item.setNegative(true);
        item.setCreatedAt(Instant.now());
        return item;
    }
}
