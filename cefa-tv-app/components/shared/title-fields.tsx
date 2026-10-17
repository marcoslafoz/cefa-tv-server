'use client'

import { useId } from 'react'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import { cn } from '@/lib/utils'

interface TitleFieldsProps {
  name: string
  description: string
  onNameChange: (value: string) => void
  onDescriptionChange: (value: string) => void
  nameLabel: string
  descriptionLabel: string
  namePlaceholder?: string
  descriptionPlaceholder?: string
  nameInvalid?: boolean
  maxLength?: number
  descriptionMaxLength?: number
  disabled?: boolean
  autoFocus?: boolean
  variant?: 'inline' | 'boxed'
  className?: string
}

const INLINE_BASE =
  'w-full rounded-md border-transparent bg-transparent px-3 shadow-none transition-colors hover:border-input hover:bg-muted/40 focus-visible:border-ring focus-visible:bg-background dark:bg-transparent dark:hover:bg-muted/40 dark:focus-visible:bg-background'

export function TitleFields({
  name,
  description,
  onNameChange,
  onDescriptionChange,
  nameLabel,
  descriptionLabel,
  namePlaceholder,
  descriptionPlaceholder,
  nameInvalid = false,
  maxLength = 120,
  descriptionMaxLength = 500,
  disabled,
  autoFocus,
  variant = 'inline',
  className,
}: TitleFieldsProps) {
  const nameId = useId()
  const descriptionId = useId()

  if (variant === 'boxed') {
    return (
      <div className={cn('grid gap-4 sm:grid-cols-2', className)}>
        <div className="space-y-2">
          <Label htmlFor={nameId}>{nameLabel}</Label>
          <Input
            id={nameId}
            required
            autoFocus={autoFocus}
            disabled={disabled}
            maxLength={maxLength}
            aria-invalid={nameInvalid || undefined}
            placeholder={namePlaceholder}
            value={name}
            onChange={(event) => onNameChange(event.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor={descriptionId}>{descriptionLabel}</Label>
          <Input
            id={descriptionId}
            disabled={disabled}
            maxLength={descriptionMaxLength}
            placeholder={descriptionPlaceholder}
            value={description}
            onChange={(event) => onDescriptionChange(event.target.value)}
          />
        </div>
      </div>
    )
  }

  return (
    <div className={cn('flex min-w-0 flex-col gap-1', className)}>
      <Label htmlFor={nameId} className="sr-only">
        {nameLabel}
      </Label>
      <Input
        id={nameId}
        disabled={disabled}
        maxLength={maxLength}
        aria-invalid={nameInvalid || undefined}
        placeholder={namePlaceholder}
        value={name}
        onChange={(event) => onNameChange(event.target.value)}
        className={cn(INLINE_BASE, 'h-10 truncate text-xl font-semibold md:text-xl')}
      />
      <Label htmlFor={descriptionId} className="sr-only">
        {descriptionLabel}
      </Label>
      <Input
        id={descriptionId}
        disabled={disabled}
        maxLength={descriptionMaxLength}
        placeholder={descriptionPlaceholder}
        value={description}
        onChange={(event) => onDescriptionChange(event.target.value)}
        className={cn(INLINE_BASE, 'text-muted-foreground h-8 truncate text-sm md:text-sm')}
      />
    </div>
  )
}
