import { BrowserRouter, Route, Routes } from "react-router-dom"

import { AuthPage } from "@/routes/auth-page"
import { ComponentShowcase } from "@/routes/component-showcase"
import { EscrowPage } from "@/routes/escrow-page"
import { HomePage } from "@/routes/home-page"
import { HowItWorksPage } from "@/routes/how-it-works-page"
import { ListingDetailPage } from "@/routes/listing-detail-page"
import { MarketplacePage } from "@/routes/marketplace-page"
import { MobileComponentShowcase } from "@/routes/mobile-component-showcase"
import { PricesPage } from "@/routes/prices-page"
import { PublicReputationPage } from "@/routes/public-reputation-page"
import { ToastProvider } from "@/shared/ui/toast"
import {
  RequireAuth,
  RequireRole,
} from "@/features/auth/components/auth-guards"
import { AccountPage } from "@/routes/account-page"
import { APP_ROLES } from "@/shared/auth/auth-store"
import { RequireOrganizationProfile } from "@/features/organization/components/organization-guards"
import { OrganizationOnboardingPage } from "@/routes/organization-onboarding-page"

function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route
              path="/onboarding"
              element={<OrganizationOnboardingPage />}
            />
            <Route element={<RequireOrganizationProfile />}>
              <Route path="/account" element={<AccountPage />} />
              <Route element={<RequireRole roles={APP_ROLES} />}>
                <Route path="/workspace" element={<AccountPage />} />
              </Route>
            </Route>
          </Route>
          <Route path="/" element={<HomePage />} />
          <Route path="/listing" element={<MarketplacePage />} />
          <Route path="/listings/:id" element={<ListingDetailPage />} />
          <Route path="/prices" element={<PricesPage />} />
          <Route
            path="/reputation/:userId"
            element={<PublicReputationPage />}
          />
          <Route path="/how-it-works" element={<HowItWorksPage />} />
          <Route path="/escrow" element={<EscrowPage />} />
          <Route path="/login" element={<AuthPage />} />
          <Route
            path="/register"
            element={<AuthPage defaultView="register" />}
          />
          <Route
            path="/forgot-password"
            element={<AuthPage defaultView="forgot_password" />}
          />
          <Route path="/components" element={<ComponentShowcase />} />
          <Route
            path="/components/mobile"
            element={<MobileComponentShowcase />}
          />
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  )
}

export default App
