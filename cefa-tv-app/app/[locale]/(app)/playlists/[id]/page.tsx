'use client'

import { Slider } from '@/components/shadcn-ui/slider'
import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
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
import {
  IconAlertCircle,
  IconArrowBackUp,
  IconArrowForwardUp,
  IconArrowLeft,
  IconCheck,
  IconHistory,
  IconLoader2,
  IconRocket,
} from '@tabler/icons-react'
import { ItemProperties } from '@/components/playlist/item-properties'
import { LibraryDock } from '@/components/playlist/library-dock'
import { PlaylistPreview } from '@/components/playlist/preview'
import { TRACK_ID, Timeline } from '@/components/playlist/timeline'
import { effectiveSeconds, newUid, type EditorItem } from '@/components/playlist/types'
import { PublishDialog } from '@/components/publish/publish-dialog'
import { MediaThumb } from '@/components/shared/media-thumb'
import { RequirePermission } from '@/components/shared/require-permission'
import { ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { TitleFields } from '@/components/shared/title-fields'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useLocalePath } from '@/hooks/use-locale-path'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/api-client'
import { defaultDurationSec } from '@/lib/media'
import { hasPermission } from '@/lib/permissions'
import { toast } from '@/lib/toast'
import { formatDuration } from '@/lib/utils'
import type { MediaAsset, Playlist } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

const AUTOSAVE_DELAY_MS = 1200
const HISTORY_LIMIT = 100

interface HistoryState {
  items: EditorItem[]
  past: EditorItem[][]
  future: EditorItem[][]
}

type HistoryAction =
  | { type: 'reset'; items: EditorItem[] }
  | { type: 'set'; items: EditorItem[] }
  | { type: 'replace'; items: EditorItem[] }
  | { type: 'checkpoint' }
  | { type: 'undo' }
  | { type: 'redo' }

function historyReducer(state: HistoryState, action: HistoryAction): HistoryState {
  switch (action.type) {
    case 'reset':
      return { items: action.items, past: [], future: [] }
    case 'set':
      return {
        items: action.items,
        past: [...state.past, state.items].slice(-HISTORY_LIMIT),
        future: [],
      }
    case 'replace':
      return { ...state, items: action.items }
    case 'checkpoint':
      return { ...state, past: [...state.past, state.items].slice(-HISTORY_LIMIT), future: [] }
    case 'undo': {
      const previous = state.past[state.past.length - 1]
      if (!previous) return state
      return {
        items: previous,
        past: state.past.slice(0, -1),
        future: [state.items, ...state.future],
      }
    }
    case 'redo': {
      const next = state.future[0]
      if (!next) return state
      return { items: next, past: [...state.past, state.items], future: state.future.slice(1) }
    }
  }
}

function toEditorItems(playlist: Playlist): EditorItem[] {
  return playlist.items.map((item) => ({
    uid: newUid(),
    assetId: item.asset.id,
    durationSec: item.durationSec,
    fit: item.fit,
    muted: item.muted,
  }))
}

function snapshot(name: string, description: string, items: EditorItem[]): string {
  return JSON.stringify({
    name,
    description,
    items: items.map(({ assetId, durationSec, fit, muted }) => ({
      assetId,
      durationSec,
      fit,
      muted,
    })),
  })
}

function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return (
    target.isContentEditable ||
    ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) ||
    target.getAttribute('role') === 'combobox'
  )
}

function SaveStatus({
  busy,
  failed,
  savedAt,
}: {
  busy: boolean
  failed: boolean
  savedAt: number | null
}) {
  const dict = useDictionary()
  const { formatRelative } = useDateFormatter()
  const [, setTick] = useState(0)

  useEffect(() => {
    const timer = window.setInterval(() => setTick((value) => value + 1), 1000)
    return () => window.clearInterval(timer)
  }, [])

  if (busy) return <>{dict.playlists.studio.saving}</>
  if (failed) return <>{dict.playlists.studio.saveError}</>
  if (!savedAt) return null
  return (
    <>
      {dict.playlists.studio.savedAt.replace(
        '{when}',
        formatRelative(new Date(savedAt).toISOString())
      )}
    </>
  )
}

