'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  pointerWithin,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import { IconCheck, IconLoader2, IconPlaylist, IconSearch, IconX } from '@tabler/icons-react'
import { LibraryPanel } from '@/components/playlist/library-panel'
import { dayFormatter, describeWhen } from '@/components/schedule/helpers'
import { TargetPicker, operableGroups, operablePlayers } from '@/components/schedule/target-picker'
import {
  DEFAULT_WHEN,
  WhenPicker,
  whenIsValid,
  whenToFields,
  type WhenValue,
} from '@/components/schedule/when-picker'
import { AdvancedOptions } from '@/components/shared/advanced-options'
import { NumberField } from '@/components/shared/number-field'
import { MediaThumb } from '@/components/shared/media-thumb'
import { Button } from '@/components/shadcn-ui/button'
import { Checkbox } from '@/components/shadcn-ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import { Tabs, TabsList, TabsTrigger } from '@/components/shadcn-ui/tabs'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { defaultDurationSec } from '@/lib/media'
import { toast } from '@/lib/toast'
import { cn, formatDuration, isoWeekday, normalizeText, parseIsoDate } from '@/lib/utils'
import type {
  MediaAsset,
  Player,
  PlayerGroup,
  Playlist,
  Page,
  PlaylistInput,
  Site,
  ScheduleInput,
  ScheduleTargets,
} from '@/types/api'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useFormat } from '@/hooks/use-format'

type Kind = 'as' | 'pl'

interface Selection {
  kind: Kind
  id: string
}

export interface WizardInitial {
  targets?: ScheduleTargets
  assetIds?: string[]
  playlistIds?: string[]
  date?: string
}

const STEPS = ['what', 'where', 'when'] as const

function StepIndicator({ step }: { step: number }) {
  const dict = useDictionary()
  return (
    <ol className="flex items-center gap-2">
      {STEPS.map((key, index) => (
        <li key={key} className="flex flex-1 items-center gap-2">
          <span
            className={cn(
              'flex size-7 shrink-0 items-center justify-center rounded-full border text-xs font-semibold',
              index < step && 'bg-primary text-primary-foreground border-primary',
              index === step && 'border-primary text-primary',
              index > step && 'text-muted-foreground'
            )}
          >
            {index < step ? <IconCheck className="size-4" /> : index + 1}
          </span>
          <span className={cn('text-sm font-medium', index > step && 'text-muted-foreground')}>
            {dict.schedule.wizard.steps[key]}
          </span>
          {index < STEPS.length - 1 ? <span className="bg-border h-px flex-1" /> : null}
        </li>
      ))}
    </ol>
  )
}

const SELECTION_DROP_ID = 'wizard-selection'

function SelectionTray({
  selection,
  labelOf,
  assetOf,
  onRemove,
}: {
  selection: Selection[]
  labelOf: (entry: Selection) => string
  assetOf: (id: string) => MediaAsset | undefined
  onRemove: (entry: Selection) => void
}) {
  const dict = useDictionary()
  const wizard = dict.schedule.wizard
  const fmt = useFormat()
  const { setNodeRef, isOver } = useDroppable({ id: SELECTION_DROP_ID })

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'space-y-2 rounded-xl border border-dashed p-3 transition-colors',
        isOver && 'border-primary bg-primary/5'
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-medium">{wizard.selectionTitle}</p>
        <span className="text-muted-foreground text-xs">
          {fmt(wizard.selected, { count: selection.length })}
        </span>
      </div>
      {selection.length === 0 ? (
        <p className="text-muted-foreground py-2 text-center text-sm">
          {isOver ? wizard.dropHere : wizard.selectionEmpty}
        </p>
      ) : (
        <ol className="flex flex-wrap gap-1.5">
          {selection.map((entry, index) => {
            const asset = entry.kind === 'as' ? assetOf(entry.id) : undefined
            return (
              <li
                key={`${entry.kind}:${entry.id}`}
                className="bg-card flex max-w-full items-center gap-1.5 rounded-lg border py-1 pr-1 pl-1.5"
              >
                <span className="bg-primary text-primary-foreground flex size-5 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold">
                  {index + 1}
                </span>
                {asset ? (
                  <MediaThumb id={asset.id} type={asset.type} className="size-7 shrink-0" />
                ) : (
                  <span className="bg-primary/10 text-primary flex size-7 shrink-0 items-center justify-center rounded-md">
                    <IconPlaylist className="size-4" />
                  </span>
                )}
                <span className="max-w-40 truncate text-xs font-medium">{labelOf(entry)}</span>
                <Button
                  type="button"
                  size="icon-sm"
                  variant="ghost"
                  className="size-6"
                  aria-label={wizard.removeFromSelection}
                  onClick={() => onRemove(entry)}
                >
                  <IconX />
                </Button>
              </li>
            )
          })}
        </ol>
      )}
    </div>
  )
}

