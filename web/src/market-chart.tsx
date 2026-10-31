import { useEffect, useRef } from 'react'
import {
  CandlestickSeries,
  ColorType,
  CrosshairMode,
  createChart,
  type CandlestickData,
  type IChartApi,
  type ISeriesApi,
  type UTCTimestamp,
} from 'lightweight-charts'
import { useMarketFeed } from './markets'

type Props = {
  symbol: string
  digits: number
  /** How many of the newest candles stay on screen. */
  range: number
}

export function MarketChart({ symbol, digits, range }: Props) {
  const host = useRef<HTMLDivElement>(null)
  const chart = useRef<IChartApi | null>(null)
  const series = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const loaded = useRef('')
  const feed = useMarketFeed()
  const quote = feed.quote(symbol)

  useEffect(() => {
    if (!host.current) return
    const instance = createChart(host.current, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: 'transparent' },
        textColor: '#8BA3CB',
        fontFamily: 'inherit',
        attributionLogo: false,
      },
      grid: {
        vertLines: { color: '#F1F4F9' },
        horzLines: { color: '#F1F4F9' },
      },
      rightPriceScale: { borderColor: '#EDF1F7', scaleMargins: { top: 0.12, bottom: 0.12 } },
      timeScale: { borderColor: '#EDF1F7', timeVisible: true, secondsVisible: true, rightOffset: 4 },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: { color: '#B1C5E8', width: 1, style: 3, labelBackgroundColor: '#1814F3' },
        horzLine: { color: '#B1C5E8', labelBackgroundColor: '#1814F3' },
      },
      handleScroll: false,
      handleScale: false,
    })
    chart.current = instance
    return () => {
      instance.remove()
      chart.current = null
      series.current = null
      loaded.current = ''
    }
  }, [])

  useEffect(() => {
    const instance = chart.current
    if (!instance) return
    series.current = instance.addSeries(CandlestickSeries, {
      upColor: '#16A34A',
      downColor: '#DC2626',
      wickUpColor: '#16A34A',
      wickDownColor: '#DC2626',
      borderVisible: false,
      priceFormat: { type: 'price', precision: digits, minMove: 10 ** -digits },
    })
    loaded.current = ''
    return () => {
      if (series.current) instance.removeSeries(series.current)
      series.current = null
    }
  }, [digits])

  useEffect(() => {
    const instance = chart.current
    const line = series.current
    if (!instance || !line || !quote) return
    if (loaded.current !== symbol) {
      line.setData(quote.candles.map(toCandle))
      instance.timeScale().setVisibleLogicalRange({
        from: Math.max(0, quote.candles.length - range),
        to: quote.candles.length + 4,
      })
      loaded.current = symbol
      return
    }
    const last = quote.candles[quote.candles.length - 1]
    if (last) line.update(toCandle(last))
  }, [feed.version, symbol, range, quote, digits])

  return <div ref={host} className="h-[340px] w-full" />
}

function toCandle(candle: { time: number; open: number; high: number; low: number; close: number }): CandlestickData {
  return {
    time: candle.time as UTCTimestamp,
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
  }
}
