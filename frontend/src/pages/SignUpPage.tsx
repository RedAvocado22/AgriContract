import { Link, Navigate } from 'react-router-dom'

import { useAuthStore } from '../stores/authStore'

export function SignUpPage() {
  const user = useAuthStore((state) => state.user)

  if (user) {
    return <Navigate to="/dashboard" replace />
  }

  return (
    <section className="auth-page">
      <div className="auth-card auth-card--wide">
        <div className="auth-card__hero">
          <div className="brand-brandmark">eco</div>
          <h1>Đăng ký tài khoản</h1>
          <p>
            Tài khoản và vai trò được quản lý trong Keycloak. Sau khi tài khoản
            được tạo và gán role, bạn có thể đăng nhập tại AgriContract.
          </p>
        </div>

        <div className="notice-panel">
          <span className="material-symbols-outlined">info</span>
          <p>
            Với cấu hình hiện tại, frontend không tự tạo tài khoản Keycloak. Hãy
            dùng user đã được seed hoặc nhờ quản trị viên tạo tài khoản và gán
            role SELLER, BUYER hoặc ADMIN.
          </p>
        </div>

        <Link className="primary-button primary-button--full" to="/login">
          Quay lại đăng nhập
        </Link>
      </div>
    </section>
  )
}
