'use client'

import { AracastClockCard } from '@/components/players/aracast-clock-card'
import { AracastConnectionCard } from '@/components/players/aracast-connection-card'
import { AracastNetworkCard } from '@/components/players/aracast-network-card'
import { useDictionary } from '@/contexts/dictionary-context'
import type { Player } from '@/types/api'

export function AracastSettings({ player, isAdmin }: { player: Player; isAdmin: boolean }) {
  const dict = useDictionary()
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold tracking-tight">{dict.aracast.settings.title}</h2>
      <div className="grid gap-4 lg:grid-cols-2">
        <AracastConnectionCard playerId={player.id} />
        <AracastClockCard playerId={player.id} online={player.online} />
        {isAdmin ? <AracastNetworkCard playerId={player.id} online={player.online} /> : null}
      </div>
    </section>
  )
}
