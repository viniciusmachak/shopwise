import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { t } from '../i18n'

/** Shown while something loads, so the page never sits blank. */
export function Loading({ label = t('status.loading') }: { label?: string }) {
  return (
    <p className="status" role="status">
      <span className="spinner" aria-hidden="true" />
      {label}
    </p>
  )
}

/** Placeholder cards with the same size as the real ones, so the layout doesn't jump. */
export function CardSkeletons({ count = 6 }: { count?: number }) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="product-card skeleton" aria-hidden="true">
          <div className="product-media" />
          <div className="product-body">
            <span className="skeleton-line" />
            <span className="skeleton-line short" />
          </div>
        </div>
      ))}
    </>
  )
}

/** An error in plain words, with a way out. */
export function ErrorState({ message = t('error.generic'), onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <div className="notice" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button type="button" className="button-secondary" onClick={onRetry}>
          {t('status.retry')}
        </button>
      )}
    </div>
  )
}

/** Nothing to show yet: say why and what to do next. */
export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <p className="empty-title">{title}</p>
      {children && <p>{children}</p>}
      {action && <div className="empty-action">{action}</div>}
    </div>
  )
}

export interface Crumb {
  label: string
  to?: string
}

/** Where you are and how to go back up. The last item is the current page. */
export function Breadcrumbs({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label={t('nav.breadcrumbs')} className="breadcrumbs">
      <ol>
        <li>
          <Link to="/">{t('nav.homeCrumb')}</Link>
        </li>
        {items.map((c, i) => (
          <li key={i}>
            {c.to && i < items.length - 1 ? (
              <Link to={c.to}>{c.label}</Link>
            ) : (
              <span aria-current="page">{c.label}</span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  )
}
