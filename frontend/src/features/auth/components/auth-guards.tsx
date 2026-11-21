import { useEffect, useState, type ReactNode } from "react"
import { Outlet } from "react-router-dom"
import { useAuthStore, type AppRole } from "@/shared/auth/auth-store"
import { login } from "@/shared/auth/keycloak"
import { Banner } from "@/shared/ui/banner"
import { Button } from "@/shared/ui/button"

export function RequireAuth({ children }: { children?: ReactNode }) {
  const { authenticated, status, error } = useAuthStore()
  const [redirectError, setRedirectError] = useState(false)
  useEffect(() => {
    if (status === "ready" && !authenticated) {
      void login({ redirectUri: window.location.href }).catch(() =>
        setRedirectError(true)
      )
    }
  }, [status, authenticated])
  if (status === "error" || redirectError)
    return (
      <div className="p-6">
        <Banner variant="danger">
          {error || "Không thể mở trang đăng nhập."}
        </Banner>
        <Button onClick={() => window.location.reload()}>Thử lại</Button>
      </div>
    )
  if (status !== "ready" || !authenticated)
    return (
      <p role="status" className="p-6">
        Đang kiểm tra phiên đăng nhập…
      </p>
    )
  return children ?? <Outlet />
}

export function RequireRole({
  roles,
  children,
}: {
  roles: readonly AppRole[]
  children?: ReactNode
}) {
  const granted = useAuthStore((state) => state.roles)
  return (
    <RequireAuth>
      {granted.some((role) => roles.includes(role)) ? (
        children ?? <Outlet />
      ) : (
        <div className="p-6">
          <Banner variant="warning">
            Tài khoản chưa có vai trò phù hợp để truy cập trang này.
          </Banner>
          <a href="/account">Về tài khoản đăng nhập</a>
        </div>
      )}
    </RequireAuth>
  )
}
