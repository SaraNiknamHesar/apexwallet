package com.apexwallet.domain.markets;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Single-threaded price-time book. Deterministic replay matters more than parallel matching.
 * Bids rank high price first. Asks rank low price first. Same price keeps arrival order.
 */
public final class OrderBook {

    private final NavigableMap<Long, ArrayDeque<Resting>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, ArrayDeque<Resting>> asks = new TreeMap<>();
    private long sequence;

    public MatchResult submit(Order order) {
        if (order.quantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (order.type() == OrderType.LIMIT && (order.limitPrice() == null || order.limitPrice() <= 0)) {
            throw new IllegalArgumentException("limit price must be positive");
        }
        long remaining = order.quantity();
        List<Trade> trades = new ArrayList<>();
        NavigableMap<Long, ArrayDeque<Resting>> opposite = order.side() == Side.BUY ? asks : bids;
        while (remaining > 0 && !opposite.isEmpty()) {
            long price = opposite.firstKey();
            if (order.type() == OrderType.LIMIT && !crosses(order, price)) {
                break;
            }
            ArrayDeque<Resting> level = opposite.get(price);
            Resting maker = level.peekFirst();
            long quantity = Math.min(remaining, maker.remaining);
            trades.add(new Trade(order.id(), maker.id, price, quantity));
            remaining -= quantity;
            maker.remaining -= quantity;
            if (maker.remaining == 0) {
                level.removeFirst();
                if (level.isEmpty()) {
                    opposite.remove(price);
                }
            }
        }
        long rested = 0;
        long cancelled = 0;
        if (remaining > 0 && order.type() == OrderType.LIMIT) {
            rested = remaining;
            rest(order, remaining);
        } else {
            cancelled = remaining;
        }
        if (crossed()) {
            throw new IllegalStateException("book is crossed");
        }
        return new MatchResult(List.copyOf(trades), rested, cancelled);
    }

    public Long bestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    public Long bestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    public boolean crossed() {
        return bestBid() != null && bestAsk() != null && bestBid() >= bestAsk();
    }

    private boolean crosses(Order order, long makerPrice) {
        return order.side() == Side.BUY ? order.limitPrice() >= makerPrice : order.limitPrice() <= makerPrice;
    }

    private void rest(Order order, long quantity) {
        NavigableMap<Long, ArrayDeque<Resting>> book = order.side() == Side.BUY ? bids : asks;
        book.computeIfAbsent(order.limitPrice(), price -> new ArrayDeque<>())
                .addLast(new Resting(order.id(), quantity, sequence++));
    }

    public record MatchResult(List<Trade> trades, long restedQuantity, long cancelledQuantity) {
        public long tradedQuantity() {
            return trades.stream().mapToLong(Trade::quantity).sum();
        }
    }

    private static final class Resting {
        private final String id;
        private long remaining;

        private Resting(String id, long remaining, long sequence) {
            this.id = id;
            this.remaining = remaining;
        }
    }
}
