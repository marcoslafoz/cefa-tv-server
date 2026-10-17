import { timeToMinutes } from '@/lib/utils'
import type { Player, Schedule, ScheduleTargets } from '@/types/api'

const DAY_MINUTES = 24 * 60

export interface Slot {
  allDay: boolean
  startTime: string | null
  endTime: string | null
  daysOfWeek: number[]
  startDate: string | null
  endDate: string | null
}

export interface Conflict {
  schedule: Schedule
  winner: 'existing' | 'new'
  playerIds: string[]
}

export interface PlaybackTarget {
  playerId?: string
  groupId: string | null
  siteId?: string | null
  timezone: string | null
  fallbackPlaylistId: string | null
}

export interface NextSlot {
  schedule: Schedule
  at: Date
}

export interface NowState {
  schedule: Schedule | null
  source: 'schedule' | 'fallback' | 'idle'
  fallbackPlaylistId: string | null
  next: NextSlot | null
}

function minuteRanges(slot: Slot): Array<[number, number]> {
  if (slot.allDay || !slot.startTime || !slot.endTime) return [[0, DAY_MINUTES]]
  const start = timeToMinutes(slot.startTime)
  const end = timeToMinutes(slot.endTime)
  if (end === 0) return [[start, DAY_MINUTES]]
  if (end <= start) {
    return [
      [start, DAY_MINUTES],
      [0, end],
    ]
  }
  return [[start, end]]
}

function startMinutes(slot: Slot): number {
  return slot.allDay || !slot.startTime ? 0 : timeToMinutes(slot.startTime)
}

export function slotsOverlap(left: Slot, right: Slot): boolean {
  const sharedDay = left.daysOfWeek.some((day) => right.daysOfWeek.includes(day))
  if (!sharedDay) return false
  const leftEnd = left.endDate ?? '9999-12-31'
  const rightEnd = right.endDate ?? '9999-12-31'
  const leftStart = left.startDate ?? '0000-01-01'
  const rightStart = right.startDate ?? '0000-01-01'
  if (leftStart > rightEnd || rightStart > leftEnd) return false
  const rightRanges = minuteRanges(right)
  return minuteRanges(left).some(([from, to]) =>
    rightRanges.some(([otherFrom, otherTo]) => from < otherTo && otherFrom < to)
  )
}

export function expandTargets(targets: ScheduleTargets, players: Player[]): string[] {
  const siteIds = targets.siteIds ?? []
  return players
    .filter(
      (player) =>
        targets.playerIds.includes(player.id) ||
        (player.groupId !== null && targets.groupIds.includes(player.groupId)) ||
        (Boolean(player.siteId) && siteIds.includes(player.siteId as string))
    )
    .map((player) => player.id)
}

export function findConflicts(
  candidate: { slot: Slot; priority: number; targets: ScheduleTargets },
  schedules: Schedule[],
  players: Player[],
  ignoreScheduleId?: string
): Conflict[] {
  const candidatePlayers = new Set(expandTargets(candidate.targets, players))
  if (candidatePlayers.size === 0) return []
  const conflicts: Conflict[] = []
  for (const schedule of schedules) {
    if (!schedule.enabled || schedule.id === ignoreScheduleId) continue
    if (!slotsOverlap(candidate.slot, schedule)) continue
    const shared = expandTargets(schedule.targets, players).filter((id) => candidatePlayers.has(id))
    if (shared.length === 0) continue
    let winner: 'existing' | 'new' = 'existing'
    if (candidate.priority > schedule.priority) winner = 'new'
    else if (candidate.priority === schedule.priority) {
      winner = startMinutes(candidate.slot) > startMinutes(schedule) ? 'new' : 'existing'
    }
    conflicts.push({ schedule, winner, playerIds: shared })
  }
  return conflicts
}

function targetsPlayback(schedule: Schedule, target: PlaybackTarget): boolean {
  if (target.playerId !== undefined && schedule.targets.playerIds.includes(target.playerId)) {
    return true
  }
  if (target.groupId !== null && schedule.targets.groupIds.includes(target.groupId)) return true
  return (
    Boolean(target.siteId) && (schedule.targets.siteIds ?? []).includes(target.siteId as string)
  )
}

function startMinutesOf(schedule: Schedule): number {
  return startMinutes(schedule)
}

export function resolveNow(schedules: Schedule[], target: PlaybackTarget): NowState {
  const relevant = schedules.filter(
    (schedule) => schedule.enabled && targetsPlayback(schedule, target)
  )
  const active = relevant
    .filter((schedule) => schedule.status === 'ACTIVE_NOW')
    .sort(
      (left, right) =>
        right.priority - left.priority || startMinutesOf(right) - startMinutesOf(left)
    )[0]
  const upcoming = relevant
    .filter((schedule) => schedule.status === 'SCHEDULED' && schedule.nextRunAt)
    .sort(
      (left, right) => Date.parse(left.nextRunAt as string) - Date.parse(right.nextRunAt as string)
    )[0]
  const next: NextSlot | null = upcoming
    ? { schedule: upcoming, at: new Date(upcoming.nextRunAt as string) }
    : null
  if (active) {
    return {
      schedule: active,
      source: 'schedule',
      fallbackPlaylistId: target.fallbackPlaylistId,
      next,
    }
  }
  return {
    schedule: null,
    source: target.fallbackPlaylistId ? 'fallback' : 'idle',
    fallbackPlaylistId: target.fallbackPlaylistId,
    next,
  }
}
