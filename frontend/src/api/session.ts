import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { newConversationId } from '../chat/conversation'
import { api } from './client'

export const sessionKey = ['session'] as const

export function useSession() {
  return useQuery({ queryKey: sessionKey, queryFn: api.session, staleTime: 60_000 })
}

/** After signing in or out, everything tied to the visitor changes: refetch it and start a fresh chat. */
function useAfterAuthChange() {
  const client = useQueryClient()
  return async () => {
    newConversationId()
    await client.invalidateQueries()
  }
}

export function useLogin() {
  const after = useAfterAuthChange()
  return useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) => api.login(email, password),
    onSuccess: after,
  })
}

export function useRegister() {
  const after = useAfterAuthChange()
  return useMutation({
    mutationFn: ({ email, password, name }: { email: string; password: string; name: string }) =>
      api.register(email, password, name),
    onSuccess: after,
  })
}

export function useLogout() {
  const after = useAfterAuthChange()
  return useMutation({ mutationFn: api.logout, onSuccess: after })
}
