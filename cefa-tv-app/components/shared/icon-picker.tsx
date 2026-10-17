'use client'

import { useMemo, useState } from 'react'
import { IconCheck } from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import { Input } from '@/components/shadcn-ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/shadcn-ui/popover'
import {
  DynamicIcon,
  GROUP_COLORS,
  GROUP_COLOR_CLASSES,
  normalizeGroupColor,
  type GroupColor,
} from '@/components/shared/group-icon'
import { GROUP_ICON_NAMES } from '@/components/shared/icon-registry'
import { useDictionary } from '@/contexts/dictionary-context'
import { cn } from '@/lib/utils'

function humanize(name: string) {
  return name
    .replace(/^Icon/, '')
    .replace(/([a-z])([A-Z0-9])/g, '$1 $2')
    .toLowerCase()
}

export function IconPicker({
  value,
  onChange,
  color,
}: {
  value: string | null
  onChange: (name: string) => void
  color?: string | null
}) {
  const dict = useDictionary()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const names = useMemo(() => {
    const q = query.trim().toLowerCase()
    return q ? GROUP_ICON_NAMES.filter((name) => humanize(name).includes(q)) : GROUP_ICON_NAMES
  }, [query])

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" className="gap-2">
          <span
            className={cn(
              'flex size-6 items-center justify-center rounded-md',
              GROUP_COLOR_CLASSES[normalizeGroupColor(color)].badge
            )}
          >
            <DynamicIcon name={value} className="size-4" />
          </span>
          {dict.iconPicker.choose}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-80 space-y-2 p-3">
        <Input
          autoFocus
          value={query}
          placeholder={dict.iconPicker.search}
          aria-label={dict.iconPicker.search}
          onChange={(event) => setQuery(event.target.value)}
        />
        <div className="grid max-h-56 grid-cols-7 gap-1 overflow-y-auto">
          {names.map((name) => (
            <button
              key={name}
              type="button"
              aria-label={humanize(name)}
              aria-pressed={value === name}
              onClick={() => {
                onChange(name)
                setOpen(false)
              }}
              className={cn(
                'hover:bg-accent flex size-9 items-center justify-center rounded-md',
                value === name && 'bg-accent ring-ring ring-1'
              )}
            >
              <DynamicIcon name={name} className="size-5" />
            </button>
          ))}
          {names.length === 0 ? (
            <p className="text-muted-foreground col-span-7 py-4 text-center text-sm">
              {dict.iconPicker.empty}
            </p>
          ) : null}
        </div>
      </PopoverContent>
    </Popover>
  )
}

export function ColorPicker({
  value,
  onChange,
}: {
  value: string | null
  onChange: (color: GroupColor) => void
}) {
  const dict = useDictionary()
  const current = normalizeGroupColor(value)
  return (
    <div className="flex flex-wrap gap-2" role="radiogroup" aria-label={dict.iconPicker.color}>
      {GROUP_COLORS.map((color) => (
        <button
          key={color}
          type="button"
          role="radio"
          aria-checked={current === color}
          aria-label={dict.iconPicker.colors[color]}
          onClick={() => onChange(color)}
          className={cn(
            'flex size-7 items-center justify-center rounded-full text-white',
            GROUP_COLOR_CLASSES[color].swatch,
            current === color && 'ring-ring ring-2 ring-offset-2'
          )}
        >
          {current === color ? <IconCheck className="size-4" /> : null}
        </button>
      ))}
    </div>
  )
}
