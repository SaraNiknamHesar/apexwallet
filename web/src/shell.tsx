import { ArrowLeftRight, Banknote, Bell, CreditCard, Home, Landmark, LineChart, Search, Send, Settings, UserRound, Wrench, type LucideIcon } from 'lucide-react'
import { createContext, useContext, useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'
import { NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { getSession, subscribe } from './api'
import { readAvatar } from './avatar'

const QueryContext = createContext('')

export function useQuery() {
  return useContext(QueryContext)
}

const titles: Record<string, string> = {
  '/desk': 'Dashboard',
  '/statement': 'Transactions',
  '/accounts': 'Accounts',
  '/loans': 'Loans',
  '/book': 'Markets',
  '/cards': 'Cards',
  '/approvals': 'Approvals',
  '/services': 'Tools',
  '/setting': 'Settings',
  '/pricing': 'Fees',
  '/checks': 'Books',
  '/files': 'Files',
  '/assistant': 'Assistant',
  '/transfer': 'Transfer',
}

const servicePaths = ['/services', '/pricing', '/checks', '/files', '/assistant']

const menuItems = [
  { label: 'Accounts', to: '/accounts' },
  { label: 'Settings', to: '/setting' },
  { label: 'Fees', to: '/pricing' },
  { label: 'Tools', to: '/services' },
]

type NavItem = {
  to: string
  label: string
  icon: LucideIcon
  match: (path: string) => boolean
  /** Omitted means every signed-in role sees it. */
  roles?: string[]
}

const links: NavItem[] = [
  { to: '/desk', label: 'Dashboard', icon: Home, match: (path: string) => path === '/desk' },
  { to: '/statement', label: 'Transactions', icon: ArrowLeftRight, match: (path: string) => path === '/statement' },
  { to: '/transfer', label: 'Transfer', icon: Send, match: (path: string) => path === '/transfer' },
  { to: '/accounts', label: 'Accounts', icon: UserRound, match: (path: string) => path === '/accounts' },
  { to: '/loans', label: 'Loans', icon: Banknote, match: (path: string) => path === '/loans' },
  { to: '/book', label: 'Markets', icon: LineChart, match: (path: string) => path === '/book' },
  { to: '/cards', label: 'Cards', icon: CreditCard, match: (path: string) => path === '/cards' },
  { to: '/approvals', label: 'Approvals', icon: Landmark, roles: ['SUPERVISOR'], match: (path: string) => path === '/approvals' },
  { to: '/services', label: 'Tools', icon: Wrench, match: (path: string) => servicePaths.includes(path) },
  { to: '/setting', label: 'Settings', icon: Settings, match: (path: string) => path === '/setting' },
]

export function Shell() {
  const session = useSyncExternalStore(subscribe, getSession, getSession)
  const location = useLocation()
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [menu, setMenu] = useState(false)
  const [photo, setPhoto] = useState('')
  const menuRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!session) return
    const sync = () => setPhoto(readAvatar(session.username) ?? '')
    sync()
    window.addEventListener('apex-avatar', sync)
    return () => window.removeEventListener('apex-avatar', sync)
  }, [session])
  useEffect(() => {
    function close(event: MouseEvent) {
      if (!menuRef.current?.contains(event.target as Node)) setMenu(false)
    }
    window.addEventListener('mousedown', close)
    return () => window.removeEventListener('mousedown', close)
  }, [])
  if (!session) return <Navigate to="/login" replace />

  const title = titles[location.pathname] ?? 'Dashboard'
  const canApprove = session.role === 'SUPERVISOR'
  const visibleLinks = links.filter((link) => !link.roles || link.roles.includes(session.role))

  return (
    <QueryContext.Provider value={query}>
      <div className="min-h-full bg-[#F5F7FA] lg:flex">
        <aside className="flex shrink-0 flex-col bg-white px-6 py-6 lg:fixed lg:inset-y-0 lg:w-[250px] lg:py-8">
          <p className="px-3 text-[25px] font-extrabold tracking-tight text-[#343C6A]">Apex</p>
          <nav className="mt-8 flex gap-1 overflow-x-auto lg:mt-10 lg:flex-col lg:gap-2 lg:overflow-visible">
            {visibleLinks.map((link) => {
              const active = link.match(location.pathname)
              return (
                <NavLink
                  key={link.to}
                  to={link.to}
                  className={`flex items-center gap-4 whitespace-nowrap rounded-xl px-3 py-3 text-[15px] font-medium transition duration-200 ${active ? 'bg-[#F5F7FA] text-[#1814F3]' : 'text-[#B1B1B1] hover:bg-[#F5F7FA] hover:text-[#343C6A]'}`}
                >
                  <link.icon size={20} strokeWidth={2} />
                  {link.label}
                </NavLink>
              )
            })}
          </nav>
        </aside>
        <div className="min-w-0 flex-1 lg:pl-[250px]">
          <header className="flex flex-wrap items-center justify-between gap-4 px-6 pt-6 lg:px-10 lg:pt-8">
            <h1 className="text-[28px] font-semibold text-[#343C6A]">{title}</h1>
            <div className="flex items-center gap-4 lg:gap-7">
              <label className="relative block">
                <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-[#718EBF]" />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Search for something"
                  className="h-12 w-56 rounded-full bg-white pl-11 pr-4 text-sm text-[#343C6A] outline-none transition duration-200 placeholder:text-[#8BA3CB] focus:shadow-[0_0_0_3px_rgba(24,20,243,0.12)] lg:w-64"
                />
              </label>
              <button type="button" className="flex h-12 w-12 items-center justify-center rounded-full bg-[#F5F7FA] text-[#718EBF] transition duration-200 hover:scale-105 hover:bg-[#E7EDFF]" aria-label="Settings" onClick={() => navigate('/setting')}>
                <Settings size={20} />
              </button>
              {canApprove ? (
                <button type="button" className="relative flex h-12 w-12 items-center justify-center rounded-full bg-[#F5F7FA] text-[#FE5C73] transition duration-200 hover:scale-105 hover:bg-[#FFE0EB]" aria-label="Approvals" onClick={() => navigate('/approvals')}>
                  <Bell size={20} />
                  <span className="dot-pulse absolute right-3 top-3 h-2 w-2 rounded-full bg-[#FE5C73]" />
                </button>
              ) : null}
              <div className="relative" ref={menuRef}>
                <button
                  type="button"
                  className="h-12 w-12 overflow-hidden rounded-full bg-[#1814F3] transition duration-200 hover:scale-105"
                  aria-label="Account menu"
                  aria-expanded={menu}
                  onClick={() => setMenu((open) => !open)}
                >
                  {photo ? (
                    <img src={photo} alt="" className="h-full w-full object-cover" />
                  ) : (
                    <span className="text-sm font-semibold text-white">{session.username.slice(0, 1).toUpperCase()}</span>
                  )}
                </button>
                {menu ? (
                  <div className="menu-rise absolute right-0 z-20 mt-3 w-48 overflow-hidden rounded-2xl bg-white py-2 text-sm text-[#343C6A] shadow-lg">
                    {menuItems.map((item, index) => (
                      <button
                        key={item.label}
                        type="button"
                        className={`block w-full px-4 py-2.5 text-left hover:bg-[#F5F7FA] ${index === 2 ? 'border-b border-[#F3F3F5]' : ''}`}
                        onClick={() => {
                          setMenu(false)
                          navigate(item.to)
                        }}
                      >
                        {item.label}
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
            </div>
          </header>
          <main key={location.pathname} className="rise-in px-6 py-6 lg:px-10 lg:py-8">
            <Outlet />
          </main>
        </div>
      </div>
    </QueryContext.Provider>
  )
}

/** Hides a page from every role that is not listed, even when the URL is typed by hand. */
export function RequireRoles({ roles, children }: { roles: string[]; children: ReactNode }) {
  const session = useSyncExternalStore(subscribe, getSession, getSession)
  if (!session) return <Navigate to="/login" replace />
  if (!roles.includes(session.role)) return <Navigate to="/desk" replace />
  return <>{children}</>
}
