import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { AuthBootstrap } from './auth/AuthBootstrap'
import { ApiErrorNotifier } from './components/feedback/ApiErrorNotifier'
import { AppShell } from './components/layout/AppShell'
import { useAuthStore } from './stores/authStore'

const queryClient = new QueryClient()
const AdminArbitratePage = lazy(() => import('./pages/AdminArbitratePage').then((module) => ({ default: module.AdminArbitratePage })))
const ContractDetailPage = lazy(() => import('./pages/ContractDetailPage').then((module) => ({ default: module.ContractDetailPage })))
const ContractsPage = lazy(() => import('./pages/ContractsPage').then((module) => ({ default: module.ContractsPage })))
const CreateListingPage = lazy(() => import('./pages/CreateListingPage').then((module) => ({ default: module.CreateListingPage })))
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })))
const EscrowPage = lazy(() => import('./pages/EscrowPage').then((module) => ({ default: module.EscrowPage })))
const ListingDetailPage = lazy(() => import('./pages/ListingDetailPage').then((module) => ({ default: module.ListingDetailPage })))
const ListingsPage = lazy(() => import('./pages/ListingsPage').then((module) => ({ default: module.ListingsPage })))
const LoginPage = lazy(() => import('./pages/LoginPage').then((module) => ({ default: module.LoginPage })))
const RegisterProfilePage = lazy(() => import('./pages/RegisterProfilePage').then((module) => ({ default: module.RegisterProfilePage })))
const SellerListingsPage = lazy(() => import('./pages/SellerListingsPage').then((module) => ({ default: module.SellerListingsPage })))

function ProtectedLayout() {
  const user = useAuthStore((state) => state.user)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const profileStatus = useAuthStore((state) => state.profileStatus)

  if (isAuthenticated && profileStatus === 'missing') {
    return <Navigate to="/register-profile" replace />
  }

  if (!user) {
    return <Navigate to="/login" replace />
  }

  return <AppShell />
}

function SellerOnlyRoute({ children }: { children: ReactNode }) {
  const user = useAuthStore((state) => state.user)
  const normalizedRole = user?.role?.trim().toUpperCase()

  if (normalizedRole !== 'SELLER') {
    return <Navigate to="/listings" replace />
  }

  return children
}

function AdminOnlyRoute({ children }: { children: ReactNode }) {
  const role = useAuthStore((state) => state.user?.role)
  return role?.trim().toUpperCase() === 'ADMIN' ? children : <Navigate to="/dashboard" replace />
}

function PublicListingsLayout() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)

  return <AppShell publicMode={!isAuthenticated} />
}

export function AppRouter() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ApiErrorNotifier />
        <AuthBootstrap>
          <Suspense fallback={<div className="empty-state">Đang tải trang...</div>}>
            <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register-profile" element={<RegisterProfilePage />} />
            <Route path="/" element={<Navigate to="/listings" replace />} />
            <Route element={<PublicListingsLayout />}>
              <Route path="/listings" element={<ListingsPage />} />
              <Route path="/listings/:listingId" element={<ListingDetailPage />} />
            </Route>
            <Route element={<ProtectedLayout />}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route
                path="/listings/create"
                element={
                  <SellerOnlyRoute>
                    <CreateListingPage />
                  </SellerOnlyRoute>
                }
              />
              <Route
                path="/listings/mine"
                element={
                  <SellerOnlyRoute>
                    <SellerListingsPage />
                  </SellerOnlyRoute>
                }
              />
              <Route path="/contracts" element={<ContractsPage />} />
              <Route path="/contracts/:contractId" element={<ContractDetailPage />} />
              <Route path="/escrow" element={<EscrowPage />} />
              <Route
                path="/admin/arbitrate/:contractId"
                element={
                  <AdminOnlyRoute>
                    <AdminArbitratePage />
                  </AdminOnlyRoute>
                }
              />
            </Route>
            </Routes>
          </Suspense>
        </AuthBootstrap>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
