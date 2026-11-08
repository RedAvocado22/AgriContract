import { useState } from "react"
import { Link } from "react-router-dom"
import { login, register } from "@/shared/auth/keycloak"
import { useAuthStore } from "@/shared/auth/auth-store"
import { Button } from "@/shared/ui/button"
import { Banner } from "@/shared/ui/banner"

export function AuthAction({
  mode,
}: {
  mode: "login" | "register" | "forgot_password"
}) {
  const { authenticated, status, error: authError } = useAuthStore()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const label =
    mode === "register"
      ? "Đăng ký"
      : mode === "forgot_password"
        ? "Quên mật khẩu"
        : "Đăng nhập"
  async function start() {
    setBusy(true)
    setError(undefined)
    try {
      if (mode === "register") await register()
      // Keycloak JS has no reset-credentials API. Enter the server login flow,
      // then use its session-bound "forgot password" link (also works on KC 24).
      else
        await login({
          redirectUri: `${location.origin}/account`,
          ...(mode === "forgot_password" ? { prompt: "login" } : {}),
        })
    } catch {
      setError("Không thể mở hệ thống đăng nhập. Vui lòng thử lại.")
      setBusy(false)
    }
  }
  return (
    <div className="flex flex-col gap-5">
      <h1 className="text-2xl">{label}</h1>
      <p className="text-sm text-text-secondary">
        {mode === "forgot_password"
          ? "Mở trang đăng nhập, chọn “Quên mật khẩu?” và làm theo hướng dẫn qua email."
          : "Bạn sẽ được chuyển đến hệ thống xác thực của AgriContract."}
      </p>
      {(authError || error) && (
        <Banner variant="danger">{authError || error}</Banner>
      )}
      {status === "error" ? (
        <Button onClick={() => location.reload()}>Thử kết nối lại</Button>
      ) : (
        <Button
          size="lg"
          disabled={busy || status !== "ready"}
          onClick={() => void start()}
        >
          {busy
            ? "Đang chuyển hướng…"
            : mode === "forgot_password"
              ? "Mở trang khôi phục tài khoản"
              : label}
        </Button>
      )}
      {authenticated && (
        <Link to="/account" className="text-primary">
          Về tài khoản đăng nhập
        </Link>
      )}
      <div className="flex flex-wrap gap-4 text-sm text-primary">
        {mode !== "login" && <Link to="/login">Đăng nhập</Link>}
        {mode !== "register" && <Link to="/register">Đăng ký</Link>}
        {mode !== "forgot_password" && (
          <Link to="/forgot-password">Quên mật khẩu?</Link>
        )}
      </div>
    </div>
  )
}
