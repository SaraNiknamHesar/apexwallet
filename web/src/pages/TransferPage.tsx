import { useEffect, useMemo, useState } from 'react'
import { AlertCircle, ArrowRight, BadgeCheck, CheckCircle2, Landmark, Loader2, ShieldCheck } from 'lucide-react'
import {
  ApiError,
  listAccounts,
  resolvePayee,
  statement,
  submitTransfer,
  type Account,
  type Payee,
  type StatementLine,
  type TransferResult,
} from '../api'
import { eurosToMinor, formatMinor } from '../money'
import { Button, Card, Field, Notice } from '../ui'

/** The demo posts at once below this and waits for a supervisor from it upwards. */
const APPROVAL_MINOR = 100_000

const demoTargets = [
  { label: 'Bob', iban: 'GB33BUKB20201555555555' },
  { label: 'Alice', iban: 'GB82WEST12345698765432' },
  { label: 'Unknown IBAN', iban: 'FR1420041010050500013M02606' },
  { label: 'Broken IBAN', iban: 'GB00WEST12345698765432' },
]

type Lookup = { state: 'idle' | 'checking' | 'ready' | 'failed'; payee?: Payee; message?: string }

function tidyIban(value: string): string {
  return value
    .replace(/[^a-zA-Z0-9]/g, '')
    .toUpperCase()
    .slice(0, 34)
}

function groupIban(value: string): string {
  return tidyIban(value).replace(/(.{4})/g, '$1 ').trim()
}

function maskIban(value: string | null): string {
  if (!value) return '—'
  const compact = value.replace(/\s/g, '')
  if (compact.length <= 8) return compact
  return `${compact.slice(0, 4)} •••• ${compact.slice(-4)}`
}

function initial(value: string): string {
  return value.trim().slice(0, 1).toUpperCase() || '?'
}

function signedAmount(line: StatementLine): string {
  const sign = line.direction === 'DEBIT' ? '−' : '+'
  return `${sign}${formatMinor(line.amountMinor, 'EUR')}`
}

