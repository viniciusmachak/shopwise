import { Route, Routes } from 'react-router'
import { Layout } from './components/Layout'
import { RequireAdmin } from './components/RequireAdmin'
import { RequireSignIn } from './components/RequireSignIn'
import { AdminProductPage } from './pages/admin/AdminProductPage'
import { AdminProductsPage } from './pages/admin/AdminProductsPage'
import { AccountPage } from './pages/AccountPage'
import { CartPage } from './pages/CartPage'
import { CatalogPage } from './pages/CatalogPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { OrderPage, OrdersPage } from './pages/OrdersPage'
import { ForgotPasswordPage, ResetPasswordPage } from './pages/PasswordResetPages'
import { ProductPage } from './pages/ProductPage'
import { SignInPage } from './pages/SignInPage'
import { SignUpPage } from './pages/SignUpPage'

export function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<CatalogPage />} />
        <Route path="/products/:id" element={<ProductPage />} />
        <Route path="/cart" element={<CartPage />} />
        <Route path="/sign-in" element={<SignInPage />} />
        <Route path="/sign-up" element={<SignUpPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/account" element={<RequireSignIn><AccountPage /></RequireSignIn>} />
        <Route path="/checkout" element={<RequireSignIn><CheckoutPage /></RequireSignIn>} />
        <Route path="/orders" element={<RequireSignIn><OrdersPage /></RequireSignIn>} />
        <Route path="/orders/:id" element={<RequireSignIn><OrderPage /></RequireSignIn>} />
        <Route path="/admin/products" element={<RequireAdmin><AdminProductsPage /></RequireAdmin>} />
        <Route path="/admin/products/new" element={<RequireAdmin><AdminProductPage /></RequireAdmin>} />
        <Route path="/admin/products/:id" element={<RequireAdmin><AdminProductPage /></RequireAdmin>} />
      </Routes>
    </Layout>
  )
}
