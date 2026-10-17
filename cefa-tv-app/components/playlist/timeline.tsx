'use client'

import { useRef } from 'react'
import { useDroppable } from '@dnd-kit/core'
import { SortableContext, horizontalListSortingStrategy, useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { IconCopy, IconTrash } from '@tabler/icons-react'
import { MediaThumb } from '@/components/shared/media-thumb'
import { useDictionary } from '@/contexts/dictionary-context'
import { effectiveSeconds, type EditorItem } from '@/components/playlist/types'
import { MEDIA_BLOCK_TONES, MEDIA_ICONS, hasFixedDuration } from '@/lib/media'
import { cn, formatDuration } from '@/lib/utils'
import type { MediaAsset } from '@/types/api'

const MIN_BLOCK_PX = 56
const RULER_STEPS = [1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600]

export const TRACK_ID = 'track'

function blockWidth(seconds: number, pxPerSec: number): number {
  return Math.max(MIN_BLOCK_PX, seconds * pxPerSec)
}

function TimelineBlock({
  item,
  asset,
  pxPerSec,
  selected,
  onSelect,
  onResize,
  onResizeStart,
  onRemove,
  onDuplicate,
}: {
  item: EditorItem
  asset: MediaAsset | undefined
  pxPerSec: number
  selected: boolean
  onSelect: () => void
  onResize: (durationSec: number) => void
  onResizeStart?: () => void
  onRemove: () => void
  onDuplicate?: () => void
}) {
  const dict = useDictionary()
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.uid,
  })
  const startRef = useRef<{ x: number; duration: number } | null>(null)
  const type = asset?.type ?? 'IMAGE'
  const TypeIcon = MEDIA_ICONS[type]
  const seconds = effectiveSeconds(item, asset)
  const resizable = !hasFixedDuration(type)
  const perUnit = type === 'PDF' ? Math.max(1, asset?.pageCount ?? 1) : 1

  function onHandleDown(event: React.PointerEvent<HTMLDivElement>) {
    event.stopPropagation()
    event.preventDefault()
    startRef.current = { x: event.clientX, duration: item.durationSec }
    onResizeStart?.()
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function onHandleMove(event: React.PointerEvent<HTMLDivElement>) {
    const start = startRef.current
    if (!start) return
    const deltaSec = (event.clientX - start.x) / (pxPerSec * perUnit)
    onResize(Math.max(1, Math.round(start.duration + deltaSec)))
  }

  function onHandleUp(event: React.PointerEvent<HTMLDivElement>) {
    startRef.current = null
    event.currentTarget.releasePointerCapture(event.pointerId)
  }

  return (
    <div
      ref={setNodeRef}
      style={{
        width: blockWidth(seconds, pxPerSec),
        transform: CSS.Translate.toString(transform),
        transition,
      }}
      className={cn(
        'group/block relative h-24 shrink-0 touch-none overflow-hidden rounded-lg border-2 select-none',
        MEDIA_BLOCK_TONES[type],
        selected && 'ring-primary ring-offset-background ring-2 ring-offset-2',
        isDragging && 'opacity-30'
      )}
      onClick={onSelect}
      {...attributes}
      {...listeners}
    >
      <div className="flex h-full cursor-grab items-stretch gap-2 p-1.5 active:cursor-grabbing">
        {asset ? (
          <MediaThumb id={asset.id} type={asset.type} className="aspect-square h-full shrink-0" />
        ) : null}
        <div className="flex min-w-0 flex-1 flex-col justify-between py-0.5">
          <p className="truncate text-xs font-medium">
            {asset?.name ?? dict.playlists.missingAsset}
          </p>
          <p className="text-muted-foreground flex items-center gap-1 text-xs tabular-nums">
            <TypeIcon className="size-3" />
            {formatDuration(seconds)}
          </p>
        </div>
      </div>
      <div className="absolute top-1 right-3 hidden gap-0.5 group-hover/block:flex">
        {onDuplicate ? (
          <button
            type="button"
            aria-label={dict.playlists.studio.duplicateItem}
            className="bg-background/80 rounded p-0.5"
            onPointerDown={(event) => event.stopPropagation()}
            onClick={(event) => {
              event.stopPropagation()
              onDuplicate()
            }}
          >
            <IconCopy className="size-3.5" />
          </button>
        ) : null}
        <button
          type="button"
          aria-label={dict.common.delete}
          className="bg-background/80 text-destructive rounded p-0.5"
          onPointerDown={(event) => event.stopPropagation()}
          onClick={(event) => {
            event.stopPropagation()
            onRemove()
          }}
        >
          <IconTrash className="size-3.5" />
        </button>
      </div>
      {resizable ? (
        <div
          role="separator"
          aria-label={dict.playlists.resize}
          className="hover:bg-primary/40 absolute inset-y-0 right-0 w-2 cursor-ew-resize"
          onPointerDown={onHandleDown}
          onPointerMove={onHandleMove}
          onPointerUp={onHandleUp}
        />
      ) : null}
    </div>
  )
}

