export const demoUsers = [
  { username: 'alice', password: 'alice-pass-1', role: 'Customer', note: 'Starts with 2,000.00 EUR' },
  { username: 'bob', password: 'bob-pass-1111', role: 'Customer', note: 'Starts at zero' },
  { username: 'sam', password: 'sam-supervisor-1', role: 'Supervisor', note: 'Approves large transfers' },
  { username: 'tim', password: 'tim-teller-1', role: 'Teller', note: 'Can move money for a customer' },
  { username: 'aud', password: 'aud-auditor-1', role: 'Auditor', note: 'Read only' },
] as const
