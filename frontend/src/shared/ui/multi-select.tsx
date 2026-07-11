import type { ReactNode } from "react"
import { Combobox as ComboboxPrimitive } from "@base-ui/react/combobox"

import { cn } from "@/shared/lib/utils"

interface MultiSelectOption {
  value: string
  label: string
}

interface MultiSelectProps {
  id?: string
  label?: ReactNode
  required?: boolean
  placeholder?: string
  options: MultiSelectOption[]
  value: string[]
  onChange: (value: string[]) => void
  error?: string
  className?: string
}

function MultiSelect({
  id,
  label,
  required,
  placeholder = "Chọn...",
  options,
  value,
  onChange,
  error,
  className,
}: MultiSelectProps) {
  const labelByValue = new Map(options.map((option) => [option.value, option.label]))

  return (
    <div className={cn("flex flex-col", className)}>
      {label ? (
        <label htmlFor={id} className="mb-1.5 text-[13px] text-text-primary">
          {label} {required ? <span className="text-danger">*</span> : null}
        </label>
      ) : null}
      <ComboboxPrimitive.Root
        items={options}
        multiple
        value={value}
        onValueChange={onChange}
      >
        <ComboboxPrimitive.Chips
          id={id}
          className={cn(
            "flex flex-wrap items-center gap-1.5 rounded-sm border-[0.5px] bg-surface px-2.5 py-2 focus-within:ring-2",
            error
              ? "border-danger focus-within:ring-danger/20"
              : "border-border focus-within:border-primary focus-within:ring-primary/20"
          )}
        >
          {value.map((item) => (
            <ComboboxPrimitive.Chip
              key={item}
              className="inline-flex items-center gap-1 rounded-full bg-surface-muted px-2.5 py-0.5 text-[13px] text-text-primary"
            >
              {labelByValue.get(item) ?? item}
              <ComboboxPrimitive.ChipRemove className="ms flex text-[14px] text-text-secondary">
                close
              </ComboboxPrimitive.ChipRemove>
            </ComboboxPrimitive.Chip>
          ))}
          <ComboboxPrimitive.Input
            placeholder={value.length === 0 ? placeholder : undefined}
            className="min-w-16 flex-1 bg-transparent text-sm text-text-primary outline-none placeholder:text-text-muted"
          />
        </ComboboxPrimitive.Chips>
        <ComboboxPrimitive.Portal>
          <ComboboxPrimitive.Positioner sideOffset={4} className="z-50">
            <ComboboxPrimitive.Popup className="max-h-64 min-w-(--anchor-width) overflow-auto rounded-sm border-[0.5px] border-border bg-surface py-1 shadow-lg">
              <ComboboxPrimitive.Empty className="px-3 py-2 text-sm text-text-muted">
                Không tìm thấy kết quả
              </ComboboxPrimitive.Empty>
              <ComboboxPrimitive.List>
                {(option: MultiSelectOption) => (
                  <ComboboxPrimitive.Item
                    key={option.value}
                    value={option.value}
                    className="flex cursor-pointer items-center justify-between px-3 py-2 text-sm text-text-primary data-[highlighted]:bg-surface-muted"
                  >
                    {option.label}
                    <ComboboxPrimitive.ItemIndicator className="ms text-base text-primary">
                      check
                    </ComboboxPrimitive.ItemIndicator>
                  </ComboboxPrimitive.Item>
                )}
              </ComboboxPrimitive.List>
            </ComboboxPrimitive.Popup>
          </ComboboxPrimitive.Positioner>
        </ComboboxPrimitive.Portal>
      </ComboboxPrimitive.Root>
      {error ? <div className="mt-1 text-[13px] text-danger">{error}</div> : null}
    </div>
  )
}

export { MultiSelect }
export type { MultiSelectOption }
