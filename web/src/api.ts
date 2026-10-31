export type Session = {
  accessToken: string
  refreshToken: string
  username: string
  role: string
}

export type Account = {
  id: string
  ownerRef: string
  currency: string
  type: string
  status: string
  balanceMinor: number
  externalAccountNumber: string | null
  version: number
}

export type StatementLine = {
  bookedAt: string
  sequence: number
  entryId: string
  transactionId: string | { value: string }
  direction: 'DEBIT' | 'CREDIT'
  amountMinor: number
  runningBalanceMinor: number
  description?: string
}

export type StatementPage = {
  items: StatementLine[]
  page: number
  size: number
  total: number
  hasNext: boolean
  closingBalanceMinor: number
}

export type TransferReceipt = {
  transactionId: string
  status: string
  fromBalanceMinor: number | null
  toBalanceMinor: number | null
}

export type TransferResult = {
  httpStatus: number
  replayed: boolean
  receipt: TransferReceipt
}

export type Approval = {
  id: string
  makerUsername: string
  fromAccountId: string
  toAccountId: string
  amountMinor: number
  currency: string
  description: string | null
  status: string
}

export class ApiError extends Error {
  status: number
  code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

const KEY = 'apex.session'
const listeners = new Set<() => void>()

function readStored(): Session | null {
  const raw = sessionStorage.getItem(KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as Session
  } catch {
    return null
  }
}

let snapshot: Session | null = readStored()

export function getSession(): Session | null {
  return snapshot
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function saveSession(session: Session | null) {
  snapshot = session
  if (session) sessionStorage.setItem(KEY, JSON.stringify(session))
  else sessionStorage.removeItem(KEY)
  listeners.forEach((listener) => listener())
}

function decode(token: string): { username?: string; roles?: string[] } {
  const segment = token.split('.')[1] ?? ''
  const padded = segment.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(segment.length / 4) * 4, '=')
  return JSON.parse(atob(padded)) as { username?: string; roles?: string[] }
}

function sessionFromTokens(tokens: { accessToken: string; refreshToken: string }): Session {
  const claims = decode(tokens.accessToken)
  const role = Array.isArray(claims.roles) && claims.roles.length > 0 ? claims.roles[0] : ''
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    username: claims.username ?? '',
    role,
  }
}

async function problem(response: Response): Promise<ApiError> {
  let code = 'REQUEST_FAILED'
  let detail = response.statusText || 'Request failed'
  try {
    const body = (await response.json()) as { code?: string; title?: string; detail?: string }
    code = body.code ?? body.title ?? code
    detail = body.detail ?? detail
  } catch {
    /* The body was empty or not JSON. */
  }
  return new ApiError(response.status, code, detail)
}

async function refresh(session: Session): Promise<Session | null> {
  const response = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: session.refreshToken }),
  })
  if (!response.ok) return null
  const tokens = (await response.json()) as { accessToken: string; refreshToken: string }
  const next = sessionFromTokens(tokens)
  saveSession(next)
  return next
}

async function send(path: string, init: RequestInit = {}, allowRefresh = true): Promise<Response> {
  const headers = new Headers(init.headers)
  const session = getSession()
  if (session) headers.set('Authorization', `Bearer ${session.accessToken}`)
  const response = await fetch(path, { ...init, headers })
  if (response.status === 401 && allowRefresh && session && !path.startsWith('/api/v1/auth/')) {
    const next = await refresh(session)
    if (!next) {
      saveSession(null)
      return response
    }
    return send(path, init, false)
  }
  return response
}

export async function login(username: string, password: string): Promise<Session> {
  let response: Response
  try {
    response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
  } catch {
    throw new ApiError(0, 'API_DOWN', 'The API on port 8080 is not running.')
  }
  if (!response.ok) throw await problem(response)
  const session = sessionFromTokens((await response.json()) as { accessToken: string; refreshToken: string })
  saveSession(session)
  return session
}

export async function logout(): Promise<void> {
  try {
    await send('/api/v1/auth/logout', { method: 'POST' })
  } finally {
    saveSession(null)
  }
}

export async function listAccounts(): Promise<Account[]> {
  const response = await send('/api/v1/accounts')
  if (!response.ok) throw await problem(response)
  return (await response.json()) as Account[]
}

export async function statement(accountId: string, page: number): Promise<StatementPage> {
  const response = await send(`/api/v1/accounts/${accountId}/statement?page=${page}&size=20`)
  if (!response.ok) throw await problem(response)
  return (await response.json()) as StatementPage
}

