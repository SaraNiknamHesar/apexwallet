package com.apexwallet.app.port;

import com.apexwallet.app.identity.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface UserDirectory {

    void save(AppUser user);

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findById(UUID id);
}
