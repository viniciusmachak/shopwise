import { useState, type FormEvent } from 'react'
import type { AddressForm, AddressView } from '../api/client'
import { t } from '../i18n'

const empty: AddressForm = { recipient: '', line1: '', line2: '', city: '', state: '', postalCode: '', country: 'US' }

export function toForm(address: AddressView): AddressForm {
  return {
    recipient: address.recipient,
    line1: address.line1,
    line2: address.line2 ?? '',
    city: address.city,
    state: address.state,
    postalCode: address.postalCode,
    country: address.country,
  }
}

interface Props {
  initial?: AddressForm
  submitLabel: string
  pending: boolean
  error?: string
  onSubmit: (form: AddressForm) => void
  onCancel?: () => void
}

export function AddressFields({ initial, submitLabel, pending, error, onSubmit, onCancel }: Props) {
  const [form, setForm] = useState<AddressForm>(initial ?? empty)
  const field = (key: keyof AddressForm) => ({
    value: form[key] ?? '',
    onChange: (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value })),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    onSubmit(form)
  }

  return (
    <form onSubmit={submit} className="form address-form">
      <label>
        {t('address.recipient')}
        <input required autoComplete="name" {...field('recipient')} />
      </label>
      <label>
        {t('address.line1')}
        <input required autoComplete="address-line1" {...field('line1')} />
      </label>
      <label>
        {t('address.line2')}
        <input autoComplete="address-line2" {...field('line2')} />
      </label>
      <label>
        {t('address.city')}
        <input required autoComplete="address-level2" {...field('city')} />
      </label>
      <label>
        {t('address.state')}
        <input required autoComplete="address-level1" {...field('state')} />
      </label>
      <label>
        {t('address.postalCode')}
        <input required autoComplete="postal-code" {...field('postalCode')} />
      </label>
      <label>
        {t('address.country')}
        <input required maxLength={2} autoComplete="country" {...field('country')} />
      </label>
      {error && <p role="alert">{error}</p>}
      <div className="actions">
        <button type="submit" disabled={pending}>
          {submitLabel}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel}>
            {t('common.cancel')}
          </button>
        )}
      </div>
    </form>
  )
}

export function AddressText({ address }: { address: AddressView | OrderAddress }) {
  return (
    <address>
      {address.recipient}
      <br />
      {address.line1}
      {address.line2 && (
        <>
          <br />
          {address.line2}
        </>
      )}
      <br />
      {address.city}, {address.state} {address.postalCode}
      <br />
      {address.country}
    </address>
  )
}

interface OrderAddress {
  recipient: string
  line1: string
  line2?: string | null
  city: string
  state: string
  postalCode: string
  country: string
}
