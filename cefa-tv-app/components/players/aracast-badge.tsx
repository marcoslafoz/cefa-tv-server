'use client'

import { Badge } from '@/components/shadcn-ui/badge'
import { useDictionary } from '@/contexts/dictionary-context'
import { cn } from '@/lib/utils'
import type { Player } from '@/types/api'

export function AracastBadge({ player, className }: { player: Player; className?: string }) {
  const dict = useDictionary()
  if (player.kind !== 'ARACAST') return null
  return (
    <Badge variant="outline" className={cn('text-[10px] font-semibold uppercase', className)}>
      {dict.aracast.badge}
    </Badge>
  )
}
