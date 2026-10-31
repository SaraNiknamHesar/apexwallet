import { useState } from 'react'
import type { Account } from './api'
import { formatMinor } from './money'

export function holderName(name: string) {
  if (!name) return 'Account'
  return name.slice(0, 1).toUpperCase() + name.slice(1)
}

export function accountLabel(account: Account) {
  if (account.type === 'CUSTOMER_LIABILITY') return holderName(account.ownerRef)
  if (account.type === 'BANK_CASH') return 'Bank cash'
  if (account.type === 'FEE_INCOME') return 'Fee income'
  if (account.type === 'LOAN_RECEIVABLE') return 'Loan book'
  return account.type
}

export function maskPan(account: Account, revealed: boolean) {
  const source = (account.externalAccountNumber ?? account.id).replace(/\s/g, '')
  if (revealed) return source
  return `${source.slice(0, 4)} **** **** ${source.slice(-4)}`
}

function grouped(value: string) {
  return value.replace(/\s/g, '').replace(/(.{4})/g, '$1 ').trim()
}

function typeLabel(type: string) {
  if (type === 'CUSTOMER_LIABILITY') return 'Customer'
  if (type === 'BANK_CASH') return 'Bank cash'
  if (type === 'FEE_INCOME') return 'Fee income'
  if (type === 'LOAN_RECEIVABLE') return 'Loan book'
  return type
}

export function BankCard({
  account,
  light = false,
  revealed,
  onToggle,
  onSelect,
}: {
  account: Account
  light?: boolean
  revealed: boolean
  onToggle: () => void
  onSelect?: () => void
}) {
  const [flipped, setFlipped] = useState(false)
  const iban = account.externalAccountNumber?.replace(/\s/g, '') ?? ''
  const face = light
    ? 'border border-[#E6EFF5] bg-white text-[#343C6A]'
    : 'bg-gradient-to-br from-[#4C49ED] to-[#0A06F4] text-white'
  const back = light
    ? 'border border-[#E6EFF5] bg-white text-[#343C6A]'
    : 'bg-gradient-to-br from-[#2E2B9A] to-[#0704C4] text-white'
  const quiet = light ? 'text-[#718EBF]' : 'text-white/70'

  return (
    <article
      onClick={() => {
        setFlipped((value) => !value)
        onSelect?.()
      }}
      className="card-scene soft-card min-h-[235px] min-w-0 cursor-pointer"
    >
      <div className={`card-flip ${flipped ? 'is-flipped' : ''}`}>
        <div className={`card-face flex flex-col justify-between ${face}`}>
          <div className="px-6 pb-4 pt-6">
            <p className={`text-xs ${light ? 'text-[#718EBF]' : 'text-white/80'}`}>Balance</p>
            <p className="num mt-2 text-[26px] font-semibold leading-none">{formatMinor(account.balanceMinor, account.currency)}</p>
            <div className="mt-7 grid grid-cols-2 gap-4 text-xs">
              <div>
                <p className={quiet}>CARD HOLDER</p>
                <p className="mt-1 text-[15px] font-semibold">{accountLabel(account)}</p>
              </div>
              <div>
                <p className={quiet}>STATUS</p>
                <p className="mt-1 text-[15px] font-semibold">{account.status}</p>
              </div>
            </div>
          </div>
          <div className={`flex items-center justify-between gap-3 px-6 py-4 ${light ? 'bg-[#F5F7FA]' : 'bg-white/10'}`}>
            <p className="num min-w-0 truncate text-[17px] tracking-[2px]">{maskPan(account, revealed)}</p>
            <button
              type="button"
              role="switch"
              aria-checked={revealed}
              aria-label={revealed ? 'Hide number' : 'Show number'}
              onClick={(event) => {
                event.stopPropagation()
                onToggle()
              }}
              className={`card-switch relative h-7 w-[52px] shrink-0 rounded-full ${revealed ? (light ? 'bg-[#1814F3]' : 'bg-white') : (light ? 'bg-[#D5DEE8]' : 'bg-white/35')}`}
            >
              <span className={`card-knob absolute top-[3px] left-[3px] h-[22px] w-[22px] rounded-full shadow-[0_2px_6px_rgba(24,20,80,0.25)] ${revealed ? 'translate-x-[24px]' : 'translate-x-0'} ${revealed && !light ? 'bg-[#1814F3]' : 'bg-white'}`} />
            </button>
          </div>
        </div>

        <div className={`card-face card-back flex flex-col ${back}`}>
          <div className={`mt-6 h-10 ${light ? 'bg-[#343C6A]' : 'bg-black/45'}`} />
          <div className="flex flex-1 flex-col justify-between px-6 py-4">
            <div>
              <p className={`text-xs tracking-[1px] ${quiet}`}>{iban ? 'IBAN' : 'ACCOUNT'}</p>
              <p className="num mt-1 text-[15px] font-semibold tracking-[1px]">{grouped(iban || account.id)}</p>
            </div>
            <div className="grid grid-cols-2 gap-4 text-xs">
              <div>
                <p className={quiet}>CURRENCY</p>
                <p className="mt-1 text-[15px] font-semibold">{account.currency}</p>
              </div>
              <div>
                <p className={quiet}>TYPE</p>
                <p className="mt-1 text-[15px] font-semibold">{typeLabel(account.type)}</p>
              </div>
            </div>
          </div>
          <div className={`flex items-center justify-between px-6 py-4 text-xs ${light ? 'bg-[#F5F7FA]' : 'bg-white/10'}`}>
            <span className={`num truncate ${quiet}`}>{account.id.slice(0, 8)}</span>
            <span className="font-semibold">Apex</span>
          </div>
        </div>
      </div>
    </article>
  )
}
