import type { Schedule, ScheduleInput, ScheduleServerStatus, TimelineOccurrence } from '@/types/api'
import { parseIsoDate } from '@/lib/utils'

export const SNAP_MINUTES = 15
export const MINUTES_PER_DAY = 24 * 60

export const BLOCK_TONES = [
  'border-chart-1/70 bg-chart-1/20',
  'border-chart-2/70 bg-chart-2/20',
  'border-chart-3/70 bg-chart-3/20',
  'border-chart-4/70 bg-chart-4/20',
  'border-chart-5/70 bg-chart-5/20',
]

export function toneFor(id: string): string {
  let hash = 0
  for (let index = 0; index < id.length; index += 1) hash = (hash * 31 + id.charCodeAt(index)) >>> 0
  return BLOCK_TONES[hash % BLOCK_TONES.length]
}

export function snapMinutes(minutes: number): number {
  return Math.round(minutes / SNAP_MINUTES) * SNAP_MINUTES
}

export function scheduleToInput(schedule: Schedule): ScheduleInput {
  return {
    name: schedule.name,
    playlistId: schedule.playlistId,
    priority: schedule.priority,
    startDate: schedule.startDate,
    endDate: schedule.endDate,
    startTime: schedule.startTime,
    endTime: schedule.endTime,
    daysOfWeek: schedule.daysOfWeek,
    allDay: schedule.allDay,
    enabled: schedule.enabled,
    targets: schedule.targets,
  }
}

export interface PlacedOccurrence {
  occurrence: TimelineOccurrence
  startMin: number
  endMin: number
  row: number
}

export function placeOccurrences(
  occurrences: TimelineOccurrence[],
  toMinutes: (time: string) => number
): { placed: PlacedOccurrence[]; rows: number } {
  const sorted = occurrences
    .map((occurrence) => ({
      occurrence,
      startMin: occurrence.allDay ? 0 : toMinutes(occurrence.startTime),
      endMin: occurrence.allDay
        ? MINUTES_PER_DAY
        : occurrence.endTime === '00:00'
          ? MINUTES_PER_DAY
          : toMinutes(occurrence.endTime),
    }))
    .sort((a, b) => a.startMin - b.startMin)
  const rowEnds: number[] = []
  const placed = sorted.map((entry) => {
    let row = rowEnds.findIndex((end) => end <= entry.startMin)
    if (row === -1) {
      row = rowEnds.length
      rowEnds.push(entry.endMin)
    } else {
      rowEnds[row] = entry.endMin
    }
    return { ...entry, row }
  })
  return { placed, rows: Math.max(1, rowEnds.length) }
}

export const ALL_DAYS = [1, 2, 3, 4, 5, 6, 7]

export interface WhenFields {
  allDay: boolean
  startTime: string | null
  endTime: string | null
  daysOfWeek: number[]
  startDate: string | null
  endDate: string | null
}

type WhenDictionary = {
  schedule: {
    allDay: string
    weekdaysShort: string[]
    wizard: {
      alwaysSummary: string
      everyDay: string
      weekdays: string
      weekend: string
      slotSummary: string
      dateRange: string
      since: string
      until: string
    }
  }
}

function sameDays(days: number[], expected: number[]): boolean {
  return days.length === expected.length && expected.every((day) => days.includes(day))
}

export function dayFormatter(locale: string): (iso: string) => string {
  const formatter = new Intl.DateTimeFormat(locale, { dateStyle: 'medium' })
  return (iso) => formatter.format(parseIsoDate(iso))
}

export function describeWhen(
  fields: WhenFields,
  dict: WhenDictionary,
  formatDay: (iso: string) => string = (iso) => iso
): string {
  const wizard = dict.schedule.wizard
  const noDates = !fields.startDate && !fields.endDate
  if (fields.allDay && sameDays(fields.daysOfWeek, ALL_DAYS) && noDates) return wizard.alwaysSummary

  const days = sameDays(fields.daysOfWeek, ALL_DAYS)
    ? wizard.everyDay
    : sameDays(fields.daysOfWeek, [1, 2, 3, 4, 5])
      ? wizard.weekdays
      : sameDays(fields.daysOfWeek, [6, 7])
        ? wizard.weekend
        : [...fields.daysOfWeek]
            .sort()
            .map((day) => dict.schedule.weekdaysShort[day - 1])
            .join(', ')

  const parts = [
    fields.allDay
      ? `${days}, ${dict.schedule.allDay.toLowerCase()}`
      : wizard.slotSummary
          .replace('{days}', days)
          .replace('{from}', fields.startTime ?? '')
          .replace('{to}', fields.endTime ?? ''),
  ]
  if (fields.startDate && fields.endDate) {
    parts.push(
      wizard.dateRange
        .replace('{from}', formatDay(fields.startDate))
        .replace('{to}', formatDay(fields.endDate))
    )
  } else if (fields.startDate) {
    parts.push(wizard.since.replace('{from}', formatDay(fields.startDate)))
  } else if (fields.endDate) {
    parts.push(wizard.until.replace('{to}', formatDay(fields.endDate)))
  }
  return parts.join(' · ')
}

export type ScheduleStatus = 'live' | 'scheduled' | 'ended' | 'disabled'

const SERVER_STATUS: Record<ScheduleServerStatus, ScheduleStatus> = {
  ACTIVE_NOW: 'live',
  SCHEDULED: 'scheduled',
  ENDED: 'ended',
  DISABLED: 'disabled',
}

export function scheduleStatus(schedule: Schedule): ScheduleStatus {
  return SERVER_STATUS[schedule.status ?? (schedule.enabled ? 'SCHEDULED' : 'DISABLED')]
}

export function nextRunDate(schedule: Schedule): Date | null {
  return schedule.nextRunAt ? new Date(schedule.nextRunAt) : null
}
