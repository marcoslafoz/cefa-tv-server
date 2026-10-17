'use client'

import { useCallback, useMemo, useRef, useState } from 'react'
import {
  DndContext,
  DragOverlay,
  MeasuringStrategy,
  PointerSensor,
  pointerWithin,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import {
  IconArrowsMove,
  IconFolder,
  IconFolderPlus,
  IconLayoutGrid,
  IconList,
  IconPencil,
  IconSearch,
  IconTrash,
  IconX,
  type Icon,
} from '@tabler/icons-react'
import { Breadcrumbs } from '@/components/explorer/breadcrumbs'
import { DeleteFoldersDialog, MoveDialog, NameDialog } from '@/components/explorer/folder-dialogs'
import { FolderTree, folderIdFromDrop } from '@/components/explorer/folder-tree'
import { useFolders } from '@/components/explorer/use-folders'
import { EmptyState, ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Button } from '@/components/shadcn-ui/button'
import { Card } from '@/components/shadcn-ui/card'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuShortcut,
  ContextMenuTrigger,
} from '@/components/shadcn-ui/context-menu'
import { Input } from '@/components/shadcn-ui/input'
import { useDictionary } from '@/contexts/dictionary-context'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/api-client'
import { readStored, writeStored } from '@/lib/storage'
import { toast } from '@/lib/toast'
import { cn } from '@/lib/utils'
import type { Folder } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

export interface ExplorerItem {
  id: string
  name: string
  folderId?: string | null
  folderPath?: Array<{ id: string; name: string }>
}

type Entry<T extends ExplorerItem> =
  { key: string; kind: 'folder'; folder: Folder } | { key: string; kind: 'item'; item: T }

type ViewMode = 'grid' | 'list'

const VIEW_STORAGE_KEY = 'cefa-tv.explorer.view'

function dropTarget(dropId: string): string | null {
  return dropId.startsWith('dropgrid:') ? dropId.slice(9) : folderIdFromDrop(dropId)
}

function ExplorerEntry({
  entryKey,
  selected,
  canManage,
  folderId,
  view,
  onClick,
  onDoubleClick,
  onContextMenu,
  children,
}: {
  entryKey: string
  selected: boolean
  canManage: boolean
  folderId: string | null
  view: ViewMode
  onClick: (event: React.MouseEvent) => void
  onDoubleClick: () => void
  onContextMenu: () => void
  children: React.ReactNode
}) {
  const drag = useDraggable({ id: `entry:${entryKey}`, disabled: !canManage })
  const drop = useDroppable({
    id: `dropgrid:${folderId ?? ''}`,
    disabled: !canManage || folderId === null,
  })

  return (
    <div
      ref={(node) => {
        drag.setNodeRef(node)
        drop.setNodeRef(node)
      }}
      data-entry={entryKey}
      {...drag.attributes}
      {...drag.listeners}
      role="option"
      aria-selected={selected}
      onClick={onClick}
      onDoubleClick={onDoubleClick}
      onContextMenu={onContextMenu}
      className={cn(
        'cursor-default touch-none rounded-xl outline-none select-none',
        view === 'grid'
          ? 'h-full min-w-0 overflow-hidden border'
          : 'flex min-w-0 items-center gap-3 rounded-lg border px-2 py-1.5',
        selected ? 'border-primary bg-primary/5 ring-primary/40 ring-2' : 'hover:border-primary/40',
        drop.isOver && 'bg-primary/15 ring-primary ring-2',
        drag.isDragging && 'opacity-40'
      )}
    >
      {children}
    </div>
  )
}

