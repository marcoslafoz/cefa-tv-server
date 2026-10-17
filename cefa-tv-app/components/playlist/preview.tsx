'use client'

import { useEffect, useState } from 'react'
import { IconPlayerPause, IconPlayerPlay, IconPlayerSkipBack } from '@tabler/icons-react'
import { MediaThumb } from '@/components/shared/media-thumb'
import { Button } from '@/components/shadcn-ui/button'
import { useDictionary } from '@/contexts/dictionary-context'
import { effectiveSeconds, type EditorItem } from '@/components/playlist/types'
import { mediaContentUrl } from '@/lib/api-client'
import { MEDIA_ICONS } from '@/lib/media'
import { cn } from '@/lib/utils'
import type { MediaAsset } from '@/types/api'

function Stage({ item, asset }: { item: EditorItem; asset: MediaAsset }) {
  const fit = item.fit === 'COVER' ? 'object-cover' : 'object-contain'
  if (asset.type === 'IMAGE') {
    return <img src={mediaContentUrl(asset.id)} alt="" className={cn('size-full', fit)} />
  }
  if (asset.type === 'VIDEO') {
    return (
      <video
        key={asset.id}
        src={mediaContentUrl(asset.id)}
        autoPlay
        muted
        loop
        playsInline
        className={cn('size-full', fit)}
      />
    )
  }
  if (asset.type === 'PDF') {
    return <MediaThumb id={asset.id} type={asset.type} className="size-full rounded-none" />
  }
  const IconComponent = MEDIA_ICONS[asset.type]
  return (
    <div className="flex size-full flex-col items-center justify-center gap-2 text-white/80">
      <IconComponent className="size-10" />
      <span className="max-w-[80%] truncate text-xs">{asset.url ?? asset.name}</span>
    </div>
  )
}

export function PlaylistPreview({
  items,
  assets,
  selectedUid,
}: {
  items: EditorItem[]
  assets: Map<string, MediaAsset>
  selectedUid: string | null
}) {
  const dict = useDictionary()
  const [playing, setPlaying] = useState(false)
  const [index, setIndex] = useState(0)

  const selectedIndex = items.findIndex((item) => item.uid === selectedUid)
  const shownIndex = playing ? index : selectedIndex >= 0 ? selectedIndex : 0
  const shown = items[shownIndex]
  const asset = shown ? assets.get(shown.assetId) : undefined

  useEffect(() => {
    if (!playing || items.length === 0) return
    const current = items[index] ?? items[0]
    const seconds = effectiveSeconds(current, assets.get(current.assetId))
    const timer = window.setTimeout(
      () => setIndex((value) => (value + 1) % items.length),
      seconds * 1000
    )
    return () => window.clearTimeout(timer)
  }, [playing, index, items, assets])

  return (
    <div className="space-y-2">
      <div className="relative aspect-video overflow-hidden rounded-xl bg-black">
        {shown && asset ? (
          <Stage item={shown} asset={asset} />
        ) : (
          <div className="flex size-full items-center justify-center text-sm text-white/60">
            {dict.playlists.previewEmpty}
          </div>
        )}
      </div>
      <div className="flex items-center justify-between">
        <div className="flex gap-1">
          <Button
            size="icon-sm"
            variant="outline"
            aria-label={playing ? dict.playlists.pause : dict.playlists.play}
            disabled={items.length === 0}
            onClick={() => {
              setIndex(selectedIndex >= 0 ? selectedIndex : 0)
              setPlaying((value) => !value)
            }}
          >
            {playing ? <IconPlayerPause /> : <IconPlayerPlay />}
          </Button>
          <Button
            size="icon-sm"
            variant="outline"
            aria-label={dict.playlists.restart}
            disabled={items.length === 0}
            onClick={() => setIndex(0)}
          >
            <IconPlayerSkipBack />
          </Button>
        </div>
        <span className="text-muted-foreground text-xs tabular-nums">
          {items.length > 0 ? `${shownIndex + 1} / ${items.length}` : ''}
        </span>
      </div>
    </div>
  )
}
