import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useSession } from '../api/session'

/** Sends visitors to the sign-in page and brings them back afterwards. */
export function RequireSignIn({ children }: { children: ReactNode }) {
  const session = useSession()
  const location = useLocation()
  if (session.isPending) return null
  if (!session.data?.authenticated) {
    return <Navigate to={`/sign-in?next=${encodeURIComponent(location.pathname + location.search)}`} replace />
  }
  return <>{children}</>
}