function PlaylistEditorPageContent() {
  const dict = useDictionary()
  const fmt = useFormat()
  const params = useParams()
  const localePath = useLocalePath()
  const { user } = useAuth()
  const id = params.id as string
  const [status, setStatus] = useState<'loading' | 'error' | 'ready'>('loading')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [history, dispatch] = useReducer(historyReducer, { items: [], past: [], future: [] })
  const items = history.items
  const [library, setLibrary] = useState<MediaAsset[]>([])

  const mergeLibrary = useCallback((loaded: MediaAsset[]) => {
    setLibrary((current) => {
      const known = new Set(current.map((asset) => asset.id))
      const extra = loaded.filter((asset) => !known.has(asset.id))
      return extra.length > 0 ? [...current, ...extra] : current
    })
  }, [])
  const [selectedUid, setSelectedUid] = useState<string | null>(null)
  const [pxPerSec, setPxPerSec] = useState(14)
  const [saved, setSaved] = useState('')
  const [savedAt, setSavedAt] = useState<number | null>(null)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'error'>('idle')
  const [publishing, setPublishing] = useState(false)
  const [dragAssetId, setDragAssetId] = useState<string | null>(null)
  const [dragUid, setDragUid] = useState<string | null>(null)
  const latest = useRef({ name, description, items, saved })
  useEffect(() => {
    latest.current = { name, description, items, saved }
  })

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }))

  const load = useCallback(
    () =>
      Promise.all([api.playlists.get(id), api.media.list({ size: 500 })])
        .then(([playlist, media]) => {
          const loaded = toEditorItems(playlist)
          setName(playlist.name)
          setDescription(playlist.description ?? '')
          dispatch({ type: 'reset', items: loaded })
          const known = new Set(media.content.map((asset) => asset.id))
          setLibrary([
            ...media.content,
            ...playlist.items.map((item) => item.asset).filter((asset) => !known.has(asset.id)),
          ])
          setSaved(snapshot(playlist.name, playlist.description ?? '', loaded))
          setSavedAt(Date.now())
          setStatus('ready')
        })
        .catch(() => setStatus('error')),
    [id]
  )

  useEffect(() => {
    void load()
  }, [load])

  const assets = useMemo(() => new Map(library.map((asset) => [asset.id, asset])), [library])
  const dirty = status === 'ready' && snapshot(name, description, items) !== saved

  const save = useCallback(async () => {
    const current = latest.current
    if (!current.name.trim()) return false
    const body = snapshot(current.name, current.description, current.items)
    if (body === current.saved) return true
    setSaveState('saving')
    try {
      const result = await api.playlists.update(id, {
        name: current.name.trim(),
        description: current.description.trim() || null,
        items: current.items.map(({ assetId, durationSec, fit, muted }) => ({
          assetId,
          durationSec,
          fit,
          muted,
        })),
      })
      setSaved(snapshot(result.name, result.description ?? '', current.items))
      setSavedAt(Date.now())
      setSaveState('idle')
      return true
    } catch (err) {
      setSaveState('error')
      toast.error(apiErrorMessage(err, dict.playlists.saveFailed))
      return false
    }
  }, [id, dict.playlists.saveFailed])

  useEffect(() => {
    if (!dirty || saveState === 'saving' || !name.trim()) return
    const timer = window.setTimeout(() => void save(), AUTOSAVE_DELAY_MS)
    return () => window.clearTimeout(timer)
  }, [dirty, name, description, items, saveState, save])

  useEffect(() => {
    if (!dirty) return
    const handler = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [dirty])

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

  const append = useCallback(
    (asset: MediaAsset) => {
      const item = buildItem(asset)
      dispatch({ type: 'set', items: [...latest.current.items, item] })
      setSelectedUid(item.uid)
    },
    [buildItem]
  )

  const removeItem = useCallback((uid: string) => {
    dispatch({ type: 'set', items: latest.current.items.filter((item) => item.uid !== uid) })
    setSelectedUid((current) => (current === uid ? null : current))
  }, [])

  const duplicateItem = useCallback((uid: string) => {
    const current = latest.current.items
    const index = current.findIndex((item) => item.uid === uid)
    if (index < 0) return
    const copy = { ...current[index], uid: newUid() }
    dispatch({
      type: 'set',
      items: [...current.slice(0, index + 1), copy, ...current.slice(index + 1)],
    })
    setSelectedUid(copy.uid)
  }, [])

  function patchItem(uid: string, patch: Partial<EditorItem>, record = true) {
    dispatch({
      type: record ? 'set' : 'replace',
      items: items.map((item) => (item.uid === uid ? { ...item, ...patch } : item)),
    })
  }

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (isTyping(event.target)) return
      const modifier = event.metaKey || event.ctrlKey
      const key = event.key.toLowerCase()
      if (modifier && key === 'z') {
        event.preventDefault()
        dispatch({ type: event.shiftKey ? 'redo' : 'undo' })
      } else if (modifier && key === 'y') {
        event.preventDefault()
        dispatch({ type: 'redo' })
      } else if (modifier && key === 'd' && selectedUid) {
        event.preventDefault()
        duplicateItem(selectedUid)
      } else if ((key === 'delete' || key === 'backspace') && selectedUid) {
        event.preventDefault()
        removeItem(selectedUid)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [selectedUid, duplicateItem, removeItem])

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
      dispatch({
        type: 'set',
        items: [...items.slice(0, insertAt), item, ...items.slice(insertAt)],
      })
      setSelectedUid(item.uid)
      return
    }

    if (over.id !== TRACK_ID && active.id !== over.id) {
      const from = items.findIndex((entry) => entry.uid === active.id)
      const to = items.findIndex((entry) => entry.uid === over.id)
      if (from >= 0 && to >= 0) dispatch({ type: 'set', items: arrayMove(items, from, to) })
    }
  }

  async function openPublish() {
    const ok = await save()
    if (ok) setPublishing(true)
  }

  if (status === 'loading') return <PageSkeleton rows={3} />
  if (status === 'error') return <ErrorState onRetry={() => void load()} />

  const dragAsset = dragAssetId ? assets.get(dragAssetId) : undefined
  const dragItem = dragUid ? items.find((item) => item.uid === dragUid) : undefined
  const dragItemAsset = dragItem ? assets.get(dragItem.assetId) : undefined
  const busy = saveState === 'saving' || (dirty && saveState !== 'error')

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
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex min-w-0 flex-1 items-center gap-3">
            <Button asChild variant="ghost" size="icon" aria-label={dict.players.back}>
              <Link href={localePath('/playlists')}>
                <IconArrowLeft />
              </Link>
            </Button>
            <TitleFields
              className="w-full max-w-2xl"
              name={name}
              description={description}
              onNameChange={setName}
              onDescriptionChange={setDescription}
              nameLabel={dict.common.name}
              descriptionLabel={dict.playlists.descriptionLabel}
              namePlaceholder={dict.playlists.wizard.namePlaceholder}
              descriptionPlaceholder={dict.playlists.descriptionPlaceholder}
              nameInvalid={!name.trim()}
            />
          </div>
          <div className="flex items-center gap-2">
            <span className="text-muted-foreground flex items-center gap-1.5 pr-1 text-xs">
              {busy ? (
                <IconLoader2 className="size-3.5 animate-spin" />
              ) : saveState === 'error' ? (
                <IconAlertCircle className="text-destructive size-3.5" />
              ) : (
                <IconCheck className="text-success size-3.5" />
              )}
              <SaveStatus busy={busy} failed={saveState === 'error'} savedAt={savedAt} />
            </span>
            <Button
              size="icon"
              variant="outline"
              aria-label={dict.playlists.studio.undo}
              title={`${dict.playlists.studio.undo} (Ctrl+Z)`}
              disabled={history.past.length === 0}
              onClick={() => dispatch({ type: 'undo' })}
            >
              <IconArrowBackUp />
            </Button>
            <Button
              size="icon"
              variant="outline"
              aria-label={dict.playlists.studio.redo}
              title={`${dict.playlists.studio.redo} (Ctrl+Shift+Z)`}
              disabled={history.future.length === 0}
              onClick={() => dispatch({ type: 'redo' })}
            >
              <IconArrowForwardUp />
            </Button>
            {hasPermission(user, 'AUDIT_VIEW') ? (
              <Button asChild size="sm" variant="ghost">
                <Link href={localePath(`/audit?entityType=PLAYLIST&entityId=${id}`)}>
                  <IconHistory /> {dict.audit.viewHistory}
                </Link>
              </Button>
            ) : null}
          </div>
        </div>

        <div className="space-y-4">
          <div className="min-w-0 space-y-4">
            <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
              <Card>
                <CardHeader>
                  <CardTitle>{dict.playlists.preview}</CardTitle>
                </CardHeader>
                <CardContent>
                  <PlaylistPreview items={items} assets={assets} selectedUid={selectedUid} />
                </CardContent>
              </Card>
              <Card>
                <CardHeader>
                  <CardTitle>{dict.playlists.properties}</CardTitle>
                </CardHeader>
                <CardContent>
                  <ItemProperties
                    item={selected}
                    asset={selected ? assets.get(selected.assetId) : undefined}
                    index={selectedIndex}
                    count={items.length}
                    onChange={(patch) => selected && patchItem(selected.uid, patch)}
                    onMove={(direction) =>
                      dispatch({
                        type: 'set',
                        items: arrayMove(items, selectedIndex, selectedIndex + direction),
                      })
                    }
                    onRemove={() => selected && removeItem(selected.uid)}
                  />
                </CardContent>
              </Card>
            </div>

            <Card>
              <CardHeader className="flex-row items-center justify-between">
                <CardTitle>{dict.playlists.timeline}</CardTitle>
                <div className="flex items-center gap-4">
                  <span className="text-muted-foreground hidden text-xs lg:inline">
                    {dict.playlists.studio.shortcuts}
                  </span>
                  <div className="text-muted-foreground flex items-center gap-2 text-xs">
                    {dict.playlists.zoom}
                    <Slider
                      min={4}
                      max={48}
                      value={[pxPerSec]}
                      onValueChange={([zoom]) => setPxPerSec(zoom)}
                      aria-label={dict.playlists.zoom}
                      className="w-32"
                    />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <Timeline
                  items={items}
                  assets={assets}
                  pxPerSec={pxPerSec}
                  selectedUid={selectedUid}
                  onSelect={setSelectedUid}
                  onResize={(uid, durationSec) => patchItem(uid, { durationSec }, false)}
                  onResizeStart={() => dispatch({ type: 'checkpoint' })}
                  onRemove={removeItem}
                  onDuplicate={duplicateItem}
                />
              </CardContent>
            </Card>
          </div>
          <LibraryDock title={dict.playlists.library} onLoaded={mergeLibrary} onAdd={append} />
        </div>
      </div>

      <div className="bg-background/95 sticky bottom-0 z-30 -mx-4 -mb-4 border-t backdrop-blur md:-mx-6 md:-mb-6">
        <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 md:px-6">
          <div className="flex flex-wrap items-center gap-3 text-sm">
            <Badge variant="secondary" className="tabular-nums">
              {fmt(dict.playlists.itemCount, { count: items.length })} · {formatDuration(totalSec)}
            </Badge>
          </div>
          <Button
            size="lg"
            disabled={items.length === 0 || !name.trim()}
            onClick={() => void openPublish()}
          >
            <IconRocket /> {dict.playlists.studio.publish}
          </Button>
        </div>
      </div>

      <PublishDialog
        open={publishing}
        onOpenChange={setPublishing}
        playlist={{ id, name: name.trim() }}
      />

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
    </DndContext>
  )
}

export default function PlaylistEditorPage() {
  return (
    <RequirePermission permission="PLAYLIST_MANAGE">
      <PlaylistEditorPageContent />
    </RequirePermission>
  )
}
