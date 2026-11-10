import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type CartView } from './client'

export const cartKey = ['cart'] as const

/** The single source of cart state, shared by the page and the chat. */
export function useCart() {
  return useQuery({ queryKey: cartKey, queryFn: api.cart })
}

function useCartMutation<A>(fn: (args: A) => Promise<CartView>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (cart) => client.setQueryData(cartKey, cart),
  })
}

export function useAddToCart() {
  return useCartMutation(({ variantId, quantity }: { variantId: number; quantity: number }) =>
    api.addToCart(variantId, quantity),
  )
}

export function useUpdateCartItem() {
  return useCartMutation(({ itemId, quantity }: { itemId: number; quantity: number }) =>
    api.updateCartItem(itemId, quantity),
  )
}

export function useApplyCoupon() {
  return useCartMutation((code: string) => api.applyCoupon(code))
}

export function useRemoveCoupon() {
  return useCartMutation(() => api.removeCoupon())
}
