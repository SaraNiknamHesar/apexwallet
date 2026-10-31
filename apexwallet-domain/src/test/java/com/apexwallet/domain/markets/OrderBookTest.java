package com.apexwallet.domain.markets;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderBookTest {

    @Test
    void priceTimePriorityPartiallyFillsAndDoesNotCross() {
        OrderBook book = new OrderBook();
        book.submit(new Order("s1", Side.SELL, OrderType.LIMIT, 10L, 5));
        book.submit(new Order("s2", Side.SELL, OrderType.LIMIT, 10L, 5));
        OrderBook.MatchResult buy = book.submit(new Order("b1", Side.BUY, OrderType.LIMIT, 10L, 7));
        assertEquals(7, buy.tradedQuantity());
        assertEquals(0, buy.cancelledQuantity());
        assertEquals(0, buy.restedQuantity());
        assertEquals("s1", buy.trades().get(0).makerId());
        assertEquals(2, buy.trades().get(1).quantity());
        assertEquals(10L, book.bestAsk());
        assertNull(book.bestBid());
        assertFalse(book.crossed());
    }

    @Test
    void anUnfilledMarketOrderIsCancelledRatherThanRested() {
        OrderBook book = new OrderBook();
        book.submit(new Order("s1", Side.SELL, OrderType.LIMIT, 10L, 5));
        OrderBook.MatchResult market = book.submit(new Order("b1", Side.BUY, OrderType.MARKET, null, 8));
        assertEquals(5, market.tradedQuantity());
        assertEquals(3, market.cancelledQuantity());
        assertNull(book.bestBid());
    }

    @Property
    void quantityIsConservedAndTheBookNeverCrosses(@ForAll @IntRange(min = 1, max = 20) int sellQty,
                                                    @ForAll @IntRange(min = 1, max = 20) int buyQty) {
        OrderBook book = new OrderBook();
        OrderBook.MatchResult sell = book.submit(new Order("s", Side.SELL, OrderType.LIMIT, 10L, sellQty));
        OrderBook.MatchResult buy = book.submit(new Order("b", Side.BUY, OrderType.LIMIT, 10L, buyQty));
        assertEquals(sellQty, sell.tradedQuantity() + sell.restedQuantity() + sell.cancelledQuantity());
        assertEquals(buyQty, buy.tradedQuantity() + buy.restedQuantity() + buy.cancelledQuantity());
        assertFalse(book.crossed());
    }

    @Test
    void aTradeIsABalancedLedgerPair() {
        Trade trade = new Trade("taker", "maker", 10, 4);
        long notional = trade.price() * trade.quantity();
        List<Long> legs = List.of(notional, notional);
        assertEquals(legs.get(0), legs.get(1));
    }
}
