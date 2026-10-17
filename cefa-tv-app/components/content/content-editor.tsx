'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { arrayMove } from '@dnd-kit/sortable'
import { IconCheck, IconLoader2 } from '@tabler/icons-react'
import { PlayNowDialog } from '@/components/content/play-now-dialog'
import { ItemProperties } from '@/components/playlist/item-properties'
import { LibraryDock } from '@/components/playlist/library-dock'
import { TRACK_ID, Timeline } from '@/components/playlist/timeline'
import { effectiveSeconds, newUid, type EditorItem } from '@/components/playlist/types'
import { MediaThumb } from '@/components/shared/media-thumb'
import { ErrorState } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { Skeleton } from '@/components/shadcn-ui/skeleton'
import { useDictionary } from '@/contexts/dictionary-context'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/api-client'
import { defaultDurationSec } from '@/lib/media'
import { toast } from '@/lib/toast'
import { formatDuration } from '@/lib/utils'
import type { MediaAsset, Playlist, ScheduleTargets } from '@/types/api'

const AUTOSAVE_DELAY_MS = 800

export interface ContentTarget {
  kind: 'player' | 'group'
  id: string
  name: string
  fallbackPlaylistId: string | null
}

type SaveState = 'idle' | 'saving' | 'saved' | 'error'

function toEditorItems(playlist: Playlist): EditorItem[] {
  return playlist.items.map((item) => ({
    uid: newUid(),
    assetId: item.asset.id,
    durationSec: item.durationSec,
    fit: item.fit,
    muted: item.muted,
  }))
}

function snapshot(items: EditorItem[]): string {
  return JSON.stringify(
    items.map(({ assetId, durationSec, fit, muted }) => ({ assetId, durationSec, fit, muted }))
  )
}

