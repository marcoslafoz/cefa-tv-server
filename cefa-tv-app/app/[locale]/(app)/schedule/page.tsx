'use client'

import { Suspense, useCallback, useEffect, useMemo, useState } from 'react'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  pointerWithin,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import {
  IconCalendarPlus,
  IconChevronLeft,
  IconChevronRight,
  IconDeviceTv,
  IconPlaylist,
} from '@tabler/icons-react'
import { ScheduleBoard, PX_PER_MINUTE, type Lane } from '@/components/schedule/board'
import { SNAP_MINUTES, scheduleToInput, snapMinutes } from '@/components/schedule/helpers'
import { ScheduleLegend } from '@/components/schedule/legend'
import { MonthView, monthGridDays } from '@/components/schedule/month-view'
import { ScheduleTable } from '@/components/schedule/schedule-table'
import { SchedulePalette } from '@/components/schedule/palette'
import { ScheduleDialog, type ScheduleDraft } from '@/components/schedule/schedule-dialog'
import { operableGroups, operablePlayers } from '@/components/schedule/target-picker'
import { ScheduleWizard, type WizardInitial } from '@/components/schedule/schedule-wizard'
import { RequirePermission } from '@/components/shared/require-permission'
import { PageHeader } from '@/components/shared/page-header'
import { EmptyState, ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { Tabs, TabsList, TabsTrigger } from '@/components/shadcn-ui/tabs'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { api, asList } from '@/lib/api'
import { apiErrorMessage } from '@/lib/api-client'
import { defaultDurationSec } from '@/lib/media'
import { toast } from '@/lib/toast'
import { cn, clamp, isoWeekday, minutesToTime, parseIsoDate, toIsoDate } from '@/lib/utils'
import { differsFromBrowser } from '@/lib/zoned-time'
import type {
  MediaAsset,
  Player,
  PlayerGroup,
  Playlist,
  Schedule,
  Site,
  TimelineOccurrence,
} from '@/types/api'

type ViewMode = 'day' | 'week' | 'month' | 'table'

const VIEWS: ViewMode[] = ['day', 'week', 'month', 'table']
const VIEW_STORAGE_KEY = 'cefa-tv.schedule-view'

function parseView(value: string | null): ViewMode | null {
  return VIEWS.includes(value as ViewMode) ? (value as ViewMode) : null
}

function storedView(): ViewMode | null {
  try {
    return parseView(window.localStorage.getItem(VIEW_STORAGE_KEY))
  } catch {
    return null
  }
}

const ALL_PLAYERS = 'all'
const DEFAULT_BLOCK_MINUTES = 60

function startOfWeek(date: Date): Date {
  const result = new Date(date)
  result.setDate(result.getDate() - (isoWeekday(result) - 1))
  return result
}

function addDays(date: Date, days: number): Date {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}

function ScheduleContent() {
  const dict = useDictionary()
  const { user } = useAuth()
  const { locale } = useDateFormatter()
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [players, setPlayers] = useState<Player[]>([])
  const [groups, setGroups] = useState<PlayerGroup[]>([])
  const [playlists, setPlaylists] = useState<Playlist[]>([])
  const [allPlaylists, setAllPlaylists] = useState<Playlist[]>([])
  const [assets, setAssets] = useState<MediaAsset[]>([])
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const [view, setViewState] = useState<ViewMode>(
    () => parseView(searchParams.get('view')) ?? storedView() ?? 'day'
  )
  const [sites, setSites] = useState<Site[]>([])
  const [tableSchedules, setTableSchedules] = useState<Schedule[]>([])
  const [selectedDay, setSelectedDay] = useState<string | null>(null)
  const [wizardInitial, setWizardInitial] = useState<WizardInitial | undefined>(undefined)
  const [date, setDate] = useState(() => toIsoDate(new Date()))
  const [scope, setScope] = useState(ALL_PLAYERS)
  const [occurrences, setOccurrences] = useState<Record<string, TimelineOccurrence[]>>({})
  const [dialog, setDialog] = useState<{
    scheduleId: string | null
    draft: ScheduleDraft | null
  } | null>(null)
  const [dragLabel, setDragLabel] = useState<string | null>(null)
  const [wizardOpen, setWizardOpen] = useState(false)

  function setView(next: ViewMode) {
    setViewState(next)
    try {
      window.localStorage.setItem(VIEW_STORAGE_KEY, next)
    } catch {}
    const params = new URLSearchParams(searchParams.toString())
    params.set('view', next)
    router.replace(`${pathname}?${params.toString()}`, { scroll: false })
  }

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }))

  const loadBase = useCallback(
    () =>
      Promise.all([
        api.players.list(),
        api.groups.list(),
        api.playlists.list(),
        api.media.list({ size: 500 }),
        api.sites.list(),
      ])
        .then(([playerList, groupList, playlistList, media, siteList]) => {
          setSites(asList(siteList))
          setPlayers(operablePlayers(user, asList(playerList)))
          setGroups(operableGroups(user, asList(groupList)))
          setAllPlaylists(asList(playlistList))
          setPlaylists(asList(playlistList).filter((entry) => !entry.hidden))
          setAssets(media.content)
          setStatus('ready')
        })
        .catch(() => setStatus('error')),
    [user]
  )

  useEffect(() => {
    void loadBase()
  }, [loadBase])

  const currentDate = useMemo(() => parseIsoDate(date), [date])
  const weekStart = useMemo(() => startOfWeek(currentDate), [currentDate])
  const weekDays = useMemo(
    () => Array.from({ length: 7 }, (_, index) => addDays(weekStart, index)),
    [weekStart]
  )

  const isPlayerScope = !scope.includes(':') && scope !== ALL_PLAYERS
  const effectiveScope = view === 'week' && !isPlayerScope ? (players[0]?.id ?? ALL_PLAYERS) : scope

  const visiblePlayers = useMemo(() => {
    if (effectiveScope === ALL_PLAYERS) return players
    const [kind, id] = effectiveScope.split(':')
    if (kind === 'g' && id) return players.filter((player) => player.groupId === id)
    if (kind === 's' && id) return players.filter((player) => player.siteId === id)
    return players.filter((player) => player.id === effectiveScope)
  }, [players, effectiveScope])

  const monthDays = useMemo(() => monthGridDays(currentDate), [currentDate])

  const range = useMemo(() => {
    if (view === 'day') return { from: date, to: date }
    if (view === 'month') return { from: toIsoDate(monthDays[0]), to: toIsoDate(monthDays[41]) }
    return { from: toIsoDate(weekDays[0]), to: toIsoDate(weekDays[6]) }
  }, [view, date, weekDays, monthDays])

  const loadTable = useCallback(
    () =>
      api.schedules
        .list()
        .then((list) => setTableSchedules(asList(list)))
        .catch((err) => toast.error(apiErrorMessage(err, dict.schedule.loadFailed))),
    [dict.schedule.loadFailed]
  )

  const loadOccurrences = useCallback(() => {
    if (view === 'table') return loadTable()
    if (visiblePlayers.length === 0) return Promise.resolve()
    return api.schedules
      .timeline({ playerIds: visiblePlayers.map((player) => player.id) }, range.from, range.to)
      .then((list) => {
        const byPlayer: Record<string, TimelineOccurrence[]> = {}
        for (const player of visiblePlayers) byPlayer[player.id] = []
        for (const entry of list) {
          if (entry.playerId) (byPlayer[entry.playerId] ??= []).push(entry)
        }
        setOccurrences(byPlayer)
      })
      .catch((err) => toast.error(apiErrorMessage(err, dict.schedule.loadFailed)))
  }, [view, loadTable, visiblePlayers, range, dict.schedule.loadFailed])

  useEffect(() => {
    if (status === 'ready') void loadOccurrences()
  }, [status, loadOccurrences])

  const todayIso = toIsoDate(new Date())
  const playlistsById = useMemo(
    () => new Map(allPlaylists.map((entry) => [entry.id, entry])),
    [allPlaylists]
  )

  const foreignZones = useMemo(() => {
    const zones = new Set<string>()
    for (const entries of Object.values(occurrences)) {
      for (const entry of entries) {
        if (entry.timezone && differsFromBrowser(entry.timezone)) zones.add(entry.timezone)
      }
    }
    return Array.from(zones)
  }, [occurrences])

  const playerZone = useCallback(
    (playerId: string) =>
      (occurrences[playerId] ?? []).find((entry) => entry.timezone)?.timezone ?? null,
    [occurrences]
  )

  const laneSublabel = useCallback(
    (player: Player): string | undefined => {
      const zone = playerZone(player.id)
      return (
        [player.groupName, differsFromBrowser(zone) ? zone : null].filter(Boolean).join(' · ') ||
        undefined
      )
    },
    [playerZone]
  )

  const lanes: Lane[] = useMemo(() => {
    if (view === 'day') {
      return visiblePlayers.map((player) => ({
        id: `${player.id}|${date}`,
        label: player.name,
        sublabel: laneSublabel(player),
        playerId: player.id,
        date,
        today: date === todayIso,
        occurrences: (occurrences[player.id] ?? []).filter((entry) => entry.date === date),
      }))
    }
    const player = visiblePlayers[0]
    if (!player) return []
    return weekDays.map((day) => {
      const iso = toIsoDate(day)
      return {
        id: `${player.id}|${iso}`,
        label: new Intl.DateTimeFormat(locale, { weekday: 'long' }).format(day),
        sublabel: new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short' }).format(day),
        playerId: player.id,
        date: iso,
        today: iso === todayIso,
        occurrences: (occurrences[player.id] ?? []).filter((entry) => entry.date === iso),
      }
    })
  }, [view, visiblePlayers, date, weekDays, occurrences, locale, todayIso, laneSublabel])

  const legendEntries = useMemo(() => {
    const seen = new Map<string, string>()
    for (const lane of lanes) {
      for (const occurrence of lane.occurrences) {
        if (!seen.has(occurrence.playlistId)) {
          seen.set(
            occurrence.playlistId,
            occurrence.playlistName ??
              playlistsById.get(occurrence.playlistId)?.name ??
              occurrence.name
          )
        }
      }
    }
    return Array.from(seen, ([playlistId, name]) => ({ playlistId, name }))
  }, [lanes, playlistsById])

  const monthOccurrences = useMemo(() => {
    const seen = new Set<string>()
    const list: TimelineOccurrence[] = []
    for (const entries of Object.values(occurrences)) {
      for (const entry of entries) {
        const key = `${entry.scheduleId}|${entry.date}|${entry.startTime}`
        if (seen.has(key)) continue
        seen.add(key)
        list.push(entry)
      }
    }
    return list
  }, [occurrences])

  const monthLegend = useMemo(() => {
    const seen = new Map<string, string>()
    for (const occurrence of monthOccurrences) {
      if (!seen.has(occurrence.playlistId)) {
        seen.set(
          occurrence.playlistId,
          occurrence.playlistName ??
            playlistsById.get(occurrence.playlistId)?.name ??
            occurrence.name
        )
      }
    }
    return Array.from(seen, ([playlistId, name]) => ({ playlistId, name }))
  }, [monthOccurrences, playlistsById])

  function shift(direction: -1 | 1) {
    if (view === 'month') {
      setDate(toIsoDate(new Date(currentDate.getFullYear(), currentDate.getMonth() + direction, 1)))
      return
    }
    const step = view === 'day' ? 1 : 7
    setDate(toIsoDate(addDays(currentDate, direction * step)))
  }

  const title =
    view === 'table'
      ? ''
      : view === 'month'
        ? new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(currentDate)
        : view === 'day'
          ? new Intl.DateTimeFormat(locale, { dateStyle: 'full' }).format(currentDate)
          : `${new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short' }).format(weekDays[0])} - ${new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' }).format(weekDays[6])}`

  function programDay(iso: string, content?: Pick<WizardInitial, 'playlistIds' | 'assetIds'>) {
    const single = visiblePlayers.length === 1 ? visiblePlayers[0] : null
    setWizardInitial({
      ...content,
      date: iso,
      targets: single ? { playerIds: [single.id], groupIds: [] } : undefined,
    })
    setWizardOpen(true)
  }

  function onDragEnd(event: DragEndEvent) {
    setDragLabel(null)
    const { active, over } = event
    if (over && String(over.id).startsWith('day:')) {
      const activeKey = String(active.id)
      const refKey = activeKey.slice(3)
      programDay(String(over.id).slice(4), {
        playlistIds: activeKey.startsWith('pl:') ? [refKey] : undefined,
        assetIds: activeKey.startsWith('as:') ? [refKey] : undefined,
      })
      return
    }
    if (!over || !String(over.id).startsWith('lane:')) return
    const laneId = String(over.id).slice(5)
    const lane = lanes.find((entry) => entry.id === laneId)
    if (!lane) return
    const origin = event.activatorEvent as PointerEvent
    const pointerX = origin.clientX + event.delta.x
    const startMin = clamp(
      snapMinutes((pointerX - over.rect.left) / PX_PER_MINUTE),
      0,
      24 * 60 - SNAP_MINUTES
    )
    const endMin = Math.min(startMin + DEFAULT_BLOCK_MINUTES, 24 * 60)

    const activeId = String(active.id)
    const kind = activeId.slice(0, 2)
    const refId = activeId.slice(3)
    const playlist = kind === 'pl' ? playlists.find((entry) => entry.id === refId) : undefined
    const asset = kind === 'as' ? assets.find((entry) => entry.id === refId) : undefined
    if (!playlist && !asset) return

    setDialog({
      scheduleId: null,
      draft: {
        input: {
          name: playlist?.name ?? asset?.name ?? '',
          playlistId: playlist?.id,
          assetId: asset?.id,
          durationSec: asset ? defaultDurationSec(asset.type, asset.durationMs) : undefined,
          priority: 10,
          startDate: lane.date,
          endDate: lane.date,
          startTime: minutesToTime(startMin),
          endTime: minutesToTime(endMin),
          daysOfWeek: [isoWeekday(parseIsoDate(lane.date))],
          allDay: false,
          enabled: true,
          targets: { playerIds: [lane.playerId], groupIds: [] },
        },
      },
    })
  }

  async function commitTimes(scheduleId: string, startTime: string, endTime: string) {
    try {
      const schedule = await api.schedules.get(scheduleId)
      await api.schedules.update(scheduleId, {
        ...scheduleToInput(schedule),
        allDay: false,
        startTime,
        endTime,
      })
      void loadOccurrences()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.saveFailed))
    }
  }

  if (status === 'loading') return <PageSkeleton rows={2} />
  if (status === 'error') return <ErrorState onRetry={() => void loadBase()} />

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={pointerWithin}
      onDragStart={(event) => {
        const activeId = String(event.active.id)
        const refId = activeId.slice(3)
        setDragLabel(
          activeId.startsWith('pl:')
            ? (playlists.find((entry) => entry.id === refId)?.name ?? null)
            : (assets.find((entry) => entry.id === refId)?.name ?? null)
        )
      }}
      onDragEnd={onDragEnd}
      onDragCancel={() => setDragLabel(null)}
    >
      <div className="space-y-4">
        <PageHeader
          title={dict.schedule.title}
          description={dict.schedule.description}
          actions={
            <Button
              onClick={() => {
                setWizardInitial(undefined)
                setWizardOpen(true)
              }}
            >
              <IconCalendarPlus /> {dict.schedule.new}
            </Button>
          }
        />

        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className={cn('flex items-center gap-2', view === 'table' && 'invisible')}>
            <Button
              variant="outline"
              size="icon"
              aria-label={dict.schedule.previous}
              onClick={() => shift(-1)}
            >
              <IconChevronLeft />
            </Button>
            <Button
              variant="outline"
              size="icon"
              aria-label={dict.schedule.next}
              onClick={() => shift(1)}
            >
              <IconChevronRight />
            </Button>
            <Button variant="outline" onClick={() => setDate(todayIso)}>
              {dict.schedule.today}
            </Button>
            <span className="ml-2 text-sm font-medium">{title}</span>
          </div>
          <div className="flex items-center gap-2">
            {view !== 'table' ? (
              <Select value={effectiveScope} onValueChange={setScope}>
                <SelectTrigger className="w-52" aria-label={dict.schedule.allPlayers}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {view !== 'week' ? (
                    <SelectItem value={ALL_PLAYERS}>{dict.schedule.allPlayers}</SelectItem>
                  ) : null}
                  {view !== 'week' && sites.length > 0 ? (
                    <SelectGroup>
                      <SelectLabel>{dict.sites.title}</SelectLabel>
                      {sites.map((site) => (
                        <SelectItem key={site.id} value={`s:${site.id}`}>
                          {site.name}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  ) : null}
                  {view !== 'week' && groups.length > 0 ? (
                    <SelectGroup>
                      <SelectLabel>{dict.groups.title}</SelectLabel>
                      {groups.map((group) => (
                        <SelectItem key={group.id} value={`g:${group.id}`}>
                          {group.name}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  ) : null}
                  <SelectGroup>
                    <SelectLabel>{dict.players.title}</SelectLabel>
                    {players.map((player) => (
                      <SelectItem key={player.id} value={player.id}>
                        {player.name}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            ) : null}
            <Tabs value={view} onValueChange={(value) => setView(value as ViewMode)}>
              <TabsList>
                <TabsTrigger value="day">{dict.schedule.day}</TabsTrigger>
                <TabsTrigger value="week">{dict.schedule.week}</TabsTrigger>
                <TabsTrigger value="month">{dict.schedule.monthTab}</TabsTrigger>
                <TabsTrigger value="table">{dict.schedule.tableTab}</TabsTrigger>
              </TabsList>
            </Tabs>
          </div>
        </div>

        {view === 'table' ? (
          <ScheduleTable
            schedules={tableSchedules}
            players={players}
            groups={groups}
            sites={sites}
            playlistsById={playlistsById}
            onEdit={(scheduleId) => setDialog({ scheduleId, draft: null })}
            onChanged={() => void loadTable()}
          />
        ) : (
          <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_18rem]">
            <div className="min-w-0 space-y-3">
              <ScheduleLegend entries={view === 'month' ? monthLegend : legendEntries} />
              {foreignZones.length > 0 ? (
                <p className="text-muted-foreground text-xs">
                  {dict.schedule.timezoneNote.replace('{zones}', foreignZones.join(', '))}
                </p>
              ) : null}
              {view === 'month' ? (
                <MonthView
                  month={currentDate}
                  todayIso={todayIso}
                  occurrences={monthOccurrences}
                  playlistsById={playlistsById}
                  selectedDay={selectedDay}
                  onSelectDay={setSelectedDay}
                  onOpenSchedule={(scheduleId) => setDialog({ scheduleId, draft: null })}
                  onProgramDay={(iso) => programDay(iso)}
                />
              ) : lanes.length === 0 ? (
                <EmptyState
                  icon={IconDeviceTv}
                  title={dict.schedule.noPlayersTitle}
                  description={dict.schedule.noPlayersDescription}
                />
              ) : (
                <ScheduleBoard
                  lanes={lanes}
                  playlistsById={playlistsById}
                  onOpen={(scheduleId) => setDialog({ scheduleId, draft: null })}
                  onCommit={(scheduleId, start, end) => void commitTimes(scheduleId, start, end)}
                />
              )}
            </div>
            <Card className="xl:sticky xl:top-20 xl:max-h-[calc(100svh-7rem)]">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <IconPlaylist className="size-4" /> {dict.schedule.palette}
                </CardTitle>
              </CardHeader>
              <CardContent className="flex min-h-0 flex-1 flex-col">
                <SchedulePalette className="max-h-[60svh] xl:max-h-none" />
              </CardContent>
            </Card>
          </div>
        )}
      </div>

      <DragOverlay dropAnimation={null}>
        {dragLabel ? (
          <div className="bg-primary text-primary-foreground rounded-lg px-3 py-2 text-sm font-medium shadow-lg">
            {dragLabel}
          </div>
        ) : null}
      </DragOverlay>

      {wizardOpen ? (
        <ScheduleWizard
          open
          initial={wizardInitial}
          onClose={() => setWizardOpen(false)}
          onPublished={() => {
            setWizardOpen(false)
            void loadOccurrences()
          }}
        />
      ) : null}

      {dialog ? (
        <ScheduleDialog
          key={dialog.scheduleId ?? 'new'}
          scheduleId={dialog.scheduleId}
          draft={dialog.draft}
          players={players}
          groups={groups}
          playlists={playlists}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null)
            void loadOccurrences()
          }}
        />
      ) : null}
    </DndContext>
  )
}

export default function SchedulePage() {
  return (
    <RequirePermission permission="SCHEDULE_MANAGE">
      <Suspense fallback={<PageSkeleton rows={2} />}>
        <ScheduleContent />
      </Suspense>
    </RequirePermission>
  )
}
