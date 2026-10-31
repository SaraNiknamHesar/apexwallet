import { useEffect, useState, type ReactNode } from 'react'

function clock() {
  return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

export function PhoneFrame({ children, dark = false }: { children: ReactNode; dark?: boolean }) {
  const [time, setTime] = useState(clock)

  useEffect(() => {
    const id = window.setInterval(() => setTime(clock()), 30_000)
    return () => window.clearInterval(id)
  }, [])

  return (
    <div className="stage">
      <div className={`device ${dark ? 'device-dark' : ''}`}>
        <div className="status" style={{ color: dark ? '#ffffff' : '#111111' }}>
          <span className="num">{time}</span>
          <span className="island" />
          <span className="text-right text-[11px] tracking-wide">Apex</span>
        </div>
        <div className="device-body">{children}</div>
        <div className="home-pill" />
      </div>
    </div>
  )
}
