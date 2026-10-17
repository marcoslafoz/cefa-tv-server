'use client'

import { IconCircleCheck, IconCloudOff, IconSend } from '@tabler/icons-react'
import { Badge } from '@/components/shadcn-ui/badge'
import { useDictionary } from '@/contexts/dictionary-context'
import { cn } from '@/lib/utils'
import type { Player } from '@/types/api'

export type DeliveryKind = 'published' | 'onTheWay' | 'offline'

export function deliveryKind(player: Pick<Player, 'online' | 'pending'>): DeliveryKind {
  if (!player.pending) return 'published'
  return player.online ? 'onTheWay' : 'offline'
}

const TONES: Record<DeliveryKind, string> = {
  published: 'bg-success/15 text-success',
  onTheWay: 'bg-warning/15 text-warning',
  offline: 'bg-destructive/10 text-destructive',
}

export function DeliveryBadge({
  player,
  long,
  className,
}: {
  player: Pick<Player, 'online' | 'pending'>
  long?: boolean
  className?: string
}) {
  const dict = useDictionary()
  const kind = deliveryKind(player)
  const Icon =
    kind === 'published' ? IconCircleCheck : kind === 'onTheWay' ? IconSend : IconCloudOff
  const label = long ? dict.delivery.long[kind] : dict.delivery.short[kind]
  return (
    <Badge variant="secondary" className={cn('gap-1', TONES[kind], className)}>
      <Icon className="size-3 shrink-0" />
      {label}
    </Badge>
  )
}
