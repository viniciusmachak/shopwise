import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { cartKey } from '../api/cart'
import { api, type CartView, type OrderView, type ProductSummary } from '../api/client'
import { OrderCard } from '../components/OrderCard'
import { ProductCard } from '../components/ProductCard'
import { formatMoney, hasKey, t } from '../i18n'
import { takeSentences, useVoice } from '../voice/useVoice'
import { sendMessage, type AssistantEvent, type ConfirmRequest } from './assistant'
import { ConfirmCard } from './ConfirmCard'
import { loadConversationId, newConversationId, onConversationChange, setConversationId } from './conversation'

type Message =
  | { id: number; role: 'user'; text: string }
  | {
      id: number
      role: 'assistant'
      text: string
      products?: ProductSummary[]
      cart?: CartView
      confirm?: ConfirmRequest
      orders?: OrderView[]
      signIn?: boolean
      error?: string
    }

let nextId = 1

export function ChatPanel() {
  const queryClient = useQueryClient()
  const [conversationId, setCurrentConversation] = useState(loadConversationId)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [status, setStatus] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const abort = useRef<AbortController | null>(null)
  const [showHistory, setShowHistory] = useState(false)
  const history = useQuery({ queryKey: ['conversations'], queryFn: api.conversations, enabled: showHistory })

  // Signing in or out starts a new conversation elsewhere; follow it.
  useEffect(
    () =>
      onConversationChange((id) => {
        setCurrentConversation((current) => {
          if (current !== id) setMessages([])
          return id
        })
      }),
    [],
  )

  const updateLast = useCallback((change: (m: Extract<Message, { role: 'assistant' }>) => Message) => {
    setMessages((all) => {
      const last = all[all.length - 1]
      if (!last || last.role !== 'assistant') return all
      return [...all.slice(0, -1), change(last)]
    })
  }, [])

  const send = useCallback(
    async (text: string, voice: boolean, speak?: (s: string) => void) => {
      const trimmed = text.trim()
      if (!trimmed || busy) return
      setInput('')
      setBusy(true)
      setMessages((all) => [
        ...all,
        { id: nextId++, role: 'user', text: trimmed },
        { id: nextId++, role: 'assistant', text: '' },
      ])
      abort.current = new AbortController()
      let spoken = ''

      const onEvent = (event: AssistantEvent) => {
        switch (event.type) {
          case 'token':
            setStatus(null)
            updateLast((m) => ({ ...m, text: m.text + event.text }))
            if (voice && speak) {
              spoken += event.text
              const { sentences, rest } = takeSentences(spoken)
              sentences.forEach(speak)
              spoken = rest
            }
            break
          case 'tool_status': {
            const key = `tool.${event.tool}`
            setStatus(hasKey(key) ? t(key) : t('tool.default'))
            break
          }
          case 'ui.products':
            updateLast((m) => ({ ...m, products: event.products }))
            break
          case 'ui.cart':
            queryClient.setQueryData(cartKey, event.cart)
            updateLast((m) => ({ ...m, cart: event.cart }))
            break
          case 'ui.confirm':
            updateLast((m) => ({ ...m, confirm: event.request }))
            break
          case 'ui.order':
            queryClient.invalidateQueries({ queryKey: ['orders'] })
            updateLast((m) => ({ ...m, orders: [...(m.orders ?? []), event.order] }))
            break
          case 'ui.signin':
            updateLast((m) => ({ ...m, signIn: true }))
            break
          case 'error':
            updateLast((m) => ({ ...m, error: event.message }))
            if (event.code === 'FORBIDDEN') newConversationId()
            break
          case 'done':
            break
        }
      }

      try {
        await sendMessage({ conversationId, text: trimmed, voice, signal: abort.current.signal, onEvent })
        if (voice && speak && spoken.trim()) speak(spoken)
      } catch (e) {
        if (!(e instanceof DOMException && e.name === 'AbortError')) {
          updateLast((m) => ({ ...m, error: t('error.generic') }))
        }
      } finally {
        setStatus(null)
        setBusy(false)
      }
    },
    [busy, conversationId, queryClient, updateLast],
  )

  const voice = useVoice({
    onInterim: setInput,
    onFinal: (text) => void send(text, true, voice.speak),
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    voice.cancelSpeech()
    void send(input, false)
  }

  function startOver() {
    abort.current?.abort()
    voice.cancelSpeech()
    setMessages([])
    newConversationId()
  }

  async function openConversation(id: string) {
    abort.current?.abort()
    voice.cancelSpeech()
    setShowHistory(false)
    const past = await api.conversation(id)
    setConversationId(id)
    setMessages(past.map((m) => ({ id: nextId++, role: m.role === 'user' ? 'user' : 'assistant', text: m.text })))
  }

  return (
    <aside className="chat" aria-label={t('chat.title')}>
      <div className="chat-header">
        <h2>{t('chat.title')}</h2>
        <button type="button" onClick={() => setShowHistory((v) => !v)} aria-expanded={showHistory}>
          {t('chat.history')}
        </button>
        <button type="button" onClick={startOver}>
          {t('chat.newConversation')}
        </button>
      </div>

      {showHistory && (
        <ul className="chat-history" aria-label={t('chat.history')}>
          {history.data?.length === 0 && <li className="muted">{t('chat.noHistory')}</li>}
          {history.data?.map((c) => (
            <li key={c.id}>
              <button type="button" onClick={() => void openConversation(c.id)} aria-current={c.id === conversationId}>
                {c.title}
              </button>
            </li>
          ))}
        </ul>
      )}

      <ol className="chat-messages" aria-live="polite">
        {messages.length === 0 && <li className="assistant">{t('chat.greeting')}</li>}
        {messages.map((m) => (
          <li key={m.id} className={m.role}>
            {m.text && <p>{m.text}</p>}
            {m.role === 'assistant' && m.products && m.products.length > 0 && (
              <div className="chat-cards">
                {m.products.map((p) => <ProductCard key={p.id} product={p} compact />)}
              </div>
            )}
            {m.role === 'assistant' && m.cart && (
              <p className="chat-cart">
                {t('chat.cartUpdated', { count: m.cart.itemCount, total: formatMoney(m.cart.total) })}
              </p>
            )}
            {m.role === 'assistant' && m.confirm && <ConfirmCard key={m.confirm.token} request={m.confirm} />}
            {m.role === 'assistant' &&
              m.orders?.map((order) => <OrderCard key={`${order.id}-${order.status}`} order={order} />)}
            {m.role === 'assistant' && m.signIn && (
              <p className="chat-signin">
                <Link to="/sign-in">{t('chat.signIn')}</Link>
              </p>
            )}
            {m.role === 'assistant' && m.error && <p role="alert">{m.error}</p>}
          </li>
        ))}
      </ol>

      {status && <p className="chat-status">{status}</p>}
      {voice.listening && <p className="chat-status">{t('chat.listening')}</p>}

      <form className="chat-input" onSubmit={onSubmit}>
        <input
          aria-label={t('chat.placeholder')}
          placeholder={t('chat.placeholder')}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={busy}
        />
        {voice.supported && (
          <button
            type="button"
            onClick={voice.listening ? voice.stop : voice.start}
            aria-pressed={voice.listening}
            aria-label={voice.listening ? t('chat.stopVoice') : t('chat.startVoice')}
            disabled={busy}
          >
            🎤
          </button>
        )}
        <button type="submit" disabled={busy || !input.trim()}>
          {t('chat.send')}
        </button>
      </form>
    </aside>
  )
}
