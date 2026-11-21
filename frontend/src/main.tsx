import React from "react"
import ReactDOM from "react-dom/client"
import App from "./App"
import "./index.css"
import { QueryClientProvider } from "@tanstack/react-query"
import { queryClient } from "./shared/api/query-client"
import { initializeAuth } from "./shared/auth/keycloak"

// Finish processing the OIDC callback before BrowserRouter reads the URL.
document.getElementById("root")!.textContent = "Đang kiểm tra phiên đăng nhập…"
await initializeAuth()
ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>
)
