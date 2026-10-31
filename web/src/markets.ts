import { useSyncExternalStore } from 'react'

/**
 * A simulated crypto market feed for the demo.
 *
 * Nothing here talks to a real exchange: every price is a random walk around a
 * fixed anchor, seeded with twenty minutes of history so the chart is never
 * empty, and advanced once a second while the page is open. The orders you place
 * from the Markets page still settle on the real ledger.
 */

export type Instrument = {
  symbol: string
  name: string
  /** Anchor price in EUR the walk keeps drifting back to. */
  anchor: number
  /** Relative standard deviation of one one-second step. */
  vol: number
  digits: number
  accent: string
}

export const instruments: Instrument[] = [
  { symbol: 'BTC', name: 'Bitcoin', anchor: 67240.5, vol: 0.0007, digits: 2, accent: '#F7931A' },
  { symbol: 'ETH', name: 'Ethereum', anchor: 3182.4, vol: 0.0009, digits: 2, accent: '#627EEA' },
  { symbol: 'SOL', name: 'Solana', anchor: 148.35, vol: 0.0013, digits: 2, accent: '#14F195' },
  { symbol: 'BNB', name: 'BNB', anchor: 512.8, vol: 0.001, digits: 2, accent: '#F3BA2F' },
  { symbol: 'XRP', name: 'XRP', anchor: 0.5238, vol: 0.0016, digits: 4, accent: '#23292F' },
  { symbol: 'ADA', name: 'Cardano', anchor: 0.4461, vol: 0.0018, digits: 4, accent: '#0033AD' },
  { symbol: 'DOGE', name: 'Dogecoin', anchor: 0.1122, vol: 0.0024, digits: 4, accent: '#C2A633' },
  { symbol: 'AVAX', name: 'Avalanche', anchor: 28.64, vol: 0.0016, digits: 2, accent: '#E84142' },
]

/** One candle covers five seconds. */
export const candleSeconds = 5
const historyCandles = 240
const sparkPoints = 48

export type Candle = { time: number; open: number; high: number; low: number; close: number }

export type Quote = {
  instrument: Instrument
  price: number
  previous: number
  opened: number
  high: number
  low: number
  volume: number
  spark: number[]
  candles: Candle[]
}

export function changePercent(quote: Quote): number {
  return ((quote.price - quote.opened) / quote.opened) * 100
}

export function formatPrice(value: number, digits: number): string {
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value)
}

export function formatUnits(value: number): string {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value)
}

function gauss(): number {
  let u = 0
  let v = 0
  while (u === 0) u = Math.random()
  while (v === 0) v = Math.random()
  return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v)
}

function round(value: number, digits: number): number {
  const scale = 10 ** digits
  return Math.round(value * scale) / scale
}

function seedQuote(instrument: Instrument): Quote {
  const candles: Candle[] = []
  const now = Math.floor(Date.now() / 1000)
  const start = now - (historyCandles - 1) * candleSeconds
  let price = instrument.anchor * (1 + gauss() * 0.01)
  for (let index = 0; index < historyCandles; index += 1) {
    const open = price
    let high = open
    let low = open
    for (let step = 0; step < candleSeconds; step += 1) {
      const pull = ((instrument.anchor - price) / instrument.anchor) * 0.02
      price = price * (1 + pull + gauss() * instrument.vol)
      high = Math.max(high, price)
      low = Math.min(low, price)
    }
    candles.push({
      time: start + index * candleSeconds,
      open: round(open, instrument.digits),
      high: round(high, instrument.digits),
      low: round(low, instrument.digits),
      close: round(price, instrument.digits),
    })
  }
  const last = candles[candles.length - 1]
  return {
    instrument,
    price: last.close,
    previous: last.close,
    opened: candles[0].open,
    high: Math.max(...candles.map((candle) => candle.high)),
    low: Math.min(...candles.map((candle) => candle.low)),
    volume: 1200 + Math.round(Math.random() * 900),
    spark: candles.slice(-sparkPoints).map((candle) => candle.close),
    candles,
  }
}

class MarketFeed {
  readonly quotes = new Map<string, Quote>()
  version = 0
  private readonly listeners = new Set<() => void>()

  constructor() {
    for (const instrument of instruments) this.quotes.set(instrument.symbol, seedQuote(instrument))
    if (typeof window !== 'undefined') window.setInterval(() => this.tick(), 1000)
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  getVersion = (): number => this.version

  quote(symbol: string): Quote {
    const found = this.quotes.get(symbol)
    if (found) return found
    return this.quotes.get(instruments[0].symbol) as Quote
  }

  private tick(): void {
    const bucket = Math.floor(Date.now() / 1000 / candleSeconds) * candleSeconds
    for (const quote of this.quotes.values()) {
      const { instrument } = quote
      const pull = ((instrument.anchor - quote.price) / instrument.anchor) * 0.03
      const next = quote.price * (1 + pull + gauss() * instrument.vol)
      quote.previous = quote.price
      quote.price = round(next, instrument.digits)
      quote.high = Math.max(quote.high, quote.price)
      quote.low = Math.min(quote.low, quote.price)
      quote.volume += Math.round(Math.random() * 4)
      quote.spark = [...quote.spark.slice(-(sparkPoints - 1)), quote.price]

      const last = quote.candles[quote.candles.length - 1]
      if (last.time < bucket) {
        quote.candles = [
          ...quote.candles.slice(-(historyCandles - 1)),
          { time: bucket, open: quote.price, high: quote.price, low: quote.price, close: quote.price },
        ]
      } else {
        last.high = Math.max(last.high, quote.price)
        last.low = Math.min(last.low, quote.price)
        last.close = quote.price
      }
    }
    this.version += 1
    for (const listener of this.listeners) listener()
  }
}

export const marketFeed = new MarketFeed()

/** Re-renders the caller once per second while the feed ticks. */
export function useMarketFeed(): MarketFeed {
  useSyncExternalStore(marketFeed.subscribe, marketFeed.getVersion, marketFeed.getVersion)
  return marketFeed
}
