import { useEffect, useRef, useState } from 'react'
import { ApiError, listAccounts, workspace } from '../api'
import { formatMinor } from '../money'
import { Button, Card, Field, Notice, ScreenTitle } from '../ui'

function euros(minor: number) {
  return formatMinor(minor, 'EUR')
}

function useResult() {
  const [lines, setLines] = useState<string[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<unknown>, explain: (value: unknown) => string[]) {
    setBusy(true)
    setError('')
    setLines([])
    try {
      setLines(explain(await action()))
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The request failed.')
    } finally {
      setBusy(false)
    }
  }

  return { lines, error, busy, run }
}

function Result({ lines, error }: { lines: string[]; error: string }) {
  return (
    <>
      {error ? <Notice tone="error">{error}</Notice> : null}
      {lines.length > 0 ? (
        <Notice>
          {lines.map((line, index) => <span key={`${index}-${line}`} className="block">{line}</span>)}
        </Notice>
      ) : null}
    </>
  )
}

const prompts = [
  'Who is Alice?',
  'What happens when I send €1,000?',
  'What does Kafka do here?',
  'Can I pay with an IBAN?',
]

export function AssistantPage() {
  const [messages, setMessages] = useState<Array<{ role: 'user' | 'assistant'; text: string }>>([])
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const end = useRef<HTMLDivElement>(null)

  useEffect(() => {
    end.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, busy])

  async function send(text: string) {
    const question = text.trim()
    if (!question || busy) return
    const history = [...messages, { role: 'user' as const, text: question }]
    setMessages(history)
    setDraft('')
    setError('')
    setBusy(true)
    try {
      const reply = await workspace<{ answer: string }>('/chat', { messages: history })
      setMessages([...history, { role: 'assistant', text: reply.answer }])
    } catch (caught) {
      setMessages(messages)
      setDraft(question)
      setError(caught instanceof ApiError ? caught.message : 'The assistant could not answer just now.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <ScreenTitle title="Assistant" hint="This chat runs in its own service. Sending money does not use it." />
      <Card className="flex h-[min(68vh,640px)] flex-col p-5">
        <div className="min-h-0 flex-1 space-y-3 overflow-y-auto pr-1">
          {messages.length === 0 ? (
            <div className="max-w-[42rem] rounded-3xl bg-[#F5F7FA] px-4 py-3 text-sm leading-6 text-[#343C6A]">
              Ask about the accounts, a payment, or what is running. I answer from this project, and I do not move money.
            </div>
          ) : null}
          {messages.map((message, index) => (
            <div key={`${index}-${message.role}`} className={message.role === 'user' ? 'flex justify-end' : 'flex justify-start'}>
              <p className={`max-w-[42rem] whitespace-pre-wrap rounded-3xl px-4 py-3 text-sm leading-6 ${message.role === 'user' ? 'bg-[#1814F3] text-white' : 'bg-[#F5F7FA] text-[#343C6A]'}`}>
                {message.text}
              </p>
            </div>
          ))}
          {busy ? <p className="w-fit rounded-3xl bg-[#F5F7FA] px-4 py-3 text-sm text-[#718EBF]">Thinking…</p> : null}
          <div ref={end} />
        </div>
        {messages.length === 0 ? (
          <div className="mt-4 flex flex-wrap gap-2">
            {prompts.map((prompt) => (
              <button key={prompt} type="button" className="rounded-full bg-[#F5F7FA] px-4 py-2 text-left text-sm text-[#343C6A] transition hover:bg-[#E6EFF5]" onClick={() => void send(prompt)}>
                {prompt}
              </button>
            ))}
          </div>
        ) : null}
        {error ? <p className="mt-3 text-sm text-[#FE5C73]">{error}</p> : null}
        <form className="mt-4 flex items-end gap-2" onSubmit={(event) => { event.preventDefault(); void send(draft) }}>
          <textarea
            value={draft}
            rows={2}
            placeholder="Ask about this desk"
            className="app-input min-h-12 flex-1 resize-none py-3"
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                void send(draft)
              }
            }}
          />
          <Button type="submit" disabled={busy || draft.trim() === ''}>Send</Button>
        </form>
      </Card>
    </div>
  )
}

