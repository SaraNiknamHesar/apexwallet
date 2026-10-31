const KEY = 'apex.avatar.'

export function defaultAvatar(username: string): string | null {
  if (username === 'alice') return '/avatars/alice.jpg'
  return null
}

export function readAvatar(username: string): string | null {
  try {
    return sessionStorage.getItem(KEY + username) || defaultAvatar(username)
  } catch {
    return defaultAvatar(username)
  }
}

export function writeAvatar(username: string, dataUrl: string) {
  sessionStorage.setItem(KEY + username, dataUrl)
  window.dispatchEvent(new Event('apex-avatar'))
}
