'use client'

import { ROW_CLASS, rowActivation } from '@/lib/row-activation'
import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import {
  IconBuildingStore,
  IconDeviceTv,
  IconFilterOff,
  IconLayoutGrid,
  IconLayoutList,
  IconList,
  IconPlus,
  IconArrowDown,
  IconArrowUp,
  IconSearch,
  IconTrash,
  IconX,
} from '@tabler/icons-react'
import { AracastBadge } from '@/components/players/aracast-badge'
import { FilterMultiSelect } from '@/components/players/filter-multi-select'
import { PairDialog } from '@/components/players/pair-dialog'
import { OutdatedBadge } from '@/components/players/outdated-badge'
import { PlayerPreview } from '@/components/players/player-preview'
import {
  compareVersions,
  formatPlayerVersion,
  isOutdated,
  latestVersion,
} from '@/components/players/player-version'
import { ConfirmDialog } from '@/components/shared/confirm-dialog'
import { DeliveryBadge } from '@/components/shared/delivery-state'
import { PageHeader } from '@/components/shared/page-header'
import { OnlineDot, PlayerBadges } from '@/components/shared/player-status'
import { RequirePermission } from '@/components/shared/require-permission'
import { EmptyState, ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card } from '@/components/shadcn-ui/card'
import { Input } from '@/components/shadcn-ui/input'
import { Switch } from '@/components/shadcn-ui/switch'
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
import { useApiResource } from '@/hooks/use-api-resource'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useFormat } from '@/hooks/use-format'
import { useLivePreviews } from '@/hooks/use-live-previews'
import { useLocalePath } from '@/hooks/use-locale-path'
import { useVisibleIds } from '@/hooks/use-visible-ids'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { hasPermission } from '@/lib/permissions'
import { readStored, writeStored } from '@/lib/storage'
import { toast } from '@/lib/toast'
import { cn, normalizeText } from '@/lib/utils'
import type { Page, Player, PlayerGroup, Playlist, Site } from '@/types/api'

type Density = 'large' | 'compact' | 'table'
type StatusKey = 'online' | 'offline' | 'pending' | 'never'
type SortKey = 'name' | 'version' | 'lastSeen'

const STATUS_KEYS: StatusKey[] = ['online', 'offline', 'pending', 'never']
const NONE = 'none'
const DENSITY_KEY = 'cefa-tv.players.density'
const GROUP_BY_SITE_KEY = 'cefa-tv.players.groupBySite'
const LIVE_KEY = 'cefa-tv.players.live'
const FILTERS_KEY = 'cefa-tv.players.filters'

function parseList(value: string | null): string[] {
  return value ? value.split(',').filter(Boolean) : []
}

function matchesStatus(player: Player, statuses: StatusKey[]): boolean {
  if (statuses.length === 0) return true
  return statuses.some((status) => {
    if (status === 'online') return player.online
    if (status === 'offline') return !player.online && !player.neverConnected
    if (status === 'pending') return player.pending
    return Boolean(player.neverConnected)
  })
}

function hasUrlFilters(params: URLSearchParams | { has(key: string): boolean }) {
  return ['groups', 'sites', 'status', 'versions', 'q'].some((key) => params.has(key))
}

