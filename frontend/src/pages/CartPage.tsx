import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useApplyCoupon, useCart, useRemoveCoupon, useUpdateCartItem } from '../api/cart'
import { formatMoney, t } from '../i18n'

export function CartPage() {
  const cart = useCart()
  const update = useUpdateCartItem()
  const applyCoupon = useApplyCoupon()
  const removeCoupon = useRemoveCoupon()
  const [code, setCode] = useState('')
  const navigate = useNavigate()

  if (!cart.data) return null
  const c = cart.data
  const items = c.items ?? []

  function onApply(event: FormEvent) {
    event.preventDefault()
    if (code.trim()) applyCoupon.mutate(code.trim(), { onSuccess: () => setCode('') })
  }

  return (
    <section className="cart-page">
      <h1>{t('cart.title')}</h1>
      {items.length === 0 && <p>{t('cart.empty')}</p>}

      <ul className="cart-lines">
        {items.map((line) => (
          <li key={line.itemId}>
            <div>
              <Link to={`/products/${line.productId}`}>{line.productName}</Link>
              {line.size && <span className="muted"> · {t('cart.size', { size: line.size })}</span>}
              {!line.available && <span className="badge">{t('cart.unavailable')}</span>}
            </div>
            <label>
              <span className="visually-hidden">{t('cart.quantity')}</span>
              <select
                value={line.quantity}
                onChange={(e) => update.mutate({ itemId: line.itemId, quantity: Number(e.target.value) })}
              >
                {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
            <span>{formatMoney(line.lineTotal)}</span>
            <button type="button" onClick={() => update.mutate({ itemId: line.itemId, quantity: 0 })}>
              {t('cart.remove')}
            </button>
          </li>
        ))}
      </ul>
      {update.isError && <p role="alert">{update.error.message}</p>}

      {items.length > 0 && (
        <>
          {c.couponCode ? (
            <p>
              {t('cart.discount', { code: c.couponCode })}: −{formatMoney(c.discount)}{' '}
              <button type="button" onClick={() => removeCoupon.mutate()}>
                {t('cart.removeCoupon')}
              </button>
            </p>
          ) : (
            <form onSubmit={onApply} className="coupon">
              <label>
                {t('cart.coupon')}
                <input value={code} onChange={(e) => setCode(e.target.value)} />
              </label>
              <button type="submit">{t('cart.applyCoupon')}</button>
              {applyCoupon.isError && <span role="alert">{applyCoupon.error.message}</span>}
            </form>
          )}
          <dl className="totals">
            <dt>{t('cart.subtotal')}</dt>
            <dd>{formatMoney(c.subtotal)}</dd>
            <dt>{t('cart.total')}</dt>
            <dd>
              <strong>{formatMoney(c.total)}</strong>
            </dd>
          </dl>
          <button type="button" disabled={items.some((l) => !l.available)} onClick={() => navigate('/checkout')}>
            {t('cart.checkout')}
          </button>
        </>
      )}
    </section>
  )
}
