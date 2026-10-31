import { useEffect, useState } from 'react'
import { ApiError, listAccounts, statement, transactionId, type Account, type StatementPage as Page } from '../api'
import { accountLabel } from '../bankCard'
import { formatMinor } from '../money'
import { useQuery } from '../shell'
import { Button, Card, Notice } from '../ui'

export function StatementPage() {
  const query = useQuery().trim().toLowerCase()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [accountId, setAccountId] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    listAccounts()
      .then((rows) => {
        setAccounts(rows)
        setAccountId((current) => current || rows.find((row) => row.type === 'CUSTOMER_LIABILITY')?.id || rows[0]?.id || '')
      })
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Could not load accounts.'))
  }, [])

  useEffect(() => {
    if (!accountId) return
    let alive = true
    statement(accountId, page)
      .then((result) => {
        if (alive) setData(result)
      })
      .catch((caught) => {
        if (alive) setError(caught instanceof ApiError ? caught.message : 'Could not load the statement.')
      })
    return () => {
      alive = false
    }
  }, [accountId, page])

  const currency = accounts.find((account) => account.id === accountId)?.currency ?? 'EUR'

  return (
    <div>
      <p className="mb-4 text-sm text-[#718EBF]">Booking order. A credit increases a customer balance.</p>
      {accounts.length > 1 ? (
        <select
          className="app-input mb-3 h-12"
          value={accountId}
          onChange={(event) => {
            setPage(0)
            setAccountId(event.target.value)
          }}
        >
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {accountLabel(account)} · {account.currency}
            </option>
          ))}
        </select>
      ) : null}
      {error ? <Notice tone="error">{error}</Notice> : null}
      {data ? (
        <>
          <p className="num mb-3 text-sm text-[#718EBF]">
            Closing {formatMinor(data.closingBalanceMinor, currency)} · {data.total} lines
          </p>
          <Card className="px-6">
          {data.items.filter((line) => {
            if (!query) return true
            const haystack = `${line.direction} ${formatMinor(line.amountMinor, currency)} ${transactionId(line)}`.toLowerCase()
            return haystack.includes(query)
          }).length === 0 ? (
            <p className="py-8 text-sm text-[#718EBF]">No lines on this page.</p>
          ) : (
            <ul>
              {data.items.filter((line) => {
                if (!query) return true
                const haystack = `${line.direction} ${formatMinor(line.amountMinor, currency)} ${transactionId(line)}`.toLowerCase()
                return haystack.includes(query)
              }).map((line) => {
                const inbound = line.direction === 'CREDIT'
                return (
                  <li key={line.entryId} className="flex items-center gap-4 border-b border-[#F5F7FA] py-4 last:border-0">
                    <span className={`flex h-12 w-12 items-center justify-center rounded-full text-lg ${inbound ? 'bg-[#DCFAF8] text-[#16DBCC]' : 'bg-[#FFE0EB] text-[#FE5C73]'}`}>
                      {inbound ? '+' : '−'}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-[15px] font-medium text-[#343C6A]">{line.description || (inbound ? 'Money in' : 'Money out')}</span>
                      <span className="block text-sm text-[#718EBF]">{new Date(line.bookedAt).toLocaleString()}</span>
                    </span>
                    <span className="text-right">
                      <span className={`num block text-sm font-semibold ${inbound ? 'text-[#16DBCC]' : 'text-[#FE5C73]'}`}>
                        {inbound ? '+' : '−'}{formatMinor(line.amountMinor, currency)}
                      </span>
                      <span className="num block text-[11px] text-[#718EBF]">{formatMinor(line.runningBalanceMinor, currency)}</span>
                    </span>
                  </li>
                )
              })}
            </ul>
          )}
          </Card>
          <div className="mt-4 flex items-center justify-between">
            <Button tone="ghost" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Earlier</Button>
            <span className="text-xs text-zinc-500">{page + 1}</span>
            <Button tone="ghost" disabled={!data.hasNext} onClick={() => setPage((value) => value + 1)}>Later</Button>
          </div>
        </>
      ) : null}
    </div>
  )
}