export function FolderExplorer<T extends ExplorerItem>({
  folderId,
  onFolderChange,
  items,
  loading,
  failed,
  onRetry,
  query,
  onQueryChange,
  searchPlaceholder,
  canManage,
  renderCard,
  renderThumb,
  renderMeta,
  onOpenItem,
  onMoveItems,
  onRenameItem,
  onDeleteItems,
  toolbar,
  filters,
  emptyIcon,
  emptyTitle,
  emptyDescription,
}: {
  folderId: string | null
  onFolderChange: (folderId: string | null) => void
  items: T[]
  loading: boolean
  failed: boolean
  onRetry: () => void
  query: string
  onQueryChange: (query: string) => void
  searchPlaceholder: string
  canManage: boolean
  renderCard: (item: T) => React.ReactNode
  renderThumb: (item: T) => React.ReactNode
  renderMeta: (item: T) => React.ReactNode
  onOpenItem?: (item: T) => void
  onMoveItems: (ids: string[], folderId: string | null) => Promise<void>
  onRenameItem?: (item: T, name: string) => Promise<void>
  onDeleteItems: (items: T[]) => void
  toolbar?: React.ReactNode
  filters?: React.ReactNode
  emptyIcon: Icon
  emptyTitle: string
  emptyDescription: string
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const model = useFolders()
  const [view, setView] = useState<ViewMode>(() => readStored<ViewMode>(VIEW_STORAGE_KEY, 'grid'))
  const [selection, setSelection] = useState<string[]>([])
  const anchorRef = useRef<string | null>(null)
  const [dialog, setDialog] = useState<'new' | 'rename' | 'move' | 'delete' | null>(null)
  const [dragKey, setDragKey] = useState<string | null>(null)
  const rootLabel = dict.explorer.rootMedia
  const searching = query.trim() !== ''

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 6 } }))

  const visibleFolders = useMemo(() => {
    if (!searching) return model.childrenOf(folderId)
    const needle = query.trim().toLowerCase()
    return model.folders
      .filter((folder) => folder.name.toLowerCase().includes(needle))
      .sort((left, right) => left.name.localeCompare(right.name))
  }, [model, folderId, searching, query])

  const entries = useMemo<Array<Entry<T>>>(
    () => [
      ...visibleFolders.map((folder) => ({
        key: `f:${folder.id}`,
        kind: 'folder' as const,
        folder,
      })),
      ...items.map((item) => ({ key: `i:${item.id}`, kind: 'item' as const, item })),
    ],
    [visibleFolders, items]
  )
  const entryByKey = useMemo(() => new Map(entries.map((entry) => [entry.key, entry])), [entries])
  const selectedEntries = selection
    .map((key) => entryByKey.get(key))
    .filter((entry): entry is Entry<T> => entry !== undefined)
  const selectedFolders = selectedEntries.flatMap((entry) =>
    entry.kind === 'folder' ? [entry.folder] : []
  )
  const selectedItems = selectedEntries.flatMap((entry) =>
    entry.kind === 'item' ? [entry.item] : []
  )
  const single = selectedEntries.length === 1 ? selectedEntries[0] : null

  const disabledMoveTargets = (() => {
    const blocked = new Set<string>()
    for (const folder of selectedFolders) {
      blocked.add(folder.id)
      for (const candidate of model.folders) {
        if (model.isWithin(candidate.id, folder.id)) blocked.add(candidate.id)
      }
    }
    return blocked
  })()

  const changeView = useCallback((next: ViewMode) => {
    setView(next)
    writeStored(VIEW_STORAGE_KEY, next)
  }, [])

  function navigate(next: string | null) {
    setSelection([])
    anchorRef.current = null
    onQueryChange('')
    onFolderChange(next)
  }

  function select(key: string, event: React.MouseEvent) {
    if (event.shiftKey && anchorRef.current) {
      const keys = entries.map((entry) => entry.key)
      const from = keys.indexOf(anchorRef.current)
      const to = keys.indexOf(key)
      if (from >= 0 && to >= 0) {
        const [low, high] = from < to ? [from, to] : [to, from]
        setSelection(keys.slice(low, high + 1))
        return
      }
    }
    if (event.metaKey || event.ctrlKey) {
      setSelection((current) =>
        current.includes(key) ? current.filter((entry) => entry !== key) : [...current, key]
      )
      anchorRef.current = key
      return
    }
    setSelection([key])
    anchorRef.current = key
  }

  function open(entry: Entry<T>) {
    if (entry.kind === 'folder') navigate(entry.folder.id)
    else onOpenItem?.(entry.item)
  }

  async function moveKeys(keys: string[], target: string | null) {
    const folders = keys.flatMap((key) => (key.startsWith('f:') ? [key.slice(2)] : []))
    const itemIds = keys.flatMap((key) => (key.startsWith('i:') ? [key.slice(2)] : []))
    if (folders.some((id) => target !== null && model.isWithin(target, id))) {
      toast.error(dict.explorer.moveIntoItself)
      return
    }
    try {
      if (itemIds.length > 0) await onMoveItems(itemIds, target)
      for (const id of folders) {
        await api.folders.update(id, target === null ? { clearParent: true } : { parentId: target })
      }
      await model.reload()
      setSelection([])
      toast.success(dict.explorer.moved)
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.explorer.moveFailed))
    }
  }

  function onDragStart(event: DragStartEvent) {
    const key = String(event.active.id).slice(6)
    setDragKey(key)
    if (!selection.includes(key)) setSelection([key])
  }

  function onDragEnd(event: DragEndEvent) {
    const key = String(event.active.id).slice(6)
    setDragKey(null)
    const over = event.over
    if (!over) return
    const target = dropTarget(String(over.id))
    const keys = selection.includes(key) ? selection : [key]
    if (target !== null && keys.includes(`f:${target}`)) return
    void moveKeys(keys, target)
  }

  async function createFolder(name: string) {
    try {
      await api.folders.create({ name, parentId: folderId })
      await model.reload()
      setDialog(null)
      toast.success(dict.explorer.folderCreated)
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.explorer.folderFailed))
    }
  }

  async function rename(name: string) {
    if (!single) return
    try {
      if (single.kind === 'folder') {
        await api.folders.update(single.folder.id, { name })
        await model.reload()
      } else {
        await onRenameItem?.(single.item, name)
      }
      setDialog(null)
      toast.success(dict.explorer.renamed)
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.explorer.renameFailed))
    }
  }

  async function deleteSelection(moveContents: boolean) {
    setDialog(null)
    try {
      for (const folder of selectedFolders) await api.folders.remove(folder.id, moveContents)
      if (selectedFolders.length > 0) {
        await model.reload()
        toast.success(dict.explorer.folderDeleted)
      }
      if (selectedItems.length > 0) onDeleteItems(selectedItems)
      setSelection([])
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.explorer.folderDeleteFailed))
    }
  }

  function requestDelete() {
    if (selectedFolders.length > 0) setDialog('delete')
    else if (selectedItems.length > 0) onDeleteItems(selectedItems)
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (
      event.target instanceof HTMLElement &&
      ['INPUT', 'TEXTAREA'].includes(event.target.tagName)
    ) {
      return
    }
    const modifier = event.metaKey || event.ctrlKey
    if (modifier && event.key.toLowerCase() === 'a') {
      event.preventDefault()
      setSelection(entries.map((entry) => entry.key))
    } else if (event.key === 'Escape') {
      setSelection([])
    } else if (canManage && event.key === 'F2' && single) {
      event.preventDefault()
      setDialog('rename')
    } else if (
      canManage &&
      (event.key === 'Delete' || event.key === 'Backspace') &&
      selection.length > 0
    ) {
      event.preventDefault()
      requestDelete()
    } else if (event.key === 'Enter' && single) {
      open(single)
    }
  }

  const dragEntry = dragKey ? entryByKey.get(dragKey) : undefined
  const dragCount = dragKey && selection.includes(dragKey) ? selection.length : 1
  const path = model.pathOf(folderId)

  function entryContent(entry: Entry<T>) {
    if (entry.kind === 'folder') {
      const sub = model.pathOf(entry.folder.parentId)
      const location =
        searching && sub.length > 0 ? sub.map((folder) => folder.name).join(' / ') : null
      return view === 'grid' ? (
        <div className="flex items-center gap-3 p-3">
          <span className="bg-primary/10 text-primary flex size-12 shrink-0 items-center justify-center rounded-lg">
            <IconFolder className="size-6" />
          </span>
          <span className="min-w-0">
            <span className="block truncate text-sm font-medium">{entry.folder.name}</span>
            <span className="text-muted-foreground block truncate text-xs">
              {location ?? dict.explorer.folder}
            </span>
          </span>
        </div>
      ) : (
        <>
          <span className="bg-primary/10 text-primary flex size-9 shrink-0 items-center justify-center rounded-md">
            <IconFolder className="size-5" />
          </span>
          <span className="min-w-0 flex-1 truncate text-sm font-medium">{entry.folder.name}</span>
          <span className="text-muted-foreground truncate text-xs">
            {location ?? dict.explorer.folder}
          </span>
        </>
      )
    }
    const location = searching
      ? (entry.item.folderPath ?? model.pathOf(entry.item.folderId ?? null))
          .map((folder) => folder.name)
          .join(' / ') || rootLabel
      : null
    const locationButton =
      searching && location ? (
        <button
          type="button"
          className="text-primary truncate text-xs hover:underline"
          onClick={(event) => {
            event.stopPropagation()
            navigate(entry.item.folderId ?? null)
          }}
          onDoubleClick={(event) => event.stopPropagation()}
        >
          {dict.explorer.inPath.replace('{path}', location)}
        </button>
      ) : null
    return view === 'grid' ? (
      <>
        {renderCard(entry.item)}
        {locationButton ? <div className="-mt-1 px-3 pb-2">{locationButton}</div> : null}
      </>
    ) : (
      <>
        {renderThumb(entry.item)}
        <span className="min-w-0 flex-1 truncate text-sm font-medium">{entry.item.name}</span>
        {locationButton}
        <span className="text-muted-foreground shrink-0 text-xs">{renderMeta(entry.item)}</span>
      </>
    )
  }

  function renderEntry(entry: Entry<T>) {
    const selected = selection.includes(entry.key)
    const multiple = selected && selection.length > 1
    return (
      <ContextMenu key={entry.key}>
        <ContextMenuTrigger asChild>
          <ExplorerEntry
            entryKey={entry.key}
            selected={selected}
            canManage={canManage}
            folderId={entry.kind === 'folder' ? entry.folder.id : null}
            view={view}
            onClick={(event) => {
              event.stopPropagation()
              select(entry.key, event)
            }}
            onDoubleClick={() => open(entry)}
            onContextMenu={() => {
              if (!selection.includes(entry.key)) {
                setSelection([entry.key])
                anchorRef.current = entry.key
              }
            }}
          >
            {entryContent(entry)}
          </ExplorerEntry>
        </ContextMenuTrigger>
        <ContextMenuContent>
          {!multiple && (entry.kind === 'folder' || onOpenItem) ? (
            <ContextMenuItem onSelect={() => open(entry)}>
              {entry.kind === 'folder' ? dict.explorer.openFolder : dict.explorer.open}
            </ContextMenuItem>
          ) : null}
          {canManage ? (
            <>
              {!multiple ? (
                <ContextMenuItem onSelect={() => setDialog('rename')}>
                  <IconPencil /> {dict.explorer.rename}
                  <ContextMenuShortcut>F2</ContextMenuShortcut>
                </ContextMenuItem>
              ) : null}
              <ContextMenuItem onSelect={() => setDialog('move')}>
                <IconArrowsMove /> {dict.explorer.moveTo}
              </ContextMenuItem>
              <ContextMenuSeparator />
              <ContextMenuItem variant="destructive" onSelect={requestDelete}>
                <IconTrash /> {dict.common.delete}
              </ContextMenuItem>
            </>
          ) : null}
        </ContextMenuContent>
      </ContextMenu>
    )
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={pointerWithin}
      measuring={{ droppable: { strategy: MeasuringStrategy.Always } }}
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      onDragCancel={() => setDragKey(null)}
    >
      <div className="grid gap-4 lg:grid-cols-[15rem_minmax(0,1fr)]">
        <Card className="hidden max-h-[calc(100svh-12rem)] gap-2 overflow-y-auto p-2 lg:flex">
          <p className="text-muted-foreground px-2 pt-1 text-xs font-medium tracking-wide uppercase">
            {dict.explorer.folders}
          </p>
          <FolderTree
            model={model}
            selectedId={folderId}
            rootLabel={rootLabel}
            droppable={canManage}
            onSelect={navigate}
          />
        </Card>

        <div
          className="min-w-0 space-y-3 outline-none"
          tabIndex={0}
          onKeyDown={onKeyDown}
          onClick={() => setSelection([])}
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <Breadcrumbs
              path={path}
              rootLabel={rootLabel}
              droppable={canManage}
              onNavigate={navigate}
            />
            <div
              className="flex flex-wrap items-center gap-2"
              onClick={(event) => event.stopPropagation()}
            >
              <div className="relative w-56">
                <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
                <Input
                  className="px-8"
                  placeholder={searchPlaceholder}
                  value={query}
                  onChange={(event) => onQueryChange(event.target.value)}
                />
                {searching ? (
                  <button
                    type="button"
                    aria-label={dict.explorer.clearSearch}
                    className="text-muted-foreground hover:text-foreground absolute top-1/2 right-2 -translate-y-1/2"
                    onClick={() => onQueryChange('')}
                  >
                    <IconX className="size-4" />
                  </button>
                ) : null}
              </div>
              <div className="flex rounded-lg border p-0.5">
                <Button
                  size="icon-sm"
                  variant={view === 'grid' ? 'secondary' : 'ghost'}
                  aria-label={dict.explorer.viewGrid}
                  aria-pressed={view === 'grid'}
                  onClick={() => changeView('grid')}
                >
                  <IconLayoutGrid />
                </Button>
                <Button
                  size="icon-sm"
                  variant={view === 'list' ? 'secondary' : 'ghost'}
                  aria-label={dict.explorer.viewList}
                  aria-pressed={view === 'list'}
                  onClick={() => changeView('list')}
                >
                  <IconList />
                </Button>
              </div>
              {canManage ? (
                <Button variant="outline" onClick={() => setDialog('new')}>
                  <IconFolderPlus /> {dict.explorer.newFolder}
                </Button>
              ) : null}
              {toolbar}
            </div>
          </div>

          {filters ? <div onClick={(event) => event.stopPropagation()}>{filters}</div> : null}

          {searching ? (
            <p className="text-muted-foreground text-sm">{dict.explorer.searchResults}</p>
          ) : null}

          {selection.length > 0 ? (
            <div
              className="bg-primary/5 border-primary/30 flex flex-wrap items-center justify-between gap-2 rounded-xl border px-3 py-2"
              onClick={(event) => event.stopPropagation()}
            >
              <span className="text-sm font-medium">
                {fmt(dict.explorer.selected, { count: selection.length })}
              </span>
              <div className="flex flex-wrap items-center gap-1.5">
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setSelection(entries.map((entry) => entry.key))}
                >
                  {dict.explorer.selectAll}
                </Button>
                {canManage ? (
                  <>
                    {single ? (
                      <Button size="sm" variant="outline" onClick={() => setDialog('rename')}>
                        <IconPencil /> {dict.explorer.rename}
                      </Button>
                    ) : null}
                    <Button size="sm" variant="outline" onClick={() => setDialog('move')}>
                      <IconArrowsMove /> {dict.explorer.moveTo}
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="text-destructive"
                      onClick={requestDelete}
                    >
                      <IconTrash /> {dict.common.delete}
                    </Button>
                  </>
                ) : null}
                <Button
                  size="icon-sm"
                  variant="ghost"
                  aria-label={dict.explorer.clearSelection}
                  onClick={() => setSelection([])}
                >
                  <IconX />
                </Button>
              </div>
            </div>
          ) : null}

          {loading ? (
            <PageSkeleton rows={4} />
          ) : failed ? (
            <ErrorState onRetry={onRetry} />
          ) : entries.length === 0 ? (
            <EmptyState
              icon={searching ? IconSearch : emptyIcon}
              title={
                searching
                  ? dict.explorer.noResults
                  : folderId
                    ? dict.explorer.emptyFolder
                    : emptyTitle
              }
              description={
                searching
                  ? dict.explorer.noResultsHint
                  : folderId
                    ? dict.explorer.emptyFolderHint
                    : emptyDescription
              }
            />
          ) : (
            <div
              role="listbox"
              aria-multiselectable
              className={cn(
                view === 'grid'
                  ? 'grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5'
                  : 'space-y-1.5'
              )}
            >
              {entries.map(renderEntry)}
            </div>
          )}
        </div>
      </div>

      <DragOverlay dropAnimation={null}>
        {dragEntry ? (
          <div className="bg-card flex items-center gap-2 rounded-lg border-2 px-3 py-2 shadow-lg">
            <IconFolder className="text-primary size-4" />
            <span className="max-w-48 truncate text-sm font-medium">
              {dragCount > 1
                ? fmt(dict.explorer.dragCount, { count: dragCount })
                : dragEntry.kind === 'folder'
                  ? dragEntry.folder.name
                  : dragEntry.item.name}
            </span>
          </div>
        ) : null}
      </DragOverlay>

      {dialog === 'new' ? (
        <NameDialog
          open
          title={dict.explorer.newFolderTitle}
          initial=""
          confirmLabel={dict.common.create}
          onSubmit={createFolder}
          onClose={() => setDialog(null)}
        />
      ) : null}
      {dialog === 'rename' && single ? (
        <NameDialog
          open
          title={single.kind === 'folder' ? dict.explorer.renameFolder : dict.explorer.renameItem}
          initial={single.kind === 'folder' ? single.folder.name : single.item.name}
          confirmLabel={dict.common.save}
          onSubmit={rename}
          onClose={() => setDialog(null)}
        />
      ) : null}
      {dialog === 'move' ? (
        <MoveDialog
          open
          model={model}
          initialFolderId={folderId}
          disabledIds={disabledMoveTargets}
          count={selection.length}
          onConfirm={async (target) => {
            await moveKeys(selection, target)
            setDialog(null)
          }}
          onClose={() => setDialog(null)}
        />
      ) : null}
      {dialog === 'delete' ? (
        <DeleteFoldersDialog
          open
          names={selectedFolders.map((folder) => folder.name)}
          onConfirm={(moveContents) => void deleteSelection(moveContents)}
          onClose={() => setDialog(null)}
        />
      ) : null}
    </DndContext>
  )
}
