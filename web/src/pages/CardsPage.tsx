import { useEffect, useState } from 'react'
import { ApiError, listAccounts, type Account } from '../api'
import { BankCard } from '../bankCard'
import { Notice } from '../ui'

export function CardsPage() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [revealed, setRevealed] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    listAccounts()
      .then(setAccounts)
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Could not load cards.'))
  }, [])

  return (
    <div>
      {error ? <Notice tone="error">{error}</Notice> : null}
      <div className="grid gap-6 md:grid-cols-2">
        {accounts.map((account, index) => (
          <BankCard
            key={account.id}
            account={account}
            light={index % 2 === 1}
            revealed={revealed}
            onToggle={() => setRevealed((value) => !value)}
          />
        ))}
      </div>
    </div>
  )
}
