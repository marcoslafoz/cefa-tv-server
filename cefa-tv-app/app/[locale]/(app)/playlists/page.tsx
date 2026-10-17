'use client'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { ROW_CLASS, rowActivation } from '@/lib/row-activation'
import { useCallback, useMemo, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import {
  IconCalendarPlus,
  IconCopy,
  IconDeviceTv,
  IconLoader2,
  IconPencil,
  IconPlaylist,
  IconPlus,
  IconSearch,
  IconTrash,
  IconLayoutGrid,
} from '@tabler/icons-react'
import { PlaylistCollage } from '@/components/playlist/playlist-collage'
import { MediaPicker } from '@/components/playlist/media-picker'
import { PublishDialog } from '@/components/publish/publish-dialog'
import { ConfirmDialog } from '@/components/shared/confirm-dialog'
import { PageHeader } from '@/components/shared/page-header'
import { RequirePermission } from '@/components/shared/require-permission'
import { EmptyState, ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card } from '@/components/shadcn-ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { Input } from '@/components/shadcn-ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/shadcn-ui/popover'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { Label } from '@/components/shadcn-ui/label'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useLocalePath } from '@/hooks/use-locale-path'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { defaultDurationSec } from '@/lib/media'
import { hasPermission } from '@/lib/permissions'
import { toast } from '@/lib/toast'
import { cn, formatDuration, normalizeText } from '@/lib/utils'
import type {
  MediaAsset,
  Page,
  Player,
  PlayerGroup,
  Playlist,
  PlaylistInput,
  Schedule,
  Site,
} from '@/types/api'
import { useFormat } from '@/hooks/use-format'

const MAX_CHIPS = 2

function NewPlaylistDialog({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const router = useRouter()
  const localePath = useLocalePath()
  const [name, setName] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [saving, setSaving] = useState(false)
  const [known, setKnown] = useState<Map<string, MediaAsset>>(new Map())

  const remember = useCallback((loaded: MediaAsset[]) => {
    setKnown((current) => {
      const next = new Map(current)
      loaded.forEach((asset) => next.set(asset.id, asset))
      return next
    })
  }, [])

  function toggle(assetId: string) {
    setSelected((current) =>
      current.includes(assetId) ? current.filter((id) => id !== assetId) : [...current, assetId]
    )
  }

  async function create() {
    setSaving(true)
    try {
      const input: PlaylistInput = {
        name: name.trim(),
        description: null,
        items: selected.flatMap((id) => {
          const asset = known.get(id)
          return asset
            ? [
                {
                  assetId: id,
                  durationSec: defaultDurationSec(asset.type, asset.durationMs),
                  fit: 'COVER' as const,
                  muted: true,
                },
              ]
            : []
        }),
      }
      const playlist = await api.playlists.create(input)
      router.push(localePath(`/playlists/${playlist.id}`))
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.playlists.createFailed))
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>{dict.playlists.wizard.title}</DialogTitle>
          <DialogDescription>{dict.playlists.wizard.description}</DialogDescription>
        </DialogHeader>
        <div className="space-y-5">
          <div className="space-y-2">
            <Label htmlFor="playlist-name">{dict.playlists.wizard.nameLabel}</Label>
            <Input
              id="playlist-name"
              autoFocus
              value={name}
              placeholder={dict.playlists.wizard.namePlaceholder}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label>{dict.playlists.wizard.pickTitle}</Label>
            <p className="text-muted-foreground text-sm">{dict.playlists.wizard.pickHint}</p>
            <MediaPicker
              selected={selected}
              onToggle={toggle}
              onLoaded={remember}
              emptyHint={
                <>
                  {dict.playlists.wizard.noMedia}{' '}
                  <Link className="text-primary underline" href={localePath('/media')}>
                    {dict.playlists.wizard.goToLibrary}
                  </Link>
                </>
              }
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {dict.common.cancel}
          </Button>
          <Button disabled={!name.trim() || saving} onClick={() => void create()}>
            {saving ? <IconLoader2 className="animate-spin" /> : null}
            {selected.length > 0
              ? fmt(dict.playlists.wizard.createWith, { count: selected.length })
              : dict.playlists.wizard.createEmpty}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function AudienceChip({ entry }: { entry: { id: string; name: string; group: boolean } }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Badge variant="secondary" className="max-w-full min-w-0 gap-1">
          {entry.group ? (
            <IconLayoutGrid className="size-3 shrink-0" />
          ) : (
            <IconDeviceTv className="size-3 shrink-0" />
          )}
          <span className="truncate">{entry.name}</span>
        </Badge>
      </TooltipTrigger>
      <TooltipContent>{entry.name}</TooltipContent>
    </Tooltip>
  )
}

function PlaylistCard({
  playlist,
  audience,
  canSchedule,
  onSchedule,
  onDuplicate,
  onDelete,
}: {
  playlist: Playlist
  audience: Array<{ id: string; name: string; group: boolean }>
  canSchedule: boolean
  onSchedule: () => void
  onDuplicate: () => void
  onDelete: () => void
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const localePath = useLocalePath()
  const editHref = localePath(`/playlists/${playlist.id}`)
  const router = useRouter()
  const scheduled = audience.length > 0
  const hidden = audience.slice(MAX_CHIPS)

  return (
    <Card
      {...rowActivation(() => router.push(editHref))}
      className={cn('hover:border-primary/50 h-full min-w-0 gap-3 overflow-hidden p-4', ROW_CLASS)}
    >
      <div className="flex min-w-0 gap-3">
        <PlaylistCollage
          assets={playlist.items.map((item) => item.asset)}
          className="size-20 shrink-0 sm:size-24"
        />
        <div className="min-w-0 flex-1 space-y-1">
          <Tooltip>
            <TooltipTrigger asChild>
              <p className="truncate font-medium">{playlist.name}</p>
            </TooltipTrigger>
            <TooltipContent>{playlist.name}</TooltipContent>
          </Tooltip>
          <p className="text-muted-foreground truncate text-xs tabular-nums">
            {fmt(dict.playlists.itemCount, { count: playlist.items.length })} ·{' '}
            {formatDuration(playlist.totalDurationSec)}
          </p>
          {scheduled ? (
            <div className="space-y-1 pt-1">
              <p className="text-muted-foreground text-xs">{dict.playlists.onAirIn}</p>
              <div className="flex min-w-0 flex-wrap items-center gap-1">
                {audience.slice(0, MAX_CHIPS).map((entry) => (
                  <AudienceChip key={entry.id} entry={entry} />
                ))}
                {hidden.length > 0 ? (
                  <Popover>
                    <PopoverTrigger asChild>
                      <button type="button" className="shrink-0">
                        <Badge variant="outline">
                          {fmt(dict.playlists.moreScreens, { count: hidden.length })}
                        </Badge>
                      </button>
                    </PopoverTrigger>
                    <PopoverContent align="start" className="flex w-64 flex-col items-start gap-1">
                      {hidden.map((entry) => (
                        <AudienceChip key={entry.id} entry={entry} />
                      ))}
                    </PopoverContent>
                  </Popover>
                ) : null}
              </div>
            </div>
          ) : (
            <div className="pt-1">
              <Badge variant="outline" className="text-muted-foreground max-w-full">
                <span className="truncate">{dict.playlists.notScheduled}</span>
              </Badge>
            </div>
          )}
        </div>
      </div>
      <div className="mt-auto flex min-w-0 items-center gap-2 border-t pt-3">
        <Button asChild size="sm" variant="outline" className="min-w-0 flex-1">
          <Link href={editHref}>
            <IconPencil /> <span className="truncate">{dict.common.edit}</span>
          </Link>
        </Button>
        {canSchedule ? (
          <Button
            size="sm"
            variant={scheduled ? 'outline' : 'default'}
            className="min-w-0 flex-1"
            onClick={onSchedule}
          >
            <IconCalendarPlus /> <span className="truncate">{dict.playlists.schedule}</span>
          </Button>
        ) : null}
        <Button
          size="icon-sm"
          variant="ghost"
          className="shrink-0"
          aria-label={dict.playlists.duplicate}
          title={dict.playlists.duplicate}
          onClick={onDuplicate}
        >
          <IconCopy />
        </Button>
        <Button
          size="icon-sm"
          variant="ghost"
          className="shrink-0"
          aria-label={dict.common.delete}
          title={dict.common.delete}
          onClick={onDelete}
        >
          <IconTrash />
        </Button>
      </div>
    </Card>
  )
}

type SortKey = 'name' | 'recent' | 'duration'

function PlaylistsPageContent() {
  const dict = useDictionary()
  const { user } = useAuth()
  const resource = useApiResource<Playlist[] | Page<Playlist>>(`${API_V1}/playlists`)
  const schedules = useApiResource<Schedule[] | Page<Schedule>>(`${API_V1}/schedules`, {
    intervalMs: 10000,
  })
  const players = useApiResource<Player[] | Page<Player>>(`${API_V1}/players`)
  const groups = useApiResource<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`)
  const sites = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`)
  const [creating, setCreating] = useState(false)
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<SortKey>('name')
  const [toDelete, setToDelete] = useState<Playlist | null>(null)
  const [toSchedule, setToSchedule] = useState<Playlist | null>(null)
  const canSchedule = hasPermission(user, 'SCHEDULE_MANAGE')

  const audienceByPlaylist = useMemo(() => {
    const playerList = asList(players.data)
    const groupList = asList(groups.data)
    const siteList = asList(sites.data)
    const result = new Map<string, Array<{ id: string; name: string; group: boolean }>>()
    for (const schedule of asList(schedules.data)) {
      if (!schedule.enabled) continue
      const entries = result.get(schedule.playlistId) ?? []
      for (const id of schedule.targets.siteIds ?? []) {
        const site = siteList.find((candidate) => candidate.id === id)
        if (site && !entries.some((entry) => entry.id === id)) {
          entries.push({ id, name: site.name, group: true })
        }
      }
      for (const id of schedule.targets.groupIds) {
        const group = groupList.find((candidate) => candidate.id === id)
        if (group && !entries.some((entry) => entry.id === id)) {
          entries.push({ id, name: group.name, group: true })
        }
      }
      for (const id of schedule.targets.playerIds) {
        const player = playerList.find((candidate) => candidate.id === id)
        if (player && !entries.some((entry) => entry.id === id)) {
          entries.push({ id, name: player.name, group: false })
        }
      }
      result.set(schedule.playlistId, entries)
    }
    return result
  }, [schedules.data, players.data, groups.data, sites.data])

  const playlists = useMemo(() => {
    const needle = normalizeText(query)
    const visible = asList(resource.data).filter(
      (playlist) =>
        !playlist.hidden && (needle === '' || normalizeText(playlist.name).includes(needle))
    )
    return visible.sort((left, right) => {
      if (sort === 'duration') return right.totalDurationSec - left.totalDurationSec
      if (sort === 'recent') {
        return Date.parse(right.updatedAt ?? '') - Date.parse(left.updatedAt ?? '') || 0
      }
      return left.name.localeCompare(right.name)
    })
  }, [resource.data, query, sort])

  async function duplicate(playlist: Playlist) {
    try {
      await api.playlists.create({
        name: `${playlist.name}${dict.playlists.copySuffix}`,
        description: playlist.description,
        items: playlist.items.map((item) => ({
          assetId: item.asset.id,
          durationSec: item.durationSec,
          fit: item.fit,
          muted: item.muted,
        })),
      })
      toast.success(dict.playlists.duplicated)
      void resource.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.playlists.duplicateFailed))
    }
  }

  async function remove(playlist: Playlist) {
    try {
      await api.playlists.remove(playlist.id)
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.playlists.deleteFailed))
    } finally {
      void resource.reload()
    }
  }

  if (resource.loading && !resource.data) return <PageSkeleton rows={2} />
  if (resource.error && !resource.data) return <ErrorState onRetry={() => void resource.reload()} />

  const newButton = (
    <Button onClick={() => setCreating(true)}>
      <IconPlus /> {dict.playlists.new}
    </Button>
  )
  const total = asList(resource.data).filter((playlist) => !playlist.hidden).length

  return (
    <div className="space-y-5">
      <PageHeader
        title={dict.playlists.title}
        description={dict.playlists.description}
        actions={newButton}
      />
      {total === 0 ? (
        <EmptyState
          icon={IconPlaylist}
          title={dict.playlists.emptyTitle}
          description={dict.playlists.emptyDescription}
          action={newButton}
        />
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative w-64 max-w-full">
              <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
              <Input
                className="pl-8"
                placeholder={dict.playlists.search}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
            </div>
            <Select value={sort} onValueChange={(value) => setSort(value as SortKey)}>
              <SelectTrigger className="w-48" aria-label={dict.playlists.sort.label}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="name">{dict.playlists.sort.name}</SelectItem>
                <SelectItem value="recent">{dict.playlists.sort.recent}</SelectItem>
                <SelectItem value="duration">{dict.playlists.sort.duration}</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {playlists.length === 0 ? (
            <p className="text-muted-foreground rounded-lg border border-dashed p-8 text-center text-sm">
              {dict.playlists.noMatches}
            </p>
          ) : (
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {playlists.map((playlist) => (
                <PlaylistCard
                  key={playlist.id}
                  playlist={playlist}
                  audience={audienceByPlaylist.get(playlist.id) ?? []}
                  canSchedule={canSchedule}
                  onSchedule={() => setToSchedule(playlist)}
                  onDuplicate={() => void duplicate(playlist)}
                  onDelete={() => setToDelete(playlist)}
                />
              ))}
            </div>
          )}
        </>
      )}

      <NewPlaylistDialog open={creating} onOpenChange={setCreating} />
      <PublishDialog
        open={toSchedule !== null}
        onOpenChange={(open) => !open && setToSchedule(null)}
        playlist={toSchedule}
        onPublished={() => void schedules.reload()}
      />
      <ConfirmDialog
        open={toDelete !== null}
        onOpenChange={(open) => !open && setToDelete(null)}
        title={dict.playlists.deleteTitle}
        description={dict.playlists.deleteDescription.replace('{name}', toDelete?.name ?? '')}
        confirmLabel={dict.common.delete}
        onConfirm={() => {
          if (toDelete) void remove(toDelete)
          setToDelete(null)
        }}
      />
    </div>
  )
}

export default function PlaylistsPage() {
  return (
    <RequirePermission permission="PLAYLIST_MANAGE">
      <PlaylistsPageContent />
    </RequirePermission>
  )
}