function PlayersContent() {
  const dict = useDictionary()
  const fmt = useFormat()
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const { user } = useAuth()
  const localePath = useLocalePath()
  const { formatRelative } = useDateFormatter()
  const [pairing, setPairing] = useState(false)
  const [toRemove, setToRemove] = useState<Player | null>(null)
  const [density, setDensity] = useState<Density>(() => readStored<Density>(DENSITY_KEY, 'large'))
  const [groupBySite, setGroupBySite] = useState(() => readStored<boolean>(GROUP_BY_SITE_KEY, true))
  const [live, setLive] = useState(() => readStored<boolean>(LIVE_KEY, true))
  const [sort, setSort] = useState<{ key: SortKey; descending: boolean }>({
    key: 'name',
    descending: false,
  })
  const [search, setSearch] = useState(() => {
    const fromUrl = searchParams.get('q')
    if (fromUrl !== null || hasUrlFilters(searchParams)) return fromUrl ?? ''
    const saved = readStored<{ q?: string } | null>(FILTERS_KEY, null)
    return typeof saved?.q === 'string' ? saved.q : ''
  })
  const resource = useApiResource<Player[] | Page<Player>>(`${API_V1}/players`, {
    intervalMs: 5000,
  })
  const sitesResource = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`)
  const groupsResource = useApiResource<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`)
  const playlistsResource = useApiResource<Playlist[] | Page<Playlist>>(`${API_V1}/playlists`)
  const { visibleIds, register } = useVisibleIds()
  const frames = useLivePreviews(live && density !== 'table', visibleIds)
  const canPair = hasPermission(user, 'PLAYER_MANAGE')

  const groupFilter = parseList(searchParams.get('groups'))
  const siteFilter = parseList(searchParams.get('sites'))
  const statusFilter = parseList(searchParams.get('status')).filter((value): value is StatusKey =>
    STATUS_KEYS.includes(value as StatusKey)
  )
  const versionFilter = parseList(searchParams.get('versions'))
  const query = searchParams.get('q') ?? ''

  const updateParams = useCallback(
    (update: Record<string, string[] | string>) => {
      const params = new URLSearchParams(searchParams.toString())
      for (const [key, value] of Object.entries(update)) {
        const joined = Array.isArray(value) ? value.join(',') : value
        if (joined) params.set(key, joined)
        else params.delete(key)
      }
      const next = params.toString()
      router.replace(next ? `${pathname}?${next}` : pathname, { scroll: false })
    },
    [router, pathname, searchParams]
  )

  const filtersRestored = useRef(false)
  const filtersKey = JSON.stringify([groupFilter, siteFilter, statusFilter, versionFilter, query])

  useEffect(() => {
    if (!filtersRestored.current) {
      filtersRestored.current = true
      const saved = readStored<Record<string, string[] | string> | null>(FILTERS_KEY, null)
      if (!hasUrlFilters(searchParams) && saved) {
        updateParams(saved)
        return
      }
    }
    writeStored(FILTERS_KEY, {
      groups: groupFilter,
      sites: siteFilter,
      status: statusFilter,
      versions: versionFilter,
      q: query,
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtersKey])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (search.trim() !== query) updateParams({ q: search.trim() })
    }, 300)
    return () => window.clearTimeout(timer)
  }, [search, query, updateParams])

  function changeDensity(next: Density) {
    setDensity(next)
    writeStored(DENSITY_KEY, next)
  }

  function changeLive(next: boolean) {
    setLive(next)
    writeStored(LIVE_KEY, next)
  }

  function changeGroupBySite(next: boolean) {
    setGroupBySite(next)
    writeStored(GROUP_BY_SITE_KEY, next)
  }

  function clearFilters() {
    setSearch('')
    updateParams({ q: '', groups: [], sites: [], status: [], versions: [] })
  }

  async function remove(player: Player) {
    try {
      await api.players.remove(player.id)
      void resource.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.players.deleteFailed))
    }
  }

  const players = useMemo(() => asList(resource.data), [resource.data])
  const sites = asList(sitesResource.data)
  const groups = asList(groupsResource.data)
  const playlistNames = useMemo(
    () => new Map(asList(playlistsResource.data).map((entry) => [entry.id, entry.name])),
    [playlistsResource.data]
  )

  const filtered = useMemo(() => {
    const needle = normalizeText(query)
    return players.filter((player) => {
      if (
        needle !== '' &&
        !normalizeText(
          `${player.name} ${player.description ?? ''} ${player.ip ?? ''} ${player.hostname ?? ''}`
        ).includes(needle)
      ) {
        return false
      }
      if (groupFilter.length > 0 && !groupFilter.includes(player.groupId ?? NONE)) return false
      if (siteFilter.length > 0 && !siteFilter.includes(player.siteId ?? NONE)) return false
      if (versionFilter.length > 0 && !versionFilter.includes(player.playerVersion ?? NONE)) {
        return false
      }
      return matchesStatus(player, statusFilter)
    })
  }, [players, query, groupFilter, siteFilter, statusFilter, versionFilter])

  const latest = useMemo(() => latestVersion(filtered), [filtered])
  const versions = useMemo(
    () =>
      Array.from(new Set(players.map((player) => player.playerVersion ?? NONE))).sort((a, b) =>
        a === NONE ? 1 : b === NONE ? -1 : compareVersions(b, a)
      ),
    [players]
  )

  if (resource.loading && !resource.data) return <PageSkeleton rows={3} />
  if (resource.error && !resource.data) return <ErrorState onRetry={() => void resource.reload()} />

  const activeFilterCount =
    groupFilter.length +
    siteFilter.length +
    statusFilter.length +
    versionFilter.length +
    (query ? 1 : 0)
  const sections = groupBySite
    ? [
        ...sites.map((site) => ({
          key: site.id,
          title: site.name,
          players: filtered.filter((player) => player.siteId === site.id),
        })),
        {
          key: NONE,
          title: dict.players.noSite,
          players: filtered.filter((player) => !player.siteId),
        },
      ].filter((section) => section.players.length > 0)
    : [{ key: 'all', title: null, players: filtered }]

  function contentTitle(player: Player): string | null {
    if (!player.online) return null
    return (
      player.currentAssetName ??
      (player.currentPlaylistId ? (playlistNames.get(player.currentPlaylistId) ?? null) : null)
    )
  }

  function description(player: Player, lines: string) {
    return player.description ? (
      <Tooltip>
        <TooltipTrigger asChild>
          <p className={cn('text-muted-foreground text-xs', lines)}>{player.description}</p>
        </TooltipTrigger>
        <TooltipContent className="max-w-sm">{player.description}</TooltipContent>
      </Tooltip>
    ) : null
  }

  function removeButton(player: Player) {
    return player.neverConnected && canPair ? (
      <Button
        size="sm"
        variant="outline"
        className="text-destructive w-fit"
        onClick={(event) => {
          event.preventDefault()
          event.stopPropagation()
          setToRemove(player)
        }}
      >
        <IconTrash /> {dict.players.removeNeverConnected}
      </Button>
    ) : null
  }

  function largeCard(player: Player) {
    const title = contentTitle(player)
    return (
      <Link
        key={player.id}
        href={localePath(`/players/${player.id}`)}
        ref={register(player.id)}
        data-player-id={player.id}
        className="block min-w-0"
      >
        <Card className="hover:border-primary/50 h-full gap-3 overflow-hidden p-0 transition-colors">
          <PlayerPreview
            player={player}
            live={live}
            frame={frames.get(player.id)}
            className="rounded-none"
          />
          <div className="space-y-3 px-4 pb-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="flex items-center gap-1.5 font-medium">
                  <span className="truncate">{player.name}</span>
                  <AracastBadge player={player} />
                </p>
                <p className="text-muted-foreground truncate text-xs">
                  {[player.siteName, player.groupName ?? dict.players.noGroup, player.ip]
                    .filter(Boolean)
                    .join(' · ')}
                </p>
              </div>
              <OnlineDot online={player.online} className="mt-1.5" />
            </div>
            {title ? (
              <p className="truncate text-xs">
                <span className="text-muted-foreground">{dict.players.nowPlayingLabel} </span>
                <span className="font-medium">{title}</span>
              </p>
            ) : null}
            {description(player, 'line-clamp-2')}
            <PlayerBadges player={player} />
            {!player.neverConnected && player.status === 'ACTIVE' ? (
              <DeliveryBadge player={player} long className="w-fit" />
            ) : null}
            <dl className="text-muted-foreground grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
              <dt>{dict.players.version}</dt>
              <dd className="text-foreground text-right tabular-nums">
                {player.appliedVersion} / {player.desiredVersion}
              </dd>
              <dt>{dict.players.lastSeen}</dt>
              <dd className="text-foreground text-right">
                {player.lastSeenAt ? formatRelative(player.lastSeenAt) : dict.players.never}
              </dd>
            </dl>
            {versionLine(player)}
            {removeButton(player)}
          </div>
        </Card>
      </Link>
    )
  }

  function compactCard(player: Player) {
    const title = contentTitle(player)
    return (
      <Link
        key={player.id}
        href={localePath(`/players/${player.id}`)}
        ref={register(player.id)}
        data-player-id={player.id}
        className="block min-w-0"
      >
        <Card className="hover:border-primary/50 h-full gap-2 overflow-hidden p-0 transition-colors">
          <PlayerPreview
            player={player}
            live={live}
            frame={frames.get(player.id)}
            className="rounded-none"
          />
          <div className="space-y-1.5 px-3 pb-3">
            <div className="flex items-center justify-between gap-2">
              <p className="flex min-w-0 items-center gap-1.5 text-sm font-medium">
                <span className="truncate">{player.name}</span>
                <AracastBadge player={player} />
              </p>
              <Tooltip>
                <TooltipTrigger asChild>
                  <span>
                    <OnlineDot online={player.online} />
                  </span>
                </TooltipTrigger>
                <TooltipContent>
                  {formatPlayerVersion(player) ?? dict.players.versionInfo.unknown}
                </TooltipContent>
              </Tooltip>
            </div>
            <div className="flex flex-wrap gap-1">
              {player.groupName ? (
                <Badge variant="secondary" className="max-w-full truncate text-[10px]">
                  {player.groupName}
                </Badge>
              ) : null}
              {player.siteName ? (
                <Badge variant="outline" className="max-w-full gap-1 truncate text-[10px]">
                  <IconBuildingStore className="size-3" />
                  {player.siteName}
                </Badge>
              ) : null}
              {player.neverConnected ? (
                <Badge variant="secondary" className="bg-warning/15 text-warning text-[10px]">
                  {dict.players.neverConnected}
                </Badge>
              ) : player.pending ? (
                <Badge variant="secondary" className="bg-warning/15 text-warning text-[10px]">
                  {dict.players.pendingDelivery}
                </Badge>
              ) : null}
            </div>
            {title ? <p className="text-muted-foreground truncate text-xs">{title}</p> : null}
            {removeButton(player)}
          </div>
        </Card>
      </Link>
    )
  }

  function sortedRows(rows: Player[]): Player[] {
    const direction = sort.descending ? -1 : 1
    return [...rows].sort((left, right) => {
      if (sort.key === 'version') {
        return direction * compareVersions(left.playerVersion ?? '0', right.playerVersion ?? '0')
      }
      if (sort.key === 'lastSeen') {
        return (
          direction *
          ((left.lastSeenAt ? Date.parse(left.lastSeenAt) : 0) -
            (right.lastSeenAt ? Date.parse(right.lastSeenAt) : 0))
        )
      }
      return direction * left.name.localeCompare(right.name)
    })
  }

  function sortHeader(key: SortKey, label: string) {
    const active = sort.key === key
    return (
      <TableHead>
        <button
          type="button"
          className="flex items-center gap-1 font-medium"
          onClick={() =>
            setSort(active ? { key, descending: !sort.descending } : { key, descending: false })
          }
        >
          {label}
          {active ? (
            sort.descending ? (
              <IconArrowDown className="size-3.5" />
            ) : (
              <IconArrowUp className="size-3.5" />
            )
          ) : null}
        </button>
      </TableHead>
    )
  }

  function versionLine(player: Player) {
    const text = formatPlayerVersion(player)
    if (!text) return null
    return (
      <span className="text-muted-foreground flex items-center gap-1.5 text-[11px] tabular-nums">
        {text}
        {latest && isOutdated(player, latest) ? <OutdatedBadge latest={latest} /> : null}
      </span>
    )
  }

  function tableView(rows: Player[]) {
    return (
      <Card className="py-0">
        <Table>
          <TableHeader>
            <TableRow>
              {sortHeader('name', dict.common.name)}
              <TableHead>{dict.players.site}</TableHead>
              <TableHead>{dict.players.group}</TableHead>
              <TableHead>{dict.players.state}</TableHead>
              <TableHead>{dict.players.nowPlayingLabel}</TableHead>
              {sortHeader('version', dict.players.versionInfo.column)}
              {sortHeader('lastSeen', dict.players.lastSeen)}
            </TableRow>
          </TableHeader>
          <TableBody>
            {sortedRows(rows).map((player) => (
              <TableRow
                key={player.id}
                {...rowActivation(() => router.push(localePath(`/players/${player.id}`)))}
                className={ROW_CLASS}
              >
                <TableCell>
                  <div className="flex min-w-0 items-center gap-2">
                    <OnlineDot online={player.online} />
                    <div className="min-w-0">
                      <p className="flex items-center gap-1.5 font-medium">
                        <span className="truncate">{player.name}</span>
                        <AracastBadge player={player} />
                      </p>
                      {description(player, 'max-w-64 truncate')}
                    </div>
                  </div>
                </TableCell>
                <TableCell>{player.siteName ?? '-'}</TableCell>
                <TableCell>{player.groupName ?? '-'}</TableCell>
                <TableCell>
                  <PlayerBadges player={player} />
                </TableCell>
                <TableCell className="max-w-48 truncate">{contentTitle(player) ?? '-'}</TableCell>
                <TableCell>{versionLine(player) ?? '-'}</TableCell>
                <TableCell className="whitespace-nowrap">
                  {player.lastSeenAt ? formatRelative(player.lastSeenAt) : dict.players.never}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    )
  }

  const addButton = canPair ? (
    <Button onClick={() => setPairing(true)}>
      <IconPlus /> {dict.players.addPlayer}
    </Button>
  ) : null

  const densities: Array<{ id: Density; label: string; icon: typeof IconList }> = [
    { id: 'large', label: dict.players.density.large, icon: IconLayoutGrid },
    { id: 'compact', label: dict.players.density.compact, icon: IconLayoutList },
    { id: 'table', label: dict.players.density.table, icon: IconList },
  ]

  return (
    <div className="space-y-5">
      <PageHeader
        title={dict.players.title}
        description={dict.players.description}
        actions={addButton}
      />
      {players.length === 0 ? (
        <EmptyState
          icon={IconDeviceTv}
          title={dict.players.emptyTitle}
          description={dict.players.emptyDescription}
          action={addButton}
        />
      ) : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative w-64">
              <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
              <Input
                className="pl-8"
                placeholder={dict.players.filters.search}
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>
            {groups.length > 0 ? (
              <FilterMultiSelect
                label={dict.players.filters.groups}
                selected={groupFilter}
                onChange={(next) => updateParams({ groups: next })}
                options={[
                  { value: NONE, label: dict.players.noGroup },
                  ...groups.map((group) => ({ value: group.id, label: group.name })),
                ]}
              />
            ) : null}
            {sites.length > 0 ? (
              <FilterMultiSelect
                label={dict.players.filters.sites}
                selected={siteFilter}
                onChange={(next) => updateParams({ sites: next })}
                options={[
                  { value: NONE, label: dict.players.noSite },
                  ...sites.map((site) => ({ value: site.id, label: site.name })),
                ]}
              />
            ) : null}
            {versions.length > 1 ? (
              <FilterMultiSelect
                label={dict.players.versionInfo.filter}
                selected={versionFilter}
                onChange={(next) => updateParams({ versions: next })}
                options={versions.map((value) => ({
                  value,
                  label: value === NONE ? dict.players.versionInfo.unknown : `v${value}`,
                }))}
              />
            ) : null}
            <FilterMultiSelect
              label={dict.players.filters.status}
              selected={statusFilter}
              onChange={(next) => updateParams({ status: next })}
              options={STATUS_KEYS.map((key) => ({
                value: key,
                label: dict.players.filters.statuses[key],
              }))}
            />
            <div className="ml-auto flex flex-wrap items-center gap-3">
              {sites.length > 0 ? (
                <label className="flex items-center gap-2 text-sm">
                  <Switch checked={groupBySite} onCheckedChange={changeGroupBySite} />
                  {dict.players.groupBySite}
                </label>
              ) : null}
              {density !== 'table' ? (
                <label className="flex items-center gap-2 text-sm">
                  <Switch checked={live} onCheckedChange={changeLive} />
                  {dict.players.preview.live}
                </label>
              ) : null}
              <div className="flex rounded-lg border p-0.5">
                {densities.map((entry) => (
                  <Button
                    key={entry.id}
                    size="icon-sm"
                    variant={density === entry.id ? 'secondary' : 'ghost'}
                    aria-label={entry.label}
                    aria-pressed={density === entry.id}
                    title={entry.label}
                    onClick={() => changeDensity(entry.id)}
                  >
                    <entry.icon />
                  </Button>
                ))}
              </div>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-1.5">
            <span className="text-muted-foreground text-sm tabular-nums">
              {fmt(dict.players.filters.results, { count: filtered.length, total: players.length })}
            </span>
            {query ? (
              <Badge variant="secondary" className="gap-1 pr-1">
                {`"${query}"`}
                <button
                  type="button"
                  aria-label={dict.common.delete}
                  className="hover:bg-foreground/10 rounded p-0.5"
                  onClick={() => {
                    setSearch('')
                    updateParams({ q: '' })
                  }}
                >
                  <IconX className="size-3" />
                </button>
              </Badge>
            ) : null}
            {groupFilter.map((id) => (
              <Badge key={`g-${id}`} variant="secondary" className="gap-1 pr-1">
                {id === NONE
                  ? dict.players.noGroup
                  : (groups.find((group) => group.id === id)?.name ?? id)}
                <button
                  type="button"
                  aria-label={dict.common.delete}
                  className="hover:bg-foreground/10 rounded p-0.5"
                  onClick={() =>
                    updateParams({ groups: groupFilter.filter((entry) => entry !== id) })
                  }
                >
                  <IconX className="size-3" />
                </button>
              </Badge>
            ))}
            {siteFilter.map((id) => (
              <Badge key={`s-${id}`} variant="secondary" className="gap-1 pr-1">
                {id === NONE
                  ? dict.players.noSite
                  : (sites.find((site) => site.id === id)?.name ?? id)}
                <button
                  type="button"
                  aria-label={dict.common.delete}
                  className="hover:bg-foreground/10 rounded p-0.5"
                  onClick={() =>
                    updateParams({ sites: siteFilter.filter((entry) => entry !== id) })
                  }
                >
                  <IconX className="size-3" />
                </button>
              </Badge>
            ))}
            {versionFilter.map((value) => (
              <Badge key={`v-${value}`} variant="secondary" className="gap-1 pr-1">
                {value === NONE ? dict.players.versionInfo.unknown : `v${value}`}
                <button
                  type="button"
                  aria-label={dict.common.delete}
                  className="hover:bg-foreground/10 rounded p-0.5"
                  onClick={() =>
                    updateParams({ versions: versionFilter.filter((entry) => entry !== value) })
                  }
                >
                  <IconX className="size-3" />
                </button>
              </Badge>
            ))}
            {statusFilter.map((key) => (
              <Badge key={`st-${key}`} variant="secondary" className="gap-1 pr-1">
                {dict.players.filters.statuses[key]}
                <button
                  type="button"
                  aria-label={dict.common.delete}
                  className="hover:bg-foreground/10 rounded p-0.5"
                  onClick={() =>
                    updateParams({ status: statusFilter.filter((entry) => entry !== key) })
                  }
                >
                  <IconX className="size-3" />
                </button>
              </Badge>
            ))}
            {activeFilterCount > 0 ? (
              <Button size="xs" variant="ghost" onClick={clearFilters}>
                <IconFilterOff /> {dict.players.filters.clear}
              </Button>
            ) : null}
          </div>

          {filtered.length === 0 ? (
            <p className="text-muted-foreground rounded-lg border border-dashed p-8 text-center text-sm">
              {dict.players.noMatches}
            </p>
          ) : null}
          {sections.map((section) => (
            <section key={section.key} className="space-y-2">
              {section.title ? (
                <h2 className="text-muted-foreground flex items-center gap-2 text-sm font-medium">
                  <IconBuildingStore className="size-4" /> {section.title}
                  <Badge variant="secondary">{section.players.length}</Badge>
                </h2>
              ) : null}
              {density === 'table' ? (
                tableView(section.players)
              ) : (
                <div
                  className={cn(
                    'grid gap-3',
                    density === 'large'
                      ? 'md:grid-cols-2 xl:grid-cols-3'
                      : 'grid-cols-2 md:grid-cols-3 xl:grid-cols-5 2xl:grid-cols-6'
                  )}
                >
                  {section.players.map(density === 'large' ? largeCard : compactCard)}
                </div>
              )}
            </section>
          ))}
        </div>
      )}
      <ConfirmDialog
        open={toRemove !== null}
        onOpenChange={(open) => !open && setToRemove(null)}
        title={dict.players.removeTitle}
        description={dict.players.removeDescription.replace('{name}', toRemove?.name ?? '')}
        confirmLabel={dict.players.remove}
        onConfirm={() => {
          if (toRemove) void remove(toRemove)
          setToRemove(null)
        }}
      />
      <PairDialog
        open={pairing}
        onOpenChange={setPairing}
        onPaired={(player) => {
          setPairing(false)
          void resource.reload()
          router.push(localePath(`/players/${player.id}`))
        }}
      />
    </div>
  )
}

export default function PlayersPage() {
  return (
    <RequirePermission permission="PLAYER_VIEW">
      <Suspense fallback={<PageSkeleton rows={3} />}>
        <PlayersContent />
      </Suspense>
    </RequirePermission>
  )
}