export function PricingPage() {
  const result = useResult()
  const [amount, setAmount] = useState('100.00')
  const [accountId, setAccountId] = useState('')

  useEffect(() => {
    listAccounts().then((rows) => setAccountId(rows[0]?.id ?? '')).catch(() => undefined)
  }, [])

  function minor() {
    const value = amount.trim()
    if (!/^\d+(\.\d{1,2})?$/.test(value)) return null
    const [whole, fraction = ''] = value.split('.')
    return Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
  }

  return (
    <div className="space-y-4">
      <ScreenTitle title="Fees" hint="A quote only calculates. Charge takes the fee from your account. Transfers share a €5,000 daily ceiling." />
      <Card className="space-y-3 p-5">
        <Field label="Amount (EUR)" value={amount} onChange={(event) => setAmount(event.target.value)} />
        <div className="flex flex-wrap gap-2">
          <Button disabled={result.busy} onClick={() => void result.run(
            () => workspace('/fees', { type: 'PERCENTAGE', amountMinor: minor(), fixedMinor: 0, basisPoints: 250, capMinor: 0 }),
            (value) => {
              const quote = value as { feeMinor: number }
              return [`The fee would be ${euros(quote.feeMinor)}.`, 'No money has left your account.']
            },
          )}>Quote 2.50%</Button>
          <Button disabled={result.busy || accountId === ''} tone="ghost" onClick={() => void result.run(
            () => workspace('/fees/charge', { type: 'PERCENTAGE', amountMinor: minor(), fixedMinor: 0, basisPoints: 250, capMinor: 0, accountId }),
            (value) => {
              const posted = value as { feeMinor: number; balanceMinor: number }
              return [`${euros(posted.feeMinor)} was taken as a fee.`, `Your balance is now ${euros(posted.balanceMinor)}.`]
            },
          )}>Charge that fee</Button>
          <Button disabled={result.busy} tone="ghost" onClick={() => void result.run(
            () => workspace('/interest', { principalMinor: minor(), annualRate: '0.02', from: '2024-01-01', to: '2024-03-31', convention: 'ACT_365' }),
            (value) => {
              const interest = value as { accruedMinor: number }
              return [`Interest for 1 Jan to 31 Mar at 2% would be ${euros(interest.accruedMinor)}.`, 'This is a calculation. It is not added to your account.']
            },
          )}>2% interest</Button>
          <Button disabled={result.busy || accountId === ''} tone="ghost" onClick={() => void result.run(
            () => workspace('/limits', { accountId, amountMinor: minor() }),
            (value) => {
              const decision = value as { allowed: boolean; remainingMinor: number }
              return [decision.allowed
                ? `This amount fits today's limit. ${euros(decision.remainingMinor)} would still be available.`
                : 'This amount is over the daily limit of €5,000.']
            },
          )}>Try the daily limit</Button>
        </div>
        <Result lines={result.lines} error={result.error} />
      </Card>
    </div>
  )
}

