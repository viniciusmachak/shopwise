import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { cartKey } from '../api/cart'
import { api, ApiError, type ShippingOption } from '../api/client'
import { AddressText } from '../components/AddressFields'
import { formatMoney, t } from '../i18n'

export function CheckoutPage() {
  const client = useQueryClient()
  const navigate = useNavigate()
  const [addressId, setAddressId] = useState<number | undefined>()
  const [shipping, setShipping] = useState<ShippingOption>('STANDARD')
  // One key per visit to this page: retries and double clicks can't create two orders.
  const [idempotencyKey] = useState(() => crypto.randomUUID())

  const addresses = useQuery({ queryKey: ['addresses'], queryFn: api.addresses })
  const quote = useQuery({
    queryKey: ['quote', addressId, shipping],
    queryFn: () => api.quote(addressId, shipping),
  })
  const place = useMutation({
    mutationFn: () => {
      const q = quote.data!
      return api.placeOrder(q.address!.id, shipping, q.cartHash, idempotencyKey)
    },
    onSuccess: (order) => {
      void client.invalidateQueries({ queryKey: cartKey })
      void client.invalidateQueries({ queryKey: ['orders'] })
      if (order.paymentUrl) {
        // Card payment happens on Stripe's page; Stripe sends the customer back to the order.
        window.location.assign(order.paymentUrl)
        return
      }
      navigate(`/orders/${order.id}`, { state: { placed: true } })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.reason === 'CART_CHANGED') void quote.refetch()
    },
  })

  const q = quote.data
  if (!q) return null
  if (q.items.length === 0) {
    return (
      <section>
        <h1>{t('checkout.title')}</h1>
        <p>{t('cart.empty')}</p>
      </section>
    )
  }

  const selectedAddress = q.address

  return (
    <section className="checkout-page">
      <h1>{t('checkout.title')}</h1>

      <h2>{t('checkout.shipTo')}</h2>
      {addresses.data?.length === 0 ? (
        <p>
          {t('checkout.noAddress')} <Link to="/account">{t('checkout.addAddress')}</Link>
        </p>
      ) : (
        <fieldset>
          <legend className="visually-hidden">{t('checkout.shipTo')}</legend>
          {addresses.data?.map((a) => (
            <label key={a.id} className="choice">
              <input
                type="radio"
                name="address"
                checked={selectedAddress?.id === a.id}
                onChange={() => setAddressId(a.id)}
              />
              <AddressText address={a} />
            </label>
          ))}
        </fieldset>
      )}

      <h2>{t('checkout.shipping')}</h2>
      <fieldset>
        <legend className="visually-hidden">{t('checkout.shipping')}</legend>
        {q.shippingChoices.map((choice) => (
          <label key={choice.option} className="choice">
            <input
              type="radio"
              name="shipping"
              checked={shipping === choice.option}
              onChange={() => setShipping(choice.option)}
            />
            {choice.label} · {choice.price.amount === 0 ? t('checkout.free') : formatMoney(choice.price)}
          </label>
        ))}
      </fieldset>

      <h2>{t('checkout.summary')}</h2>
      <ul className="cart-lines">
        {q.items.map((line, i) => (
          <li key={i}>
            <div>
              {line.quantity} × {line.productName}
              {line.size && <span className="muted"> · {t('cart.size', { size: line.size })}</span>}
            </div>
            <span>{formatMoney(line.lineTotal)}</span>
          </li>
        ))}
      </ul>
      <dl className="totals">
        <dt>{t('cart.subtotal')}</dt>
        <dd>{formatMoney(q.subtotal)}</dd>
        {q.couponCode && (
          <>
            <dt>{t('cart.discount', { code: q.couponCode })}</dt>
            <dd>−{formatMoney(q.discount)}</dd>
          </>
        )}
        <dt>{t('checkout.shipping')}</dt>
        <dd>{q.shipping.amount === 0 ? t('checkout.free') : formatMoney(q.shipping)}</dd>
        <dt>{t('cart.total')}</dt>
        <dd>
          <strong>{formatMoney(q.total)}</strong>
        </dd>
      </dl>
      <p className="muted">{t('checkout.paymentNote')}</p>
      {place.isError && <p role="alert">{place.error.message}</p>}
      <button type="button" disabled={!selectedAddress || place.isPending} onClick={() => place.mutate()}>
        {t('checkout.placeOrder', { total: formatMoney(q.total) })}
      </button>
    </section>
  )
}