export async function submitTransfer(
  body: {
    fromAccountId: string
    toAccountId: string
    amountMinor: number
    currency: string
    description: string
  },
  idempotencyKey: string,
): Promise<TransferResult> {
  const response = await send('/api/v1/transfers', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw await problem(response)
  return {
    httpStatus: response.status,
    replayed: response.headers.get('Idempotency-Replayed') === 'true',
    receipt: (await response.json()) as TransferReceipt,
  }
}

export type Loan = {
  id: string
  borrowerUsername: string
  accountId: string
  principalMinor: number
  outstandingMinor: number
  currency: string
  status: string
}

export async function listLoans(): Promise<Loan[]> {
  const response = await send('/api/v1/loans')
  if (!response.ok) throw await problem(response)
  return (await response.json()) as Loan[]
}

export async function requestLoan(amountMinor: number): Promise<{ httpStatus: number; loan: Loan }> {
  const response = await send('/api/v1/loans', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amountMinor }),
  })
  if (!response.ok) throw await problem(response)
  return { httpStatus: response.status, loan: (await response.json()) as Loan }
}

export async function repayLoan(id: string, amountMinor: number): Promise<Loan> {
  const response = await send(`/api/v1/loans/${id}/repay`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amountMinor }),
  })
  if (!response.ok) throw await problem(response)
  return (await response.json()) as Loan
}

export async function decideLoan(id: string, action: 'approve' | 'reject'): Promise<Loan> {
  const response = await send(`/api/v1/loans/${id}/${action}`, { method: 'POST' })
  if (!response.ok) throw await problem(response)
  return (await response.json()) as Loan
}

export type Payee = {
  valid: boolean
  found: boolean
  ownerRef: string | null
  accountId: string | null
  currency: string | null
  iban: string
  reason: string | null
}

/** Confirmation of Payee: which account, if any, owns this IBAN. */
export async function resolvePayee(iban: string): Promise<Payee> {
  const response = await send('/api/v1/transfers/resolve', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ iban }),
  })
  if (!response.ok) throw await problem(response)
  return (await response.json()) as Payee
}

export async function listPending(): Promise<Approval[]> {
  const response = await send('/api/v1/transfers/pending')
  if (!response.ok) throw await problem(response)
  return (await response.json()) as Approval[]
}

export async function decide(id: string, action: 'approve' | 'reject'): Promise<TransferReceipt> {
  const response = await send(`/api/v1/transfers/${id}/${action}`, { method: 'POST' })
  if (!response.ok) throw await problem(response)
  return (await response.json()) as TransferReceipt
}

export async function lookupOwnAccount(username: string, password: string): Promise<Account> {
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!response.ok) throw await problem(response)
  const tokens = sessionFromTokens((await response.json()) as { accessToken: string; refreshToken: string })
  const accounts = await fetch('/api/v1/accounts', {
    headers: { Authorization: `Bearer ${tokens.accessToken}` },
  })
  if (!accounts.ok) throw await problem(accounts)
  const rows = (await accounts.json()) as Account[]
  const account = rows[0]
  if (!account) throw new ApiError(404, 'NOT_FOUND', `${username} has no account yet.`)
  return account
}

export function transactionId(line: StatementLine): string {
  return typeof line.transactionId === 'string' ? line.transactionId : line.transactionId.value
}

export async function register(username: string, password: string): Promise<void> {
  const response = await fetch('/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!response.ok) throw await problem(response)
}

export async function changeOverdraft(id: string, limitMinor: number, expectedVersion: number): Promise<Account> {
  const response = await send(`/api/v1/accounts/${id}/overdraft`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ limitMinor, expectedVersion }),
  })
  if (!response.ok) throw await problem(response)
  return (await response.json()) as Account
}

export type Coverage = {
  ledger: boolean
  pricing: boolean
  isoStudySubset: boolean
  offlineAssistant: boolean
  orderBook: boolean
  inProcessOutbox: boolean
  postgresOnThisProcess: boolean
  kafkaConsumer: boolean
  grafanaServer: boolean
  hostedModel: boolean
  assistantService: boolean
  demoVideo: boolean
  mutationScore: boolean
}

export async function workspace<T>(path: string, body?: unknown): Promise<T> {
  const response = await send(`/api/v1/workspace${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw await problem(response)
  return (await response.json()) as T
}
