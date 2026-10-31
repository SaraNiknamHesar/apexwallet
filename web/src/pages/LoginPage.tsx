import { useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { ApiError, getSession, login, register } from '../api'
import { demoUsers } from '../demoUsers'
import { Button, Card, Field, Notice } from '../ui'

export function LoginPage() {
  const [username, setUsername] = useState('alice')
  const [password, setPassword] = useState('alice-pass-1')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [signedIn, setSignedIn] = useState(Boolean(getSession()))

  if (signedIn) return <Navigate to="/desk" replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await login(username.trim(), password)
      setSignedIn(true)
    } catch (caught) {
      setError(caught instanceof ApiError ? `${caught.code}: ${caught.message}` : 'Sign-in failed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center bg-[#F5F7FA] p-6">
      <div className="rise-in w-full max-w-md">
        <p className="text-center text-[32px] font-extrabold tracking-tight text-[#343C6A]">Apex</p>
        <p className="mt-1 text-center text-sm text-[#718EBF]">Sign in to the desk on this laptop.</p>
        <Card className="mt-6 p-6">
          <form className="space-y-4" onSubmit={submit}>
            <Field label="Username" value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
            <Field label="Password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" />
            {error ? <Notice tone="error">{error}</Notice> : null}
            <Button type="submit" className="w-full" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</Button>
            <Button
              type="button"
              tone="ghost"
              className="w-full"
              disabled={busy}
              onClick={() => {
                setBusy(true)
                setError('')
                void register(username.trim(), password)
                  .then(() => login(username.trim(), password))
                  .then(() => setSignedIn(true))
                  .catch((caught) => setError(caught instanceof ApiError ? `${caught.code}: ${caught.message}` : 'Could not create the customer.'))
                  .finally(() => setBusy(false))
              }}
            >
              Create customer
            </Button>
          </form>
        </Card>
        <Card className="mt-4 divide-y divide-[#F5F7FA]">
          {demoUsers.map((user) => (
            <button
              key={user.username}
              type="button"
              className="flex w-full items-center justify-between px-5 py-3 text-left hover:bg-[#F5F7FA]"
              onClick={() => {
                setUsername(user.username)
                setPassword(user.password)
              }}
            >
              <span>
                <span className="block text-sm font-semibold text-[#343C6A]">{user.username}</span>
                <span className="block text-xs text-[#718EBF]">{user.note}</span>
              </span>
              <span className="text-xs text-[#1814F3]">{user.role}</span>
            </button>
          ))}
        </Card>
      </div>
    </div>
  )
}