function ContentStep({
  selection,
  labelOf,
  assetOf,
  onToggle,
  onAddAsset,
  onRemove,
  onAssetsLoaded,
}: {
  selection: Selection[]
  labelOf: (entry: Selection) => string
  assetOf: (id: string) => MediaAsset | undefined
  onToggle: (selection: Selection) => void
  onAddAsset: (asset: MediaAsset) => void
  onRemove: (selection: Selection) => void
  onAssetsLoaded: (assets: MediaAsset[]) => void
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const [tab, setTab] = useState<Kind>('as')
  const [playlistQuery, setPlaylistQuery] = useState('')
  const [dragging, setDragging] = useState<MediaAsset | null>(null)
  const loadedAssets = useRef(new Map<string, MediaAsset>())
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }))
  const playlistResource = useApiResource<Playlist[] | Page<Playlist>>(
    tab === 'pl' ? `${API_V1}/playlists` : null
  )
  const playlistNeedle = normalizeText(playlistQuery)
  const filteredPlaylists = asList(playlistResource.data).filter(
    (playlist) =>
      !playlist.hidden &&
      (playlistNeedle === '' || normalizeText(playlist.name).includes(playlistNeedle))
  )

  const handleLoaded = useCallback(
    (assets: MediaAsset[]) => {
      for (const asset of assets) loadedAssets.current.set(asset.id, asset)
      onAssetsLoaded(assets)
    },
    [onAssetsLoaded]
  )

  function assetFromDragId(id: string): MediaAsset | undefined {
    return loadedAssets.current.get(id.slice(4))
  }

  function onDragEnd(event: DragEndEvent) {
    setDragging(null)
    if (event.over?.id !== SELECTION_DROP_ID) return
    const asset = assetFromDragId(String(event.active.id))
    if (asset) onAddAsset(asset)
  }

  function playlistRow(playlist: Playlist) {
    const order =
      selection.findIndex((entry) => entry.kind === 'pl' && entry.id === playlist.id) + 1
    return (
      <label
        key={playlist.id}
        className={cn(
          'hover:bg-muted/60 flex cursor-pointer items-center gap-3 rounded-lg border p-1.5 transition-colors',
          order > 0 && 'border-primary bg-primary/5'
        )}
      >
        <Checkbox
          checked={order > 0}
          onCheckedChange={() => onToggle({ kind: 'pl', id: playlist.id })}
        />
        <span className="bg-primary/10 text-primary flex size-11 shrink-0 items-center justify-center rounded-md">
          <IconPlaylist className="size-5" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium">{playlist.name}</span>
          <span className="text-muted-foreground block truncate text-xs">
            {`${fmt(dict.playlists.itemCount, { count: playlist.items.length })} · ${formatDuration(playlist.totalDurationSec)}`}
          </span>
        </span>
        {order > 0 ? (
          <span className="bg-primary text-primary-foreground flex size-6 items-center justify-center rounded-full text-xs font-semibold">
            {order}
          </span>
        ) : null}
      </label>
    )
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={pointerWithin}
      onDragStart={(event) => setDragging(assetFromDragId(String(event.active.id)) ?? null)}
      onDragEnd={onDragEnd}
      onDragCancel={() => setDragging(null)}
    >
      <div className="space-y-3">
        <p className="text-muted-foreground text-sm">{dict.schedule.wizard.whatHint}</p>
        <SelectionTray
          selection={selection}
          labelOf={labelOf}
          assetOf={assetOf}
          onRemove={onRemove}
        />
        <Tabs value={tab} onValueChange={(value) => setTab(value as Kind)}>
          <TabsList>
            <TabsTrigger value="as">{dict.schedule.mediaTab}</TabsTrigger>
            <TabsTrigger value="pl">{dict.schedule.playlistsTab}</TabsTrigger>
          </TabsList>
        </Tabs>
        {tab === 'as' ? (
          <LibraryPanel
            layout="grid"
            className="h-80"
            addLabel={dict.schedule.wizard.addToSelection}
            onAdd={onAddAsset}
            onLoaded={handleLoaded}
          />
        ) : (
          <div className="space-y-3">
            <div className="relative">
              <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
              <Input
                className="pl-8"
                placeholder={dict.media.search}
                value={playlistQuery}
                onChange={(event) => setPlaylistQuery(event.target.value)}
              />
            </div>
            <div className="max-h-72 space-y-1.5 overflow-y-auto pr-1">
              {filteredPlaylists.map(playlistRow)}
              {filteredPlaylists.length === 0 ? (
                <p className="text-muted-foreground py-8 text-center text-sm">
                  {dict.playlists.libraryEmpty}
                </p>
              ) : null}
            </div>
          </div>
        )}
      </div>
      {createPortal(
        <DragOverlay dropAnimation={null}>
          {dragging ? (
            <div className="bg-card flex w-48 items-center gap-2 rounded-lg border p-1.5 shadow-lg">
              <MediaThumb id={dragging.id} type={dragging.type} className="size-10 shrink-0" />
              <span className="truncate text-sm font-medium">{dragging.name}</span>
            </div>
          ) : null}
        </DragOverlay>,
        document.body
      )}
    </DndContext>
  )
}

