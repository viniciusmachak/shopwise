import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api, type AddressForm, type AddressView } from '../api/client'
import { sessionKey, useSession } from '../api/session'
import { AddressFields, AddressText, toForm } from '../components/AddressFields'
import { t } from '../i18n'

const addressesKey = ['addresses'] as const

export function AccountPage() {
  const session = useSession()
  const me = session.data?.customer
  if (!me) return null
  return (
    <section className="account-page">
      <h1>{t('account.title')}</h1>
      <p className="muted">{me.email}</p>
      <ProfileForm name={me.name} />
      <Addresses />
    </section>
  )
}

function ProfileForm({ name: initial }: { name: string }) {
  const client = useQueryClient()
  const [name, setName] = useState(initial)
  const save = useMutation({
    mutationFn: api.updateProfile,
    onSuccess: () => client.invalidateQueries({ queryKey: sessionKey }),
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    save.mutate(name)
  }

  return (
    <form onSubmit={onSubmit} className="form">
      <label>
        {t('auth.name')}
        <input required value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      <button type="submit" disabled={save.isPending}>
        {t('common.save')}
      </button>
      <span aria-live="polite">{save.isSuccess && t('common.saved')}</span>
    </form>
  )
}

function Addresses() {
  const client = useQueryClient()
  const addresses = useQuery({ queryKey: addressesKey, queryFn: api.addresses })
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<number | null>(null)
  const refresh = () => client.invalidateQueries({ queryKey: addressesKey })

  const add = useMutation({ mutationFn: api.addAddress, onSuccess: () => { setAdding(false); void refresh() } })
  const update = useMutation({
    mutationFn: ({ id, form }: { id: number; form: AddressForm }) => api.updateAddress(id, form),
    onSuccess: () => { setEditing(null); void refresh() },
  })
  const remove = useMutation({ mutationFn: api.deleteAddress, onSuccess: refresh })
  const makeDefault = useMutation({ mutationFn: api.makeDefaultAddress, onSuccess: refresh })

  return (
    <section>
      <h2>{t('account.addresses')}</h2>
      {addresses.data?.length === 0 && !adding && <p>{t('account.noAddresses')}</p>}
      <ul className="address-list">
        {addresses.data?.map((a: AddressView) => (
          <li key={a.id}>
            {editing === a.id ? (
              <AddressFields
                initial={toForm(a)}
                submitLabel={t('common.save')}
                pending={update.isPending}
                error={update.error?.message}
                onSubmit={(form) => update.mutate({ id: a.id, form })}
                onCancel={() => setEditing(null)}
              />
            ) : (
              <>
                <AddressText address={a} />
                {a.isDefault ? (
                  <span className="badge">{t('account.default')}</span>
                ) : (
                  <button type="button" onClick={() => makeDefault.mutate(a.id)}>
                    {t('account.makeDefault')}
                  </button>
                )}
                <button type="button" onClick={() => setEditing(a.id)}>
                  {t('common.edit')}
                </button>
                <button type="button" onClick={() => remove.mutate(a.id)}>
                  {t('common.delete')}
                </button>
              </>
            )}
          </li>
        ))}
      </ul>
      {adding ? (
        <AddressFields
          submitLabel={t('account.saveAddress')}
          pending={add.isPending}
          error={add.error?.message}
          onSubmit={(form) => add.mutate(form)}
          onCancel={() => setAdding(false)}
        />
      ) : (
        <button type="button" onClick={() => setAdding(true)}>
          {t('account.addAddress')}
        </button>
      )}
    </section>
  )
}
