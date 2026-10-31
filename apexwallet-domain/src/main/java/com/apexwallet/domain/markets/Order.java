package com.apexwallet.domain.markets;

public record Order(String id, Side side, OrderType type, Long limitPrice, long quantity) {
}
