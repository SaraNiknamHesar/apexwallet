import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from 'react'

export function Button({
  tone = 'primary',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { tone?: 'primary' | 'ghost' | 'danger' }) {
  const tones = {
    primary: 'bg-[#1814F3] text-white hover:bg-[#0f0cdb]',
    ghost: 'bg-[#F5F7FA] text-[#343C6A] hover:bg-[#E6EFF5]',
    danger: 'bg-[#FE5C73] text-white hover:bg-[#e14b61]',
  }
  return (
    <button
      className={`inline-flex h-11 items-center justify-center rounded-full px-5 text-sm font-medium transition duration-200 ease-out hover:-translate-y-px active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-40 ${tones[tone]} ${className}`}
      {...props}
    />
  )
}

export function Field({
  label,
  hint,
  className = '',
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string }) {
  return (
    <label className="block space-y-2 text-sm">
      <span className="font-medium text-[#718EBF]">{label}</span>
      <input className={`app-input h-12 ${className}`} {...props} />
      {hint ? <span className="block text-xs leading-5 text-[#718EBF]">{hint}</span> : null}
    </label>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`soft-card rounded-[25px] bg-white ${className}`}>{children}</section>
}

export function Notice({ children, tone = 'info' }: { children: ReactNode; tone?: 'info' | 'error' | 'ok' }) {
  const tones = {
    info: 'bg-[#E7EDFF] text-[#343C6A]',
    error: 'bg-[#FFE0EB] text-[#FE5C73]',
    ok: 'bg-[#DCFAF8] text-[#16DBCC]',
  }
  return <div className={`rounded-2xl px-4 py-3 text-sm ${tones[tone]}`}>{children}</div>
}

export function ScreenTitle({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="mb-5">
      <h2 className="text-[22px] font-semibold text-[#343C6A]">{title}</h2>
      {hint ? <p className="mt-1 max-w-3xl text-sm leading-6 text-[#718EBF]">{hint}</p> : null}
    </div>
  )
}

export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="text-[22px] font-semibold text-[#343C6A]">{children}</h2>
      {action}
    </div>
  )
}
