import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowUpRight } from 'lucide-react'
import { getSession, listAccounts, statement, transactionId, type Account, type StatementLine } from '../api'
import { accountLabel, BankCard } from '../bankCard'
import { formatMinor } from '../money'
import { useQuery } from '../shell'
import { SectionTitle } from '../ui'

const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function dayIndex(iso: string) {
  const day = new Date(iso).getDay()
  return day === 0 ? 6 : day - 1
}

export function DeskPage() {
  const session = getSession()
  const query = useQuery().trim().toLowerCase()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [lines, setLines] = useState<StatementLine[]>([])
  const [revealed, setRevealed] = useState(false)

  const canSend = session?.role === 'CUSTOMER' || session?.role === 'TELLER'

  useEffect(() => {
    let alive = true
    listAccounts()
      .then((rows) => {
        if (!alive) return
        setAccounts(rows)
        setSelectedId((current) => current || rows.find((row) => row.type === 'CUSTOMER_LIABILITY')?.id || rows[0]?.id || '')
      })
      .catch(() => undefined)
    return () => {
      alive = false
    }
  }, [])

  useEffect(() => {
    if (!selectedId) return
    let alive = true
    statement(selectedId, 0)
      .then((page) => {
        if (alive) setLines(page.items)
      })
      .catch(() => {
        if (alive) setLines([])
      })
    return () => {
      alive = false
    }
  }, [selectedId])

  const selected = accounts.find((account) => account.id === selectedId) ?? accounts[0]
  const second = accounts.find((account) => account.id !== selected?.id)
  const currency = selected?.currency ?? 'EUR'

  const visibleLines = useMemo(() => {
    const newest = [...lines].reverse()
    if (!query) return newest
    return newest.filter((line) => {
      const haystack = `${line.direction} ${formatMinor(line.amountMinor, currency)} ${transactionId(line)} ${new Date(line.bookedAt).toDateString()}`.toLowerCase()
      return haystack.includes(query)
    })
  }, [lines, query, currency])

  const weekly = useMemo(() => {
    const buckets = days.map((label) => ({ label, deposit: 0, withdraw: 0 }))
    for (const line of lines) {
      const bucket = buckets[dayIndex(line.bookedAt)]
      const major = line.amountMinor / 100
      if (line.direction === 'CREDIT') bucket.deposit += major
      else bucket.withdraw += major
    }
    return buckets
  }, [lines])

  const expenses = useMemo(() => {
    const totals = new Map<string, number>()
    for (const line of lines) {
      if (line.direction !== 'DEBIT') continue
      const label = line.description || 'Money out'
      totals.set(label, (totals.get(label) ?? 0) + line.amountMinor)
    }
    const palette = ['#1814F3', '#FC7900', '#FA00FF', '#16DBCC', '#343C6A', '#396AFF', '#FF4B4A']
    return [...totals.entries()].map(([label, value], index) => ({
      label,
      value,
      color: palette[index % palette.length],
    }))
  }, [lines])

  return (
    <div className="grid items-start gap-6 xl:grid-cols-12">
      <section className="xl:col-span-8">
        <SectionTitle action={<Link to="/cards" className="text-sm font-semibold text-[#343C6A]">See All</Link>}>My Cards</SectionTitle>
        <div className="grid gap-6 md:grid-cols-2">
          {selected ? (
            <BankCard account={selected} revealed={revealed} onToggle={() => setRevealed((value) => !value)} onSelect={() => setSelectedId(selected.id)} />
          ) : null}
          {second ? (
            <BankCard account={second} light revealed={revealed} onToggle={() => setRevealed((value) => !value)} onSelect={() => setSelectedId(second.id)} />
          ) : selected ? (
            <BankCard account={selected} light revealed={revealed} onToggle={() => setRevealed((value) => !value)} />
          ) : null}
        </div>
      </section>

      <section className="xl:col-span-4">
        <SectionTitle>Recent Transaction</SectionTitle>
        <div className="soft-card rounded-[25px] bg-white px-5 py-2">
          {visibleLines.length === 0 ? (
            <p className="py-6 text-sm text-[#718EBF]">No payments on this card yet.</p>
          ) : visibleLines.slice(0, 3).map((line) => {
            const inbound = line.direction === 'CREDIT'
            return (
              <div key={line.entryId} className="flex items-center gap-4 border-b border-[#F5F7FA] py-4 last:border-0">
                <span className={`flex h-12 w-12 items-center justify-center rounded-full text-lg ${inbound ? 'bg-[#DCFAF8] text-[#16DBCC]' : 'bg-[#FFE0EB] text-[#FE5C73]'}`}>
                  {inbound ? '+' : '−'}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[15px] font-medium text-[#343C6A]">{line.description || (inbound ? 'Money in' : 'Money out')}</span>
                  <span className="block text-sm text-[#718EBF]">{new Date(line.bookedAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' })}</span>
                </span>
                <span className={`num shrink-0 text-sm font-semibold ${inbound ? 'text-[#16DBCC]' : 'text-[#FE5C73]'}`}>
                  {inbound ? '+' : '−'}{formatMinor(line.amountMinor, currency)}
                </span>
              </div>
            )
          })}
          <Link to="/statement" className="block py-3 text-center text-sm font-semibold text-[#1814F3]">See All</Link>
        </div>
      </section>

      <section className="xl:col-span-8">
        <SectionTitle>Weekly Activity</SectionTitle>
        <div className="soft-card rounded-[25px] bg-white p-6">
          <div className="mb-4 flex gap-4 text-xs text-[#718EBF]">
            <span className="flex items-center gap-2"><i className="h-3 w-3 rounded-sm bg-[#1814F3]" /> Deposit</span>
            <span className="flex items-center gap-2"><i className="h-3 w-3 rounded-sm bg-[#16DBCC]" /> Withdraw</span>
          </div>
          <Bars series={weekly} />
        </div>
      </section>

      <section className="xl:col-span-4">
        <SectionTitle>Expense Statistics</SectionTitle>
        <div className="soft-card flex min-h-[280px] items-center justify-center rounded-[25px] bg-white p-6">
          <Donut parts={expenses} />
        </div>
      </section>

      <section className="xl:col-span-5">
        <SectionTitle>Transfer</SectionTitle>
        <div className="soft-card rounded-[25px] bg-white p-6">
          <div className="flex items-center gap-3">
            <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#EEF2FF] text-[#1814F3]">
              <ArrowUpRight size={20} />
            </span>
            <div className="min-w-0">
              <p className="text-sm font-semibold text-[#343C6A]">Send money by IBAN</p>
              <p className="num truncate text-xs text-[#718EBF]">
                {selected ? `${accountLabel(selected)} · ${formatMinor(selected.balanceMinor, currency)}` : 'Loading…'}
              </p>
            </div>
          </div>
          <div className="mt-4 rounded-2xl bg-[#F7F9FC] px-4 py-3">
            <p className="text-[10px] font-semibold uppercase tracking-wide text-[#8BA3CB]">How it works</p>
            <ul className="mt-1 space-y-1 text-xs leading-5 text-[#718EBF]">
              <li>The IBAN is checked, and we show who owns it before sending.</li>
              <li>Up to €999.99 posts now. €1,000.00 and above waits for Sam.</li>
            </ul>
          </div>
          {canSend ? (
            <Link
              to="/transfer"
              className="mt-4 flex h-11 items-center justify-center rounded-full bg-[#1814F3] text-sm font-medium text-white transition hover:-translate-y-px"
            >
              New transfer
            </Link>
          ) : (
            <p className="mt-4 rounded-2xl bg-[#F5F7FA] px-4 py-3 text-xs text-[#718EBF]">
              This role can read the desk but cannot move money.
            </p>
          )}
        </div>
      </section>

      <section className="xl:col-span-7">
        <SectionTitle>Balance History</SectionTitle>
        <div className="soft-card rounded-[25px] bg-white p-6">
          <History lines={lines} currency={currency} />
        </div>
      </section>
    </div>
  )
}

function Bars({ series }: { series: Array<{ label: string; deposit: number; withdraw: number }> }) {
  const max = Math.max(1, ...series.flatMap((item) => [item.deposit, item.withdraw]))
  const height = 180
  return (
    <svg viewBox="0 0 560 220" className="h-64 w-full">
      {[0, 0.25, 0.5, 0.75, 1].map((step) => {
        const y = 20 + (1 - step) * height
        return <line key={step} x1="36" x2="540" y1={y} y2={y} stroke="#F3F3F5" />
      })}
      {series.map((item, index) => {
        const x = 58 + index * 72
        const deposit = (item.deposit / max) * height
        const withdraw = (item.withdraw / max) * height
        return (
          <g key={item.label}>
            <rect className="bar-grow" style={{ animationDelay: `${index * 50}ms` }} x={x} y={20 + height - deposit} width="14" height={Math.max(deposit, 0)} rx="7" fill="#1814F3" />
            <rect className="bar-grow" style={{ animationDelay: `${index * 50 + 80}ms` }} x={x + 18} y={20 + height - withdraw} width="14" height={Math.max(withdraw, 0)} rx="7" fill="#16DBCC" />
            <text x={x + 16} y="214" textAnchor="middle" fontSize="12" fill="#718EBF">{item.label}</text>
          </g>
        )
      })}
    </svg>
  )
}

function Donut({ parts }: { parts: Array<{ label: string; value: number; color: string }> }) {
  const total = parts.reduce((sum, part) => sum + part.value, 0)
  if (total <= 0) return <p className="text-sm text-[#718EBF]">No movements on this card yet.</p>
  const radius = 68
  const circumference = 2 * Math.PI * radius
  let offset = 0
  return (
    <div className="flex flex-col items-center">
      <svg viewBox="0 0 200 200" className="donut-in h-44 w-44">
        {parts.filter((part) => part.value > 0).map((part) => {
          const length = (part.value / total) * circumference
          const circle = (
            <circle
              key={part.label}
              cx="100"
              cy="100"
              r={radius}
              fill="none"
              stroke={part.color}
              strokeWidth="26"
              strokeDasharray={`${length} ${circumference - length}`}
              strokeDashoffset={-offset}
              transform="rotate(-90 100 100)"
            />
          )
          offset += length
          return circle
        })}
      </svg>
      <ul className="mt-3 space-y-1 text-xs text-[#718EBF]">
        {parts.map((part) => (
          <li key={part.label} className="flex items-center gap-2">
            <span className="h-2.5 w-2.5 rounded-full" style={{ background: part.color }} />
            {part.label} {Math.round((part.value / total) * 100)}%
          </li>
        ))}
      </ul>
    </div>
  )
}

function History({ lines, currency }: { lines: StatementLine[]; currency: string }) {
  if (lines.length === 0) return <p className="text-sm text-[#718EBF]">No balance history yet.</p>
  const width = 640
  const height = 220
  const values = lines.map((line) => line.runningBalanceMinor)
  const low = Math.min(...values)
  const high = Math.max(...values)
  const pad = Math.max(1, Math.round((high - low) * 0.2))
  const min = low - pad
  const max = high + pad
  const coords = lines.map((line, index) => {
    const x = lines.length === 1 ? width / 2 : 24 + (index / (lines.length - 1)) * (width - 48)
    const y = 24 + (1 - (line.runningBalanceMinor - min) / (max - min)) * 150
    return { x, y, month: new Date(line.bookedAt).toLocaleDateString('en-GB', { month: 'short' }) }
  })
  const path = coords.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ')
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="h-64 w-full">
      <path className="line-draw" pathLength={1} d={path} fill="none" stroke="#1814F3" strokeWidth="3" />
      {coords.map((point, index) => index === 0 || point.month !== coords[index - 1].month ? (
        <text key={`${point.month}-${point.x}`} x={point.x} y="210" textAnchor="middle" fontSize="12" fill="#718EBF">{point.month}</text>
      ) : null)}
      <text x="8" y="20" fontSize="11" fill="#718EBF">{formatMinor(high, currency)}</text>
    </svg>
  )
}
