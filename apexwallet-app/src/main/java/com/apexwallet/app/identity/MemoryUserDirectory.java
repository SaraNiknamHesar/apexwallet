package com.apexwallet.app.identity;

import com.apexwallet.app.port.UserDirectory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class MemoryUserDirectory implements UserDirectory {

    private final ConcurrentHashMap<UUID, AppUser> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> byName = new ConcurrentHashMap<>();

    @Override
    public void save(AppUser user) {
        byId.put(user.id(), user);
        byName.put(user.username(), user.id());
    }

    @Override
    public Optional<AppUser> findByUsername(String username) {
        UUID id = byName.get(username);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<AppUser> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }
}
