'use client'

import { NumberField } from '@/components/shared/number-field'
import { IconArrowLeft, IconArrowRight, IconTrash } from '@tabler/icons-react'
import { MediaThumb } from '@/components/shared/media-thumb'
import { Button } from '@/components/shadcn-ui/button'
import { Label } from '@/components/shadcn-ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { Switch } from '@/components/shadcn-ui/switch'
import { useDictionary } from '@/contexts/dictionary-context'
import { effectiveSeconds, type EditorItem } from '@/components/playlist/types'
import { hasFixedDuration } from '@/lib/media'
import { formatDuration } from '@/lib/utils'
import type { MediaAsset, PlaylistFit } from '@/types/api'

export function ItemProperties({
  item,
  asset,
  index,
  count,
  onChange,
  onMove,
  onRemove,
}: {
  item: EditorItem | null
  asset: MediaAsset | undefined
  index: number
  count: number
  onChange: (patch: Partial<EditorItem>) => void
  onMove: (direction: -1 | 1) => void
  onRemove: () => void
}) {
  const dict = useDictionary()

  if (!item || !asset) {
    return (
      <p className="text-muted-foreground py-10 text-center text-sm">{dict.playlists.selectItem}</p>
    )
  }

  const fixed = hasFixedDuration(asset.type)
  const isPdf = asset.type === 'PDF'
  const showFit = asset.type === 'IMAGE' || asset.type === 'VIDEO' || asset.type === 'PDF'

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <MediaThumb id={asset.id} type={asset.type} className="size-14 shrink-0" />
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{asset.name}</p>
          <p className="text-muted-foreground text-xs">
            {dict.media.types[asset.type]} · {formatDuration(effectiveSeconds(item, asset))}
          </p>
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="item-duration">
          {isPdf ? dict.playlists.secondsPerPage : dict.playlists.duration}
        </Label>
        <NumberField
          id="item-duration"
          min={1}
          disabled={fixed}
          value={item.durationSec}
          onValueChange={(durationSec) => onChange({ durationSec })}
        />
        {fixed ? (
          <p className="text-muted-foreground text-xs">{dict.playlists.videoFixed}</p>
        ) : null}
      </div>

      {showFit ? (
        <div className="space-y-2">
          <Label>{dict.playlists.fit}</Label>
          <Select
            value={item.fit}
            onValueChange={(value) => onChange({ fit: value as PlaylistFit })}
          >
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="COVER">{dict.playlists.fitCover}</SelectItem>
              <SelectItem value="CONTAIN">{dict.playlists.fitContain}</SelectItem>
            </SelectContent>
          </Select>
        </div>
      ) : null}

      {asset.type === 'VIDEO' ? (
        <div className="flex items-center justify-between rounded-lg border px-3 py-2">
          <Label htmlFor="item-muted">{dict.playlists.muted}</Label>
          <Switch
            id="item-muted"
            checked={item.muted}
            onCheckedChange={(value) => onChange({ muted: value })}
          />
        </div>
      ) : null}

      <div className="flex items-center justify-between gap-2">
        <div className="flex gap-1">
          <Button
            size="icon-sm"
            variant="outline"
            aria-label={dict.playlists.moveLeft}
            disabled={index === 0}
            onClick={() => onMove(-1)}
          >
            <IconArrowLeft />
          </Button>
          <Button
            size="icon-sm"
            variant="outline"
            aria-label={dict.playlists.moveRight}
            disabled={index === count - 1}
            onClick={() => onMove(1)}
          >
            <IconArrowRight />
          </Button>
        </div>
        <Button size="sm" variant="destructive" onClick={onRemove}>
          <IconTrash /> {dict.playlists.removeItem}
        </Button>
      </div>
    </div>
  )
}
