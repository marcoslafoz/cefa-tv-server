'use client'

import { useEffect, useState, useSyncExternalStore } from 'react'
import { IconCloudOff, IconDeviceTv } from '@tabler/icons-react'
import { useDictionary } from '@/contexts/dictionary-context'
import type { PreviewFrame } from '@/hooks/use-live-previews'
import { CORE_API_PREFIX, playerPreviewUrl } from '@/lib/api-client'
import { cn } from '@/lib/utils'
import type { Player } from '@/types/api'

const listeners = new Set<() => void>()
let tickTimer: number | undefined
let tickValue = 0

function subscribeTick(listener: () => void) {
  listeners.add(listener)
  if (listeners.size === 1) {
    tickValue = Date.now()
    tickTimer = window.setInterval(() => {
      tickValue = Date.now()
      listeners.forEach((notify) => notify())
    }, 1000)
  }
  return () => {
    listeners.delete(listener)
    if (listeners.size === 0) window.clearInterval(tickTimer)
  }
}

function useSecondTick(): number {
  return useSyncExternalStore(
    subscribeTick,
    () => tickValue,
    () => 0
  )
}

function AgeLabel({ at }: { at: number }) {
  const dict = useDictionary()
  const now = useSecondTick()
  const seconds = Math.max(0, Math.round((now - at) / 1000))
  return <>{dict.players.preview.ago.replace('{seconds}', String(seconds))}</>
}

function coreUrl(url: string): string {
  return url.startsWith('/') ? `${CORE_API_PREFIX}${url}` : url
}

export function resolvePreviewSource(
  player: Player,
  live: boolean,
  frame: PreviewFrame | undefined
): string | null {
  if (live) return frame?.url ?? playerPreviewUrl(player.id)
  return player.currentAssetThumbnailUrl ? coreUrl(player.currentAssetThumbnailUrl) : null
}

export function PlayerPreview({
  player,
  live,
  frame,
  className,
}: {
  player: Player
  live: boolean
  frame: PreviewFrame | undefined
  className?: string
}) {
  const dict = useDictionary()
  const source = resolvePreviewSource(player, live, frame)
  const [shown, setShown] = useState<string | null>(null)

  useEffect(() => {
    if (!source) return
    let cancelled = false
    const image = new Image()
    image.onload = () => {
      if (!cancelled) setShown(source)
    }
    image.src = source
    return () => {
      cancelled = true
    }
  }, [source])

  const visible = source ? shown : null
  const offline = !player.online

  return (
    <div
      className={cn(
        'bg-muted relative flex aspect-video items-center justify-center overflow-hidden rounded-lg',
        className
      )}
    >
      {visible ? (
        <img
          src={visible}
          alt=""
          draggable={false}
          className={cn('size-full object-cover', offline && 'opacity-40 grayscale')}
        />
      ) : (
        <IconDeviceTv className="text-muted-foreground size-8" />
      )}
      {offline ? (
        <span className="bg-background/80 text-muted-foreground absolute inset-x-0 bottom-0 flex items-center justify-center gap-1 py-1 text-xs font-medium">
          <IconCloudOff className="size-3.5" /> {dict.players.preview.offline}
        </span>
      ) : live && frame ? (
        <span className="absolute right-1.5 bottom-1.5 rounded bg-black/60 px-1.5 py-0.5 text-[10px] text-white tabular-nums">
          <AgeLabel at={frame.at} />
        </span>
      ) : null}
    </div>
  )
}