export function ScheduleWizard({
  open,
  initial,
  onClose,
  onPublished,
}: {
  open: boolean
  initial?: WizardInitial
  onClose: () => void
  onPublished: () => void
}) {
  const dict = useDictionary()
  const { locale } = useDateFormatter()
  const { user } = useAuth()
  const sitesResource = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`)
  const [loaded, setLoaded] = useState(false)
  const [players, setPlayers] = useState<Player[]>([])
  const [groups, setGroups] = useState<PlayerGroup[]>([])
  const [playlists, setPlaylists] = useState<Playlist[]>([])
  const [assets, setAssets] = useState<MediaAsset[]>([])
  const [step, setStep] = useState(0)
  const [selection, setSelection] = useState<Selection[]>(() => [
    ...(initial?.playlistIds ?? []).map((id) => ({ kind: 'pl' as const, id })),
    ...(initial?.assetIds ?? []).map((id) => ({ kind: 'as' as const, id })),
  ])
  const [targets, setTargets] = useState<ScheduleTargets>(
    initial?.targets ?? { playerIds: [], groupIds: [] }
  )
  const [when, setWhen] = useState<WhenValue>(() =>
    initial?.date
      ? {
          ...DEFAULT_WHEN,
          mode: 'slot',
          days: [isoWeekday(parseIsoDate(initial.date))],
          startDate: initial.date,
          endDate: initial.date,
        }
      : DEFAULT_WHEN
  )
  const [name, setName] = useState('')
  const [nameTouched, setNameTouched] = useState(false)
  const [priority, setPriority] = useState(10)
  const [publishing, setPublishing] = useState(false)

  useEffect(() => {
    if (!open) return
    let active = true
    Promise.all([
      api.players.list(),
      api.groups.list(),
      api.playlists.list(),
      api.media.list({ size: 500 }),
    ])
      .then(([playerList, groupList, playlistList, media]) => {
        if (!active) return
        setPlayers(asList(playerList))
        setGroups(asList(groupList))
        setPlaylists(asList(playlistList).filter((entry) => !entry.hidden))
        setAssets(media.content)
        setLoaded(true)
      })
      .catch(() => active && toast.error(dict.schedule.loadFailed))
    return () => {
      active = false
    }
  }, [open, dict.schedule.loadFailed])

  const assetById = useMemo(() => new Map(assets.map((asset) => [asset.id, asset])), [assets])
  const playlistById = useMemo(
    () => new Map(playlists.map((playlist) => [playlist.id, playlist])),
    [playlists]
  )

  function contentLabel(entry: Selection): string {
    return (
      (entry.kind === 'as' ? assetById.get(entry.id)?.name : playlistById.get(entry.id)?.name) ?? ''
    )
  }

  const contentName = selection.map(contentLabel).filter(Boolean).slice(0, 3).join(', ')

  const rememberAssets = useCallback((loadedAssets: MediaAsset[]) => {
    setAssets((current) => {
      const known = new Set(current.map((asset) => asset.id))
      const fresh = loadedAssets.filter((asset) => !known.has(asset.id))
      return fresh.length === 0 ? current : [...current, ...fresh]
    })
  }, [])

  function addAsset(asset: MediaAsset) {
    rememberAssets([asset])
    setSelection((current) =>
      current.some((item) => item.kind === 'as' && item.id === asset.id)
        ? current
        : [...current, { kind: 'as', id: asset.id }]
    )
  }

  function removeSelection(entry: Selection) {
    setSelection((current) =>
      current.filter((item) => !(item.kind === entry.kind && item.id === entry.id))
    )
  }

  function toggleSelection(entry: Selection) {
    setSelection((current) =>
      current.some((item) => item.kind === entry.kind && item.id === entry.id)
        ? current.filter((item) => !(item.kind === entry.kind && item.id === entry.id))
        : [...current, entry]
    )
  }

  const siteList = asList(sitesResource.data)
  const targetCount =
    targets.playerIds.length + targets.groupIds.length + (targets.siteIds ?? []).length

  const targetLabels = [
    ...(targets.siteIds ?? []).map((id) => siteList.find((site) => site.id === id)?.name),
    ...targets.groupIds.map((id) => groups.find((group) => group.id === id)?.name),
    ...targets.playerIds.map((id) => players.find((player) => player.id === id)?.name),
  ].filter(Boolean)
  const contentLabels = selection.map(contentLabel).filter(Boolean)
  const suggestedName =
    contentName && targetLabels.length > 0
      ? dict.schedule.wizard.suggestedName
          .replace('{content}', contentName)
          .replace('{targets}', targetLabels.join(', '))
      : contentName
  const shownName = nameTouched ? name : suggestedName
  const finalName = shownName.trim().slice(0, 120)
  const stepValid = [selection.length > 0, targetCount > 0, whenIsValid(when) && finalName !== ''][
    step
  ]
  const fields = whenToFields(when)

  async function buildContent(): Promise<
    Pick<ScheduleInput, 'playlistId' | 'assetId' | 'durationSec'>
  > {
    if (selection.length === 1) {
      const [only] = selection
      if (only.kind === 'pl') return { playlistId: only.id }
      const asset = assetById.get(only.id)
      return {
        assetId: only.id,
        durationSec: asset ? defaultDurationSec(asset.type, asset.durationMs) : undefined,
      }
    }
    const items: PlaylistInput['items'] = selection.flatMap((entry) => {
      if (entry.kind === 'as') {
        const asset = assetById.get(entry.id)
        if (!asset) return []
        return [
          {
            assetId: asset.id,
            durationSec: defaultDurationSec(asset.type, asset.durationMs),
            fit: 'COVER' as const,
            muted: true,
          },
        ]
      }
      const playlist = playlistById.get(entry.id)
      return (playlist?.items ?? []).map((item) => ({
        assetId: item.asset.id,
        durationSec: item.durationSec,
        fit: item.fit,
        muted: item.muted,
      }))
    })
    const created = await api.playlists.create({
      name: finalName,
      description: null,
      hidden: true,
      items,
    })
    return { playlistId: created.id }
  }

  async function publish() {
    setPublishing(true)
    try {
      const content = await buildContent()
      await api.schedules.create({
        name: finalName,
        ...content,
        priority,
        ...fields,
        enabled: true,
        targets,
      })
      toast.success(dict.schedule.wizard.published)
      onPublished()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.saveFailed))
    } finally {
      setPublishing(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(value) => !value && onClose()}>
      <DialogContent className="flex max-h-[90vh] flex-col gap-0 p-0 sm:max-w-3xl">
        <DialogHeader className="space-y-4 p-6 pb-3">
          <div>
            <DialogTitle>{dict.schedule.wizard.title}</DialogTitle>
            <DialogDescription>{dict.schedule.wizard.description}</DialogDescription>
          </div>
          <StepIndicator step={step} />
        </DialogHeader>
        <div className="min-h-0 flex-1 overflow-y-auto px-6 py-3">
          {!loaded ? (
            <div className="flex justify-center py-12">
              <IconLoader2 className="text-muted-foreground animate-spin" />
            </div>
          ) : step === 0 ? (
            <ContentStep
              selection={selection}
              labelOf={contentLabel}
              assetOf={(id) => assetById.get(id)}
              onToggle={toggleSelection}
              onAddAsset={addAsset}
              onRemove={removeSelection}
              onAssetsLoaded={rememberAssets}
            />
          ) : step === 1 ? (
            <div className="space-y-3">
              <p className="text-muted-foreground text-sm">{dict.schedule.wizard.whereHint}</p>
              <TargetPicker
                players={operablePlayers(user, players)}
                groups={operableGroups(user, groups)}
                value={targets}
                onChange={setTargets}
              />
            </div>
          ) : (
            <div className="space-y-5">
              <p className="text-muted-foreground text-sm">{dict.schedule.wizard.whenHint}</p>
              <WhenPicker value={when} onChange={setWhen} />
              <div className="bg-muted/40 space-y-2 rounded-xl border p-4 text-sm">
                <p className="font-medium">{dict.schedule.wizard.summary}</p>
                <dl className="grid grid-cols-[7rem_1fr] gap-x-3 gap-y-1.5">
                  <dt className="text-muted-foreground">{dict.schedule.wizard.summaryContent}</dt>
                  <dd>{contentLabels.join(', ')}</dd>
                  <dt className="text-muted-foreground">{dict.schedule.wizard.summaryTargets}</dt>
                  <dd>{targetLabels.join(', ')}</dd>
                  <dt className="text-muted-foreground">{dict.schedule.wizard.summaryWhen}</dt>
                  <dd>{describeWhen(fields, dict, dayFormatter(locale))}</dd>
                </dl>
              </div>
              <div className="space-y-2">
                <Label htmlFor="wizard-name">{dict.schedule.wizard.name}</Label>
                <Input
                  id="wizard-name"
                  required
                  maxLength={120}
                  placeholder={dict.schedule.wizard.namePlaceholder}
                  value={shownName}
                  onChange={(event) => {
                    setName(event.target.value)
                    setNameTouched(true)
                  }}
                />
              </div>
              <AdvancedOptions title={dict.schedule.wizard.advanced}>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="wizard-priority">{dict.schedule.priority}</Label>
                    <NumberField
                      id="wizard-priority"
                      min={0}
                      max={100}
                      value={priority}
                      onValueChange={setPriority}
                    />
                  </div>
                  <p className="text-muted-foreground text-xs sm:col-span-2">
                    {dict.schedule.priorityHint}
                  </p>
                </div>
              </AdvancedOptions>
            </div>
          )}
        </div>
        <DialogFooter className="border-t p-4 sm:justify-between">
          <Button
            type="button"
            variant="outline"
            onClick={() => (step === 0 ? onClose() : setStep(step - 1))}
          >
            {step === 0 ? dict.common.cancel : dict.common.back}
          </Button>
          {step < STEPS.length - 1 ? (
            <Button
              type="button"
              disabled={!loaded || !stepValid}
              onClick={() => setStep(step + 1)}
            >
              {dict.common.next}
            </Button>
          ) : (
            <Button
              type="button"
              disabled={!loaded || !stepValid || publishing}
              onClick={() => void publish()}
            >
              {publishing ? <IconLoader2 className="animate-spin" /> : null}
              {dict.schedule.wizard.publish}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
