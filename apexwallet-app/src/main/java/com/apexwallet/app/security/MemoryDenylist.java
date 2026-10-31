package com.apexwallet.app.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class MemoryDenylist implements Denylist {

    private final ConcurrentHashMap<String, Instant> until = new ConcurrentHashMap<>();

    @Override
    public void revoke(String jti, Instant expiry) {
        until.put(jti, expiry);
    }

    @Override
    public boolean contains(String jti) {
        return jti != null && until.containsKey(jti);
    }
}
