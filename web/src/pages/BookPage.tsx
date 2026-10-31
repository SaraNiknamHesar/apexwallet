import { useEffect, useMemo, useState } from 'react'
import { ApiError, listAccounts, workspace } from '../api'
import { MarketChart } from '../market-chart'
import { changePercent, formatPrice, formatUnits, instruments, useMarketFeed, type Quote } from '../markets'
import { formatMinor } from '../money'
import { Button, Card, Field, Notice } from '../ui'

type BookTrade = { price: number; quantity: number }
type BookResult = {
  market?: string
  bestBid?: number | null
  bestAsk?: number | null
  restedQuantity: number
  cancelledQuantity: number
  trades: BookTrade[]
  history: BookTrade[]
}

const ranges = [
  { label: '1m', candles: 12 },
  { label: '5m', candles: 60 },
  { label: '10m', candles: 120 },
  { label: '20m', candles: 240 },
]

function Sparkline({ points, accent }: { points: number[]; accent: string }) {
  const path = useMemo(() => {
    if (points.length < 2) return ''
    const min = Math.min(...points)
    const max = Math.max(...points)
    const span = max - min || 1
    return points
      .map((value, index) => {
        const x = (index / (points.length - 1)) * 64
        const y = 24 - ((value - min) / span) * 22
        return `${index === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
      })
      .join(' ')
  }, [points])
  return (
    <svg viewBox="0 0 64 26" className="h-7 w-16 shrink-0" aria-hidden="true">
      <path d={path} fill="none" stroke={accent} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function ChangePill({ value }: { value: number }) {
  const up = value >= 0
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${
        up ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-[#FEE2E2] text-[#B91C1C]'
      }`}
    >
      {up ? '▲' : '▼'} {Math.abs(value).toFixed(2)}%
    </span>
  )
}

function Watchlist({ active, onSelect }: { active: string; onSelect: (symbol: string) => void }) {
  const feed = useMarketFeed()
  return (
    <Card className="p-3">
      <p className="px-2 pb-2 pt-1 text-xs font-semibold uppercase tracking-wide text-[#8BA3CB]">Markets</p>
      <div className="space-y-1">
        {instruments.map((instrument) => {
          const quote = feed.quote(instrument.symbol)
          const change = changePercent(quote)
          const selected = instrument.symbol === active
          return (
            <button
              key={instrument.symbol}
              type="button"
              onClick={() => onSelect(instrument.symbol)}
              className={`flex w-full items-center gap-2 rounded-2xl px-2.5 py-2 text-left transition duration-200 ${
                selected ? 'bg-[#EEF2FF]' : 'hover:bg-[#F5F7FA]'
              }`}
            >
              <span
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[11px] font-bold text-white"
                style={{ backgroundColor: instrument.accent }}
              >
                {instrument.symbol.slice(0, 3)}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold text-[#343C6A]">{instrument.symbol}</span>
                <span className={`num block text-xs ${change >= 0 ? 'text-[#15803D]' : 'text-[#B91C1C]'}`}>
                  {change >= 0 ? '+' : ''}
                  {change.toFixed(2)}%
                </span>
              </span>
              <span className="flex shrink-0 flex-col items-end gap-0.5">
                <span className="num text-sm font-semibold text-[#343C6A]">
                  {formatPrice(quote.price, instrument.digits)}
                </span>
                <Sparkline points={quote.spark} accent={instrument.accent} />
              </span>
            </button>
          )
        })}
      </div>
    </Card>
  )
}

function Depth({ quote }: { quote: Quote }) {
  const { price, instrument } = quote
  const step = Math.max(price * 0.0012, 2 * 10 ** -instrument.digits)
  const levels = [0, 1, 2, 3, 4]
  const size = (index: number, bid: boolean) => 0.35 + (Math.sin((price + index * 7 + (bid ? 3 : 11)) * 13.7) + 1) * 0.9
  const bids = levels.map((index) => ({ price: price - step * (index + 1), size: size(index, true) }))
  const asks = levels.map((index) => ({ price: price + step * (index + 1), size: size(index, false) }))
  const max = Math.max(...bids.map((row) => row.size), ...asks.map((row) => row.size))
  const digits = instrument.digits

  const row = (level: { price: number; size: number }, bid: boolean, index: number) => (
    <div key={`${bid ? 'b' : 'a'}${index}`} className="relative flex items-center justify-between px-2 py-[3px]">
      <span
        className={`absolute inset-y-0 ${bid ? 'right-0 bg-[#DCFCE7]' : 'right-0 bg-[#FEE2E2]'}`}
        style={{ width: `${(level.size / max) * 100}%` }}
      />
      <span className={`num relative text-xs font-medium ${bid ? 'text-[#15803D]' : 'text-[#B91C1C]'}`}>
        {formatPrice(level.price, digits)}
      </span>
      <span className="num relative text-xs text-[#718EBF]">{level.size.toFixed(2)}</span>
    </div>
  )

  return (
    <Card className="p-4">
      <div className="mb-2 flex items-center justify-between">
        <p className="text-sm font-semibold text-[#343C6A]">Depth · {instrument.symbol}/EUR</p>
        <p className="text-xs text-[#8BA3CB]">simulated</p>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="px-2 pb-1 text-[10px] font-semibold uppercase tracking-wide text-[#8BA3CB]">Bids</p>
          {bids.map((level, index) => row(level, true, index))}
        </div>
        <div>
          <p className="px-2 pb-1 text-[10px] font-semibold uppercase tracking-wide text-[#8BA3CB]">Asks</p>
          {asks.map((level, index) => row(level, false, index))}
        </div>
      </div>
    </Card>
  )
}

function describe(book: BookResult, placed: { side: string; quantity: number; price: number } | null, symbol: string) {
  const lines: string[] = []
  if (placed) {
    const verb = placed.side === 'BUY' ? 'buy' : 'sell'
    if (book.trades.length > 0) {
      const units = book.trades.reduce((sum, trade) => sum + trade.quantity, 0)
      const paid = book.trades.reduce((sum, trade) => sum + trade.price * trade.quantity, 0)
      lines.push(
        placed.side === 'BUY'
          ? `Matched: ${units} ${symbol} bought, ${formatMinor(paid, 'EUR')} left your account.`
          : `Matched: ${units} ${symbol} sold, ${formatMinor(paid, 'EUR')} arrived in your account.`,
      )
      if (book.restedQuantity > 0) lines.push(`${book.restedQuantity} more units stay in the book.`)
    } else if (book.restedQuantity > 0) {
      lines.push(`Your ${verb} rests in the ${symbol} book: ${placed.quantity} units at ${formatMinor(placed.price, 'EUR')}.`)
      lines.push(`Nothing has been paid. A match would move ${formatMinor(placed.price * placed.quantity, 'EUR')}.`)
    }
    if (book.cancelledQuantity > 0) lines.push(`${book.cancelledQuantity} units were not placed.`)
  }
  if (book.bestBid != null) lines.push(`Best bid waiting: ${formatMinor(book.bestBid, 'EUR')}.`)
  if (book.bestAsk != null) lines.push(`Best ask waiting: ${formatMinor(book.bestAsk, 'EUR')}.`)
  if (!placed && book.bestBid == null && book.bestAsk == null) lines.push(`The ${symbol} book is empty.`)
  if (book.history.length > 0) {
    lines.push(`${book.history.length} trade${book.history.length === 1 ? '' : 's'} settled on the ledger this session.`)
  }
  return lines
}

export function BookPage() {
  const feed = useMarketFeed()
  const [symbol, setSymbol] = useState('BTC')
  const [range, setRange] = useState(ranges[1].candles)
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY')
  const [quantity, setQuantity] = useState('1')
  const [priceEuro, setPriceEuro] = useState('')
  const [accountId, setAccountId] = useState('')
  const [balanceMinor, setBalanceMinor] = useState<number | null>(null)
  const [lines, setLines] = useState<string[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const quote = feed.quote(symbol)
  const instrument = quote.instrument
  const change = changePercent(quote)
  const up = quote.price >= quote.previous

  useEffect(() => {
    listAccounts()
      .then((rows) => {
        setAccountId(rows[0]?.id ?? '')
        setBalanceMinor(rows[0]?.balanceMinor ?? null)
      })
      .catch(() => undefined)
  }, [])

  useEffect(() => {
    setPriceEuro(feed.quote(symbol).price.toFixed(2))
    setLines([])
    setError('')
  }, [symbol])

  const units = Number(quantity)
  const cents = Math.round(Number(priceEuro) * 100)
  const cost = Number.isFinite(cents) && Number.isInteger(units) && units > 0 ? cents * units : 0
  const affordable = balanceMinor != null && cents > 0 ? Math.floor(balanceMinor / cents) : 0

  async function load(refreshOnly: boolean) {
    if (!refreshOnly && (!Number.isInteger(units) || units <= 0 || !Number.isInteger(cents) || cents <= 0)) {
      setError('Enter a whole number of units and a price in EUR.')
      setLines([])
      return
    }
    setBusy(true)
    setError('')
    try {
      const book = await workspace<BookResult>(
        refreshOnly ? `/orders?symbol=${symbol}` : '/orders',
        refreshOnly
          ? undefined
          : {
              id: crypto.randomUUID(),
              side,
              type: 'LIMIT',
              limitPrice: cents,
              quantity: units,
              accountId,
              symbol,
            },
      )
      setLines(describe(book, refreshOnly ? null : { side, quantity: units, price: cents }, symbol))
      if (!refreshOnly) {
        listAccounts()
          .then((rows) => setBalanceMinor(rows[0]?.balanceMinor ?? null))
          .catch(() => undefined)
      }
    } catch (caught) {
      setLines([])
      setError(caught instanceof ApiError ? `${caught.code}: ${caught.message}` : 'The order could not be placed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <p className="-mt-2 mb-1 max-w-3xl text-sm leading-6 text-[#718EBF]">
        Simulated price feed for the desk demo. Your orders settle on the real double-entry ledger, in whole units and whole cents.
      </p>
      <div className="grid gap-4 lg:grid-cols-[226px_minmax(0,1fr)_300px]">
        <Watchlist active={symbol} onSelect={setSymbol} />

        <div className="space-y-4">
          <Card className="p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <span
                  className="flex h-11 w-11 items-center justify-center rounded-2xl text-xs font-bold text-white"
                  style={{ backgroundColor: instrument.accent }}
                >
                  {instrument.symbol.slice(0, 3)}
                </span>
                <div>
                  <p className="text-sm font-semibold text-[#343C6A]">
                    {instrument.symbol}/EUR <span className="font-normal text-[#8BA3CB]">· {instrument.name}</span>
                  </p>
                  <div className="flex items-center gap-2">
                    <span className={`num text-[26px] font-semibold leading-tight ${up ? 'text-[#15803D]' : 'text-[#B91C1C]'}`}>
                      €{formatPrice(quote.price, instrument.digits)}
                    </span>
                    <ChangePill value={change} />
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-1 rounded-full bg-[#F5F7FA] p-1">
                {ranges.map((option) => (
                  <button
                    key={option.label}
                    type="button"
                    onClick={() => setRange(option.candles)}
                    className={`rounded-full px-3 py-1 text-xs font-medium transition ${
                      range === option.candles ? 'bg-white text-[#1814F3] shadow-sm' : 'text-[#8BA3CB] hover:text-[#343C6A]'
                    }`}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="mt-4">
              <MarketChart symbol={symbol} digits={instrument.digits} range={range} />
            </div>

            <dl className="mt-3 flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-t border-[#F1F4F9] pt-3">
              {[
                ['Open', `€${formatPrice(quote.opened, instrument.digits)}`],
                ['High', `€${formatPrice(quote.high, instrument.digits)}`],
                ['Low', `€${formatPrice(quote.low, instrument.digits)}`],
                ['Volume', formatUnits(quote.volume)],
              ].map(([label, value]) => (
                <div key={label} className="min-w-0">
                  <dt className="text-[10px] font-semibold uppercase tracking-wide text-[#8BA3CB]">{label}</dt>
                  <dd className="num whitespace-nowrap text-[13px] font-semibold text-[#343C6A]">{value}</dd>
                </div>
              ))}
            </dl>
          </Card>

          <Depth quote={quote} />
        </div>

        <Card className="h-fit p-5">
          <div className="mb-3 flex items-center justify-between">
            <p className="text-sm font-semibold text-[#343C6A]">Order ticket</p>
            <span className="rounded-full bg-[#EEF2FF] px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-[#1814F3]">
              limit
            </span>
          </div>

          <div className="mb-3 grid grid-cols-2 gap-2 rounded-full bg-[#F5F7FA] p-1">
            {(['BUY', 'SELL'] as const).map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => setSide(option)}
                className={`h-9 rounded-full text-sm font-semibold transition ${
                  side === option
                    ? option === 'BUY'
                      ? 'bg-[#16A34A] text-white shadow-sm'
                      : 'bg-[#DC2626] text-white shadow-sm'
                    : 'text-[#8BA3CB] hover:text-[#343C6A]'
                }`}
              >
                {option === 'BUY' ? 'Buy' : 'Sell'}
              </button>
            ))}
          </div>

          <div className="space-y-3">
            <Field
              label={`Quantity (${instrument.symbol})`}
              value={quantity}
              inputMode="numeric"
              onChange={(event) => setQuantity(event.target.value)}
              hint={balanceMinor != null ? `Your balance covers ${affordable} units at this price.` : undefined}
            />
            <Field
              label="Limit price (EUR)"
              value={priceEuro}
              inputMode="decimal"
              onChange={(event) => setPriceEuro(event.target.value)}
              hint={cents > 0 ? `The book trades in whole cents: ${cents}¢ per unit.` : undefined}
            />
          </div>

          <div className="mt-3 space-y-1 rounded-2xl bg-[#F7F9FC] px-3 py-2 text-xs text-[#718EBF]">
            <div className="flex items-center justify-between">
              <span>Order value</span>
              <span className="num font-semibold text-[#343C6A]">{formatMinor(Number.isFinite(cost) ? cost : 0, 'EUR')}</span>
            </div>
            <div className="flex items-center justify-between">
              <span>Cash after a match</span>
              <span className="num font-semibold text-[#343C6A]">
                {balanceMinor == null
                  ? '—'
                  : formatMinor(side === 'BUY' ? balanceMinor - cost : balanceMinor + cost, 'EUR')}
              </span>
            </div>
          </div>

          {balanceMinor != null && side === 'BUY' && cost > balanceMinor ? (
            <div className="mt-3">
              <Notice tone="error">
                Your balance does not cover this order. Try a smaller quantity, or a cheaper market such as XRP or DOGE.
              </Notice>
            </div>
          ) : null}

          <div className="mt-3 flex flex-wrap gap-2">
            <Button disabled={busy || accountId === ''} onClick={() => void load(false)}>
              Place order
            </Button>
            <Button disabled={busy} tone="ghost" onClick={() => void load(true)}>
              Refresh book
            </Button>
          </div>

          <div className="mt-3 space-y-2">
            {error ? <Notice tone="error">{error}</Notice> : null}
            {lines.length > 0 ? (
              <Notice>
                {lines.map((line) => (
                  <span key={line} className="block">
                    {line}
                  </span>
                ))}
              </Notice>
            ) : null}
          </div>
        </Card>
      </div>
    </div>
  )
}