function Ruler({ totalSec, pxPerSec }: { totalSec: number; pxPerSec: number }) {
  const step = RULER_STEPS.find((candidate) => candidate * pxPerSec >= 72) ?? 3600
  const span = Math.max(totalSec + step * 2, step * 6)
  const ticks: number[] = []
  for (let second = 0; second <= span; second += step) ticks.push(second)

  return (
    <div className="relative h-6 border-b" style={{ width: span * pxPerSec }}>
      {ticks.map((second) => (
        <div
          key={second}
          className="text-muted-foreground absolute top-0 flex h-full items-end border-l pl-1 text-[10px] tabular-nums"
          style={{ left: second * pxPerSec }}
        >
          {formatDuration(second)}
        </div>
      ))}
    </div>
  )
}

export function Timeline({
  items,
  assets,
  pxPerSec,
  selectedUid,
  onSelect,
  onResize,
  onResizeStart,
  onRemove,
  onDuplicate,
}: {
  items: EditorItem[]
  assets: Map<string, MediaAsset>
  pxPerSec: number
  selectedUid: string | null
  onSelect: (uid: string) => void
  onResize: (uid: string, durationSec: number) => void
  onResizeStart?: () => void
  onRemove: (uid: string) => void
  onDuplicate?: (uid: string) => void
}) {
  const dict = useDictionary()
  const { setNodeRef, isOver } = useDroppable({ id: TRACK_ID })
  const totalSec = items.reduce(
    (sum, item) => sum + effectiveSeconds(item, assets.get(item.assetId)),
    0
  )
  const totalPx = items.reduce(
    (sum, item) => sum + blockWidth(effectiveSeconds(item, assets.get(item.assetId)), pxPerSec),
    0
  )

  return (
    <div className="overflow-x-auto rounded-xl border">
      <div style={{ minWidth: Math.max(totalPx + 160, 640) }}>
        <Ruler totalSec={totalSec} pxPerSec={pxPerSec} />
        <div
          ref={setNodeRef}
          className={cn(
            'bg-muted/30 flex min-h-32 items-center gap-0.5 p-3 transition-colors',
            isOver && 'bg-primary/5'
          )}
        >
          <SortableContext
            items={items.map((item) => item.uid)}
            strategy={horizontalListSortingStrategy}
          >
            {items.map((item) => (
              <TimelineBlock
                key={item.uid}
                item={item}
                asset={assets.get(item.assetId)}
                pxPerSec={pxPerSec}
                selected={selectedUid === item.uid}
                onSelect={() => onSelect(item.uid)}
                onResize={(durationSec) => onResize(item.uid, durationSec)}
                onResizeStart={onResizeStart}
                onRemove={() => onRemove(item.uid)}
                onDuplicate={onDuplicate ? () => onDuplicate(item.uid) : undefined}
              />
            ))}
          </SortableContext>
          {items.length === 0 ? (
            <div className="w-full space-y-1 py-6 text-center">
              <p className="text-sm font-medium">{dict.playlists.studio.emptyTitle}</p>
              <p className="text-muted-foreground text-sm">{dict.playlists.timelineEmpty}</p>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  )
}
