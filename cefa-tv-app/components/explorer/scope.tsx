'use client'

import { useEffect, useState } from 'react'
import { IconChevronRight, IconFolder } from '@tabler/icons-react'
import { Breadcrumbs } from '@/components/explorer/breadcrumbs'
import { useFolders } from '@/components/explorer/use-folders'
import { useDictionary } from '@/contexts/dictionary-context'

export function useFolderScope() {
  const [folderId, setFolderId] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(query.trim()), 250)
    return () => window.clearTimeout(timer)
  }, [query])

  return {
    folderId,
    setFolderId: (next: string | null) => {
      setQuery('')
      setDebounced('')
      setFolderId(next)
    },
    query,
    setQuery,
    debouncedQuery: debounced,
    searching: debounced !== '',
  }
}

export type FolderScope = ReturnType<typeof useFolderScope>

export function folderQuery(folderId: string | null, query: string): URLSearchParams {
  const params = new URLSearchParams()
  if (query !== '') {
    params.set('q', query)
    params.set('recursive', 'true')
    return params
  }
  params.set('folderId', folderId ?? 'root')
  params.set('recursive', 'false')
  return params
}

export function scopeParams(scope: FolderScope): URLSearchParams {
  return folderQuery(scope.folderId, scope.debouncedQuery)
}

export function PickerFolders({ scope }: { scope: FolderScope }) {
  const dict = useDictionary()
  const model = useFolders()
  const rootLabel = dict.explorer.rootMedia
  const needle = scope.debouncedQuery.toLowerCase()
  const folders = scope.searching
    ? model.folders.filter((folder) => folder.name.toLowerCase().includes(needle))
    : model.childrenOf(scope.folderId)

  if (model.folders.length === 0) return null

  return (
    <div className="space-y-1.5">
      {scope.searching ? null : (
        <Breadcrumbs
          path={model.pathOf(scope.folderId)}
          rootLabel={rootLabel}
          droppable={false}
          onNavigate={scope.setFolderId}
        />
      )}
      {folders.length > 0 ? (
        <ul className="space-y-1">
          {folders.map((folder) => {
            const location = model
              .pathOf(folder.parentId)
              .map((entry) => entry.name)
              .join(' / ')
            return (
              <li key={folder.id}>
                <button
                  type="button"
                  className="hover:bg-muted flex w-full items-center gap-2 rounded-lg border px-2 py-1.5 text-left text-sm"
                  onClick={() => scope.setFolderId(folder.id)}
                >
                  <IconFolder className="text-primary size-4 shrink-0" />
                  <span className="min-w-0 flex-1 truncate font-medium">{folder.name}</span>
                  {scope.searching && location ? (
                    <span className="text-muted-foreground truncate text-xs">{location}</span>
                  ) : null}
                  <IconChevronRight className="text-muted-foreground size-4 shrink-0" />
                </button>
              </li>
            )
          })}
        </ul>
      ) : null}
    </div>
  )
}
