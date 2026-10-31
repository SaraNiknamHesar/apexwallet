import { useEffect, useState } from 'react'
import { ApiError, decide, listPending, type Approval, type TransferReceipt } from '../api'
import { formatMinor } from '../money'
import { Button, Card, Notice } from '../ui'

export function ApprovalsPage() {
  const [rows, setRows] = useState<Approval[]>([])
  const [error, setError] = useState('')
  const [note, setNote] = useState('')
  const [busyId, setBusyId] = useState('')

  async function load() {
    setRows(await listPending())
  }

  useEffect(() => {
    load().catch((caught) => setError(caught instanceof ApiError ? `${caught.code}: ${caught.message}` : 'Could not load the queue.'))
  }, [])

  async function act(row: Approval, action: 'approve' | 'reject') {
    setBusyId(row.id)
    setError('')
    setNote('')
    try {
      const receipt: TransferReceipt = await decide(row.id, action)
      const balance = receipt.fromBalanceMinor === null ? '' : ` Alice now has ${formatMinor(receipt.fromBalanceMinor, row.currency)}.`
      const word = action === 'approve' ? 'Approved.' : 'Rejected. No money moved.'
      setNote(`${word}${balance}`)
      await load()
    } catch (caught) {
      setError(caught instanceof ApiError ? `${caught.code}: ${caught.message}` : 'The decision failed.')
    } finally {
      setBusyId('')
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-sm text-[#718EBF]">Sam approves or rejects. The person who sent it cannot approve it.</p>
      {error ? <Notice tone="error">{error}</Notice> : null}
      {note ? <Notice tone="ok">{note}</Notice> : null}
      {rows.length === 0 && !error ? (
        <p className="text-sm text-[#718EBF]">Nothing is waiting.</p>
      ) : rows.map((row) => (
        <Card key={row.id} className="p-6">
          <p className="text-xs text-zinc-500">{row.makerUsername}</p>
          <p className="num mt-1 text-3xl font-semibold tracking-tight">{formatMinor(row.amountMinor, row.currency)}</p>
          <p className="mt-1 text-sm">{row.description || 'No note'}</p>
          <div className="mt-4 grid grid-cols-2 gap-2">
            <Button disabled={busyId === row.id} onClick={() => void act(row, 'approve')}>Approve</Button>
            <Button tone="danger" disabled={busyId === row.id} onClick={() => void act(row, 'reject')}>Reject</Button>
          </div>
        </Card>
      ))}
    </div>
  )
}
