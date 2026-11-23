import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAddToCart, useApplyCoupon, useCart, useRemoveCoupon, useUpdateCartItem } from '../api/cart'
import { EmptyState, ErrorState, Loading } from '../components/Status'
import { formatMoney, t } from '../i18n'

export function CartPage() {
  const cart = useCart()
  const update = useUpdateCartItem()
  const applyCoupon = useApplyCoupon()
  const removeCoupon = useRemoveCoupon()
  const addBack = useAddToCart()
  const [code, setCode] = useState('')
  const [removed, setRemoved] = useState<{ variantId: number; quantity: number; name: string } | null>(null)
  const navigate = useNavigate()

  if (cart.isError) return <ErrorState onRetry={() => void cart.refetch()} />
  if (!cart.data) return <Loading />
  const c = cart.data
  const items = c.items ?? []

  function onApply(event: FormEvent) {
    event.preventDefault()
    if (code.trim()) applyCoupon.mutate(code.trim(), { onSuccess: () => setCode('') })
  }

  return (
    <section className="cart-page">
      <h1>{t('cart.title')}</h1>
      {removed && (
        <p className="notice" role="status">
          {t('cart.removed', { name: removed.name })}{' '}
          <button
            type="button"
            className="link-button"
            disabled={addBack.isPending}
            onClick={() =>
              addBack.mutate(
                { variantId: removed.variantId, quantity: removed.quantity },
                { onSuccess: () => setRemoved(null) },
              )
            }
          >
            {t('cart.undo')}
          </button>
        </p>
      )}
      {items.length === 0 && (
        <EmptyState
          title={t('cart.empty')}
          action={
            <Link to="/shop" className="button">
              {t('cart.continue')}
            </Link>
          }
        >
          {t('cart.emptyHelp')}
        </EmptyState>
      )}

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
            <button
              type="button"
              className="button-secondary"
              aria-label={t('cart.removeItem', { name: line.productName })}
              onClick={() =>
                update.mutate(
                  { itemId: line.itemId, quantity: 0 },
                  {
                    onSuccess: () =>
                      setRemoved({ variantId: line.variantId, quantity: line.quantity, name: line.productName }),
                  },
                )
              }
            >
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
              <button type="button" className="button-secondary" onClick={() => removeCoupon.mutate()}>
                {t('cart.removeCoupon')}
              </button>
            </p>
          ) : (
            <form onSubmit={onApply} className="coupon">
              <label>
                {t('cart.coupon')}
                <input value={code} onChange={(e) => setCode(e.target.value)} />
              </label>
              <button type="submit" className="button-secondary">
                {t('cart.applyCoupon')}
              </button>
              {applyCoupon.isError && <span role="alert">{applyCoupon.error.message}</span>}
            </form>
          )}
          <dl className="totals">
            <dt>{t('cart.subtotal')}</dt>
            <dd>{formatMoney(c.subtotal)}</dd>
            <dt>{t('cart.total')}</dt>
            <dd className="total">{formatMoney(c.total)}</dd>
          </dl>
          <div className="actions">
            <button type="button" disabled={items.some((l) => !l.available)} onClick={() => navigate('/checkout')}>
              {t('cart.checkout')}
            </button>
            <Link to="/shop" className="button button-secondary">
              {t('cart.continue')}
            </Link>
          </div>
          {items.some((l) => !l.available) && <p className="hint">{t('cart.fixUnavailable')}</p>}
        </>
      )}
    </section>
  )
}