export function ChecksPage() {
  const result = useResult()
  return (
    <div className="space-y-4">
      <ScreenTitle title="Books" hint="See whether both sides of each payment match, and what has happened." />
      <Card className="space-y-3 p-5">
        <div className="flex flex-wrap gap-2">
          <Button disabled={result.busy} onClick={() => void result.run(
            () => workspace('/reconciliation'),
            (value) => {
              const report = value as { clean: boolean }
              return [report.clean ? 'The books balance. Every payment has matching sides.' : 'The books do not balance.']
            },
          )}>Check the books</Button>
          <Button disabled={result.busy} tone="ghost" onClick={() => void result.run(
            () => workspace('/audit'),
            (value) => {
              const events = value as { action: string; actor: string }[]
              if (events.length === 0) return ['Nothing has been recorded yet.']
              const person = (actor: string) => actor === 'anonymous' || /^[0-9a-f-]{20,}$/i.test(actor) ? 'Someone' : actor
              const line = (event: { action: string; actor: string }) => {
                const who = person(event.actor)
                if (event.action === 'LOGIN') return `${who} signed in.`
                if (event.action === 'TRANSFER') return `${who} sent money.`
                if (event.action === 'APPROVE') return `${who} approved a payment.`
                if (event.action === 'REFRESH_REUSE') return 'An old sign-in was used again.'
                return `${who} used the app.`
              }
              return [`${events.length} actions are recorded.`, ...events.slice(-5).map(line)]
            },
          )}>Recent actions</Button>
          <Button disabled={result.busy} tone="ghost" onClick={() => void result.run(
            () => workspace('/outbox'),
            (value) => {
              const waiting = value as unknown[]
              if (waiting.length === 0) return ['No messages are waiting.']
              return [waiting.length === 1 ? '1 message is waiting to be passed on.' : `${waiting.length} messages are waiting to be passed on.`]
            },
          )}>Waiting messages</Button>
          <Button disabled={result.busy} tone="ghost" onClick={() => void result.run(
            () => workspace('/outbox/relay', {}),
            (value) => {
              const relay = value as { published: number }
              if (relay.published === 0) return ['Nothing was passed on.']
              return [relay.published === 1 ? '1 message was passed on inside this application.' : `${relay.published} messages were passed on inside this application.`]
            },
          )}>Pass them on</Button>
        </div>
        <Result lines={result.lines} error={result.error} />
      </Card>
    </div>
  )
}

const sampleCamt = '<Document><Stmt><Id>STMT-1</Id><Ntry><NtryRef>R1</NtryRef><Amt>1500</Amt></Ntry></Stmt></Document>'

export function FilesPage() {
  const result = useResult()
  const [iban, setIban] = useState('GB82WEST12345698765432')
  const [accountId, setAccountId] = useState('')

  useEffect(() => {
    listAccounts().then((rows) => setAccountId(rows[0]?.id ?? '')).catch(() => undefined)
  }, [])
  return (
    <div className="space-y-4">
      <ScreenTitle title="Files" hint="Check an account number, or read a practice statement. These files are for study, not an official bank message." />
      <Card className="space-y-3 p-5">
        <Field label="IBAN" value={iban} onChange={(event) => setIban(event.target.value)} />
        <div className="flex flex-wrap gap-2">
          <Button disabled={result.busy} onClick={() => void result.run(
            () => workspace('/iban', { iban }),
            (value) => [(value as { valid: boolean }).valid ? 'This IBAN is well formed.' : 'This IBAN is not well formed.'],
          )}>Check IBAN</Button>
          <Button disabled={result.busy || accountId === ''} tone="ghost" onClick={() => void result.run(
            () => workspace('/camt', { xml: sampleCamt, accountId }),
            (value) => {
              const imported = value as { fresh: boolean; postedMinor: number; entries: number }
              if (!imported.fresh) return ['This statement was already imported.']
              return [`The practice statement has ${imported.entries} line${imported.entries === 1 ? '' : 's'}.`, `${euros(imported.postedMinor)} was added to your account.`]
            },
          )}>Import the practice statement</Button>
          <Button disabled={result.busy} tone="ghost" onClick={() => void result.run(
            () => workspace('/pain', { messageId: 'MSG-1', endToEndIds: ['E2E-1'], amountMinor: 1500 }),
            () => ['A practice payment file for €15.00 was prepared.', 'It was not sent to a bank.'],
          )}>Prepare a €15 file</Button>
        </div>
        <Result lines={result.lines} error={result.error} />
      </Card>
    </div>
  )
}

type BookTrade = { price: number; quantity: number }
type BookResult = {
  bestBid?: number | null
  bestAsk?: number | null
  restedQuantity: number
  cancelledQuantity: number
  trades: BookTrade[]
  history: BookTrade[]
}

