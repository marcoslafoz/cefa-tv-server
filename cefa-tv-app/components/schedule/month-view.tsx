'use client'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { useMemo } from 'react'
import { useDroppable } from '@dnd-kit/core'
import { IconCalendarPlus } from '@tabler/icons-react'
import { toneFor } from '@/components/schedule/helpers'
import { ThumbCell, resolveThumbItems } from '@/components/schedule/thumb-strip'
import { Button } from '@/components/shadcn-ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/shadcn-ui/popover'
import { useDictionary } from '@/contexts/dictionary-context'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useFormat } from '@/hooks/use-format'
import { ROW_CLASS, rowActivation } from '@/lib/row-activation'
import { cn, parseIsoDate, toIsoDate } from '@/lib/utils'
import type { Playlist, TimelineOccurrence } from '@/types/api'

const MAX_CHIPS = 3

export function monthGridDays(month: Date): Date[] {
  const first = new Date(month.getFullYear(), month.getMonth(), 1)
  const offset = (first.getDay() + 6) % 7
  const start = new Date(first)
  start.setDate(first.getDate() - offset)
  return Array.from({ length: 42 }, (_, index) => {
    const day = new Date(start)
    day.setDate(start.getDate() + index)
    return day
  })
}

function timeLabel(occurrence: TimelineOccurrence, allDay: string) {
  return occurrence.allDay ? allDay : occurrence.startTime
}

function Chip({
  occurrence,
  playlist,
  onOpen,
}: {
  occurrence: TimelineOccurrence
  playlist: Playlist | undefined
  onOpen: (scheduleId: string) => void
}) {
  const dict = useDictionary()
  const thumb = resolveThumbItems(occurrence.thumbnailUrls, playlist)[0]
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onOpen(occurrence.scheduleId)
          }}
          className={cn(
            'flex w-full min-w-0 items-center gap-1 overflow-hidden rounded border px-1 py-0.5 text-left text-[11px] leading-tight',
            toneFor(occurrence.playlistId)
          )}
        >
          {thumb ? <ThumbCell item={thumb} className="size-3.5 shrink-0 rounded-sm" /> : null}
          <span className="shrink-0 tabular-nums">
            {timeLabel(occurrence, dict.schedule.allDay)}
          </span>
          <span className="truncate font-medium">{occurrence.name}</span>
        </button>
      </TooltipTrigger>
      <TooltipContent>{`${occurrence.name} · ${timeLabel(occurrence, dict.schedule.allDay)}`}</TooltipContent>
    </Tooltip>
  )
}

