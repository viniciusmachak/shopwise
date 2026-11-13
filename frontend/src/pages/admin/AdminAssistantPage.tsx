import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, type ChatMode, type ConversationOverview } from '../../api/client'
import { AdminNav } from '../../components/AdminNav'
import { t } from '../../i18n'
import { formatDate } from '../orderStatus'

const number = new Intl.NumberFormat('en-US')
const percent = new Intl.NumberFormat('en-US', { style: 'percent', maximumFractionDigits: 0 })

export function AdminAssistantPage() {
  return (
    <section className="admin-page">
      <AdminNav />
      <h1>{t('assistantAdmin.title')}</h1>
      <ModeSwitch />
      <Stats />
      <Conversations />
    </section>
  )
}

function ModeSwitch() {
  const client = useQueryClient()
  const status = useQuery({ queryKey: ['admin', 'assistant', 'status'], queryFn: api.admin.assistantStatus })
  const change = useMutation({
    mutationFn: (mode: ChatMode) => api.admin.changeChatMode(mode),
    onSuccess: (updated) => client.setQueryData(['admin', 'assistant', 'status'], updated),
  })
  const s = status.data
  if (!s) return null
  const configuredCloud = s.cloudProviders.filter((p) => p.configured)

  return (
    <section>
      <h2>{t('assistantAdmin.mode')}</h2>
      <fieldset>
        <legend className="visually-hidden">{t('assistantAdmin.mode')}</legend>
        {(['CLOUD', 'LOCAL'] as ChatMode[]).map((mode) => (
          <label key={mode} className="choice">
            <input
              type="radio"
              name="chat-mode"
              checked={s.mode === mode}
              disabled={change.isPending}
              onChange={() => change.mutate(mode)}
            />
            {mode === 'CLOUD' ? t('assistantAdmin.cloud') : t('assistantAdmin.local', { model: s.localModel })}
          </label>
        ))}
      </fieldset>
      {change.isError && <p role="alert">{change.error.message}</p>}
      <ul className="muted">
        {s.cloudProviders.map((p) => (
          <li key={p.name}>
            {p.name} ({p.model}): {p.configured ? t('assistantAdmin.configured') : t('assistantAdmin.noKey')}
          </li>
        ))}
        <li>{s.fallbackToLocal ? t('assistantAdmin.fallbackOn') : t('assistantAdmin.fallbackOff')}</li>
        {s.changedAt && <li>{t('assistantAdmin.changedAt', { date: formatDate(s.changedAt) })}</li>}
      </ul>
      {s.mode === 'CLOUD' && configuredCloud.length === 0 && (
        <p role="alert">{t('assistantAdmin.noCloudKeys')}</p>
      )}
    </section>
  )
}

function Stats() {
  const [days, setDays] = useState(7)
  const stats = useQuery({ queryKey: ['admin', 'assistant', 'stats', days], queryFn: () => api.admin.assistantStats(days) })
  const s = stats.data

  return (
    <section>
      <h2>{t('assistantAdmin.activity')}</h2>
      <label>
        {t('assistantAdmin.period')}
        <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
          <option value={1}>{t('assistantAdmin.lastDay')}</option>
          <option value={7}>{t('assistantAdmin.lastDays', { days: 7 })}</option>
          <option value={30}>{t('assistantAdmin.lastDays', { days: 30 })}</option>
        </select>
      </label>
      {s && (
        <>
          <dl className="totals">
            <dt>{t('assistantAdmin.conversations')}</dt>
            <dd>{number.format(s.conversations)}</dd>
            <dt>{t('assistantAdmin.messages')}</dt>
            <dd>{number.format(s.userMessages)}</dd>
            <dt>{t('assistantAdmin.checkouts')}</dt>
            <dd>{number.format(s.checkoutsPrepared)}</dd>
            <dt>{t('assistantAdmin.orders')}</dt>
            <dd>{number.format(s.ordersPlaced)}</dd>
            <dt>{t('assistantAdmin.conversion')}</dt>
            <dd>{percent.format(s.conversionRate)}</dd>
          </dl>
          <h3>{t('assistantAdmin.tools')}</h3>
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t('assistantAdmin.tool')}</th>
                <th>{t('assistantAdmin.ok')}</th>
                <th>{t('assistantAdmin.rejected')}</th>
              </tr>
            </thead>
            <tbody>
              {s.tools.map((tool) => (
                <tr key={tool.tool}>
                  <td>{tool.tool}</td>
                  <td>{number.format(tool.ok)}</td>
                  <td>{number.format(tool.rejected)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <h3>{t('assistantAdmin.models')}</h3>
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t('assistantAdmin.provider')}</th>
                <th>{t('assistantAdmin.requests')}</th>
                <th>{t('assistantAdmin.failures')}</th>
                <th>{t('assistantAdmin.tokens')}</th>
              </tr>
            </thead>
            <tbody>
              {s.usage.map((u) => (
                <tr key={u.provider}>
                  <td>{u.provider}</td>
                  <td>{number.format(u.requests)}</td>
                  <td>{number.format(u.failures)}</td>
                  <td>{t('assistantAdmin.tokenSplit', { prompt: number.format(u.promptTokens), completion: number.format(u.completionTokens) })}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {s.usage.length === 0 && <p className="muted">{t('assistantAdmin.noUsage')}</p>}
        </>
      )}
    </section>
  )
}

function Conversations() {
  const conversations = useQuery({ queryKey: ['admin', 'assistant', 'conversations'], queryFn: api.admin.assistantConversations })
  const [open, setOpen] = useState<string | null>(null)

  return (
    <section>
      <h2>{t('assistantAdmin.recent')}</h2>
      {conversations.data?.length === 0 && <p>{t('assistantAdmin.noConversations')}</p>}
      <ul className="conversation-list">
        {conversations.data?.map((c) => (
          <li key={c.id}>
            <ConversationRow conversation={c} open={open === c.id} onToggle={() => setOpen(open === c.id ? null : c.id)} />
          </li>
        ))}
      </ul>
    </section>
  )
}

function ConversationRow({ conversation: c, open, onToggle }: { conversation: ConversationOverview; open: boolean; onToggle: () => void }) {
  const transcript = useQuery({
    queryKey: ['admin', 'assistant', 'transcript', c.id],
    queryFn: () => api.admin.assistantTranscript(c.id),
    enabled: open,
  })
  return (
    <>
      <button type="button" aria-expanded={open} onClick={onToggle}>
        {formatDate(c.createdAt)} · {c.customer} · “{c.firstQuestion}” · {t('assistantAdmin.messageCount', { count: c.userMessages })}
        {c.placedOrder ? ` · ${t('assistantAdmin.ordered')}` : ''}
      </button>
      {open && (
        <ol className="transcript">
          {transcript.data?.map((line, i) => (
            <li key={i} className={line.role}>
              <strong>{line.role === 'user' ? t('assistantAdmin.customer') : t('assistantAdmin.assistant')}:</strong> {line.text}
            </li>
          ))}
        </ol>
      )}
    </>
  )
}
