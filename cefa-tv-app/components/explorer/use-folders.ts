'use client'

import { useMemo } from 'react'
import { useApiResource } from '@/hooks/use-api-resource'
import { asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import type { Folder, Page } from '@/types/api'

export function useFolders() {
  const resource = useApiResource<Folder[] | Page<Folder>>(`${API_V1}/folders?kind=MEDIA`)
  const folders = useMemo(() => asList(resource.data), [resource.data])

  return useMemo(() => {
    const byId = new Map(folders.map((folder) => [folder.id, folder]))

    function childrenOf(parentId: string | null): Folder[] {
      return folders
        .filter((folder) => folder.parentId === parentId)
        .sort((left, right) => left.name.localeCompare(right.name))
    }

    function pathOf(folderId: string | null): Folder[] {
      const path: Folder[] = []
      let cursor = folderId ? byId.get(folderId) : undefined
      while (cursor) {
        path.unshift(cursor)
        cursor = cursor.parentId ? byId.get(cursor.parentId) : undefined
      }
      return path
    }

    function isWithin(folderId: string | null, ancestorId: string): boolean {
      return pathOf(folderId).some((folder) => folder.id === ancestorId)
    }

    return {
      folders,
      byId,
      childrenOf,
      pathOf,
      isWithin,
      loading: resource.loading && !resource.data,
      reload: resource.reload,
    }
  }, [folders, resource.loading, resource.data, resource.reload])
}

export type FolderModel = ReturnType<typeof useFolders>
