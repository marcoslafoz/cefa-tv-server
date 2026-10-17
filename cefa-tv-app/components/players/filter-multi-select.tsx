'use client'

import { IconChevronDown } from '@tabler/icons-react'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Checkbox } from '@/components/shadcn-ui/checkbox'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/shadcn-ui/popover'

export interface FilterOption {
  value: string
  label: string
}

export function FilterMultiSelect({
  label,
  options,
  selected,
  onChange,
}: {
  label: string
  options: FilterOption[]
  selected: string[]
  onChange: (selected: string[]) => void
}) {
  function toggle(value: string) {
    onChange(
      selected.includes(value) ? selected.filter((entry) => entry !== value) : [...selected, value]
    )
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="outline" className="gap-2">
          {label}
          {selected.length > 0 ? <Badge variant="secondary">{selected.length}</Badge> : null}
          <IconChevronDown className="size-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="max-h-72 w-64 space-y-0.5 overflow-y-auto p-1.5">
        {options.map((option) => (
          <label
            key={option.value}
            className="hover:bg-muted flex cursor-pointer items-center gap-3 rounded-lg px-2.5 py-2"
          >
            <Checkbox
              checked={selected.includes(option.value)}
              onCheckedChange={() => toggle(option.value)}
            />
            <span className="min-w-0 truncate text-sm">{option.label}</span>
          </label>
        ))}
      </PopoverContent>
    </Popover>
  )
}
