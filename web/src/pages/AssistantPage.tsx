import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Bot,
  Landmark,
  MessageSquarePlus,
  Send,
  ServerCog,
  Sparkles,
  Trash2,
  Wallet,
  Wifi,
  WifiOff,
} from 'lucide-react'
import { ApiError, getSession, workspace, type Coverage } from '../api'

type Msg = { role: 'user' | 'assistant'; text: string; at: number }
type Thread = { id: string; messages: Msg[]; updatedAt: number }

const STORE = 'apex.assistant.v1'

const starters = [
  { icon: Wallet, title: 'Who is Alice?', text: 'Balance, IBAN and role of the seeded customer.' },
  { icon: Send, title: 'What happens when I send €1,000?', text: 'The review threshold and who approves it.' },
  { icon: Landmark, title: 'Can I pay with an IBAN?', text: 'How the beneficiary lookup works.' },
  { icon: ServerCog, title: 'What does Kafka do here?', text: 'Which parts run in this process and which do not.' },
]

function newThread(): Thread {
  return { id: crypto.randomUUID(), messages: [], updatedAt: Date.now() }
}

function titleOf(thread: Thread): string {
  const first = thread.messages.find((message) => message.role === 'user')
  if (!first) return 'New chat'
  return first.text.length > 34 ? `${first.text.slice(0, 34)}…` : first.text
}

function clockOf(at: number): string {
  return new Date(at).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}

function load(): { threads: Thread[]; activeId: string } {
  try {
    const raw = sessionStorage.getItem(STORE)
    if (raw) {
      const parsed = JSON.parse(raw) as { threads: Thread[]; activeId: string }
      if (Array.isArray(parsed.threads) && parsed.threads.length > 0) return parsed
    }
  } catch {
    /* start with a fresh thread */
  }
  const thread = newThread()
  return { threads: [thread], activeId: thread.id }
}

