package edu.cqupt.devbrain.auth.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class InMemorySecurityCache implements SecurityCache {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        entries.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    @Override
    public long increment(String key, Duration ttl) {
        long next = get(key).map(Long::parseLong).orElse(0L) + 1;
        set(key, Long.toString(next), ttl);
        return next;
    }

    @Override
    public void delete(String key) {
        entries.remove(key);
    }

    private record Entry(String value, Instant expiresAt) {
    }
}
