import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'

interface AuthHeaderProps {
  action?: ReactNode
}

export function AuthHeader({ action }: AuthHeaderProps) {
  return (
    <header className="topbar">
      <Link className="brand" to="/listings" aria-label="Trang chủ AgriContract">
        <div className="brand__mark">eco</div>
        <div>
          <strong>AgriContract</strong>
          <span>Nền tảng B2B nông sản</span>
        </div>
      </Link>

      {action ? <div className="topbar__actions">{action}</div> : null}
    </header>
  )
}
