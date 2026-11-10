const STORAGE_KEY = 'shopwise.conversationId'

export function loadConversationId(): string {
  try {
    const existing = localStorage.getItem(STORAGE_KEY)
    if (existing) return existing
  } catch {
    // storage unavailable (private mode, blocked): fall through to a fresh id
  }
  return newConversationId()
}

export function newConversationId(): string {
  const id = crypto.randomUUID()
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    // keep the id in memory only
  }
  return id
}
