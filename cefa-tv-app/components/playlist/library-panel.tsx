'use client'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { useEffect, useState } from 'react'
import { useDraggable } from '@dnd-kit/core'
import { IconPlayerPlay, IconPlus, IconSearch } from '@tabler/icons-react'
import { PickerFolders, scopeParams, useFolderScope } from '@/components/explorer/scope'
import { MediaThumb } from '@/components/shared/media-thumb'
import { Button } from '@/components/shadcn-ui/button'
import { Input } from '@/components/shadcn-ui/input'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { API_V1 } from '@/lib/api-client'
import { MEDIA_ICONS, MEDIA_TYPES } from '@/lib/media'
import { cn, formatDuration } from '@/lib/utils'
import type { MediaAsset, MediaType, Page } from '@/types/api'

function GridCard({
  asset,
  onAdd,
  onPlayNow,
  addLabel,
}: {
  asset: MediaAsset
  onAdd: (asset: MediaAsset) => void
  onPlayNow?: (asset: MediaAsset) => void
  addLabel?: string
}) {
  const dict = useDictionary()
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `lib:${asset.id}`,
  })
  const TypeIcon = MEDIA_ICONS[asset.type]

  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      className={cn(
        'bg-card hover:border-primary/50 group relative flex min-w-0 cursor-grab touch-none flex-col gap-1.5 rounded-lg border p-1.5 transition-colors active:cursor-grabbing',
        isDragging && 'opacity-40'
      )}
    >
      <MediaThumb id={asset.id} type={asset.type} className="aspect-video w-full" />
      <div className="min-w-0 px-0.5">
        <Tooltip>
          <TooltipTrigger asChild>
            <p className="truncate text-xs font-medium">{asset.name}</p>
          </TooltipTrigger>
          <TooltipContent>{asset.name}</TooltipContent>
        </Tooltip>
        <p className="text-muted-foreground flex items-center gap-1 truncate text-[11px]">
          <TypeIcon className="size-3 shrink-0" />
          <span className="truncate">
            {dict.media.types[asset.type]}
            {asset.type === 'VIDEO' && asset.durationMs
              ? ` · ${formatDuration(asset.durationMs / 1000)}`
              : ''}
          </span>
        </p>
      </div>
      <div className="absolute top-2.5 right-2.5 flex gap-1">
        {onPlayNow ? (
          <Button
            size="icon-sm"
            variant="secondary"
            className="size-6 opacity-90"
            aria-label={dict.content.playNow}
            title={dict.content.playNow}
            onPointerDown={(event) => event.stopPropagation()}
            onClick={() => onPlayNow(asset)}
          >
            <IconPlayerPlay />
          </Button>
        ) : null}
        <Button
          size="icon-sm"
          variant="secondary"
          className="size-6 opacity-90"
          aria-label={addLabel ?? dict.playlists.addToTimeline}
          title={addLabel ?? dict.playlists.addToTimeline}
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => onAdd(asset)}
        >
          <IconPlus />
        </Button>
      </div>
    </div>
  )
}

function LibraryCard({
  asset,
  onAdd,
  onPlayNow,
  addLabel,
}: {
  asset: MediaAsset
  onAdd: (asset: MediaAsset) => void
  onPlayNow?: (asset: MediaAsset) => void
  addLabel?: string
}) {
  const dict = useDictionary()
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `lib:${asset.id}`,
  })
  const TypeIcon = MEDIA_ICONS[asset.type]

  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      className={cn(
        'bg-card hover:border-primary/50 flex cursor-grab touch-none items-center gap-2.5 rounded-lg border p-1.5 transition-colors active:cursor-grabbing',
        isDragging && 'opacity-40'
      )}
    >
      <MediaThumb id={asset.id} type={asset.type} className="size-12 shrink-0" />
      <div className="min-w-0 flex-1">
        <Tooltip>
          <TooltipTrigger asChild>
            <p className="truncate text-sm font-medium">{asset.name}</p>
          </TooltipTrigger>
          <TooltipContent>{asset.name}</TooltipContent>
        </Tooltip>
        <p className="text-muted-foreground flex items-center gap-1 text-xs">
          <TypeIcon className="size-3" />
          {dict.media.types[asset.type]}
          {asset.type === 'VIDEO' && asset.durationMs
            ? ` · ${formatDuration(asset.durationMs / 1000)}`
            : ''}
        </p>
      </div>
      {onPlayNow ? (
        <Button
          size="icon-sm"
          variant="ghost"
          aria-label={dict.content.playNow}
          title={dict.content.playNow}
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => onPlayNow(asset)}
        >
          <IconPlayerPlay />
        </Button>
      ) : null}
      <Button
        size="icon-sm"
        variant="ghost"
        aria-label={addLabel ?? dict.playlists.addToTimeline}
        onPointerDown={(event) => event.stopPropagation()}
        onClick={() => onAdd(asset)}
      >
        <IconPlus />
      </Button>
    </div>
  )
}

export function LibraryPanel({
  onAdd,
  onPlayNow,
  onLoaded,
  className,
  layout = 'list',
  addLabel,
}: {
  onAdd: (asset: MediaAsset) => void
  onPlayNow?: (asset: MediaAsset) => void
  onLoaded?: (assets: MediaAsset[]) => void
  className?: string
  layout?: 'list' | 'grid'
  addLabel?: string
}) {
  const dict = useDictionary()
  const scope = useFolderScope()
  const [type, setType] = useState<MediaType | null>(null)

  const params = scopeParams(scope)
  if (type) params.set('type', type)
  params.set('size', '200')
  const media = useApiResource<Page<MediaAsset>>(`${API_V1}/media?${params.toString()}`)
  const assets = (media.data?.content ?? []).filter((asset) => asset.status === 'READY')

  useEffect(() => {
    if (media.data) onLoaded?.(media.data.content)
  }, [media.data, onLoaded])

  return (
    <div className={cn('flex min-h-0 flex-col gap-3', className)}>
      <div className="relative">
        <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
        <Input
          className="pl-8"
          placeholder={dict.media.search}
          value={scope.query}
          onChange={(event) => scope.setQuery(event.target.value)}
        />
      </div>
      <div className="flex flex-wrap gap-1">
        <Button
          size="xs"
          variant={type === null ? 'default' : 'outline'}
          onClick={() => setType(null)}
        >
          {dict.media.all}
        </Button>
        {MEDIA_TYPES.map((entry) => (
          <Button
            key={entry}
            size="xs"
            variant={type === entry ? 'default' : 'outline'}
            onClick={() => setType(type === entry ? null : entry)}
          >
            {dict.media.types[entry]}
          </Button>
        ))}
      </div>
      <PickerFolders scope={scope} />
      <div
        className={cn(
          'min-h-0 flex-1 overflow-y-auto pr-1',
          layout === 'grid'
            ? 'grid grid-cols-[repeat(auto-fill,minmax(8.5rem,1fr))] content-start gap-2'
            : 'space-y-1.5'
        )}
      >
        {assets.length === 0 && !media.loading ? (
          <p className="text-muted-foreground col-span-full py-8 text-center text-sm">
            {dict.playlists.libraryEmpty}
          </p>
        ) : (
          assets.map((asset) =>
            layout === 'grid' ? (
              <GridCard
                key={asset.id}
                asset={asset}
                onAdd={onAdd}
                onPlayNow={onPlayNow}
                addLabel={addLabel}
              />
            ) : (
              <LibraryCard
                key={asset.id}
                asset={asset}
                onAdd={onAdd}
                onPlayNow={onPlayNow}
                addLabel={addLabel}
              />
            )
          )
        )}
      </div>
    </div>
  )
}
