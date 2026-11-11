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

type Listener = (id: string) => void
const listeners = new Set<Listener>()

/** Lets the chat panel follow conversation changes made elsewhere (e.g. on sign-in). */
export function onConversationChange(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function setConversationId(id: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    // keep the id in memory only
  }
  listeners.forEach((l) => l(id))
}

export function newConversationId(): string {
  const id = crypto.randomUUID()
  setConversationId(id)
  return id
}
