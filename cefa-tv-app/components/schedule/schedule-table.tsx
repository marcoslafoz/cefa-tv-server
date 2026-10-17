'use client'

import { useMemo, useState } from 'react'
import {
  IconArrowDown,
  IconArrowUp,
  IconArrowsSort,
  IconCheck,
  IconChevronLeft,
  IconChevronRight,
  IconCopy,
  IconDeviceTv,
  IconDots,
  IconLayoutGrid,
  IconMapPin,
  IconPencil,
  IconPlayerPause,
  IconPlayerPlay,
  IconSearch,
  IconTrash,
} from '@tabler/icons-react'
import {
  dayFormatter,
  describeWhen,
  nextRunDate,
  scheduleStatus,
  scheduleToInput,
} from '@/components/schedule/helpers'
import type { ScheduleStatus } from '@/components/schedule/helpers'
import { ThumbCell, resolveThumbItems } from '@/components/schedule/thumb-strip'
import { ConfirmDialog } from '@/components/shared/confirm-dialog'
import { DynamicIcon } from '@/components/shared/group-icon'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card } from '@/components/shadcn-ui/card'
import { Checkbox } from '@/components/shadcn-ui/checkbox'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/shadcn-ui/dropdown-menu'
import { Input } from '@/components/shadcn-ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/shadcn-ui/table'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useFormat } from '@/hooks/use-format'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/api-client'
import { canAccessGroup, canAccessSite, canOnPlayer, hasPermission } from '@/lib/permissions'
import { ROW_CLASS, rowActivation } from '@/lib/row-activation'
import { toast } from '@/lib/toast'
import { cn, normalizeText } from '@/lib/utils'
import { formatInstant } from '@/lib/zoned-time'
import type { Player, PlayerGroup, Playlist, Schedule, Site } from '@/types/api'

const PAGE_SIZE = 10
const ALL = 'all'
const MAX_CHIPS = 2

type SortKey = 'name' | 'playlist' | 'priority' | 'status' | 'next'

const STATUS_ORDER: Record<ScheduleStatus, number> = {
  live: 0,
  scheduled: 1,
  ended: 2,
  disabled: 3,
}

const STATUS_STYLE: Record<ScheduleStatus, string> = {
  live: 'bg-success/15 text-success',
  scheduled: 'bg-primary/10 text-primary',
  ended: 'bg-muted text-muted-foreground',
  disabled: 'bg-warning/15 text-warning',
}

interface Chip {
  key: string
  label: string
  kind: 'player' | 'group' | 'site'
  icon?: string | null
}

function targetChips(
  schedule: Schedule,
  players: Player[],
  groups: PlayerGroup[],
  sites: Site[]
): Chip[] {
  return [
    ...(schedule.targets.siteIds ?? []).map((id) => ({
      key: `s:${id}`,
      label: sites.find((site) => site.id === id)?.name ?? id,
      kind: 'site' as const,
    })),
    ...schedule.targets.groupIds.map((id) => {
      const group = groups.find((entry) => entry.id === id)
      return { key: `g:${id}`, label: group?.name ?? id, kind: 'group' as const, icon: group?.icon }
    }),
    ...schedule.targets.playerIds.map((id) => ({
      key: `p:${id}`,
      label: players.find((player) => player.id === id)?.name ?? id,
      kind: 'player' as const,
    })),
  ]
}

function ChipView({ chip }: { chip: Chip }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Badge variant="secondary" className="max-w-40 min-w-0 gap-1">
          {chip.kind === 'site' ? (
            <IconMapPin className="size-3 shrink-0" />
          ) : chip.kind === 'group' ? (
            chip.icon ? (
              <DynamicIcon name={chip.icon} className="size-3 shrink-0" />
            ) : (
              <IconLayoutGrid className="size-3 shrink-0" />
            )
          ) : (
            <IconDeviceTv className="size-3 shrink-0" />
          )}
          <span className="truncate">{chip.label}</span>
        </Badge>
      </TooltipTrigger>
      <TooltipContent>{chip.label}</TooltipContent>
    </Tooltip>
  )
}

