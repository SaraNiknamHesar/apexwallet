import { useEffect, useState } from 'react'
import { ApiError, changeOverdraft, getSession, listAccounts, type Account } from '../api'
import { accountLabel } from '../bankCard'
import { eurosToMinor, formatMinor } from '../money'
import { useQuery } from '../shell'
import { Button, Card, Field, Notice } from '../ui'

export function AccountsPage() {
  const session = getSession()
  const query = useQuery().trim().toLowerCase()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [limit, setLimit] = useState('0.00')
  const [error, setError] = useState('')
  const canEdit = session?.role === 'CUSTOMER' || session?.role === 'TELLER' || session?.role === 'SUPERVISOR'

  useEffect(() => {
    listAccounts()
      .then(setAccounts)
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Could not load accounts.'))
  }, [])

  const visible = accounts.filter((account) => {
    if (!query) return true
    return `${accountLabel(account)} ${account.externalAccountNumber ?? ''} ${account.currency} ${account.status}`.toLowerCase().includes(query)
  })

  return (
    <div className="space-y-4">
      <p className="text-sm text-[#718EBF]">An overdraft of 100.00 lets the balance reach −100.00.</p>
      {error ? <Notice tone="error">{error}</Notice> : null}
      <Card className="overflow-hidden">
        <table className="w-full text-left text-sm">
          <thead className="text-xs uppercase tracking-wide text-[#718EBF]">
            <tr>
              <th className="px-6 py-4 font-medium">Name</th>
              <th className="px-6 py-4 font-medium">Number</th>
              <th className="px-6 py-4 font-medium">Status</th>
              <th className="px-6 py-4 text-right font-medium">Balance</th>
            </tr>
          </thead>
          <tbody>
            {visible.length === 0 ? (
              <tr><td className="px-6 py-8 text-[#718EBF]" colSpan={4}>No accounts match.</td></tr>
            ) : visible.map((account) => (
              <tr key={account.id} className="border-t border-[#F5F7FA]">
                <td className="px-6 py-4 font-medium text-[#343C6A]">{accountLabel(account)}</td>
                <td className="px-6 py-4 font-mono text-xs text-[#718EBF]">{account.externalAccountNumber ?? account.id.slice(0, 8)}</td>
                <td className="px-6 py-4">{account.status}</td>
                <td className="num px-6 py-4 text-right font-semibold">{formatMinor(account.balanceMinor, account.currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
      {canEdit ? visible.filter((account) => account.type === 'CUSTOMER_LIABILITY').map((account) => (
        <Card key={account.id} className="p-6">
          <p className="font-semibold text-[#343C6A]">{accountLabel(account)}</p>
          <form
            className="mt-4 flex flex-wrap items-end gap-3"
            onSubmit={(event) => {
              event.preventDefault()
              const next = eurosToMinor(limit)
              if (next === null) {
                setError('The overdraft needs at most two decimals.')
                return
              }
              changeOverdraft(account.id, next, account.version)
                .then(() => listAccounts())
                .then((rows) => {
                  setAccounts(rows)
                  setError('')
                })
                .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Overdraft failed.'))
            }}
          >
            <div className="w-56">
              <Field label="Overdraft limit (EUR)" value={limit} onChange={(event) => setLimit(event.target.value)} />
            </div>
            <Button type="submit">Save overdraft</Button>
          </form>
        </Card>
      )) : null}
    </div>
  )
}
