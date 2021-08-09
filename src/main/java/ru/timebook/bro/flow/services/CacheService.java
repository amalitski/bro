package ru.timebook.bro.flow.services;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;

@Service
public class CacheService {
    private final HashMap<String, Value> map;

    @Data
    @Builder
    protected static class Value {
        private long expired;
        private Object value;
    }

    public CacheService() {
        this.map = new HashMap<>();
    }

    public synchronized void set(String key, Object value, int ttlSec) {
        var expired = Instant.now().getEpochSecond() + ttlSec;
        map.put(key, Value.builder().value(value).expired(expired).build());
    }

    public synchronized Optional<?> get(String key) {
        if (!map.containsKey(key)) {
            return Optional.empty();
        }
        var val = map.get(key);
        if (Instant.now().getEpochSecond() < val.getExpired()) {
            map.remove(key);
            return Optional.of(val.getValue());
        }
        return Optional.empty();
    }
}
