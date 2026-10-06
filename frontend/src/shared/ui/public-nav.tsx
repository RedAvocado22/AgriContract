import { Link, useLocation } from "react-router-dom"

import { cn } from "@/shared/lib/utils"
import { buttonVariants } from "@/shared/ui/button"

const NAV_LINKS = [
  { label: "Sàn nguồn hàng", to: "/listing" },
  { label: "Bảng giá tham khảo", to: "/prices" },
  { label: "Cách hoạt động", to: "/how-it-works" },
  { label: "Về ký quỹ", to: "/escrow" },
]

function PublicNav() {
  const { pathname } = useLocation()

  return (
    <div className="sticky top-0 z-10 flex h-16 items-center gap-8 border-b-[0.5px] border-border bg-surface px-10">
      <Link to="/" className="text-[17px] font-medium text-primary">
        AgriContract
      </Link>
      <nav className="flex gap-[26px] text-sm text-text-secondary">
        {NAV_LINKS.map((link) => {
          const isActive = pathname === link.to || pathname.startsWith(link.to + "/")
          return (
            <Link
              key={link.to}
              to={link.to}
              className={cn(
                "hover:text-text-primary",
                isActive && "font-medium text-primary"
              )}
              aria-current={isActive ? "page" : undefined}
            >
              {link.label}
            </Link>
          )
        })}
      </nav>
      <div className="flex-1" />
      <Link
        to="/login"
        className="text-sm text-text-secondary hover:text-text-primary"
      >
        Đăng nhập
      </Link>
      <Link to="/register" className={cn(buttonVariants({ size: "nav" }))}>
        Đăng ký doanh nghiệp
      </Link>
    </div>
  )
}

export { PublicNav }
