'use client'

import { useMemo, useState } from 'react'
import { useDroppable } from '@dnd-kit/core'
import { IconChevronRight, IconFolder, IconFolderOpen, IconHome } from '@tabler/icons-react'
import type { FolderModel } from '@/components/explorer/use-folders'
import { readStored, writeStored } from '@/lib/storage'
import { cn } from '@/lib/utils'
import type { Folder } from '@/types/api'

export function dropIdFor(folderId: string | null): string {
  return folderId ? `drop:${folderId}` : 'drop:root'
}

export function folderIdFromDrop(dropId: string): string | null {
  return dropId === 'drop:root' ? null : dropId.slice(5)
}

function TreeRow({
  droppableId,
  droppable,
  depth,
  active,
  label,
  open,
  expandable,
  onToggle,
  onSelect,
  disabled,
  icon,
}: {
  droppableId: string
  droppable: boolean
  depth: number
  active: boolean
  label: string
  open: boolean
  expandable: boolean
  onToggle: () => void
  onSelect: () => void
  disabled?: boolean
  icon: React.ReactNode
}) {
  const { setNodeRef, isOver } = useDroppable({ id: droppableId, disabled: !droppable })
  return (
    <div
      ref={setNodeRef}
      role="treeitem"
      aria-selected={active}
      aria-expanded={expandable ? open : undefined}
      className={cn(
        'flex items-center gap-1 rounded-md py-1 pr-2 text-sm transition-colors',
        active ? 'bg-primary/10 text-primary font-medium' : 'hover:bg-muted',
        isOver && 'bg-primary/20 ring-primary ring-2',
        disabled && 'opacity-50'
      )}
      style={{ paddingLeft: 4 + depth * 14 }}
    >
      <button
        type="button"
        aria-label={label}
        tabIndex={-1}
        className={cn(
          'flex size-5 shrink-0 items-center justify-center rounded',
          !expandable && 'invisible'
        )}
        onClick={onToggle}
      >
        <IconChevronRight className={cn('size-3.5 transition-transform', open && 'rotate-90')} />
      </button>
      <button
        type="button"
        className="flex min-w-0 flex-1 items-center gap-1.5 text-left"
        onClick={onSelect}
        disabled={disabled}
      >
        {icon}
        <span className="truncate">{label}</span>
      </button>
    </div>
  )
}

export function FolderTree({
  model,
  selectedId,
  rootLabel,
  droppable,
  disabledIds,
  onSelect,
  className,
}: {
  model: FolderModel
  selectedId: string | null
  rootLabel: string
  droppable: boolean
  disabledIds?: Set<string>
  onSelect: (folderId: string | null) => void
  className?: string
}) {
  const storageKey = 'cefa-tv.explorer.expanded'
  const [expanded, setExpanded] = useState<string[]>(() => readStored<string[]>(storageKey, []))
  const pathIds = useMemo(
    () => new Set(model.pathOf(selectedId).map((folder) => folder.id)),
    [model, selectedId]
  )

  function toggle(folderId: string) {
    setExpanded((current) => {
      const next = current.includes(folderId)
        ? current.filter((id) => id !== folderId)
        : [...current, folderId]
      writeStored(storageKey, next)
      return next
    })
  }

  function renderNodes(parentId: string | null, depth: number): React.ReactNode {
    return model.childrenOf(parentId).map((folder: Folder) => {
      const hasChildren = model.childrenOf(folder.id).length > 0
      const open = pathIds.has(folder.id) || expanded.includes(folder.id)
      return (
        <div key={folder.id}>
          <TreeRow
            droppableId={dropIdFor(folder.id)}
            droppable={droppable && !disabledIds?.has(folder.id)}
            depth={depth}
            active={selectedId === folder.id}
            label={folder.name}
            open={open}
            expandable={hasChildren}
            onToggle={() => !pathIds.has(folder.id) && toggle(folder.id)}
            onSelect={() => onSelect(folder.id)}
            disabled={disabledIds?.has(folder.id)}
            icon={
              open && hasChildren ? (
                <IconFolderOpen className="text-primary size-4 shrink-0" />
              ) : (
                <IconFolder className="text-primary size-4 shrink-0" />
              )
            }
          />
          {open && hasChildren ? renderNodes(folder.id, depth + 1) : null}
        </div>
      )
    })
  }

  return (
    <div role="tree" className={cn('space-y-0.5', className)}>
      <TreeRow
        droppableId={dropIdFor(null)}
        droppable={droppable}
        depth={0}
        active={selectedId === null}
        label={rootLabel}
        open
        expandable={false}
        onToggle={() => undefined}
        onSelect={() => onSelect(null)}
        icon={<IconHome className="size-4 shrink-0" />}
      />
      {renderNodes(null, 1)}
    </div>
  )
}
