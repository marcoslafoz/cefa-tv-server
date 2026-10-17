'use client'

import { useRef, useState } from 'react'
import { useDroppable } from '@dnd-kit/core'
import { useDictionary } from '@/contexts/dictionary-context'
import {
  MINUTES_PER_DAY,
  SNAP_MINUTES,
  placeOccurrences,
  snapMinutes,
  toneFor,
} from '@/components/schedule/helpers'
import { ThumbStrip, resolveThumbItems } from '@/components/schedule/thumb-strip'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { cn, clamp, formatDuration, minutesToTime, timeToMinutes } from '@/lib/utils'
import type { Playlist, TimelineOccurrence } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

export const PX_PER_HOUR = 56
export const PX_PER_MINUTE = PX_PER_HOUR / 60
export const LABEL_WIDTH = 168
const ROW_HEIGHT = 60

export interface Lane {
  id: string
  label: string
  sublabel?: string
  playerId: string
  date: string
  occurrences: TimelineOccurrence[]
  today: boolean
}

type DragMode = 'move' | 'start' | 'end'

interface ActiveDrag {
  key: string
  mode: DragMode
  originX: number
  startMin: number
  endMin: number
  moved: boolean
}

function BlockView({
  occurrence,
  startMin,
  endMin,
  row,
  playlist,
  onOpen,
  onCommit,
}: {
  occurrence: TimelineOccurrence
  startMin: number
  endMin: number
  row: number
  playlist: Playlist | undefined
  onOpen: (scheduleId: string) => void
  onCommit: (scheduleId: string, startTime: string, endTime: string) => void
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const key = `${occurrence.scheduleId}:${occurrence.date}`
  const dragRef = useRef<ActiveDrag | null>(null)
  const [preview, setPreview] = useState<{ start: number; end: number } | null>(null)

  const start = preview?.start ?? startMin
  const end = preview?.end ?? endMin

  function begin(event: React.PointerEvent<HTMLElement>, mode: DragMode) {
    event.stopPropagation()
    event.currentTarget.setPointerCapture(event.pointerId)
    dragRef.current = {
      key,
      mode,
      originX: event.clientX,
      startMin,
      endMin,
      moved: false,
    }
  }

  function move(event: React.PointerEvent<HTMLElement>) {
    const drag = dragRef.current
    if (!drag) return
    const deltaPx = event.clientX - drag.originX
    if (Math.abs(deltaPx) > 3) drag.moved = true
    if (!drag.moved) return
    const deltaMin = snapMinutes(deltaPx / PX_PER_MINUTE)
    if (drag.mode === 'move') {
      const length = drag.endMin - drag.startMin
      const nextStart = clamp(drag.startMin + deltaMin, 0, MINUTES_PER_DAY - length)
      setPreview({ start: nextStart, end: nextStart + length })
    } else if (drag.mode === 'start') {
      setPreview({
        start: clamp(drag.startMin + deltaMin, 0, drag.endMin - SNAP_MINUTES),
        end: drag.endMin,
      })
    } else {
      setPreview({
        start: drag.startMin,
        end: clamp(drag.endMin + deltaMin, drag.startMin + SNAP_MINUTES, MINUTES_PER_DAY),
      })
    }
  }

  function finish(event: React.PointerEvent<HTMLElement>) {
    const drag = dragRef.current
    dragRef.current = null
    event.currentTarget.releasePointerCapture(event.pointerId)
    if (!drag) return
    if (!drag.moved || !preview) {
      setPreview(null)
      onOpen(occurrence.scheduleId)
      return
    }
    const final = preview
    setPreview(null)
    if (final.start !== drag.startMin || final.end !== drag.endMin) {
      onCommit(occurrence.scheduleId, minutesToTime(final.start), minutesToTime(final.end))
    }
  }

  const widthPx = Math.max((end - start) * PX_PER_MINUTE, 8)
  const heightPx = ROW_HEIGHT - 8
  const thumbs = resolveThumbItems(occurrence.thumbnailUrls, playlist)
  const sequence = playlist?.items ?? []
  const itemCount = occurrence.itemCount ?? playlist?.items.length ?? thumbs.length
  const totalSec = occurrence.totalDurationSec ?? playlist?.totalDurationSec

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div
          className={cn(
            'group/block absolute z-[1] flex cursor-grab touch-none items-center overflow-hidden rounded-md border-2 text-xs select-none active:cursor-grabbing',
            toneFor(occurrence.playlistId),
            preview && 'ring-primary ring-2'
          )}
          style={{
            left: start * PX_PER_MINUTE,
            width: widthPx,
            top: row * ROW_HEIGHT + 4,
            height: heightPx,
          }}
          onPointerDown={(event) => begin(event, 'move')}
          onPointerMove={move}
          onPointerUp={finish}
        >
          <ThumbStrip items={thumbs} width={widthPx} height={heightPx} />
          <div className="from-background/90 via-background/45 pointer-events-none absolute inset-0 bg-gradient-to-r from-0% via-25% to-transparent to-55%" />
          <div
            role="separator"
            aria-label={dict.schedule.resizeStart}
            className="hover:bg-primary/40 absolute inset-y-0 left-0 w-1.5 cursor-ew-resize"
            onPointerDown={(event) => begin(event, 'start')}
            onPointerMove={move}
            onPointerUp={finish}
          />
          <div className="pointer-events-none relative flex min-w-0 flex-col px-2 leading-tight">
            <span className="truncate font-semibold drop-shadow-[0_0_4px_var(--background)]">
              {occurrence.name}
            </span>
            <span className="text-muted-foreground truncate tabular-nums drop-shadow-[0_0_4px_var(--background)]">
              {minutesToTime(start)}-{minutesToTime(end)}
            </span>
          </div>
          <div
            role="separator"
            aria-label={dict.schedule.resizeEnd}
            className="hover:bg-primary/40 absolute inset-y-0 right-0 w-1.5 cursor-ew-resize"
            onPointerDown={(event) => begin(event, 'end')}
            onPointerMove={move}
            onPointerUp={finish}
          />
        </div>
      </TooltipTrigger>
      <TooltipContent side="top" className="max-w-xs space-y-1.5 p-3">
        <p className="font-semibold">{occurrence.name}</p>
        {occurrence.playlistName ? (
          <p className="opacity-80">
            {dict.schedule.playlistLine.replace('{name}', occurrence.playlistName)}
          </p>
        ) : null}
        <p className="tabular-nums opacity-80">
          {minutesToTime(start)}-{minutesToTime(end)}
          {itemCount ? ` · ${fmt(dict.playlists.itemCount, { count: itemCount })}` : ''}
          {totalSec ? ` · ${formatDuration(totalSec)}` : ''}
        </p>
        {sequence.length > 0 ? (
          <ol className="space-y-0.5">
            {sequence.slice(0, 8).map((item, index) => (
              <li key={`${item.asset.id}-${index}`} className="flex justify-between gap-3">
                <span className="truncate">
                  {index + 1}. {item.asset.name}
                </span>
                <span className="tabular-nums opacity-80">{formatDuration(item.durationSec)}</span>
              </li>
            ))}
            {sequence.length > 8 ? (
              <li className="opacity-80">
                {fmt(dict.schedule.sequenceMore, { count: sequence.length - 8 })}
              </li>
            ) : null}
          </ol>
        ) : null}
      </TooltipContent>
    </Tooltip>
  )
}