function DayCell({
  day,
  inMonth,
  today,
  occurrences,
  playlistsById,
  onOpenDay,
  onOpenSchedule,
}: {
  day: Date
  inMonth: boolean
  today: boolean
  occurrences: TimelineOccurrence[]
  playlistsById: Map<string, Playlist>
  onOpenDay: (iso: string) => void
  onOpenSchedule: (scheduleId: string) => void
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const iso = toIsoDate(day)
  const { setNodeRef, isOver } = useDroppable({ id: `day:${iso}` })
  const visible = occurrences.slice(0, MAX_CHIPS)
  const hidden = occurrences.slice(MAX_CHIPS)

  return (
    <div
      ref={setNodeRef}
      {...rowActivation(() => onOpenDay(iso), 'button')}
      aria-label={new Intl.DateTimeFormat(undefined, { dateStyle: 'full' }).format(day)}
      className={cn(
        'hover:bg-accent/40 flex min-h-24 min-w-0 flex-col gap-1 border-r border-b p-1',
        ROW_CLASS,
        !inMonth && 'bg-muted/30 text-muted-foreground',
        isOver && 'ring-primary bg-primary/10 ring-2 ring-inset'
      )}
    >
      <span
        className={cn(
          'flex size-6 items-center justify-center rounded-full text-xs font-medium tabular-nums',
          today && 'bg-primary text-primary-foreground'
        )}
      >
        {day.getDate()}
      </span>
      <div className={cn('flex min-w-0 flex-col gap-0.5', !inMonth && 'opacity-60')}>
        {visible.map((occurrence) => (
          <Chip
            key={`${occurrence.scheduleId}-${occurrence.startTime}`}
            occurrence={occurrence}
            playlist={playlistsById.get(occurrence.playlistId)}
            onOpen={onOpenSchedule}
          />
        ))}
        {hidden.length > 0 ? (
          <Popover>
            <PopoverTrigger asChild>
              <button
                type="button"
                onClick={(event) => event.stopPropagation()}
                className="text-muted-foreground hover:text-foreground px-1 text-left text-[11px] font-medium"
              >
                {fmt(dict.schedule.month.more, { count: hidden.length })}
              </button>
            </PopoverTrigger>
            <PopoverContent align="start" className="w-64 space-y-1 p-2">
              {hidden.map((occurrence) => (
                <Chip
                  key={`${occurrence.scheduleId}-${occurrence.startTime}`}
                  occurrence={occurrence}
                  playlist={playlistsById.get(occurrence.playlistId)}
                  onOpen={onOpenSchedule}
                />
              ))}
            </PopoverContent>
          </Popover>
        ) : null}
      </div>
    </div>
  )
}

export function MonthView({
  month,
  todayIso,
  occurrences,
  playlistsById,
  selectedDay,
  onSelectDay,
  onOpenSchedule,
  onProgramDay,
}: {
  month: Date
  todayIso: string
  occurrences: TimelineOccurrence[]
  playlistsById: Map<string, Playlist>
  selectedDay: string | null
  onSelectDay: (iso: string | null) => void
  onOpenSchedule: (scheduleId: string) => void
  onProgramDay: (iso: string) => void
}) {
  const dict = useDictionary()
  const { locale } = useDateFormatter()
  const days = useMemo(() => monthGridDays(month), [month])
  const byDay = useMemo(() => {
    const map = new Map<string, TimelineOccurrence[]>()
    for (const occurrence of occurrences) {
      const list = map.get(occurrence.date) ?? []
      list.push(occurrence)
      map.set(occurrence.date, list)
    }
    for (const list of map.values()) {
      list.sort((a, b) => (a.allDay ? '' : a.startTime).localeCompare(b.allDay ? '' : b.startTime))
    }
    return map
  }, [occurrences])
  const weekdayFormat = new Intl.DateTimeFormat(locale, { weekday: 'short' })
  const dayList = selectedDay ? (byDay.get(selectedDay) ?? []) : []

  return (
    <>
      <div className="overflow-hidden rounded-xl border">
        <div className="bg-muted/40 grid grid-cols-7 border-b">
          {days.slice(0, 7).map((day) => (
            <div
              key={day.getDay()}
              className="text-muted-foreground truncate px-2 py-1.5 text-xs font-medium capitalize"
            >
              {weekdayFormat.format(day)}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7 [&>*:nth-child(7n)]:border-r-0">
          {days.map((day) => {
            const iso = toIsoDate(day)
            return (
              <DayCell
                key={iso}
                day={day}
                inMonth={day.getMonth() === month.getMonth()}
                today={iso === todayIso}
                occurrences={byDay.get(iso) ?? []}
                playlistsById={playlistsById}
                onOpenDay={onSelectDay}
                onOpenSchedule={onOpenSchedule}
              />
            )
          })}
        </div>
      </div>

      <Dialog open={selectedDay !== null} onOpenChange={(open) => !open && onSelectDay(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="capitalize">
              {selectedDay
                ? new Intl.DateTimeFormat(locale, { dateStyle: 'full' }).format(
                    parseIsoDate(selectedDay)
                  )
                : ''}
            </DialogTitle>
            <DialogDescription>{dict.schedule.month.dayDescription}</DialogDescription>
          </DialogHeader>
          {dayList.length === 0 ? (
            <p className="text-muted-foreground py-4 text-center text-sm">
              {dict.schedule.month.dayEmpty}
            </p>
          ) : (
            <ul className="space-y-1.5">
              {dayList.map((occurrence) => (
                <li key={`${occurrence.scheduleId}-${occurrence.startTime}`}>
                  <button
                    type="button"
                    onClick={() => {
                      onSelectDay(null)
                      onOpenSchedule(occurrence.scheduleId)
                    }}
                    className={cn(
                      'flex w-full items-center justify-between gap-3 rounded-lg border px-3 py-2 text-left text-sm',
                      toneFor(occurrence.playlistId)
                    )}
                  >
                    <span className="truncate font-medium">{occurrence.name}</span>
                    <span className="shrink-0 text-xs tabular-nums">
                      {occurrence.allDay
                        ? dict.schedule.allDay
                        : `${occurrence.startTime} - ${occurrence.endTime}`}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          <DialogFooter>
            <Button
              onClick={() => {
                if (selectedDay) onProgramDay(selectedDay)
                onSelectDay(null)
              }}
            >
              <IconCalendarPlus /> {dict.schedule.month.programDay}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
