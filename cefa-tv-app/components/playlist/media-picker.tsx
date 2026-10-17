'use client'

import { useEffect, useState } from 'react'
import { IconSearch } from '@tabler/icons-react'
import { PickerFolders, scopeParams, useFolderScope } from '@/components/explorer/scope'
import { MediaThumb } from '@/components/shared/media-thumb'
import { Button } from '@/components/shadcn-ui/button'
import { Input } from '@/components/shadcn-ui/input'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { API_V1 } from '@/lib/api-client'
import { MEDIA_TYPES } from '@/lib/media'
import { cn } from '@/lib/utils'
import type { MediaAsset, MediaType, Page } from '@/types/api'

export function MediaPicker({
  selected,
  onToggle,
  onLoaded,
  emptyHint,
}: {
  selected: string[]
  onToggle: (assetId: string) => void
  onLoaded: (assets: MediaAsset[]) => void
  emptyHint?: React.ReactNode
}) {
  const dict = useDictionary()
  const scope = useFolderScope()
  const [type, setType] = useState<MediaType | null>(null)

  const params = scopeParams(scope)
  if (type) params.set('type', type)
  params.set('size', '200')
  const media = useApiResource<Page<MediaAsset>>(`${API_V1}/media?${params.toString()}`)
  const assets = (media.data?.content ?? []).filter((asset) => asset.status === 'READY')

  useEffect(() => {
    if (media.data) onLoaded(media.data.content)
  }, [media.data, onLoaded])

  const nothingAtAll = media.data !== null && assets.length === 0 && !scope.searching && !type

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-48 flex-1">
          <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
          <Input
            className="pl-8"
            placeholder={dict.media.search}
            value={scope.query}
            onChange={(event) => scope.setQuery(event.target.value)}
          />
        </div>
        <div className="flex flex-wrap gap-1">
          <Button
            size="xs"
            variant={type === null ? 'default' : 'outline'}
            onClick={() => setType(null)}
          >
            {dict.media.all}
          </Button>
          {MEDIA_TYPES.map((entry) => (
            <Button
              key={entry}
              size="xs"
              variant={type === entry ? 'default' : 'outline'}
              onClick={() => setType(type === entry ? null : entry)}
            >
              {dict.media.types[entry]}
            </Button>
          ))}
        </div>
      </div>
      <PickerFolders scope={scope} />
      <div className="grid max-h-80 grid-cols-2 gap-2 overflow-y-auto pr-1 sm:grid-cols-3 md:grid-cols-4">
        {assets.map((asset) => {
          const order = selected.indexOf(asset.id) + 1
          return (
            <button
              key={asset.id}
              type="button"
              aria-pressed={order > 0}
              onClick={() => onToggle(asset.id)}
              className={cn(
                'bg-card hover:border-primary/50 relative overflow-hidden rounded-lg border text-left transition-colors',
                order > 0 && 'border-primary ring-primary/40 ring-2'
              )}
            >
              <MediaThumb
                id={asset.id}
                type={asset.type}
                className="aspect-video w-full rounded-none"
              />
              <p className="truncate px-2 py-1.5 text-xs font-medium">{asset.name}</p>
              {order > 0 ? (
                <span className="bg-primary text-primary-foreground absolute top-1.5 left-1.5 flex size-6 items-center justify-center rounded-full text-xs font-semibold">
                  {order}
                </span>
              ) : null}
            </button>
          )
        })}
        {assets.length === 0 ? (
          <p className="text-muted-foreground col-span-full py-8 text-center text-sm">
            {nothingAtAll && emptyHint ? emptyHint : dict.playlists.libraryEmpty}
          </p>
        ) : null}
      </div>
    </div>
  )
}
