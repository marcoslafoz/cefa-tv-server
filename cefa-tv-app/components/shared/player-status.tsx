'use client'

import { Badge } from '@/components/shadcn-ui/badge'
import { useDictionary } from '@/contexts/dictionary-context'
import { cn } from '@/lib/utils'
import type { Player } from '@/types/api'

export function OnlineDot({ online, className }: { online: boolean; className?: string }) {
  return (
    <span
      className={cn(
        'inline-block size-2.5 rounded-full',
        online ? 'bg-success shadow-success/20 shadow-[0_0_0_3px]' : 'bg-muted-foreground/40',
        className
      )}
    />
  )
}

export function PlayerBadges({ player }: { player: Player }) {
  const dict = useDictionary()
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <Badge variant={player.online ? 'secondary' : 'destructive'}>
        {player.online ? dict.players.online : dict.players.offline}
      </Badge>
      {player.status === 'DISABLED' ? (
        <Badge variant="outline">{dict.players.disabled}</Badge>
      ) : null}
      {player.neverConnected ? (
        <Badge variant="secondary" className="bg-warning/15 text-warning">
          {dict.players.neverConnected}
        </Badge>
      ) : player.pending ? (
        <Badge variant="secondary" className="bg-warning/15 text-warning">
          {dict.players.pendingDelivery}
        </Badge>
      ) : null}
      {player.lastError ? <Badge variant="destructive">{dict.players.error}</Badge> : null}
    </div>
  )
}
