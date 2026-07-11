import type { ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

type SearchInputProps = Omit<ComponentProps<"input">, "type">

function SearchInput({ className, placeholder = "Tìm kiếm...", ...props }: SearchInputProps) {
  return (
    <div
      className={cn(
        "flex h-9 items-center gap-2 rounded-sm border-[0.5px] border-border bg-surface px-3 focus-within:border-primary focus-within:ring-2 focus-within:ring-primary/20",
        className
      )}
    >
      <span className="ms text-lg text-text-muted">search</span>
      <input
        type="search"
        placeholder={placeholder}
        className="w-full bg-transparent text-sm text-text-primary outline-none placeholder:text-text-muted"
        {...props}
      />
    </div>
  )
}

export { SearchInput }
