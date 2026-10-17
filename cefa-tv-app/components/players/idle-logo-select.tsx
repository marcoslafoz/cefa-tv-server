'use client'

import Image from 'next/image'
import { IconCheck, IconCircleCheck, IconHourglass } from '@tabler/icons-react'
import { Badge } from '@/components/shadcn-ui/badge'
import { Label } from '@/components/shadcn-ui/label'
import { useDictionary } from '@/contexts/dictionary-context'
import { cn } from '@/lib/utils'
import type { IdleLogo, Player } from '@/types/api'

export const IDLE_LOGO_INHERIT = 'inherit'
export const DEFAULT_IDLE_LOGO: IdleLogo = 'CEFA'
export const IDLE_LOGOS: IdleLogo[] = ['CEFA', 'ALGONTEC']

export type IdleLogoValue = IdleLogo | typeof IDLE_LOGO_INHERIT

const LOGO_FILES: Record<IdleLogo, string> = {
  CEFA: '/brand/cefa-logo-white.svg',
  ALGONTEC: '/brand/algontec-logo-white.svg',
}

export function idleLogoStatus(player: Player): 'applied' | 'pending' | 'offline' {
  if (!player.pending) return 'applied'
  return player.online ? 'pending' : 'offline'
}

export function LogoTile({
  logo,
  className,
  imageClassName,
}: {
  logo: IdleLogo
  className?: string
  imageClassName?: string
}) {
  const dict = useDictionary()
  const name = dict.idleLogo.logos[logo]
  return (
    <span
      className={cn(
        'flex items-center justify-center rounded-lg border border-neutral-800 bg-neutral-950 p-2',
        className
      )}
    >
      <Image
        src={LOGO_FILES[logo]}
        alt={dict.idleLogo.preview.replace('{logo}', name)}
        width={160}
        height={64}
        unoptimized
        className={cn('max-h-12 w-auto object-contain', imageClassName)}
      />
    </span>
  )
}

export function IdleLogoSelect({
  value,
  onChange,
  inheritLabel,
  inheritLogo,
  hint,
  effective,
  status,
  disabled,
}: {
  value: IdleLogoValue
  onChange: (value: IdleLogoValue) => void
  inheritLabel: string
  inheritLogo: IdleLogo
  hint: string
  effective?: { logo: IdleLogo; text: string } | null
  status?: 'applied' | 'pending' | 'offline'
  disabled?: boolean
}) {
  const dict = useDictionary()
  const text = dict.idleLogo

  const options: Array<{ value: IdleLogoValue; logo: IdleLogo; label: string }> = [
    ...IDLE_LOGOS.map((logo) => ({ value: logo, logo, label: text.logos[logo] })),
    { value: IDLE_LOGO_INHERIT, logo: inheritLogo, label: inheritLabel },
  ]

  return (
    <div className="space-y-2 sm:col-span-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Label id="idle-logo-label">{text.label}</Label>
        {status === 'applied' ? (
          <Badge variant="secondary" className="bg-success/15 text-success gap-1">
            <IconCircleCheck className="size-3" /> {text.applied}
          </Badge>
        ) : status ? (
          <Badge variant="secondary" className="bg-warning/15 text-warning gap-1">
            <IconHourglass className="size-3" />
            {status === 'offline' ? text.pendingOffline : text.pendingApplying}
          </Badge>
        ) : null}
      </div>
      <div
        role="radiogroup"
        aria-labelledby="idle-logo-label"
        className="grid gap-2 sm:grid-cols-3"
      >
        {options.map((option) => {
          const selected = value === option.value
          return (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={selected}
              disabled={disabled}
              onClick={() => onChange(option.value)}
              className={cn(
                'focus-visible:ring-ring/50 group relative flex min-w-0 flex-col gap-2 rounded-xl border p-2 text-left transition-colors outline-none focus-visible:ring-3 disabled:opacity-60',
                selected
                  ? 'border-primary bg-primary/5 ring-primary/30 ring-2'
                  : 'hover:bg-muted/50'
              )}
            >
              <LogoTile logo={option.logo} className="h-20 w-full" />
              <span className="flex min-w-0 items-start justify-between gap-2 px-1">
                <span className="line-clamp-2 text-sm leading-tight font-medium break-words">
                  {option.label}
                </span>
                {selected ? <IconCheck className="text-primary size-4 shrink-0" /> : null}
              </span>
            </button>
          )
        })}
      </div>
      <p className="text-muted-foreground text-xs">{hint}</p>
      {effective ? (
        <p className="text-muted-foreground flex items-center gap-2 text-xs">
          <LogoTile logo={effective.logo} className="h-8 w-14 p-1" imageClassName="max-h-6" />
          <span>{effective.text}</span>
        </p>
      ) : null}
    </div>
  )
}
