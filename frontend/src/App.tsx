import { ComponentShowcase } from "@/routes/component-showcase"
import { MobileComponentShowcase } from "@/routes/mobile-component-showcase"
import { ToastProvider } from "@/shared/ui/toast"

function App() {
  const isMobilePage = new URLSearchParams(window.location.search).has("mobile")

  return (
    <ToastProvider>
      {isMobilePage ? <MobileComponentShowcase /> : <ComponentShowcase />}
    </ToastProvider>
  )
}

export default App