export function TransferPage() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [fromAccountId, setFromAccountId] = useState('')
  const [iban, setIban] = useState('')
  const [lookup, setLookup] = useState<Lookup>({ state: 'idle' })
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [receipt, setReceipt] = useState<TransferResult | null>(null)
  const [receiptAmount, setReceiptAmount] = useState(0)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [recent, setRecent] = useState<StatementLine[]>([])
  const [key, setKey] = useState(() => crypto.randomUUID())

  const source = accounts.find((account) => account.id === fromAccountId)
  const currency = source?.currency ?? 'EUR'
  const compact = tidyIban(iban)
  const amountMinor = eurosToMinor(amount)

  useEffect(() => {
    listAccounts()
      .then((rows) => {
        const own = rows.filter((row) => row.type === 'CUSTOMER_LIABILITY')
        const choices = own.length > 0 ? own : rows
        setAccounts(choices)
        setFromAccountId((current) => current || choices[0]?.id || '')
      })
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Could not load accounts.'))
  }, [])

  useEffect(() => {
    if (!fromAccountId) return
    statement(fromAccountId, 0)
      .then((page) => setRecent(page.items.slice(0, 6)))
      .catch(() => setRecent([]))
  }, [fromAccountId, receipt])

  useEffect(() => {
    if (compact.length < 5) {
      setLookup({ state: 'idle' })
      return
    }
    let alive = true
    setLookup({ state: 'checking' })
    const timer = window.setTimeout(() => {
      resolvePayee(compact)
        .then((payee) => {
          if (!alive) return
          if (!payee.valid) {
            setLookup({ state: 'failed', payee, message: payee.reason ?? 'That is not a valid IBAN.' })
          } else if (!payee.found) {
            setLookup({ state: 'failed', payee, message: payee.reason ?? 'No account here uses that IBAN.' })
          } else {
            setLookup({ state: 'ready', payee })
          }
        })
        .catch(() => {
          if (alive) setLookup({ state: 'failed', message: 'The lookup failed. Check that the API is running.' })
        })
    }, 350)
    return () => {
      alive = false
      window.clearTimeout(timer)
    }
  }, [compact])

  const payee = lookup.state === 'ready' ? lookup.payee : undefined
  const selfTransfer = payee != null && payee.accountId === fromAccountId
  const needsApproval = amountMinor != null && amountMinor >= APPROVAL_MINOR
  const overBalance = amountMinor != null && source != null && amountMinor > source.balanceMinor
  const canSend = payee != null && !selfTransfer && amountMinor != null && amountMinor > 0 && !busy

  const status = useMemo(() => {
    if (lookup.state === 'checking') return { tone: 'muted' as const, text: 'Checking the IBAN…' }
    if (lookup.state === 'failed') return { tone: 'error' as const, text: lookup.message ?? 'That IBAN cannot be used.' }
    if (lookup.state === 'ready' && selfTransfer) {
      return { tone: 'error' as const, text: 'That is the account you are sending from.' }
    }
    return null
  }, [lookup, selfTransfer])

  async function send() {
    if (!source || !payee?.accountId || amountMinor == null) return
    const sending = amountMinor
    setBusy(true)
    setError('')
    setReceipt(null)
    try {
      const result = await submitTransfer(
        {
          fromAccountId: source.id,
          toAccountId: payee.accountId,
          amountMinor: sending,
          currency,
          description: note.trim() || `Transfer to ${payee.ownerRef}`,
        },
        key,
      )
      setReceipt(result)
      setReceiptAmount(sending)
      setKey(crypto.randomUUID())
      setIban('')
      setAmount('')
      setNote('')
      const rows = await listAccounts()
      const own = rows.filter((row) => row.type === 'CUSTOMER_LIABILITY')
      setAccounts(own.length > 0 ? own : rows)
    } catch (caught) {
      setError(caught instanceof ApiError ? `${caught.code}: ${caught.message}` : 'The transfer failed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_330px]">
      <Card className="p-6">
        <div className="mb-5 flex items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold text-[#343C6A]">New transfer</h3>
            <p className="mt-1 text-sm text-[#718EBF]">
              Type the beneficiary IBAN — we check it and show who owns it before any money moves.
            </p>
          </div>
          <span className="rounded-full bg-[#EEF2FF] px-3 py-1 text-[10px] font-semibold uppercase tracking-wide text-[#1814F3]">
            SEPA
          </span>
        </div>

        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-[#8BA3CB]">From</p>
        <div className="flex items-center gap-3 rounded-2xl bg-[#F7F9FC] p-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-full bg-[#1814F3] text-sm font-semibold text-white">
            {initial(source?.ownerRef ?? '')}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold capitalize text-[#343C6A]">{source?.ownerRef ?? 'Loading…'}</p>
            <p className="num truncate text-xs text-[#8BA3CB]">
              {maskIban(source?.externalAccountNumber ?? null)} · {currency}
            </p>
          </div>
          <div className="text-right">
            <p className="text-[10px] font-semibold uppercase tracking-wide text-[#8BA3CB]">Available</p>
            <p className="num text-sm font-semibold text-[#343C6A]">
              {source ? formatMinor(source.balanceMinor, currency) : '—'}
            </p>
          </div>
        </div>
        {accounts.length > 1 ? (
          <label className="mt-2 block text-sm">
            <span className="sr-only">Source account</span>
            <select
              className="app-input h-10 text-sm"
              value={fromAccountId}
              onChange={(event) => setFromAccountId(event.target.value)}
            >
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.ownerRef} · {formatMinor(account.balanceMinor, account.currency)}
                </option>
              ))}
            </select>
          </label>
        ) : null}

        <p className="mb-2 mt-5 text-xs font-semibold uppercase tracking-wide text-[#8BA3CB]">Beneficiary</p>
        <label className="block">
          <span className="sr-only">Beneficiary IBAN</span>
          <div className="flex items-center gap-2 rounded-2xl border border-[#E6EDF7] bg-white px-4">
            <Landmark size={18} className="shrink-0 text-[#8BA3CB]" />
            <input
              value={iban}
              onChange={(event) => {
                setIban(groupIban(event.target.value))
                setReceipt(null)
              }}
              placeholder="GB33 BUKB 2020 1555 5555 55"
              spellCheck={false}
              className="num h-12 min-w-0 flex-1 bg-transparent text-sm tracking-wide text-[#343C6A] outline-none placeholder:text-[#B1C5E8]"
            />
            {lookup.state === 'checking' ? <Loader2 size={18} className="animate-spin text-[#8BA3CB]" /> : null}
            {lookup.state === 'ready' && !selfTransfer ? <CheckCircle2 size={18} className="text-[#16A34A]" /> : null}
            {status?.tone === 'error' ? <AlertCircle size={18} className="text-[#DC2626]" /> : null}
          </div>
        </label>

        {lookup.state === 'ready' && payee && !selfTransfer ? (
          <div className="mt-2 flex items-center gap-3 rounded-2xl bg-[#F0FDF4] p-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[#16A34A] text-sm font-semibold text-white">
              {initial(payee.ownerRef ?? '')}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold capitalize text-[#14532D]">{payee.ownerRef}</p>
              <p className="num truncate text-xs text-[#15803D]">
                {groupIban(payee.iban)} · {payee.currency}
              </p>
            </div>
            <span className="flex items-center gap-1 rounded-full bg-white px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-[#15803D]">
              <BadgeCheck size={12} /> matched
            </span>
          </div>
        ) : null}

        {status ? (
          <p
            className={`mt-2 flex items-start gap-1.5 text-xs ${
              status.tone === 'error' ? 'text-[#DC2626]' : 'text-[#8BA3CB]'
            }`}
          >
            {status.tone === 'error' ? <AlertCircle size={13} className="mt-0.5 shrink-0" /> : null}
            {status.text}
          </p>
        ) : null}

        <div className="mt-2 flex flex-wrap gap-1.5">
          {demoTargets.map((target) => (
            <button
              key={target.label}
              type="button"
              onClick={() => setIban(groupIban(target.iban))}
              className="rounded-full bg-[#F5F7FA] px-3 py-1 text-xs text-[#343C6A] transition hover:bg-[#E7EDFF]"
            >
              {target.label}
            </button>
          ))}
        </div>

        <p className="mb-2 mt-5 text-xs font-semibold uppercase tracking-wide text-[#8BA3CB]">Amount</p>
        <div className="flex items-center rounded-2xl border border-[#E6EDF7] bg-white px-4">
          <span className="text-lg font-semibold text-[#8BA3CB]">€</span>
          <input
            value={amount}
            inputMode="decimal"
            onChange={(event) => {
              setAmount(event.target.value)
              setReceipt(null)
            }}
            placeholder="0.00"
            className="num h-14 min-w-0 flex-1 bg-transparent px-3 text-2xl font-semibold text-[#343C6A] outline-none placeholder:text-[#D6E2F5]"
          />
        </div>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {['10.00', '100.00', '999.99', '1000.00'].map((preset) => (
            <button
              key={preset}
              type="button"
              onClick={() => setAmount(preset)}
              className="rounded-full bg-[#F5F7FA] px-3 py-1 text-xs text-[#343C6A] transition hover:bg-[#E7EDFF]"
            >
              €{preset}
            </button>
          ))}
          {source ? (
            <button
              type="button"
              onClick={() => setAmount((source.balanceMinor / 100).toFixed(2))}
              className="rounded-full bg-[#EEF2FF] px-3 py-1 text-xs font-medium text-[#1814F3]"
            >
              Max
            </button>
          ) : null}
        </div>

        <div className="mt-4">
          <Field
            label="Reference"
            value={note}
            maxLength={140}
            onChange={(event) => setNote(event.target.value)}
            placeholder="Rent, invoice 42…"
          />
        </div>

        <div className="mt-4 space-y-1 rounded-2xl bg-[#F7F9FC] px-4 py-3 text-xs text-[#718EBF]">
          <div className="flex items-center justify-between">
            <span>Amount</span>
            <span className="num font-semibold text-[#343C6A]">{formatMinor(amountMinor ?? 0, currency)}</span>
          </div>
          <div className="flex items-center justify-between">
            <span>Balance after sending</span>
            <span className="num font-semibold text-[#343C6A]">
              {source && amountMinor != null ? formatMinor(source.balanceMinor - amountMinor, currency) : '—'}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span>Needs</span>
            <span className="font-medium text-[#343C6A]">{needsApproval ? 'a supervisor (Sam)' : 'no approval'}</span>
          </div>
        </div>

        {overBalance ? (
          <div className="mt-3">
            <Notice tone="error">This is more than the balance on the account.</Notice>
          </div>
        ) : null}

        <div className="mt-4 flex items-center gap-2">
          <Button className="flex-1" disabled={!canSend} onClick={() => void send()}>
            {busy ? 'Sending…' : needsApproval ? 'Send for approval' : 'Send transfer'}
            {!busy ? <ArrowRight size={16} className="ml-1" /> : null}
          </Button>
        </div>

        {error ? (
          <div className="mt-3">
            <Notice tone="error">{error}</Notice>
          </div>
        ) : null}

        {receipt ? (
          <div className="mt-4 rounded-2xl border border-[#DCFCE7] bg-[#F0FDF4] p-4">
            <div className="flex items-center gap-2">
              <CheckCircle2 size={18} className="text-[#16A34A]" />
              <p className="text-sm font-semibold text-[#14532D]">
                {receipt.httpStatus === 202 ? 'Queued for a supervisor' : 'Transfer posted'}
              </p>
              <span className="num ml-auto text-xs text-[#15803D]">#{receipt.receipt.transactionId.slice(0, 8)}</span>
            </div>
            <p className="mt-1 text-xs leading-5 text-[#15803D]">
              {receipt.httpStatus === 202
                ? `The money has not moved yet. Sam has to approve this ${formatMinor(receiptAmount, currency)} payment.`
                : `The ledger now reads ${
                    receipt.receipt.fromBalanceMinor != null
                      ? formatMinor(receipt.receipt.fromBalanceMinor, currency)
                      : '—'
                  } on your side.`}
            </p>
          </div>
        ) : null}
      </Card>

      <div className="space-y-4">
        <Card className="p-5">
          <p className="flex items-center gap-2 text-sm font-semibold text-[#343C6A]">
            <ShieldCheck size={16} className="text-[#1814F3]" /> How this demo behaves
          </p>
          <ul className="mt-3 space-y-2 text-xs leading-5 text-[#718EBF]">
            <li>An IBAN that fails the checksum is refused before sending.</li>
            <li>A valid IBAN with no account here is reported as unknown.</li>
            <li>Up to {formatMinor(APPROVAL_MINOR - 1, 'EUR')} posts immediately.</li>
            <li>{formatMinor(APPROVAL_MINOR, 'EUR')} and above waits for a supervisor.</li>
            <li>The same request with the same key is a replay, not a second payment.</li>
          </ul>
        </Card>

        <Card className="p-5">
          <p className="text-sm font-semibold text-[#343C6A]">Recent activity</p>
          {recent.length === 0 ? (
            <p className="mt-3 text-xs text-[#8BA3CB]">Nothing on this account yet.</p>
          ) : (
            <ul className="mt-3 space-y-2">
              {recent.map((line) => (
                <li key={line.entryId} className="flex items-center justify-between gap-2">
                  <span className="min-w-0">
                    <span className="block truncate text-xs font-medium text-[#343C6A]">
                      {line.description || 'Transfer'}
                    </span>
                    <span className="num block text-[10px] text-[#8BA3CB]">
                      {new Date(line.bookedAt).toLocaleString('en-GB', { dateStyle: 'short', timeStyle: 'short' })}
                    </span>
                  </span>
                  <span
                    className={`num shrink-0 text-xs font-semibold ${
                      line.direction === 'DEBIT' ? 'text-[#DC2626]' : 'text-[#16A34A]'
                    }`}
                  >
                    {signedAmount(line)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
