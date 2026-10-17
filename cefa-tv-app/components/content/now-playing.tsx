'use client'

import { useMemo } from 'react'
import { IconBroadcast, IconClockPlay } from '@tabler/icons-react'
import { dayFormatter, describeWhen } from '@/components/schedule/helpers'
import { ThumbRow, resolveThumbItems } from '@/components/schedule/thumb-strip'
import { DeliveryBadge, deliveryKind } from '@/components/shared/delivery-state'
import { Badge } from '@/components/shadcn-ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useDictionary } from '@/contexts/dictionary-context'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useApiResource } from '@/hooks/use-api-resource'
import { asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { resolveNow, type PlaybackTarget } from '@/lib/schedule-logic'
import { dayDifference, differsFromBrowser, zonedClock } from '@/lib/zoned-time'
import type { Page, Player, Playlist, Schedule } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

export function NowPlayingPanel({
  target,
  player,
  groupPlayers,
}: {
  target: PlaybackTarget
  player?: Player
  groupPlayers?: Player[]
}) {
  const dict = useDictionary()
  const { locale } = useDateFormatter()
  const fmt = useFormat()
  const schedules = useApiResource<Schedule[] | Page<Schedule>>(`${API_V1}/schedules`, {
    intervalMs: 10000,
  })
  const playlists = useApiResource<Playlist[] | Page<Playlist>>(`${API_V1}/playlists`)

  const state = useMemo(() => resolveNow(asList(schedules.data), target), [schedules.data, target])
  const playlistList = asList(playlists.data)
  const fallbackPlaylist = playlistList.find((playlist) => playlist.id === state.fallbackPlaylistId)
  const fallbackName = fallbackPlaylist?.name
  const thumbs = state.schedule
    ? resolveThumbItems(
        state.schedule.thumbnailUrls,
        playlistList.find((playlist) => playlist.id === state.schedule?.playlistId)
      )
    : resolveThumbItems(undefined, fallbackPlaylist)

  const labels = dict.nowPlaying
  const active = state.schedule
  const title = active
    ? active.name
    : state.source === 'fallback'
      ? (fallbackName ?? labels.defaultContent)
      : labels.nothing

  const reason = active
    ? labels.reasonSchedule
        .replace('{name}', active.name)
        .replace('{when}', describeWhen(active, dict, dayFormatter(locale)))
        .replace('{priority}', String(active.priority))
    : state.source === 'fallback'
      ? labels.reasonFallback
      : labels.reasonIdle

  const nextZone = state.next ? (target.timezone ?? state.next.schedule.timezone) : null
  const nextOffset = state.next ? dayDifference(new Date(), state.next.at, nextZone) : null
  const nextWhen =
    nextOffset === null
      ? null
      : nextOffset <= 0
        ? labels.today
        : nextOffset === 1
          ? labels.tomorrow
          : fmt(labels.inDays, { count: nextOffset })
  const nextClock = state.next
    ? `${zonedClock(state.next.at, nextZone)}${differsFromBrowser(nextZone) ? ` (${nextZone})` : ''}`
    : ''

  const groupSummary = groupPlayers
    ? {
        total: groupPlayers.length,
        upToDate: groupPlayers.filter((entry) => deliveryKind(entry) === 'published').length,
        onTheWay: groupPlayers.filter((entry) => deliveryKind(entry) === 'onTheWay').length,
        offline: groupPlayers.filter((entry) => deliveryKind(entry) === 'offline').length,
      }
    : null

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-2">
        <CardTitle className="flex items-center gap-2">
          <IconBroadcast className="size-5" /> {labels.title}
        </CardTitle>
        {player ? <DeliveryBadge player={player} long /> : null}
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-lg font-semibold">{title}</p>
          <Badge variant={active ? 'default' : 'outline'}>
            {active
              ? labels.sourceSchedule
              : state.source === 'fallback'
                ? labels.sourceDefault
                : labels.sourceNone}
          </Badge>
          {active ? (
            <Badge variant="secondary">
              {labels.priority.replace('{priority}', String(active.priority))}
            </Badge>
          ) : null}
        </div>
        {active?.playlistName ? (
          <p className="text-muted-foreground truncate text-xs">
            {dict.schedule.playlistLine.replace('{name}', active.playlistName)}
          </p>
        ) : null}
        <ThumbRow items={thumbs} max={6} />
        <p className="text-muted-foreground text-sm">{reason}</p>
        {state.next ? (
          <p className="flex items-center gap-2 rounded-lg border px-3 py-2 text-sm">
            <IconClockPlay className="text-muted-foreground size-4 shrink-0" />
            {labels.next
              .replace('{name}', state.next.schedule.playlistName ?? state.next.schedule.name)
              .replace('{day}', nextWhen ?? '')
              .replace('{time}', nextClock)}
          </p>
        ) : null}
        {groupSummary && groupSummary.total > 0 ? (
          <div className="flex flex-wrap gap-2 text-xs">
            <Badge variant="secondary" className="bg-success/15 text-success">
              {fmt(labels.groupUpToDate, { count: groupSummary.upToDate })}
            </Badge>
            {groupSummary.onTheWay > 0 ? (
              <Badge variant="secondary" className="bg-warning/15 text-warning">
                {fmt(labels.groupOnTheWay, { count: groupSummary.onTheWay })}
              </Badge>
            ) : null}
            {groupSummary.offline > 0 ? (
              <Badge variant="secondary" className="bg-destructive/10 text-destructive">
                {fmt(labels.groupOffline, { count: groupSummary.offline })}
              </Badge>
            ) : null}
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}
