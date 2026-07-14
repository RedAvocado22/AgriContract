import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'

import { useAuthStore } from '../../stores/authStore'

const privateNavItems = [
  { to: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
  { to: '/listings', label: 'Listings', icon: 'list_alt' },
  { to: '/contracts', label: 'Contracts', icon: 'description' },
  { to: '/escrow', label: 'Escrow', icon: 'account_balance_wallet' },
]

const publicNavItems = [{ to: '/listings', label: 'Listings', icon: 'list_alt' }]

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
        <Link className="brand" to="/listings" aria-label="AgriContract home">
          <div className="brand__mark">eco</div>
          <div>
            <strong>AgriContract</strong>
            <span>Escrow-backed B2B agriculture</span>
          </div>
        </Link>

        <div className="topbar__actions">
          {publicMode ? (
            <Link className="primary-button" to="/login">
              <span className="material-symbols-outlined">login</span>
              Sign in
            </Link>
          ) : (
            <>
              <button className="icon-button" type="button" aria-label="Notifications">
                <span className="material-symbols-outlined">notifications</span>
              </button>
              <div className="user-pill">
                <span className="material-symbols-outlined">account_circle</span>
                <div>
                  <strong>{user?.name ?? 'User'}</strong>
                  <span>{user?.organization ?? 'AgriContract'}</span>
                </div>
              </div>

              <button className="ghost-button" type="button" onClick={handleLogout}>
                Sign out
              </button>
            </>
          )}
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
                Create listing
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
