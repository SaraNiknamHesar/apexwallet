import { useEffect, useState } from 'react'
import {
  ApiError,
  decideLoan,
  getSession,
  listLoans,
  repayLoan,
  requestLoan,
  type Loan,
} from '../api'
import { eurosToMinor, formatMinor } from '../money'
import { Button, Card, Field, Notice } from '../ui'

function line(loan: Loan) {
  if (loan.status === 'PENDING') return 'Waiting for Sam. Nothing has been paid in yet.'
  if (loan.status === 'ACTIVE') return `${formatMinor(loan.outstandingMinor, loan.currency)} still to repay.`
  if (loan.status === 'REPAID') return 'Repaid.'
  return 'Sam declined this request.'
}

export function LoansPage() {
  const session = getSession()
  const customer = session?.role === 'CUSTOMER'
  const supervisor = session?.role === 'SUPERVISOR'
  const [loans, setLoans] = useState<Loan[]>([])
  const [amount, setAmount] = useState('100.00')
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  function load() {
    return listLoans()
      .then((rows) => {
        setLoans(rows)
        setError('')
      })
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Could not load loans.'))
  }

  useEffect(() => {
    void load()
  }, [])

  return (
    <div className="space-y-4">
      <p className="max-w-3xl text-sm leading-6 text-[#718EBF]">
        Up to €500.00 is paid into your account now. From €500.01 to €2,000.00, Sam approves it first.
        This sample charges no interest. Repayment comes out of your balance.
      </p>
      {error ? <Notice tone="error">{error}</Notice> : null}
      {notice ? <Notice tone="ok">{notice}</Notice> : null}
      {customer ? (
        <Card className="p-6">
          <form
            className="flex flex-wrap items-end gap-3"
            onSubmit={(event) => {
              event.preventDefault()
              const minor = eurosToMinor(amount)
              if (minor === null || minor <= 0) {
                setError('The amount needs at most two decimals.')
                setNotice('')
                return
              }
              requestLoan(minor)
                .then((result) => {
                  setNotice(result.loan.status === 'PENDING'
                    ? 'Sam still has to approve this loan.'
                    : `${formatMinor(result.loan.principalMinor, result.loan.currency)} is now in your account.`)
                  return load()
                })
                .catch((caught) => {
                  setNotice('')
                  setError(caught instanceof ApiError ? caught.message : 'The loan was not granted.')
                })
            }}
          >
            <div className="w-56">
              <Field label="Amount (EUR)" value={amount} onChange={(event) => setAmount(event.target.value)} />
            </div>
            <Button type="submit">Ask for this loan</Button>
          </form>
        </Card>
      ) : null}
      <Card className="overflow-hidden">
        <table className="w-full text-left text-sm">
          <thead className="text-xs uppercase tracking-wide text-[#718EBF]">
            <tr>
              <th className="px-6 py-4 font-medium">Borrower</th>
              <th className="px-6 py-4 font-medium">Asked for</th>
              <th className="px-6 py-4 font-medium">Still open</th>
              <th className="px-6 py-4 font-medium">State</th>
            </tr>
          </thead>
          <tbody>
            {loans.length === 0 ? (
              <tr><td className="px-6 py-8 text-[#718EBF]" colSpan={4}>No loans yet.</td></tr>
            ) : loans.map((loan) => (
              <tr key={loan.id} className="border-t border-[#F5F7FA]">
                <td className="px-6 py-4 font-medium text-[#343C6A]">{loan.borrowerUsername}</td>
                <td className="num px-6 py-4">{formatMinor(loan.principalMinor, loan.currency)}</td>
                <td className="num px-6 py-4">{formatMinor(loan.outstandingMinor, loan.currency)}</td>
                <td className="px-6 py-4 text-[#718EBF]">{line(loan)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
      {loans.filter((loan) => loan.status === 'ACTIVE' && customer).map((loan) => (
        <Card key={loan.id} className="p-6">
          <p className="font-semibold text-[#343C6A]">Repay {formatMinor(loan.outstandingMinor, loan.currency)}</p>
          <form
            className="mt-4 flex flex-wrap items-end gap-3"
            onSubmit={(event) => {
              event.preventDefault()
              const minor = eurosToMinor(repayAmounts[loan.id] ?? '')
              if (minor === null || minor <= 0) {
                setError('The repayment needs at most two decimals.')
                setNotice('')
                return
              }
              repayLoan(loan.id, minor)
                .then((next) => {
                  setNotice(next.status === 'REPAID' ? 'This loan is repaid.' : `${formatMinor(next.outstandingMinor, next.currency)} is still open.`)
                  return load()
                })
                .catch((caught) => {
                  setNotice('')
                  setError(caught instanceof ApiError ? caught.message : 'Repayment failed.')
                })
            }}
          >
            <div className="w-56">
              <Field
                label="Repay (EUR)"
                value={repayAmounts[loan.id] ?? ''}
                onChange={(event) => setRepayAmounts((current) => ({ ...current, [loan.id]: event.target.value }))}
              />
            </div>
            <Button type="submit">Repay</Button>
          </form>
        </Card>
      ))}
      {supervisor ? loans.filter((loan) => loan.status === 'PENDING').map((loan) => (
        <Card key={loan.id} className="flex flex-wrap items-center justify-between gap-3 p-6">
          <p className="font-semibold text-[#343C6A]">
            {loan.borrowerUsername} asked for {formatMinor(loan.principalMinor, loan.currency)}
          </p>
          <div className="flex gap-3">
            <Button
              type="button"
              onClick={() => {
                decideLoan(loan.id, 'approve')
                  .then(() => {
                    setNotice('The loan is paid into the account.')
                    return load()
                  })
                  .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Approval failed.'))
              }}
            >
              Approve
            </Button>
            <Button
              tone="danger"
              type="button"
              onClick={() => {
                decideLoan(loan.id, 'reject')
                  .then(() => {
                    setNotice('The request was declined.')
                    return load()
                  })
                  .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'The request could not be declined.'))
              }}
            >
              Decline
            </Button>
          </div>
        </Card>
      )) : null}
    </div>
  )
}
