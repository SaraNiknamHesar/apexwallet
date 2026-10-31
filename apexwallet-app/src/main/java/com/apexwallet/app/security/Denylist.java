package com.apexwallet.app.security;

import java.time.Instant;

public interface Denylist {

    void revoke(String jti, Instant until);

    boolean contains(String jti);
}
