import { useState } from "react"
import { Link } from "react-router-dom"
import { useAuthStore } from "@/shared/auth/auth-store"
import { logout } from "@/shared/auth/keycloak"
import { useMyProfile } from "../hooks/use-my-profile"
import { Button } from "@/shared/ui/button"

export function AccountMenu() {
  const { authenticated, email } = useAuthStore()
  const profile = useMyProfile()
  const [error, setError] = useState(false)
  if (!authenticated)
    return (
      <div className="flex items-center gap-4 text-sm">
        <Link to="/login">Đăng nhập</Link>
        <Link to="/register" className="text-primary">
          Đăng ký
        </Link>
      </div>
    )
  return (
    <div className="flex flex-wrap items-center gap-3 text-sm">
      <Link
        to="/account"
        className="max-w-52 truncate"
        title={profile.data?.organizationName || email}
      >
        {profile.data?.organizationName || email || "Tài khoản"}
      </Link>
      <Button
        variant="outline"
        onClick={() => void logout().catch(() => setError(true))}
      >
        Đăng xuất
      </Button>
      {error && (
        <span role="alert" className="text-danger">
          Chưa đăng xuất được. Vui lòng thử lại.
        </span>
      )}
    </div>
  )
}
