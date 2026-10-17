'use client'

import { IconCircleCheck, IconHourglass } from '@tabler/icons-react'
import { Badge } from '@/components/shadcn-ui/badge'
import { Label } from '@/components/shadcn-ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { useDictionary } from '@/contexts/dictionary-context'
import type { Player, PlayerDisplay } from '@/types/api'

export const AUTO_OUTPUT = 'auto'

function describeDisplay(
  display: PlayerDisplay,
  text: { connected: string; disconnected: string }
): string {
  const size = display.width && display.height ? `${display.width}x${display.height}` : null
  const rate = display.refreshHz ? `${Math.round(display.refreshHz)} Hz` : null
  const state = display.connected ? text.connected : text.disconnected
  return [display.output, size, rate, state].filter(Boolean).join(' · ')
}

export function outputStatus(player: Player): 'applied' | 'pending' | 'offline' {
  const preferred = player.preferredOutput ?? null
  const selected = (player.displays ?? []).find((display) => display.selected)?.output ?? null
  const matches = preferred === null || selected === preferred
  if (matches) return 'applied'
  return player.online ? 'pending' : 'offline'
}

export function OutputSelect({
  player,
  value,
  onChange,
}: {
  player: Player
  value: string
  onChange: (value: string) => void
}) {
  const dict = useDictionary()
  const text = dict.players.output
  const displays = player.displays ?? []
  const known = displays.some((display) => display.output === value)
  const status = outputStatus(player)

  return (
    <div className="space-y-2 sm:col-span-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Label>{text.label}</Label>
        {status === 'applied' ? (
          <Badge variant="secondary" className="bg-success/15 text-success gap-1">
            <IconCircleCheck className="size-3" /> {text.applied}
          </Badge>
        ) : (
          <Badge variant="secondary" className="bg-warning/15 text-warning gap-1">
            <IconHourglass className="size-3" />
            {status === 'offline' ? text.pendingOffline : text.pendingApplying}
          </Badge>
        )}
      </div>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={AUTO_OUTPUT}>{text.auto}</SelectItem>
          {displays.map((display) => (
            <SelectItem key={display.output} value={display.output}>
              {describeDisplay(display, text)}
            </SelectItem>
          ))}
          {value !== AUTO_OUTPUT && !known ? <SelectItem value={value}>{value}</SelectItem> : null}
        </SelectContent>
      </Select>
      <p className="text-muted-foreground text-xs">{text.hint}</p>
    </div>
  )
}
