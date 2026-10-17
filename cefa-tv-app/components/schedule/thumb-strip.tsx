'use client'

import { useState } from 'react'
import { MediaThumb } from '@/components/shared/media-thumb'
import { CORE_API_PREFIX } from '@/lib/api-client'
import { cn } from '@/lib/utils'
import type { MediaType, Playlist } from '@/types/api'

export interface ThumbItem {
  key: string
  url?: string
  assetId?: string
  type?: MediaType
  name?: string
  durationSec?: number
}

export function thumbItemsFromPlaylist(playlist: Playlist | undefined): ThumbItem[] {
  if (!playlist) return []
  return playlist.items.map((item, index) => ({
    key: `${item.asset.id}-${index}`,
    assetId: item.asset.id,
    type: item.asset.type,
    name: item.asset.name,
    durationSec: item.durationSec,
  }))
}

export function thumbItemsFromUrls(urls: string[] | undefined): ThumbItem[] {
  return (urls ?? []).map((url, index) => ({ key: `${url}-${index}`, url }))
}

export function resolveThumbItems(
  urls: string[] | undefined,
  playlist: Playlist | undefined
): ThumbItem[] {
  const fromPlaylist = thumbItemsFromPlaylist(playlist)
  if (fromPlaylist.length > 0) return fromPlaylist
  return thumbItemsFromUrls(urls)
}

function coreUrl(url: string): string {
  return url.startsWith('/') ? `${CORE_API_PREFIX}${url}` : url
}

export function ThumbCell({ item, className }: { item: ThumbItem; className?: string }) {
  const [failed, setFailed] = useState(false)
  if (item.assetId && item.type) {
    return (
      <MediaThumb id={item.assetId} type={item.type} className={cn('rounded-none', className)} />
    )
  }
  if (!item.url || failed) return <div className={cn('bg-muted', className)} />
  return (
    <img
      src={coreUrl(item.url)}
      alt=""
      draggable={false}
      loading="lazy"
      className={cn('object-cover', className)}
      onError={() => setFailed(true)}
    />
  )
}

export function ThumbStrip({
  items,
  width,
  height,
  className,
}: {
  items: ThumbItem[]
  width: number
  height: number
  className?: string
}) {
  if (items.length === 0) return null
  const cellWidth = Math.max(24, Math.round((height * 16) / 9))
  const count = Math.max(1, Math.min(Math.ceil(width / cellWidth), Math.max(items.length, 1) * 4))
  return (
    <div
      aria-hidden
      className={cn('pointer-events-none absolute inset-0 flex overflow-hidden', className)}
    >
      {Array.from({ length: count }).map((_, index) => {
        const item = items[index % items.length]
        return (
          <div
            key={`${item.key}-${index}`}
            className="h-full shrink-0 overflow-hidden"
            style={{ width: cellWidth }}
          >
            <ThumbCell item={item} className="size-full" />
          </div>
        )
      })}
    </div>
  )
}

export function ThumbRow({
  items,
  max = 5,
  className,
}: {
  items: ThumbItem[]
  max?: number
  className?: string
}) {
  if (items.length === 0) return null
  const shown = items.slice(0, max)
  const extra = items.length - shown.length
  return (
    <div className={cn('flex items-center gap-1', className)}>
      {shown.map((item, index) => (
        <ThumbCell
          key={`${item.key}-${index}`}
          item={item}
          className="size-9 shrink-0 rounded-md"
        />
      ))}
      {extra > 0 ? (
        <span className="text-muted-foreground bg-muted flex size-9 shrink-0 items-center justify-center rounded-md text-xs tabular-nums">
          +{extra}
        </span>
      ) : null}
    </div>
  )
}
