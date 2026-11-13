import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useSession } from '../api/session'
import { t } from '../i18n'

export function RequireAdmin({ children }: { children: ReactNode }) {
  const session = useSession()
  const location = useLocation()
  if (session.isPending) return null
  if (!session.data?.authenticated) {
    return <Navigate to={`/sign-in?next=${encodeURIComponent(location.pathname)}`} replace />
  }
  if (!session.data.customer?.admin) return <p role="alert">{t('admin.forbidden')}</p>
  return <>{children}</>
}
