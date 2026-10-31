package com.apexwallet.app.identity;

import java.util.UUID;

public record ApexPrincipal(UUID id, String username, Role role) {
}