export function ContentEditor({
  target,
  canEdit,
  publishNote,
  onLinked,
}: {
  target: ContentTarget
  canEdit: boolean
  publishNote: React.ReactNode
  onLinked: () => void
}) {
  const dict = useDictionary()
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [library, setLibrary] = useState<MediaAsset[]>([])

  const mergeLibrary = useCallback((loaded: MediaAsset[]) => {
    setLibrary((current) => {
      const known = new Set(current.map((asset) => asset.id))
      const extra = loaded.filter((asset) => !known.has(asset.id))
      return extra.length > 0 ? [...current, ...extra] : current
    })
  }, [])
  const [items, setItems] = useState<EditorItem[]>([])
  const [selectedUid, setSelectedUid] = useState<string | null>(null)
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [dragAssetId, setDragAssetId] = useState<string | null>(null)
  const [dragUid, setDragUid] = useState<string | null>(null)
  const [playNow, setPlayNow] = useState<MediaAsset | null>(null)
  const playlistIdRef = useRef<string | null>(target.fallbackPlaylistId)
  const playlistNameRef = useRef<string>(dict.content.defaultName.replace('{name}', target.name))
  const savedRef = useRef('[]')
  const queueRef = useRef<Promise<void>>(Promise.resolve())

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }))

  const load = useCallback(() => {
    const playlistId = playlistIdRef.current
    return Promise.all([
      playlistId ? api.playlists.get(playlistId) : Promise.resolve(null),
      api.media.list({ size: 500 }),
    ])
      .then(([playlist, media]) => {
        const loaded = playlist ? toEditorItems(playlist) : []
        if (playlist) playlistNameRef.current = playlist.name
        setItems(loaded)
        savedRef.current = snapshot(loaded)
        const known = new Set(media.content.map((asset) => asset.id))
        setLibrary([
          ...media.content,
          ...(playlist?.items ?? [])
            .map((item) => item.asset)
            .filter((asset) => !known.has(asset.id)),
        ])
        setStatus('ready')
      })
      .catch(() => setStatus('error'))
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const persist = useCallback(
    (snapshotItems: EditorItem[]) => {
      const input = {
        name: playlistNameRef.current,
        description: null,
        hidden: true,
        items: snapshotItems.map(({ assetId, durationSec, fit, muted }) => ({
          assetId,
          durationSec,
          fit,
          muted,
        })),
      }
      queueRef.current = queueRef.current.then(async () => {
        setSaveState('saving')
        try {
          const existing = playlistIdRef.current
          if (existing) {
            await api.playlists.update(existing, input)
          } else {
            const created = await api.playlists.create(input)
            playlistIdRef.current = created.id
            if (target.kind === 'player') {
              await api.players.update(target.id, { fallbackPlaylistId: created.id })
            } else {
              await api.groups.update(target.id, {
                name: target.name,
                fallbackPlaylistId: created.id,
              })
            }
            onLinked()
          }
          savedRef.current = snapshot(snapshotItems)
          setSaveState('saved')
        } catch (err) {
          setSaveState('error')
          toast.error(apiErrorMessage(err, dict.content.saveFailed))
        }
      })
    },
    [target.kind, target.id, target.name, onLinked, dict.content.saveFailed]
  )

  useEffect(() => {
    if (status !== 'ready' || !canEdit) return
    if (snapshot(items) === savedRef.current) return
    const timer = window.setTimeout(() => persist(items), AUTOSAVE_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [items, status, canEdit, persist])

  const assets = useMemo(() => new Map(library.map((asset) => [asset.id, asset])), [library])
  const totalSec = items.reduce(
    (sum, item) => sum + effectiveSeconds(item, assets.get(item.assetId)),
    0
  )
  const selectedIndex = items.findIndex((item) => item.uid === selectedUid)
  const selected = selectedIndex >= 0 ? items[selectedIndex] : null

  const buildItem = useCallback(
    (asset: MediaAsset): EditorItem => ({
      uid: newUid(),
      assetId: asset.id,
      durationSec: defaultDurationSec(asset.type, asset.durationMs),
      fit: 'COVER',
      muted: true,
    }),
    []
  )

  function append(asset: MediaAsset) {
    const item = buildItem(asset)
    setItems((current) => [...current, item])
    setSelectedUid(item.uid)
  }

  function patchItem(uid: string, patch: Partial<EditorItem>) {
    setItems((current) => current.map((item) => (item.uid === uid ? { ...item, ...patch } : item)))
  }

  function removeItem(uid: string) {
    setItems((current) => current.filter((item) => item.uid !== uid))
    setSelectedUid((current) => (current === uid ? null : current))
  }

  function onDragStart(event: DragStartEvent) {
    const activeId = String(event.active.id)
    if (activeId.startsWith('lib:')) setDragAssetId(activeId.slice(4))
    else setDragUid(activeId)
  }

  function onDragEnd(event: DragEndEvent) {
    const { active, over } = event
    setDragAssetId(null)
    setDragUid(null)
    if (!over) return
    const activeId = String(active.id)

    if (activeId.startsWith('lib:')) {
      const asset = assets.get(activeId.slice(4))
      if (!asset) return
      const item = buildItem(asset)
      let insertAt = items.length
      if (over.id !== TRACK_ID) {
        const overIndex = items.findIndex((entry) => entry.uid === over.id)
        const origin = event.activatorEvent as PointerEvent
        const pointerX = origin.clientX + event.delta.x
        const after = pointerX > over.rect.left + over.rect.width / 2
        insertAt = overIndex + (after ? 1 : 0)
      }
      setItems((current) => [...current.slice(0, insertAt), item, ...current.slice(insertAt)])
      setSelectedUid(item.uid)
      return
    }

    if (over.id !== TRACK_ID && active.id !== over.id) {
      setItems((current) => {
        const from = current.findIndex((entry) => entry.uid === active.id)
        const to = current.findIndex((entry) => entry.uid === over.id)
        return from < 0 || to < 0 ? current : arrayMove(current, from, to)
      })
    }
  }

  if (status === 'loading') return <Skeleton className="h-72 rounded-xl" />
  if (status === 'error') return <ErrorState onRetry={() => void load()} />

  const playNowTargets: ScheduleTargets =
    target.kind === 'player'
      ? { playerIds: [target.id], groupIds: [] }
      : { playerIds: [], groupIds: [target.id] }
  const dragAsset = dragAssetId ? assets.get(dragAssetId) : undefined
  const dragItem = dragUid ? items.find((item) => item.uid === dragUid) : undefined
  const dragItemAsset = dragItem ? assets.get(dragItem.assetId) : undefined

  const saveBadge =
    saveState === 'saving' ? (
      <Badge variant="outline" className="gap-1.5">
        <IconLoader2 className="size-3 animate-spin" /> {dict.content.saving}
      </Badge>
    ) : saveState === 'saved' ? (
      <Badge variant="outline" className="gap-1.5">
        <IconCheck className="size-3" /> {dict.content.saved}
      </Badge>
    ) : null

  const description =
    target.kind === 'player' ? dict.content.descriptionPlayer : dict.content.descriptionGroup

  if (!canEdit) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{dict.content.title}</CardTitle>
          <p className="text-muted-foreground text-sm">{description}</p>
        </CardHeader>
        <CardContent className="space-y-3">
          {items.length === 0 ? (
            <p className="text-muted-foreground py-4 text-center text-sm">{dict.content.empty}</p>
          ) : (
            <ul className="divide-y">
              {items.map((item) => {
                const asset = assets.get(item.assetId)
                return (
                  <li key={item.uid} className="flex items-center gap-3 py-2">
                    {asset ? (
                      <MediaThumb id={asset.id} type={asset.type} className="size-10 shrink-0" />
                    ) : null}
                    <span className="min-w-0 flex-1 truncate text-sm font-medium">
                      {asset?.name ?? dict.playlists.missingAsset}
                    </span>
                    <span className="text-muted-foreground text-xs tabular-nums">
                      {formatDuration(effectiveSeconds(item, asset))}
                    </span>
                  </li>
                )
              })}
            </ul>
          )}
          <p className="text-muted-foreground text-xs">{dict.content.readOnly}</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      onDragCancel={() => {
        setDragAssetId(null)
        setDragUid(null)
      }}
    >
      <div className="space-y-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold tracking-tight">{dict.content.title}</h2>
            <p className="text-muted-foreground text-sm">{description}</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {saveBadge}
            <Badge variant="secondary" className="tabular-nums">
              {dict.content.total}: {formatDuration(totalSec)}
            </Badge>
            {publishNote}
          </div>
        </div>

        <div className="space-y-4">
          <div className="min-w-0 space-y-4">
            <Timeline
              items={items}
              assets={assets}
              pxPerSec={14}
              selectedUid={selectedUid}
              onSelect={setSelectedUid}
              onResize={(uid, durationSec) => patchItem(uid, { durationSec })}
              onRemove={removeItem}
            />
            {selected ? (
              <Card>
                <CardContent>
                  <ItemProperties
                    item={selected}
                    asset={assets.get(selected.assetId)}
                    index={selectedIndex}
                    count={items.length}
                    onChange={(patch) => patchItem(selected.uid, patch)}
                    onMove={(direction) =>
                      setItems((current) =>
                        arrayMove(current, selectedIndex, selectedIndex + direction)
                      )
                    }
                    onRemove={() => removeItem(selected.uid)}
                  />
                </CardContent>
              </Card>
            ) : null}
          </div>

          <LibraryDock
            title={dict.content.library}
            onLoaded={mergeLibrary}
            onAdd={append}
            onPlayNow={setPlayNow}
          />
        </div>
      </div>

      <DragOverlay dropAnimation={null}>
        {dragAsset ? (
          <div className="bg-card flex w-60 items-center gap-2 rounded-lg border p-1.5 shadow-lg">
            <MediaThumb id={dragAsset.id} type={dragAsset.type} className="size-12 shrink-0" />
            <p className="truncate text-sm font-medium">{dragAsset.name}</p>
          </div>
        ) : dragItem && dragItemAsset ? (
          <div className="bg-card flex h-24 w-48 items-center gap-2 rounded-lg border-2 p-1.5 shadow-lg">
            <MediaThumb
              id={dragItemAsset.id}
              type={dragItemAsset.type}
              className="size-16 shrink-0"
            />
            <p className="truncate text-sm font-medium">{dragItemAsset.name}</p>
          </div>
        ) : null}
      </DragOverlay>

      {playNow ? (
        <PlayNowDialog
          asset={playNow}
          targetName={target.name}
          targets={playNowTargets}
          onClose={() => setPlayNow(null)}
          onStarted={() => setPlayNow(null)}
        />
      ) : null}
    </DndContext>
  )
}
