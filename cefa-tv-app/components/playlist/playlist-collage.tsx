'use client'

import { IconPlaylist } from '@tabler/icons-react'
import { MediaThumb } from '@/components/shared/media-thumb'
import { cn } from '@/lib/utils'
import type { MediaAsset } from '@/types/api'

export function PlaylistCollage({
  assets,
  className,
}: {
  assets: MediaAsset[]
  className?: string
}) {
  const shown = assets.slice(0, 4)
  if (shown.length === 0) {
    return (
      <div
        className={cn(
          'bg-muted text-muted-foreground flex items-center justify-center rounded-lg',
          className
        )}
      >
        <IconPlaylist className="size-8" />
      </div>
    )
  }
  const columns = shown.length === 1 ? 'grid-cols-1' : 'grid-cols-2'
  return (
    <div className={cn('grid gap-0.5 overflow-hidden rounded-lg', columns, className)}>
      {shown.map((asset, index) => (
        <MediaThumb
          key={`${asset.id}-${index}`}
          id={asset.id}
          type={asset.type}
          className={cn(
            'size-full rounded-none',
            shown.length === 3 && index === 0 && 'row-span-2'
          )}
        />
      ))}
    </div>
  )
}