function SortHead({
  id,
  label,
  sort,
  onSort,
}: {
  id: SortKey
  label: string
  sort: { key: SortKey; dir: 'asc' | 'desc' }
  onSort: (key: SortKey) => void
}) {
  const active = sort.key === id
  const Icon = !active ? IconArrowsSort : sort.dir === 'asc' ? IconArrowUp : IconArrowDown
  return (
    <TableHead aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <button
        type="button"
        onClick={() => onSort(id)}
        className="hover:text-foreground flex items-center gap-1"
      >
        {label}
        <Icon className={cn('size-3.5', !active && 'opacity-50')} />
      </button>
    </TableHead>
  )
}

export function ScheduleTable({
  schedules,
  players,
  groups,
  sites,
  playlistsById,
  onEdit,
  onChanged,
}: {
  schedules: Schedule[]
  players: Player[]
  groups: PlayerGroup[]
  sites: Site[]
  playlistsById: Map<string, Playlist>
  onEdit: (scheduleId: string) => void
  onChanged: () => void
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const { user } = useAuth()
  const { locale } = useDateFormatter()
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState(ALL)
  const [targetFilter, setTargetFilter] = useState(ALL)
  const [playlistFilter, setPlaylistFilter] = useState(ALL)
  const [sort, setSort] = useState<{ key: SortKey; dir: 'asc' | 'desc' }>({
    key: 'next',
    dir: 'asc',
  })
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [renaming, setRenaming] = useState<string | null>(null)
  const [draftName, setDraftName] = useState('')
  const [toDelete, setToDelete] = useState<Schedule[] | null>(null)
  const canSchedule = hasPermission(user, 'SCHEDULE_MANAGE')

  function editable(schedule: Schedule): boolean {
    if (!canSchedule) return false
    if (user?.admin) return true
    const playerOk = schedule.targets.playerIds.every((id) => {
      const player = players.find((entry) => entry.id === id)
      return (
        player !== undefined &&
        canOnPlayer(
          user,
          'SCHEDULE_MANAGE',
          { groupId: player.groupId, siteId: player.siteId },
          'OPERATE'
        )
      )
    })
    return (
      playerOk &&
      schedule.targets.groupIds.every((id) => canAccessGroup(user, id, 'OPERATE')) &&
      (schedule.targets.siteIds ?? []).every((id) => canAccessSite(user, id, 'OPERATE'))
    )
  }

  const rows = useMemo(
    () =>
      schedules.map((schedule) => ({
        schedule,
        status: scheduleStatus(schedule),
        next: nextRunDate(schedule),
        chips: targetChips(schedule, players, groups, sites),
        playlistName:
          schedule.playlistName ?? playlistsById.get(schedule.playlistId)?.name ?? schedule.name,
      })),
    [schedules, players, groups, sites, playlistsById]
  )

  const filtered = useMemo(() => {
    const needle = normalizeText(query)
    const list = rows.filter((row) => {
      if (needle && !normalizeText(`${row.schedule.name} ${row.playlistName}`).includes(needle)) {
        return false
      }
      if (statusFilter !== ALL && row.status !== statusFilter) return false
      if (playlistFilter !== ALL && row.schedule.playlistId !== playlistFilter) return false
      if (targetFilter !== ALL) {
        const [kind, id] = targetFilter.split(':')
        const targets = row.schedule.targets
        const hit =
          kind === 'p'
            ? targets.playerIds.includes(id)
            : kind === 'g'
              ? targets.groupIds.includes(id)
              : (targets.siteIds ?? []).includes(id)
        if (!hit) return false
      }
      return true
    })
    const factor = sort.dir === 'asc' ? 1 : -1
    return [...list].sort((a, b) => {
      switch (sort.key) {
        case 'name':
          return factor * a.schedule.name.localeCompare(b.schedule.name, locale)
        case 'playlist':
          return factor * a.playlistName.localeCompare(b.playlistName, locale)
        case 'priority':
          return factor * (a.schedule.priority - b.schedule.priority)
        case 'status':
          return factor * (STATUS_ORDER[a.status] - STATUS_ORDER[b.status])
        default: {
          const left = a.next?.getTime() ?? Number.MAX_SAFE_INTEGER
          const right = b.next?.getTime() ?? Number.MAX_SAFE_INTEGER
          return factor * (left - right)
        }
      }
    })
  }, [rows, query, statusFilter, targetFilter, playlistFilter, sort, locale])

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const currentPage = Math.min(page, pageCount - 1)
  const visible = filtered.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE)
  const selectable = visible.filter((row) => editable(row.schedule))
  const allSelected =
    selectable.length > 0 && selectable.every((row) => selected.has(row.schedule.id))
  const selectedSchedules = rows
    .filter((row) => selected.has(row.schedule.id) && editable(row.schedule))
    .map((row) => row.schedule)

  function toggleSort(key: SortKey) {
    setSort((current) =>
      current.key === key
        ? { key, dir: current.dir === 'asc' ? 'desc' : 'asc' }
        : { key, dir: 'asc' }
    )
  }

  function toggleRow(id: string, value: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      if (value) next.add(id)
      else next.delete(id)
      return next
    })
  }

  function toggleAll(value: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      for (const row of selectable) {
        if (value) next.add(row.schedule.id)
        else next.delete(row.schedule.id)
      }
      return next
    })
  }

  async function setEnabled(list: Schedule[], enabled: boolean) {
    try {
      await Promise.all(
        list.map((schedule) =>
          api.schedules.update(schedule.id, { ...scheduleToInput(schedule), enabled })
        )
      )
      onChanged()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.saveFailed))
    }
  }

  async function duplicate(schedule: Schedule) {
    try {
      await api.schedules.create({
        ...scheduleToInput(schedule),
        name: dict.schedule.table.copyName.replace('{name}', schedule.name),
      })
      onChanged()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.saveFailed))
    }
  }

  async function removeAll(list: Schedule[]) {
    try {
      await Promise.all(list.map((schedule) => api.schedules.remove(schedule.id)))
      setSelected(new Set())
      onChanged()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.deleteFailed))
    }
  }

  async function rename(schedule: Schedule) {
    const name = draftName.trim()
    setRenaming(null)
    if (!name || name === schedule.name) return
    try {
      await api.schedules.update(schedule.id, { ...scheduleToInput(schedule), name })
      onChanged()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.saveFailed))
    }
  }

  function formatNext(row: (typeof rows)[number]) {
    if (row.status === 'live') return dict.schedule.table.statuses.live
    if (!row.next) return dict.schedule.table.noNext
    return formatInstant(row.next, locale, row.schedule.timezone)
  }

  const usedPlaylists = Array.from(new Set(schedules.map((schedule) => schedule.playlistId)))

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-52 flex-1">
          <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
          <Input
            className="pl-8"
            placeholder={dict.schedule.table.search}
            aria-label={dict.schedule.table.search}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setPage(0)
            }}
          />
        </div>
        <Select
          value={statusFilter}
          onValueChange={(value) => {
            setStatusFilter(value)
            setPage(0)
          }}
        >
          <SelectTrigger className="w-44" aria-label={dict.schedule.table.status}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{dict.schedule.table.allStatuses}</SelectItem>
            {(Object.keys(STATUS_ORDER) as ScheduleStatus[]).map((status) => (
              <SelectItem key={status} value={status}>
                {dict.schedule.table.statuses[status]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={targetFilter}
          onValueChange={(value) => {
            setTargetFilter(value)
            setPage(0)
          }}
        >
          <SelectTrigger className="w-52" aria-label={dict.schedule.table.targets}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{dict.schedule.table.allTargets}</SelectItem>
            {sites.map((site) => (
              <SelectItem key={`s:${site.id}`} value={`s:${site.id}`}>
                {site.name}
              </SelectItem>
            ))}
            {groups.map((group) => (
              <SelectItem key={`g:${group.id}`} value={`g:${group.id}`}>
                {group.name}
              </SelectItem>
            ))}
            {players.map((player) => (
              <SelectItem key={`p:${player.id}`} value={`p:${player.id}`}>
                {player.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={playlistFilter}
          onValueChange={(value) => {
            setPlaylistFilter(value)
            setPage(0)
          }}
        >
          <SelectTrigger className="w-48" aria-label={dict.schedule.playlist}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>{dict.schedule.table.allPlaylists}</SelectItem>
            {usedPlaylists.map((id) => (
              <SelectItem key={id} value={id}>
                {playlistsById.get(id)?.name ??
                  schedules.find((entry) => entry.playlistId === id)?.playlistName ??
                  id}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {selectedSchedules.length > 0 ? (
        <div className="bg-muted/60 flex flex-wrap items-center gap-2 rounded-lg border px-3 py-2 text-sm">
          <span className="font-medium">
            {fmt(dict.schedule.table.selected, { count: selectedSchedules.length })}
          </span>
          <Button
            size="sm"
            variant="outline"
            onClick={() => void setEnabled(selectedSchedules, true)}
          >
            <IconPlayerPlay /> {dict.schedule.table.enable}
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => void setEnabled(selectedSchedules, false)}
          >
            <IconPlayerPause /> {dict.schedule.table.disable}
          </Button>
          <Button size="sm" variant="outline" onClick={() => setToDelete(selectedSchedules)}>
            <IconTrash /> {dict.common.delete}
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setSelected(new Set())}>
            {dict.schedule.table.clearSelection}
          </Button>
        </div>
      ) : null}

      <Card className="overflow-hidden p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10">
                <Checkbox
                  checked={allSelected}
                  disabled={selectable.length === 0}
                  aria-label={dict.schedule.table.selectAll}
                  onCheckedChange={(value) => toggleAll(value === true)}
                />
              </TableHead>
              <SortHead id="name" label={dict.common.name} sort={sort} onSort={toggleSort} />
              <SortHead
                id="playlist"
                label={dict.schedule.playlist}
                sort={sort}
                onSort={toggleSort}
              />
              <TableHead>{dict.schedule.table.targets}</TableHead>
              <TableHead>{dict.schedule.table.when}</TableHead>
              <SortHead
                id="priority"
                label={dict.schedule.priority}
                sort={sort}
                onSort={toggleSort}
              />
              <SortHead
                id="status"
                label={dict.schedule.table.status}
                sort={sort}
                onSort={toggleSort}
              />
              <SortHead
                id="next"
                label={dict.schedule.table.next}
                sort={sort}
                onSort={toggleSort}
              />
              <TableHead className="w-10">
                <span className="sr-only">{dict.common.actions}</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {visible.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9} className="text-muted-foreground py-10 text-center">
                  {dict.schedule.table.empty}
                </TableCell>
              </TableRow>
            ) : null}
            {visible.map((row) => {
              const { schedule } = row
              const canEdit = editable(schedule)
              const thumbs = resolveThumbItems(
                schedule.thumbnailUrls,
                playlistsById.get(schedule.playlistId)
              ).slice(0, 3)
              const hidden = row.chips.slice(MAX_CHIPS)
              return (
                <TableRow
                  key={schedule.id}
                  {...rowActivation(() => canEdit && onEdit(schedule.id), 'button')}
                  className={ROW_CLASS}
                  data-state={selected.has(schedule.id) ? 'selected' : undefined}
                >
                  <TableCell>
                    <Checkbox
                      checked={selected.has(schedule.id)}
                      disabled={!canEdit}
                      aria-label={schedule.name}
                      onCheckedChange={(value) => toggleRow(schedule.id, value === true)}
                    />
                  </TableCell>
                  <TableCell className="max-w-56">
                    {renaming === schedule.id ? (
                      <form
                        className="flex items-center gap-1"
                        onSubmit={(event) => {
                          event.preventDefault()
                          void rename(schedule)
                        }}
                      >
                        <Input
                          autoFocus
                          value={draftName}
                          className="h-8"
                          aria-label={dict.schedule.renameSchedule}
                          onChange={(event) => setDraftName(event.target.value)}
                          onKeyDown={(event) => event.key === 'Escape' && setRenaming(null)}
                          onBlur={() => void rename(schedule)}
                        />
                        <Button
                          size="icon-sm"
                          type="submit"
                          variant="ghost"
                          aria-label={dict.common.save}
                        >
                          <IconCheck />
                        </Button>
                      </form>
                    ) : (
                      <div className="flex min-w-0 items-center gap-1">
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <span
                              className="truncate font-medium"
                              onDoubleClick={() => {
                                if (!canEdit) return
                                setDraftName(schedule.name)
                                setRenaming(schedule.id)
                              }}
                            >
                              {schedule.name}
                            </span>
                          </TooltipTrigger>
                          <TooltipContent>{schedule.name}</TooltipContent>
                        </Tooltip>
                        {canEdit ? (
                          <Button
                            size="icon-sm"
                            variant="ghost"
                            className="size-6 shrink-0"
                            aria-label={dict.schedule.renameSchedule}
                            onClick={() => {
                              setDraftName(schedule.name)
                              setRenaming(schedule.id)
                            }}
                          >
                            <IconPencil />
                          </Button>
                        ) : null}
                      </div>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <div className="flex shrink-0 -space-x-2">
                        {thumbs.map((item) => (
                          <ThumbCell
                            key={item.key}
                            item={item}
                            className="ring-background size-7 rounded-md ring-2"
                          />
                        ))}
                      </div>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <span className="max-w-36 truncate text-sm">{row.playlistName}</span>
                        </TooltipTrigger>
                        <TooltipContent>{row.playlistName}</TooltipContent>
                      </Tooltip>
                    </div>
                  </TableCell>
                  <TableCell className="max-w-64">
                    <div className="flex flex-wrap items-center gap-1">
                      {row.chips.slice(0, MAX_CHIPS).map((chip) => (
                        <ChipView key={chip.key} chip={chip} />
                      ))}
                      {hidden.length > 0 ? (
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Badge variant="outline">+{hidden.length}</Badge>
                          </TooltipTrigger>
                          <TooltipContent>
                            {hidden.map((chip) => chip.label).join(', ')}
                          </TooltipContent>
                        </Tooltip>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell className="max-w-56 text-xs whitespace-normal">
                    {describeWhen(schedule, dict, dayFormatter(locale))}
                  </TableCell>
                  <TableCell className="tabular-nums">{schedule.priority}</TableCell>
                  <TableCell>
                    <Badge variant="secondary" className={STATUS_STYLE[row.status]}>
                      {dict.schedule.table.statuses[row.status]}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-xs whitespace-nowrap">{formatNext(row)}</TableCell>
                  <TableCell>
                    {canEdit ? (
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button size="icon-sm" variant="ghost" aria-label={dict.common.actions}>
                            <IconDots />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onSelect={() => onEdit(schedule.id)}>
                            <IconPencil /> {dict.common.edit}
                          </DropdownMenuItem>
                          <DropdownMenuItem onSelect={() => void duplicate(schedule)}>
                            <IconCopy /> {dict.playlists.duplicate}
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onSelect={() => void setEnabled([schedule], !schedule.enabled)}
                          >
                            {schedule.enabled ? <IconPlayerPause /> : <IconPlayerPlay />}
                            {schedule.enabled
                              ? dict.schedule.table.disable
                              : dict.schedule.table.enable}
                          </DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem
                            variant="destructive"
                            onSelect={() => setToDelete([schedule])}
                          >
                            <IconTrash /> {dict.common.delete}
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    ) : (
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <span>
                            <Button
                              size="icon-sm"
                              variant="ghost"
                              disabled
                              aria-label={dict.common.actions}
                            >
                              <IconDots />
                            </Button>
                          </span>
                        </TooltipTrigger>
                        <TooltipContent>{dict.schedule.table.noPermission}</TooltipContent>
                      </Tooltip>
                    )}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </Card>

      <div className="flex items-center justify-between gap-3 text-sm">
        <span className="text-muted-foreground">
          {fmt(dict.schedule.table.total, { count: filtered.length })}
        </span>
        <div className="flex items-center gap-2">
          <span className="text-muted-foreground tabular-nums">
            {currentPage + 1} / {pageCount}
          </span>
          <Button
            size="icon-sm"
            variant="outline"
            aria-label={dict.schedule.previous}
            disabled={currentPage === 0}
            onClick={() => setPage(currentPage - 1)}
          >
            <IconChevronLeft />
          </Button>
          <Button
            size="icon-sm"
            variant="outline"
            aria-label={dict.schedule.next}
            disabled={currentPage >= pageCount - 1}
            onClick={() => setPage(currentPage + 1)}
          >
            <IconChevronRight />
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={toDelete !== null}
        onOpenChange={(open) => !open && setToDelete(null)}
        title={dict.schedule.table.deleteTitle}
        description={fmt(dict.schedule.table.deleteDescription, { count: toDelete?.length ?? 0 })}
        confirmLabel={dict.common.delete}
        onConfirm={() => {
          if (toDelete) void removeAll(toDelete)
          setToDelete(null)
        }}
      />
    </div>
  )
}
