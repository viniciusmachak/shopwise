import { csrfHeaders, type CartView, type CheckoutQuote, type OrderView, type ProductSummary } from '../api/client'
import { SseParser } from './sse'

export type ConfirmRequest =
  | { token: string; action: 'checkout'; summary: CheckoutQuote; expiresAt: string }
  | { token: string; action: 'cancel'; summary: OrderView; expiresAt: string }

export type AssistantEvent =
  | { type: 'token'; text: string }
  | { type: 'tool_status'; tool: string }
  | { type: 'ui.products'; products: ProductSummary[] }
  | { type: 'ui.cart'; cart: CartView }
  | { type: 'ui.confirm'; request: ConfirmRequest }
  | { type: 'ui.order'; order: OrderView }
  | { type: 'ui.signin' }
  | { type: 'error'; code: string; message: string }
  | { type: 'done' }

export interface SendOptions {
  conversationId: string
  text: string
  voice: boolean
  signal?: AbortSignal
  onEvent: (event: AssistantEvent) => void
}

export async function sendMessage({ conversationId, text, voice, signal, onEvent }: SendOptions): Promise<void> {
  const response = await fetch(`/api/assistant/conversations/${conversationId}/messages`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...csrfHeaders('POST') },
    body: JSON.stringify({ text, mode: voice ? 'voice' : 'chat' }),
    signal,
  })
  if (!response.ok || !response.body) {
    throw new Error(`Assistant request failed: ${response.status}`)
  }

  const parser = new SseParser()
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    for (const raw of parser.push(value)) {
      const event = toAssistantEvent(raw.event, raw.data)
      if (event) onEvent(event)
    }
  }
}

/** Maps a raw SSE event to a typed one. Unknown event types are ignored. */
export function toAssistantEvent(type: string, data: string): AssistantEvent | null {
  let payload: unknown
  try {
    payload = JSON.parse(data)
  } catch {
    return null
  }
  const body = payload as Record<string, unknown>
  switch (type) {
    case 'token':
      return { type, text: String(body.text ?? '') }
    case 'tool_status':
      return { type, tool: String(body.tool ?? '') }
    case 'ui.products':
      return { type, products: payload as ProductSummary[] }
    case 'ui.cart':
      return { type, cart: payload as CartView }
    case 'ui.confirm':
      return body.action === 'checkout' || body.action === 'cancel' ? { type, request: payload as ConfirmRequest } : null
    case 'ui.order':
      return { type, order: payload as OrderView }
    case 'ui.signin':
      return { type }
    case 'error':
      return { type, code: String(body.code ?? ''), message: String(body.message ?? '') }
    case 'done':
      return { type }
    default:
      return null
  }
}
