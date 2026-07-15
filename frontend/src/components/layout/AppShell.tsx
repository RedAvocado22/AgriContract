import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'

import { useAuthStore } from '../../stores/authStore'

const privateNavItems = [
  { to: '/dashboard', label: 'Tổng quan', icon: 'dashboard' },
  { to: '/listings', label: 'Tin hàng', icon: 'list_alt' },
  { to: '/contracts', label: 'Hợp đồng', icon: 'description' },
  { to: '/escrow', label: 'Ký quỹ', icon: 'account_balance_wallet' },
]

const publicNavItems = [{ to: '/listings', label: 'Tin hàng', icon: 'list_alt' }]

interface AppShellProps {
  publicMode?: boolean
}

export function AppShell({ publicMode = false }: AppShellProps) {
  const { user, logout } = useAuthStore()
  const navigate = useNavigate()
  const normalizedRole = user?.role?.trim().toUpperCase()
  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" to="/listings" aria-label="Trang chủ AgriContract">
          <div className="brand__mark">eco</div>
          <div>
            <strong>AgriContract</strong>
            <span>Nông sản B2B có ký quỹ bảo chứng</span>
          </div>
        </Link>

        <div className="topbar__actions">
          {publicMode ? (
            <Link className="primary-button" to="/login">
              <span className="material-symbols-outlined">login</span>
              Đăng nhập
            </Link>
          ) : (
            <>
              <button className="icon-button" type="button" aria-label="Thông báo">
                <span className="material-symbols-outlined">notifications</span>
              </button>
              <div className="user-pill">
                <span className="material-symbols-outlined">account_circle</span>
                <div>
                  <strong>{user?.name ?? 'Người dùng'}</strong>
                  <span>{user?.organization ?? 'AgriContract'}</span>
                </div>
              </div>

              <button className="ghost-button" type="button" onClick={handleLogout}>
                Đăng xuất
              </button>
            </>
          )}
        </div>
      </header>

      <div className="shell-grid">
        <aside className="sidebar">
          <div className="sidebar__group">
            {(publicMode ? publicNavItems : privateNavItems).map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  isActive ? 'sidebar-link sidebar-link--active' : 'sidebar-link'
                }
              >
                <span className="material-symbols-outlined">{item.icon}</span>
                {item.label}
              </NavLink>
            ))}

            {!publicMode && normalizedRole === 'SELLER' ? (
              <NavLink to="/listings/create" className="sidebar-link">
                <span className="material-symbols-outlined">add_circle</span>
                Đăng tin hàng
              </NavLink>
            ) : null}
          </div>
        </aside>

        <main className="shell-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
