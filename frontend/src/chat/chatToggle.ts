import { createContext, useContext } from 'react'

export interface ChatToggle {
  open: boolean
  setOpen: (open: boolean) => void
}

export const ChatToggleContext = createContext<ChatToggle>({ open: false, setOpen: () => {} })

/** Lets any page open the assistant panel that the layout owns. */
export function useChatToggle(): ChatToggle {
  return useContext(ChatToggleContext)
}
