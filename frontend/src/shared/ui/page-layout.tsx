import type { ReactNode } from "react"

import { cn } from "@/shared/lib/utils"

interface PageLayoutProps {
  sidebar: ReactNode
  topBar: ReactNode
  children: ReactNode
  className?: string
}

function PageLayout({ sidebar, topBar, children, className }: PageLayoutProps) {
  return (
    <div className={cn("min-h-screen bg-page-bg", className)}>
      <div className="mx-auto flex max-w-[1440px] gap-6 p-6">
        {sidebar}
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          {topBar}
          <main className="flex-1">{children}</main>
        </div>
      </div>
    </div>
  )
}

export { PageLayout }
