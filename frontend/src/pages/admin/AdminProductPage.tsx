import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { api, type AdminProductView, type AdminVariant, type ProductForm } from '../../api/client'
import { AdminNav } from '../../components/AdminNav'
import { t } from '../../i18n'

export function AdminProductPage() {
  const params = useParams()
  const isNew = params.id === undefined
  const id = Number(params.id)
  const product = useQuery({
    queryKey: ['admin', 'product', id],
    queryFn: () => api.admin.product(id),
    enabled: !isNew,
  })

  if (!isNew && !product.data) return product.isError ? <p role="alert">{t('product.notFound')}</p> : null

  return (
    <section className="admin-page">
      <AdminNav />
      <h1>{isNew ? t('admin.newProduct') : product.data!.name}</h1>
      <ProductEditor key={product.data?.id ?? 'new'} product={product.data} />
      {product.data && <Variants product={product.data} />}
    </section>
  )
}

function useSaveProduct(product?: AdminProductView) {
  const client = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: (form: ProductForm) =>
      product ? api.admin.updateProduct(product.id, form) : api.admin.createProduct(form),
    onSuccess: (saved) => {
      client.setQueryData(['admin', 'product', saved.id], saved)
      void client.invalidateQueries({ queryKey: ['admin', 'products'] })
      if (!product) navigate(`/admin/products/${saved.id}`, { replace: true })
    },
  })
}

function ProductEditor({ product }: { product?: AdminProductView }) {
  const client = useQueryClient()
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.categories })
  const save = useSaveProduct(product)
  const [name, setName] = useState(product?.name ?? '')
  const [brand, setBrand] = useState(product?.brand ?? '')
  const [category, setCategory] = useState(product?.category ?? '')
  const [price, setPrice] = useState(product ? String(product.price) : '')
  const [description, setDescription] = useState(product?.description ?? '')
  const [imageUrl, setImageUrl] = useState(product?.imageUrl ?? '')
  const [attributes, setAttributes] = useState(JSON.stringify(product?.attributes ?? {}, null, 2))
  const [attributesError, setAttributesError] = useState<string | null>(null)

  const toggle = useMutation({
    mutationFn: () => (product!.active ? api.admin.archive(product!.id) : api.admin.restore(product!.id)),
    onSuccess: (saved) => {
      client.setQueryData(['admin', 'product', saved.id], saved)
      void client.invalidateQueries({ queryKey: ['admin', 'products'] })
    },
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    let parsed: Record<string, unknown>
    try {
      parsed = JSON.parse(attributes || '{}') as Record<string, unknown>
    } catch {
      setAttributesError(t('admin.attributesInvalid'))
      return
    }
    setAttributesError(null)
    save.mutate({ name, brand, category, price: Number(price), description, imageUrl: imageUrl || null, attributes: parsed })
  }

  return (
    <form onSubmit={onSubmit} className="form">
      <label>
        {t('admin.name')}
        <input required value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      <label>
        {t('admin.brand')}
        <input required value={brand} onChange={(e) => setBrand(e.target.value)} />
      </label>
      <label>
        {t('admin.category')}
        <select required value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">{t('admin.chooseCategory')}</option>
          {categories.data?.map((c) => (
            <option key={c.slug} value={c.slug}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t('admin.price')}
        <input required type="number" min={0} step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} />
      </label>
      <label>
        {t('admin.description')}
        <textarea required rows={4} value={description} onChange={(e) => setDescription(e.target.value)} />
      </label>
      <label>
        {t('admin.imageUrl')}
        <input type="url" value={imageUrl} onChange={(e) => setImageUrl(e.target.value)} />
      </label>
      <label>
        {t('admin.attributes')}
        <textarea rows={4} value={attributes} onChange={(e) => setAttributes(e.target.value)} />
      </label>
      {attributesError && <p role="alert">{attributesError}</p>}
      {save.isError && <p role="alert">{save.error.message}</p>}
      <div className="actions">
        <button type="submit" disabled={save.isPending}>
          {product ? t('common.save') : t('admin.createProduct')}
        </button>
        {product && (
          <button type="button" disabled={toggle.isPending} onClick={() => toggle.mutate()}>
            {product.active ? t('admin.archive') : t('admin.restore')}
          </button>
        )}
        <span aria-live="polite">{save.isSuccess && product && t('common.saved')}</span>
      </div>
      {product && !product.active && <p className="muted">{t('admin.archivedHint')}</p>}
    </form>
  )
}

function Variants({ product }: { product: AdminProductView }) {
  const client = useQueryClient()
  const update = (saved: AdminProductView) => {
    client.setQueryData(['admin', 'product', saved.id], saved)
    void client.invalidateQueries({ queryKey: ['admin', 'products'] })
  }
  const add = useMutation({
    mutationFn: ({ sku, size, color, stock }: { sku: string; size: string; color: string; stock: number }) =>
      api.admin.addVariant(product.id, { sku, size: size || null, color: color || null, priceOverride: null }, stock),
    onSuccess: update,
  })
  const [sku, setSku] = useState('')
  const [size, setSize] = useState('')
  const [color, setColor] = useState('')
  const [stock, setStock] = useState('0')

  function onAdd(event: FormEvent) {
    event.preventDefault()
    add.mutate(
      { sku, size, color, stock: Number(stock) },
      { onSuccess: () => { setSku(''); setSize(''); setColor(''); setStock('0') } },
    )
  }

  return (
    <section>
      <h2>{t('admin.variants')}</h2>
      <table className="admin-table">
        <thead>
          <tr>
            <th>{t('admin.sku')}</th>
            <th>{t('product.size')}</th>
            <th>{t('admin.color')}</th>
            <th>{t('admin.stock')}</th>
          </tr>
        </thead>
        <tbody>
          {product.variants.map((v) => (
            <VariantRow key={v.id} variant={v} onSaved={update} />
          ))}
        </tbody>
      </table>
      <form onSubmit={onAdd} className="form inline-form">
        <label>
          {t('admin.sku')}
          <input required value={sku} onChange={(e) => setSku(e.target.value)} />
        </label>
        <label>
          {t('product.size')}
          <input value={size} onChange={(e) => setSize(e.target.value)} />
        </label>
        <label>
          {t('admin.color')}
          <input value={color} onChange={(e) => setColor(e.target.value)} />
        </label>
        <label>
          {t('admin.stock')}
          <input type="number" min={0} value={stock} onChange={(e) => setStock(e.target.value)} />
        </label>
        <button type="submit" disabled={add.isPending}>
          {t('admin.addVariant')}
        </button>
        {add.isError && <p role="alert">{add.error.message}</p>}
      </form>
    </section>
  )
}

function VariantRow({ variant, onSaved }: { variant: AdminVariant; onSaved: (p: AdminProductView) => void }) {
  const [quantity, setQuantity] = useState(String(variant.stock))
  const save = useMutation({ mutationFn: () => api.admin.setStock(variant.id, Number(quantity)), onSuccess: onSaved })
  return (
    <tr>
      <td>{variant.sku}</td>
      <td>{variant.size ?? '—'}</td>
      <td>{variant.color ?? '—'}</td>
      <td>
        <input
          type="number"
          min={0}
          aria-label={t('admin.stockFor', { sku: variant.sku })}
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
        />
        <button
          type="button"
          disabled={save.isPending || Number(quantity) === variant.stock}
          onClick={() => save.mutate()}
        >
          {t('admin.saveStock')}
        </button>
        {save.isError && <span role="alert">{save.error.message}</span>}
      </td>
    </tr>
  )
}