export function AssistantPage() {
  const [initial] = useState(load)
  const [threads, setThreads] = useState<Thread[]>(initial.threads)
  const [activeId, setActiveId] = useState(initial.activeId)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState<{ service: boolean; model: boolean } | null>(null)
  const end = useRef<HTMLDivElement>(null)
  const box = useRef<HTMLTextAreaElement>(null)

  const active = threads.find((thread) => thread.id === activeId) ?? threads[0]
  const messages = active?.messages ?? []
  const me = (getSession()?.username ?? 'you').slice(0, 1).toUpperCase()

  useEffect(() => {
    sessionStorage.setItem(STORE, JSON.stringify({ threads, activeId }))
  }, [threads, activeId])

  useEffect(() => {
    workspace<Coverage>('/coverage')
      .then((rows) => setStatus({ service: rows.assistantService, model: rows.hostedModel }))
      .catch(() => setStatus(null))
  }, [])

  useEffect(() => {
    end.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, busy])

  const sorted = useMemo(
    () => [...threads].sort((left, right) => right.updatedAt - left.updatedAt),
    [threads],
  )

  function patchThread(id: string, next: Msg[]) {
    setThreads((current) =>
      current.map((thread) => (thread.id === id ? { ...thread, messages: next, updatedAt: Date.now() } : thread)),
    )
  }

  function startChat() {
    const thread = newThread()
    setThreads((current) => [thread, ...current])
    setActiveId(thread.id)
    setDraft('')
    setError('')
    box.current?.focus()
  }

  function removeThread(id: string) {
    setThreads((current) => {
      const left = current.filter((thread) => thread.id !== id)
      if (left.length === 0) {
        const fresh = newThread()
        setActiveId(fresh.id)
        return [fresh]
      }
      if (id === activeId) setActiveId(left[0].id)
      return left
    })
  }

  async function send(text: string) {
    const question = text.trim()
    if (!question || busy || !active) return
    const history: Msg[] = [...active.messages, { role: 'user', text: question, at: Date.now() }]
    patchThread(active.id, history)
    setDraft('')
    setError('')
    setBusy(true)
    try {
      const reply = await workspace<{ answer: string }>('/chat', {
        messages: history.map((message) => ({ role: message.role, text: message.text })),
      })
      patchThread(active.id, [...history, { role: 'assistant', text: reply.answer, at: Date.now() }])
    } catch (caught) {
      patchThread(active.id, active.messages)
      setDraft(question)
      setError(caught instanceof ApiError ? caught.message : 'The assistant could not answer just now.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid gap-4 lg:h-[min(84vh,780px)] lg:grid-cols-[290px_minmax(0,1fr)]">
      <aside className="hidden min-h-0 flex-col overflow-hidden rounded-[25px] bg-white soft-card lg:flex">
        <div className="flex items-center gap-2.5 border-b border-[#F1F4F9] px-4 py-4">
          <span className="flex h-9 w-9 items-center justify-center rounded-2xl bg-[#EEF2FF] text-[#1814F3]">
            <Sparkles size={17} />
          </span>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-[#343C6A]">Apex Assistant</p>
            <p className="text-[10px] uppercase tracking-wide text-[#8BA3CB]">desk copilot</p>
          </div>
        </div>

        <div className="p-3">
          <button
            type="button"
            onClick={startChat}
            className="flex h-10 w-full items-center justify-center gap-2 rounded-full bg-[#1814F3] text-sm font-medium text-white transition hover:-translate-y-px"
          >
            <MessageSquarePlus size={16} /> New chat
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-2">
          <p className="px-2 pb-1 pt-1 text-[10px] font-semibold uppercase tracking-wide text-[#8BA3CB]">Recent</p>
          <ul className="space-y-0.5">
            {sorted.map((thread) => (
              <li key={thread.id}>
                <div
                  className={`group flex items-center gap-1 rounded-xl px-2 ${
                    thread.id === activeId ? 'bg-[#EEF2FF]' : 'hover:bg-[#F5F7FA]'
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => {
                      setActiveId(thread.id)
                      setError('')
                    }}
                    className="min-w-0 flex-1 py-2 text-left"
                  >
                    <span
                      className={`block truncate text-xs ${
                        thread.id === activeId ? 'font-semibold text-[#1814F3]' : 'text-[#343C6A]'
                      }`}
                    >
                      {titleOf(thread)}
                    </span>
                    <span className="block text-[10px] text-[#8BA3CB]">
                      {thread.messages.length === 0
                        ? 'empty'
                        : `${thread.messages.length} message${thread.messages.length === 1 ? '' : 's'} · ${clockOf(thread.updatedAt)}`}
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => removeThread(thread.id)}
                    aria-label="Delete chat"
                    className="rounded-full p-1.5 text-[#B1C5E8] opacity-0 transition group-hover:opacity-100 hover:bg-white hover:text-[#FE5C73]"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="border-t border-[#F1F4F9] p-3">
          <p className="px-1 pb-1.5 text-[10px] font-semibold uppercase tracking-wide text-[#8BA3CB]">Service</p>
          <div className="space-y-1 text-[11px] text-[#718EBF]">
            <p className="flex items-center gap-1.5">
              {status?.service ? <Wifi size={12} className="text-[#16A34A]" /> : <WifiOff size={12} className="text-[#DC2626]" />}
              assistant service {status ? (status.service ? 'connected' : 'offline') : '…'}
            </p>
            <p className="flex items-center gap-1.5">
              {status?.model ? <Wifi size={12} className="text-[#16A34A]" /> : <WifiOff size={12} className="text-[#F59E0B]" />}
              model {status ? (status.model ? 'hosted model' : 'local answers') : '…'}
            </p>
          </div>
        </div>
      </aside>

      <section className="flex min-h-0 flex-col overflow-hidden rounded-[25px] bg-white soft-card">
        <header className="flex items-center gap-3 border-b border-[#F1F4F9] px-5 py-4">
          <span className="relative flex h-10 w-10 items-center justify-center rounded-2xl bg-[#1814F3] text-white">
            <Bot size={19} />
            <span className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full border-2 border-white bg-[#16A34A]" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-[#343C6A]">Apex Assistant</p>
            <p className="truncate text-xs text-[#8BA3CB]">Answers from this codebase · it cannot move money</p>
          </div>
          <span
            className={`hidden rounded-full px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wide sm:block ${
              status?.service === false ? 'bg-[#FEF3C7] text-[#B45309]' : 'bg-[#F0FDF4] text-[#15803D]'
            }`}
          >
            {status?.service === false ? 'degraded' : 'online'}
          </span>
        </header>

        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-5 py-5">
          {messages.length === 0 ? (
            <div className="mx-auto my-auto flex max-w-2xl flex-col items-center py-2 text-center">
              <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#EEF2FF] text-[#1814F3]">
                <Sparkles size={20} />
              </span>
              <h3 className="mt-2 text-base font-semibold text-[#343C6A]">How can I help with the desk?</h3>
              <p className="mt-1 max-w-md text-xs leading-5 text-[#718EBF]">
                Ask about accounts, payments, loans or how this project is built. I answer from the project itself.
              </p>
              <div className="mt-4 grid w-full max-w-xl gap-2 sm:grid-cols-2">
                {starters.map((starter) => (
                  <button
                    key={starter.title}
                    type="button"
                    onClick={() => void send(starter.title)}
                    className="flex items-start gap-2 rounded-2xl border border-[#EDF1F7] bg-[#FBFCFE] px-3 py-2.5 text-left transition hover:border-[#D6E2F5] hover:bg-white"
                  >
                    <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-lg bg-[#EEF2FF] text-[#1814F3]">
                      <starter.icon size={13} />
                    </span>
                    <span className="min-w-0">
                      <span className="block text-[11px] font-semibold leading-4 text-[#343C6A]">{starter.title}</span>
                      <span className="block text-[10px] leading-4 text-[#8BA3CB]">{starter.text}</span>
                    </span>
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {messages.map((message, index) => {
            const mine = message.role === 'user'
            return (
              <div key={`${index}-${message.at}`} className={`flex gap-2.5 ${mine ? 'flex-row-reverse' : ''}`}>
                <span
                  className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold ${
                    mine ? 'bg-[#EEF2FF] text-[#1814F3]' : 'bg-[#1814F3] text-white'
                  }`}
                >
                  {mine ? me : <Bot size={15} />}
                </span>
                <div className={`max-w-[min(46rem,85%)] ${mine ? 'text-right' : ''}`}>
                  <p className="mb-1 text-[10px] font-medium uppercase tracking-wide text-[#8BA3CB]">
                    {mine ? 'You' : 'Assistant'} · {clockOf(message.at)}
                  </p>
                  <p
                    className={`whitespace-pre-wrap rounded-2xl px-4 py-2.5 text-sm leading-6 ${
                      mine ? 'bg-[#1814F3] text-white' : 'bg-[#F7F9FC] text-[#343C6A]'
                    }`}
                  >
                    {message.text}
                  </p>
                </div>
              </div>
            )
          })}

          {busy ? (
            <div className="flex gap-2.5">
              <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#1814F3] text-white">
                <Bot size={15} />
              </span>
              <div className="rounded-2xl bg-[#F7F9FC] px-4 py-3">
                <span className="flex items-center gap-1">
                  {[0, 150, 300].map((delay) => (
                    <span
                      key={delay}
                      className="h-1.5 w-1.5 animate-bounce rounded-full bg-[#B1C5E8]"
                      style={{ animationDelay: `${delay}ms` }}
                    />
                  ))}
                </span>
              </div>
            </div>
          ) : null}
          <div ref={end} />
        </div>

        {error ? (
          <div className="mx-5 mb-2 rounded-2xl bg-[#FFE0EB] px-4 py-2.5 text-xs text-[#FE5C73]">{error}</div>
        ) : null}

        <form
          className="border-t border-[#F1F4F9] px-4 py-3"
          onSubmit={(event) => {
            event.preventDefault()
            void send(draft)
          }}
        >
          <div className="flex items-end gap-2 rounded-2xl border border-[#E6EDF7] bg-[#FBFCFE] px-3 py-2 focus-within:border-[#C7D4EA]">
            <textarea
              ref={box}
              value={draft}
              rows={1}
              placeholder="Ask about this desk…"
              className="max-h-32 min-h-[30px] flex-1 resize-none bg-transparent py-1 text-sm leading-6 text-[#343C6A] outline-none placeholder:text-[#8BA3CB]"
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  void send(draft)
                }
              }}
            />
            <button
              type="submit"
              disabled={busy || draft.trim() === ''}
              aria-label="Send message"
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#1814F3] text-white transition disabled:cursor-not-allowed disabled:opacity-40"
            >
              <Send size={15} />
            </button>
          </div>
          <p className="mt-1.5 px-1 text-[10px] text-[#8BA3CB]">
            Enter sends · Shift+Enter adds a line · answers come from this project, not a live model of the bank
          </p>
        </form>
      </section>
    </div>
  )
}
