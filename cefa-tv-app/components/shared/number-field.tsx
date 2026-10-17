'use client'

import { useState } from 'react'
import { IconMinus, IconPlus } from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import { Input } from '@/components/shadcn-ui/input'
import { useDictionary } from '@/contexts/dictionary-context'
import { clamp, cn } from '@/lib/utils'

export function NumberField({
  id,
  value,
  onValueChange,
  min = 0,
  max = Number.MAX_SAFE_INTEGER,
  step = 1,
  disabled,
  className,
}: {
  id?: string
  value: number
  onValueChange: (value: number) => void
  min?: number
  max?: number
  step?: number
  disabled?: boolean
  className?: string
}) {
  const dict = useDictionary()
  const [draft, setDraft] = useState<string | null>(null)

  function commit(next: number) {
    onValueChange(clamp(Math.round(next), min, max))
    setDraft(null)
  }

  return (
    <div className={cn('flex items-center gap-1', className)}>
      <Button
        type="button"
        variant="outline"
        size="icon"
        disabled={disabled || value <= min}
        aria-label={dict.common.decrease}
        onClick={() => commit(value - step)}
      >
        <IconMinus />
      </Button>
      <Input
        id={id}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        disabled={disabled}
        value={draft ?? String(value)}
        className="text-center tabular-nums"
        onChange={(event) => {
          const digits = event.target.value.replace(/[^\d-]/g, '')
          setDraft(digits)
          if (digits !== '' && digits !== '-') onValueChange(clamp(Number(digits), min, max))
        }}
        onBlur={() =>
          commit(draft === null || draft === '' || draft === '-' ? value : Number(draft))
        }
        onKeyDown={(event) => {
          if (event.key === 'ArrowUp') {
            event.preventDefault()
            commit(value + step)
          } else if (event.key === 'ArrowDown') {
            event.preventDefault()
            commit(value - step)
          }
        }}
      />
      <Button
        type="button"
        variant="outline"
        size="icon"
        disabled={disabled || value >= max}
        aria-label={dict.common.increase}
        onClick={() => commit(value + step)}
      >
        <IconPlus />
      </Button>
    </div>
  )
}
