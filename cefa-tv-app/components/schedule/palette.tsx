'use client'

import { useState } from 'react'
import { useDraggable } from '@dnd-kit/core'
import { IconPlaylist, IconSearch } from '@tabler/icons-react'
import { PickerFolders, scopeParams, useFolderScope } from '@/components/explorer/scope'
import { MediaThumb } from '@/components/shared/media-thumb'
import { Input } from '@/components/shadcn-ui/input'
import { Tabs, TabsList, TabsTrigger } from '@/components/shadcn-ui/tabs'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { cn, formatDuration, normalizeText } from '@/lib/utils'
import type { MediaAsset, Page, Playlist } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

function DraggableRow({ id, children }: { id: string; children: React.ReactNode }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id })
  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      className={cn(
        'bg-card hover:border-primary/50 flex cursor-grab touch-none items-center gap-2.5 rounded-lg border p-1.5 transition-colors active:cursor-grabbing',
        isDragging && 'opacity-40'
      )}
    >
      {children}
    </div>
  )
}

export function SchedulePalette({ className }: { className?: string }) {
  const dict = useDictionary()
  const fmt = useFormat()
  const [tab, setTab] = useState<'playlists' | 'media'>('playlists')
  const [playlistQuery, setPlaylistQuery] = useState('')
  const mediaScope = useFolderScope()
  const query = tab === 'playlists' ? playlistQuery : mediaScope.query

  const playlists = useApiResource<Playlist[] | Page<Playlist>>(
    tab === 'playlists' ? `${API_V1}/playlists` : null
  )
  const mediaParams = scopeParams(mediaScope)
  mediaParams.set('size', '200')
  const media = useApiResource<Page<MediaAsset>>(
    tab === 'media' ? `${API_V1}/media?${mediaParams.toString()}` : null
  )
  const playlistNeedle = normalizeText(playlistQuery)
  const visiblePlaylists = asList(playlists.data).filter(
    (entry) =>
      !entry.hidden && (playlistNeedle === '' || normalizeText(entry.name).includes(playlistNeedle))
  )
  const visibleAssets = asList(media.data).filter((entry) => entry.status === 'READY')
  const empty = tab === 'playlists' ? visiblePlaylists.length === 0 : visibleAssets.length === 0

  return (
    <div className={cn('flex min-h-0 flex-col gap-3', className)}>
      <Tabs value={tab} onValueChange={(value) => setTab(value as 'playlists' | 'media')}>
        <TabsList className="w-full">
          <TabsTrigger value="playlists">{dict.schedule.playlistsTab}</TabsTrigger>
          <TabsTrigger value="media">{dict.schedule.mediaTab}</TabsTrigger>
        </TabsList>
      </Tabs>
      <div className="relative">
        <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
        <Input
          className="pl-8"
          placeholder={dict.media.search}
          value={query}
          onChange={(event) =>
            tab === 'playlists'
              ? setPlaylistQuery(event.target.value)
              : mediaScope.setQuery(event.target.value)
          }
        />
      </div>
      {tab === 'media' ? <PickerFolders scope={mediaScope} /> : null}
      <p className="text-muted-foreground text-xs">{dict.schedule.dragHint}</p>
      <div className="min-h-0 flex-1 space-y-1.5 overflow-y-auto pr-1">
        {tab === 'playlists'
          ? visiblePlaylists.map((entry) => (
              <DraggableRow key={entry.id} id={`pl:${entry.id}`}>
                <span className="bg-primary/10 text-primary flex size-12 shrink-0 items-center justify-center rounded-md">
                  <IconPlaylist className="size-5" />
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{entry.name}</p>
                  <p className="text-muted-foreground text-xs tabular-nums">
                    {fmt(dict.playlists.itemCount, { count: entry.items.length })} ·{' '}
                    {formatDuration(entry.totalDurationSec)}
                  </p>
                </div>
              </DraggableRow>
            ))
          : visibleAssets.map((entry) => (
              <DraggableRow key={entry.id} id={`as:${entry.id}`}>
                <MediaThumb id={entry.id} type={entry.type} className="size-12 shrink-0" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{entry.name}</p>
                  <p className="text-muted-foreground text-xs">{dict.media.types[entry.type]}</p>
                </div>
              </DraggableRow>
            ))}
        {empty ? (
          <p className="text-muted-foreground py-8 text-center text-sm">
            {dict.playlists.libraryEmpty}
          </p>
        ) : null}
      </div>
    </div>
  )
}
