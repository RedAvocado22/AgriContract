import { Dialog } from "@base-ui/react/dialog"

import { cn } from "@/shared/lib/utils"
import { Button } from "@/shared/ui/button"

interface PublicMobileNavLink {
  key: string
  label: string
  href?: string
}

interface PublicMobileNavProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  links: PublicMobileNavLink[]
  onLogin?: () => void
  onRegister?: () => void
  className?: string
}

/** Menu ☰ toàn màn cho trang công khai — Drawer app chỉ dành cho sidebar 240px (brief Mobile §16). */
function PublicMobileNav({ open, onOpenChange, links, onLogin, onRegister, className }: PublicMobileNavProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Popup className={cn("fixed inset-0 flex flex-col bg-surface", className)}>
          <div className="flex h-14 items-center justify-between border-b-[0.5px] border-border px-4">
            <span className="text-[17px] font-medium text-primary">AgriContract</span>
            <Dialog.Close
              aria-label="Đóng menu"
              className="flex size-10 items-center justify-center rounded-[10px] border-[0.5px] border-border"
            >
              <span className="ms text-2xl">close</span>
            </Dialog.Close>
          </div>
          <nav className="flex flex-col">
            {links.map((link) => (
              <a
                key={link.key}
                href={link.href ?? "#"}
                className="flex items-center justify-between border-b-[0.5px] border-surface-muted px-5 py-3.5 text-[15px] text-text-primary"
              >
                {link.label}
                <span className="ms text-xl text-border-strong">chevron_right</span>
              </a>
            ))}
          </nav>
          <div className="flex flex-col gap-2.5 px-5 pt-4">
            <Button variant="outline" className="h-[46px] rounded-[10px]" onClick={onLogin}>
              Đăng nhập
            </Button>
            <Button className="h-[46px] rounded-[10px]" onClick={onRegister}>
              Đăng ký doanh nghiệp
            </Button>
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

export { PublicMobileNav }
export type { PublicMobileNavLink }
