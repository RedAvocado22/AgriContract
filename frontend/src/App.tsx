import { BrowserRouter, Route, Routes } from "react-router-dom"

import { AuthPage } from "@/routes/auth-page"
import { ComponentShowcase } from "@/routes/component-showcase"
import { HomePage } from "@/routes/home-page"
import { MobileComponentShowcase } from "@/routes/mobile-component-showcase"
import { ToastProvider } from "@/shared/ui/toast"

function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<AuthPage />} />
          <Route path="/register" element={<AuthPage defaultView="register" />} />
          <Route path="/forgot-password" element={<AuthPage defaultView="forgot_password" />} />
          <Route path="/components" element={<ComponentShowcase />} />
          <Route path="/components/mobile" element={<MobileComponentShowcase />} />
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  )
}

export default App
