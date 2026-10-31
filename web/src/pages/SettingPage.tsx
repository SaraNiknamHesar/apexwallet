import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getSession, logout } from '../api'
import { readAvatar, writeAvatar } from '../avatar'
import { Button, Card } from '../ui'

export function SettingPage() {
  const session = getSession()
  const navigate = useNavigate()
  const [photo, setPhoto] = useState(() => (session ? readAvatar(session.username) : null))

  return (
    <Card className="max-w-xl p-8">
      <p className="text-sm text-[#718EBF]">Profile</p>
      <div className="mt-4 flex items-center gap-4">
        <div className="h-20 w-20 overflow-hidden rounded-full bg-[#1814F3]">
          {photo ? (
            <img src={photo} alt="" className="h-full w-full object-cover" />
          ) : (
            <span className="flex h-full items-center justify-center text-2xl font-semibold text-white">
              {(session?.username ?? '?').slice(0, 1).toUpperCase()}
            </span>
          )}
        </div>
        <div>
          <p className="text-[28px] font-semibold text-[#343C6A]">{session?.username}</p>
          <p className="text-sm text-[#1814F3]">{session?.role}</p>
          <label className="mt-2 inline-block cursor-pointer text-sm font-medium text-[#1814F3]">
            Change photo
            <input
              type="file"
              accept="image/*"
              className="sr-only"
              onChange={(event) => {
                const file = event.target.files?.[0]
                if (!file || !session) return
                const reader = new FileReader()
                reader.onload = () => {
                  const dataUrl = String(reader.result ?? '')
                  writeAvatar(session.username, dataUrl)
                  setPhoto(dataUrl)
                }
                reader.readAsDataURL(file)
              }}
            />
          </label>
        </div>
      </div>
      <p className="mt-4 text-sm leading-6 text-[#718EBF]">These passwords exist only on this laptop. Balances live in memory and return to the opening amounts when the API restarts.</p>
      <Button
        className="mt-6"
        onClick={() => {
          void logout().finally(() => navigate('/login'))
        }}
      >
        Sign out
      </Button>
    </Card>
  )
}
