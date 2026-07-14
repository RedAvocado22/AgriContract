import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { AuthBootstrap } from './auth/AuthBootstrap'
import { AppShell } from './components/layout/AppShell'
import { ContractsPage } from './pages/ContractsPage'
import { CreateListingPage } from './pages/CreateListingPage'
import { DashboardPage } from './pages/DashboardPage'
import { EscrowPage } from './pages/EscrowPage'
import { ListingDetailPage } from './pages/ListingDetailPage'
import { ListingsPage } from './pages/ListingsPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterProfilePage } from './pages/RegisterProfilePage'
import { useAuthStore } from './stores/authStore'

const queryClient = new QueryClient()

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

function SellerOnlyRoute({ children }: { children: React.ReactNode }) {
  const user = useAuthStore((state) => state.user)
  const normalizedRole = user?.role?.trim().toUpperCase()

  if (normalizedRole !== 'SELLER') {
    return <Navigate to="/listings" replace />
  }

  return children
}

function PublicListingsLayout() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)

  return <AppShell publicMode={!isAuthenticated} />
}

export function AppRouter() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthBootstrap>
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
              <Route path="/contracts" element={<ContractsPage />} />
              <Route path="/escrow" element={<EscrowPage />} />
            </Route>
          </Routes>
        </AuthBootstrap>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
