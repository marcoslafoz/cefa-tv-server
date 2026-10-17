'use client'

import Link from 'next/link'
import { IconBuildingStore } from '@tabler/icons-react'
import { Badge } from '@/components/shadcn-ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useLocalePath } from '@/hooks/use-locale-path'
import { asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { offlineCount } from '@/lib/utils'
import type { Page, Site } from '@/types/api'

export function SitesStatus() {
  const dict = useDictionary()
  const localePath = useLocalePath()
  const sites = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`, { intervalMs: 10000 })
  const list = asList(sites.data)
  if (list.length === 0) return null

  return (
    <Card>
      <CardHeader>
        <CardTitle>{dict.sites.statusTitle}</CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="divide-y">
          {list.map((site) => (
            <li key={site.id}>
              <Link
                href={localePath(`/sites/${site.id}`)}
                className="hover:bg-muted/50 flex items-center justify-between gap-3 rounded-md px-1 py-2.5"
              >
                <span className="flex min-w-0 items-center gap-3">
                  <IconBuildingStore className="text-muted-foreground size-4 shrink-0" />
                  <span className="truncate text-sm font-medium">{site.name}</span>
                </span>
                <span className="flex items-center gap-1.5">
                  <Badge variant="secondary" className="bg-success/15 text-success">
                    {site.onlineCount}
                  </Badge>
                  {offlineCount(site) > 0 ? (
                    <Badge variant="secondary" className="bg-destructive/10 text-destructive">
                      {offlineCount(site)}
                    </Badge>
                  ) : null}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
