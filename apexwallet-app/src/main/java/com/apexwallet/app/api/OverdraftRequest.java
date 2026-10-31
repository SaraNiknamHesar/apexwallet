package com.apexwallet.app.api;

import jakarta.validation.constraints.PositiveOrZero;

public record OverdraftRequest(@PositiveOrZero long limitMinor, @PositiveOrZero long expectedVersion) {
}
