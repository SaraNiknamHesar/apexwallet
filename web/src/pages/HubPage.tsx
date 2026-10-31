import { ChevronRight, LogOut } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getSession, logout, workspace, type Coverage } from '../api'
import { ScreenTitle } from '../ui'

const tools = [
  { to: '/pricing', label: 'Fees', note: 'Quote a fee, or charge it' },
  { to: '/checks', label: 'Checks', note: 'Reconcile, audit, outbox' },
  { to: '/files', label: 'Files', note: 'IBAN and study messages' },
  { to: '/assistant', label: 'Assistant', note: 'Offline, off the payment path' },
  { to: '/book', label: 'Order book', note: 'Price-time trades' },
  { to: '/approvals', label: 'Review', note: 'Large payments waiting for Sam', roles: ['SUPERVISOR'] },
]

export function HubPage() {
  const session = getSession()
  const navigate = useNavigate()
  const [coverage, setCoverage] = useState<Coverage | null>(null)

  useEffect(() => {
    let alive = true
    workspace<Coverage>('/coverage')
      .then((rows) => {
        if (alive) setCoverage(rows)
      })
      .catch(() => undefined)
    return () => {
      alive = false
    }
  }, [])

  const rows = tools.filter((tool) => !tool.roles || (session && tool.roles.includes(session.role)))

  return (
    <div>
      <ScreenTitle title="More" hint={`${session?.username ?? ''} · ${session?.role ? session.role[0] + session.role.slice(1).toLowerCase() : ''}`} />
      <div className="overflow-hidden rounded-3xl bg-zinc-50">
        {rows.map((tool) => (
          <Link key={tool.to} to={tool.to} className="flex items-center gap-3 border-b border-white px-4 py-3 last:border-0">
            <span className="min-w-0 flex-1">
              <span className="block text-sm font-semibold">{tool.label}</span>
              <span className="block text-xs text-zinc-500">{tool.note}</span>
            </span>
            <ChevronRight size={16} className="text-zinc-400" />
          </Link>
        ))}
      </div>
      {coverage ? <CoverageMap coverage={coverage} /> : null}
      <button
        type="button"
        className="mt-4 flex h-12 w-full items-center justify-center gap-2 rounded-full bg-zinc-100 text-sm font-semibold"
        onClick={() => {
          void logout().finally(() => navigate('/login'))
        }}
      >
        <LogOut size={16} />
        Sign out
      </button>
    </div>
  )
}

function CoverageMap({ coverage }: { coverage: Coverage }) {
  const rows: Array<[string, boolean]> = [
    ['Ledger and payments', coverage.ledger],
    ['Fees and the daily limit', coverage.pricing],
    ['IBAN and study files', coverage.isoStudySubset],
    ['Offline assistant', coverage.offlineAssistant],
    ['Order book', coverage.orderBook],
    ['Outbox in this process', coverage.inProcessOutbox],
    ['Postgres', coverage.postgresOnThisProcess],
    ['Kafka', coverage.kafkaConsumer],
    ['Grafana', coverage.grafanaServer],
    ['Hosted model', coverage.hostedModel],
    ['Assistant service', coverage.assistantService],
    ['Demo video', coverage.demoVideo],
    ['Mutation score', coverage.mutationScore],
  ]
  return (
    <div className="mt-5">
      <h2 className="text-sm font-semibold">On this phone</h2>
      <p className="mt-1 text-xs leading-5 text-zinc-500">
        This phone talks to the local process. Postgres, Kafka, Grafana, and a hosted model stay off until that profile is started.
      </p>
      <ul className="mt-3 space-y-2">
        {rows.map(([label, on]) => (
          <li key={label} className="flex items-center justify-between gap-3 text-sm">
            <span>{label}</span>
            <span className={on ? 'text-emerald-600' : 'text-zinc-400'}>{on ? 'On' : 'Off'}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
