'use client'

import { toneFor } from '@/components/schedule/helpers'
import { useDictionary } from '@/contexts/dictionary-context'
import { cn } from '@/lib/utils'

export function ScheduleLegend({
  entries,
}: {
  entries: Array<{ playlistId: string; name: string }>
}) {
  const dict = useDictionary()
  if (entries.length === 0) return null
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs">
      <span className="text-muted-foreground font-medium">{dict.schedule.legend}</span>
      {entries.map((entry) => (
        <span key={entry.playlistId} className="flex items-center gap-1.5">
          <span className={cn('size-3 rounded border-2', toneFor(entry.playlistId))} />
          {entry.name}
        </span>
      ))}
    </div>
  )
}
