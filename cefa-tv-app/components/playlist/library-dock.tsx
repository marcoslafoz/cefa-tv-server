'use client'

import { useCallback, useRef, useState } from 'react'
import { IconChevronDown, IconChevronUp, IconGripHorizontal } from '@tabler/icons-react'
import { LibraryPanel } from '@/components/playlist/library-panel'
import { Button } from '@/components/shadcn-ui/button'
import { useDictionary } from '@/contexts/dictionary-context'
import { cn } from '@/lib/utils'
import type { MediaAsset } from '@/types/api'

const STORAGE_KEY = 'cefa-tv.library-dock'
const MIN_HEIGHT = 200
const MAX_HEIGHT = 720
const DEFAULT_HEIGHT = 320
const STEP = 24

interface DockState {
  height: number
  collapsed: boolean
}

function clamp(value: number) {
  return Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, value))
}

function readState(): DockState {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<DockState>
      return {
        height: clamp(Number(parsed.height) || DEFAULT_HEIGHT),
        collapsed: parsed.collapsed === true,
      }
    }
  } catch {}
  return { height: DEFAULT_HEIGHT, collapsed: false }
}

function writeState(state: DockState) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {}
}

export function LibraryDock({
  title,
  onAdd,
  onPlayNow,
  onLoaded,
  className,
}: {
  title: string
  onAdd: (asset: MediaAsset) => void
  onPlayNow?: (asset: MediaAsset) => void
  onLoaded?: (assets: MediaAsset[]) => void
  className?: string
}) {
  const dict = useDictionary()
  const [state, setState] = useState<DockState>(() =>
    typeof window === 'undefined' ? { height: DEFAULT_HEIGHT, collapsed: false } : readState()
  )
  const drag = useRef<{ startY: number; startHeight: number } | null>(null)

  const update = useCallback((next: DockState) => {
    setState(next)
    writeState(next)
  }, [])

  function onPointerDown(event: React.PointerEvent<HTMLDivElement>) {
    if (state.collapsed) return
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { startY: event.clientY, startHeight: state.height }
  }

  function onPointerMove(event: React.PointerEvent<HTMLDivElement>) {
    if (!drag.current) return
    const height = clamp(drag.current.startHeight + drag.current.startY - event.clientY)
    setState((current) => ({ ...current, height }))
  }

  function onPointerUp() {
    if (!drag.current) return
    drag.current = null
    writeState(state)
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      update({ ...state, height: clamp(state.height + STEP) })
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      update({ ...state, height: clamp(state.height - STEP) })
    }
  }

  return (
    <section className={cn('bg-card rounded-xl border', className)} aria-label={title}>
      <div
        role="separator"
        aria-orientation="horizontal"
        aria-label={dict.libraryDock.resize}
        aria-valuemin={MIN_HEIGHT}
        aria-valuemax={MAX_HEIGHT}
        aria-valuenow={state.height}
        tabIndex={state.collapsed ? -1 : 0}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onKeyDown={onKeyDown}
        className={cn(
          'text-muted-foreground flex h-3 touch-none items-center justify-center rounded-t-xl outline-none',
          state.collapsed
            ? 'cursor-default'
            : 'hover:bg-accent focus-visible:bg-accent cursor-row-resize'
        )}
      >
        <IconGripHorizontal className="size-4" />
      </div>
      <div className="flex items-center justify-between px-4 pb-2">
        <h3 className="text-sm font-semibold">{title}</h3>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          aria-expanded={!state.collapsed}
          onClick={() => update({ ...state, collapsed: !state.collapsed })}
        >
          {state.collapsed ? <IconChevronUp /> : <IconChevronDown />}
          {state.collapsed ? dict.libraryDock.expand : dict.libraryDock.collapse}
        </Button>
      </div>
      <div
        className={cn('px-4 pb-4', state.collapsed && 'hidden')}
        style={{ height: state.height }}
      >
        <LibraryPanel
          layout="grid"
          onAdd={onAdd}
          onPlayNow={onPlayNow}
          onLoaded={onLoaded}
          className="h-full"
        />
      </div>
    </section>
  )
}
