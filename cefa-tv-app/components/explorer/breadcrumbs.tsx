'use client'

import { useDroppable } from '@dnd-kit/core'
import { IconChevronRight, IconHome } from '@tabler/icons-react'
import { dropIdFor } from '@/components/explorer/folder-tree'
import { cn } from '@/lib/utils'
import type { Folder } from '@/types/api'

function Crumb({
  folderId,
  droppable,
  current,
  onClick,
  children,
}: {
  folderId: string | null
  droppable: boolean
  current: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  const { setNodeRef, isOver } = useDroppable({ id: dropIdFor(folderId), disabled: !droppable })
  return (
    <button
      ref={setNodeRef}
      type="button"
      aria-current={current ? 'page' : undefined}
      onClick={onClick}
      className={cn(
        'flex items-center gap-1 rounded-md px-1.5 py-0.5 text-sm transition-colors',
        current ? 'font-semibold' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
        isOver && 'bg-primary/20 ring-primary ring-2'
      )}
    >
      {children}
    </button>
  )
}

export function Breadcrumbs({
  path,
  rootLabel,
  droppable,
  onNavigate,
}: {
  path: Folder[]
  rootLabel: string
  droppable: boolean
  onNavigate: (folderId: string | null) => void
}) {
  return (
    <nav aria-label={rootLabel} className="flex min-w-0 flex-wrap items-center gap-0.5">
      <Crumb
        folderId={null}
        droppable={droppable}
        current={path.length === 0}
        onClick={() => onNavigate(null)}
      >
        <IconHome className="size-4" /> {rootLabel}
      </Crumb>
      {path.map((folder, index) => (
        <span key={folder.id} className="flex items-center gap-0.5">
          <IconChevronRight className="text-muted-foreground size-3.5" />
          <Crumb
            folderId={folder.id}
            droppable={droppable}
            current={index === path.length - 1}
            onClick={() => onNavigate(folder.id)}
          >
            {folder.name}
          </Crumb>
        </span>
      ))}
    </nav>
  )
}