function orderSentences(book: BookResult, placed: { side: string; quantity: number; price: number } | null) {
  const lines: string[] = []
  if (placed) {
    const verb = placed.side === 'BUY' ? 'buy' : 'sell'
    const cost = euros(placed.price * placed.quantity)
    if (book.trades.length === 0 && book.restedQuantity > 0) {
      lines.push(`Your order is waiting. You want to ${verb} ${placed.quantity} units at ${euros(placed.price)} each.`)
      lines.push(`Nothing has been paid. A match would move ${cost}.`)
    } else if (book.trades.length > 0) {
      const units = book.trades.reduce((sum, trade) => sum + trade.quantity, 0)
      const paid = book.trades.reduce((sum, trade) => sum + trade.price * trade.quantity, 0)
      lines.push(placed.side === 'BUY'
        ? `Your buy matched. ${units} units were bought and ${euros(paid)} left your account.`
        : `Your sell matched. ${units} units were sold and ${euros(paid)} was added to your account.`)
      if (book.restedQuantity > 0) lines.push(`${book.restedQuantity} units are still waiting.`)
    }
    if (book.cancelledQuantity > 0) lines.push(`${book.cancelledQuantity} units were not placed.`)
  }
  if (book.bestBid != null) lines.push(`The highest buy waiting is ${euros(book.bestBid)} per unit.`)
  if (book.bestAsk != null) lines.push(`The lowest sell waiting is ${euros(book.bestAsk)} per unit.`)
  if (!placed && book.bestBid == null && book.bestAsk == null) lines.push('No orders are waiting.')
  if (book.history.length > 0) lines.push(`${book.history.length} trade${book.history.length === 1 ? '' : 's'} completed in this session.`)
  return lines
}

export function BookPage() {
  const [side, setSide] = useState('BUY')
  const [quantity, setQuantity] = useState('2')
  const [price, setPrice] = useState('100')
  const [accountId, setAccountId] = useState('')
  const [lines, setLines] = useState<string[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    listAccounts().then((rows) => setAccountId(rows[0]?.id ?? '')).catch(() => undefined)
  }, [])

  async function place(refreshOnly: boolean) {
    const units = Number(quantity)
    const unitPrice = Number(price)
    if (!refreshOnly && (!Number.isInteger(units) || units <= 0 || !Number.isInteger(unitPrice) || unitPrice <= 0)) {
      setError('Enter a whole quantity and a whole price in cents.')
      setLines([])
      return
    }
    setBusy(true)
    setError('')
    try {
      const book = await workspace<BookResult>(
        '/orders',
        refreshOnly ? undefined : {
          id: crypto.randomUUID(),
          side,
          type: 'LIMIT',
          limitPrice: unitPrice,
          quantity: units,
          accountId,
        },
      )
      setLines(orderSentences(book, refreshOnly ? null : { side, quantity: units, price: unitPrice }))
    } catch (caught) {
      setLines([])
      setError(caught instanceof ApiError ? caught.message : 'The order could not be placed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-4">
      <ScreenTitle title="Order book" hint="A buy waits until someone sells at your price. Your balance changes only then." />
      <Card className="space-y-3 p-5">
        <div className="grid gap-3">
          <label className="block space-y-1.5 text-sm">
            <span className="font-medium text-[#718EBF]">I want to</span>
            <select className="app-input h-12" value={side} onChange={(event) => setSide(event.target.value)}>
              <option value="BUY">Buy</option>
              <option value="SELL">Sell</option>
            </select>
          </label>
          <Field label="Quantity" value={quantity} onChange={(event) => setQuantity(event.target.value)} />
          <Field label="Price per unit, in cents" value={price} onChange={(event) => setPrice(event.target.value)} />
        </div>
        <p className="text-sm text-[#718EBF]">
          {Number.isInteger(Number(quantity)) && Number(quantity) > 0 && Number.isInteger(Number(price)) && Number(price) > 0
            ? `${price} cents is ${euros(Number(price))} per unit. ${quantity} units cost ${euros(Number(price) * Number(quantity))} when someone matches you.`
            : 'Enter a whole quantity and a price in cents.'}
        </p>
        <div className="flex flex-wrap gap-2">
          <Button disabled={busy || accountId === ''} onClick={() => void place(false)}>Place order</Button>
          <Button disabled={busy} tone="ghost" onClick={() => void place(true)}>Refresh</Button>
        </div>
        {error ? <Notice tone="error">{error}</Notice> : null}
        {lines.length > 0 ? (
          <Notice>
            {lines.map((line) => <span key={line} className="block">{line}</span>)}
          </Notice>
        ) : null}
      </Card>
    </div>
  )
}
