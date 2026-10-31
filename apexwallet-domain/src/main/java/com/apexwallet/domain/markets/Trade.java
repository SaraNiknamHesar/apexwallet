package com.apexwallet.domain.markets;

public record Trade(String takerId, String makerId, long price, long quantity) {
}
