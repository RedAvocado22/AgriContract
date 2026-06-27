import { NavLink, Outlet } from 'react-router-dom'

import { useAuthStore } from '../../stores/authStore'

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
  { to: '/listings', label: 'Listings', icon: 'list_alt' },
  { to: '/contracts', label: 'Contracts', icon: 'description' },
  { to: '/escrow', label: 'Escrow', icon: 'account_balance_wallet' },
]

export function AppShell() {
  const { user, logout } = useAuthStore()
  const handleLogout = () => {
    logout()
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand__mark">eco</div>
          <div>
            <strong>AgriContract</strong>
            <span>B2B Platform</span>
          </div>
        </div>

        <div className="topbar__actions">
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
        </div>
      </header>

      <div className="shell-grid">
        <aside className="sidebar">
          <div className="sidebar__group">
            <div className="sidebar-brand">
              <div className="brand__mark brand__mark--small">eco</div>
              <div>
                <strong>AgriContract</strong>
                <span>B2B Platform</span>
              </div>
            </div>
            {navItems.map((item) => (
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
          </div>
        </aside>

        <main className="shell-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
