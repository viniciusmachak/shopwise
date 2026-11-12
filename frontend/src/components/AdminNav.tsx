import { NavLink } from 'react-router'
import { t } from '../i18n'

export function AdminNav() {
  return (
    <nav aria-label={t('nav.admin')} className="admin-nav">
      <NavLink to="/admin/products">{t('admin.products')}</NavLink>
      <NavLink to="/admin/orders">{t('admin.orders')}</NavLink>
      <NavLink to="/admin/import">{t('import.title')}</NavLink>
    </nav>
  )
}