function LaneRow({
  lane,
  playlistsById,
  onOpen,
  onCommit,
  nowMinutes,
}: {
  lane: Lane
  playlistsById: Map<string, Playlist>
  onOpen: (scheduleId: string) => void
  onCommit: (scheduleId: string, startTime: string, endTime: string) => void
  nowMinutes: number
}) {
  const { setNodeRef, isOver } = useDroppable({ id: `lane:${lane.id}` })
  const { placed, rows } = placeOccurrences(lane.occurrences, timeToMinutes)

  return (
    <div className="flex border-b last:border-b-0">
      <div
        className={cn(
          'bg-background sticky left-0 z-10 flex shrink-0 flex-col justify-center border-r px-3',
          lane.today && 'bg-primary/5'
        )}
        style={{ width: LABEL_WIDTH }}
      >
        <p className="truncate text-sm font-medium">{lane.label}</p>
        {lane.sublabel ? (
          <p className="text-muted-foreground truncate text-xs">{lane.sublabel}</p>
        ) : null}
      </div>
      <div
        ref={setNodeRef}
        data-lane={lane.id}
        className={cn(
          'relative shrink-0 transition-colors',
          isOver && 'bg-primary/10',
          lane.today && !isOver && 'bg-primary/[0.03]'
        )}
        style={{
          width: 24 * PX_PER_HOUR,
          height: rows * ROW_HEIGHT + 4,
          backgroundImage: 'linear-gradient(to right, var(--border) 1px, transparent 1px)',
          backgroundSize: `${PX_PER_HOUR}px 100%`,
        }}
      >
        {lane.today ? (
          <div
            className="bg-destructive absolute inset-y-0 z-[2] w-px"
            style={{ left: nowMinutes * PX_PER_MINUTE }}
          />
        ) : null}
        {placed.map((entry) => (
          <BlockView
            key={`${entry.occurrence.scheduleId}:${entry.occurrence.date}`}
            occurrence={entry.occurrence}
            startMin={entry.startMin}
            endMin={entry.endMin}
            row={entry.row}
            playlist={playlistsById.get(entry.occurrence.playlistId)}
            onOpen={onOpen}
            onCommit={onCommit}
          />
        ))}
      </div>
    </div>
  )
}

export function ScheduleBoard({
  lanes,
  playlistsById,
  onOpen,
  onCommit,
}: {
  lanes: Lane[]
  playlistsById: Map<string, Playlist>
  onOpen: (scheduleId: string) => void
  onCommit: (scheduleId: string, startTime: string, endTime: string) => void
}) {
  const now = new Date()
  const nowMinutes = now.getHours() * 60 + now.getMinutes()

  return (
    <div className="overflow-x-auto rounded-xl border">
      <div style={{ minWidth: LABEL_WIDTH + 24 * PX_PER_HOUR }}>
        <div className="bg-muted/40 flex border-b">
          <div
            className="bg-muted sticky left-0 z-10 shrink-0 border-r"
            style={{ width: LABEL_WIDTH }}
          />
          <div className="relative h-7 shrink-0" style={{ width: 24 * PX_PER_HOUR }}>
            {Array.from({ length: 24 }).map((_, hour) => (
              <span
                key={hour}
                className="text-muted-foreground absolute top-0 flex h-full items-center border-l pl-1 text-[10px] tabular-nums"
                style={{ left: hour * PX_PER_HOUR }}
              >
                {String(hour).padStart(2, '0')}:00
              </span>
            ))}
          </div>
        </div>
        {lanes.map((lane) => (
          <LaneRow
            key={lane.id}
            lane={lane}
            playlistsById={playlistsById}
            onOpen={onOpen}
            onCommit={onCommit}
            nowMinutes={nowMinutes}
          />
        ))}
      </div>
    </div>
  )
}
