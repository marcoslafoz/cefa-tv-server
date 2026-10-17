'use client'

import { useRouter } from 'next/navigation'
import { ROW_CLASS, rowActivation } from '@/lib/row-activation'
import { cn } from '@/lib/utils'
import { useState } from 'react'
import Link from 'next/link'
import {
  IconBuildingStore,
  IconClock,
  IconMapPin,
  IconPencil,
  IconPlus,
  IconTrash,
} from '@tabler/icons-react'
import { DeleteSiteDialog } from '@/components/sites/delete-site-dialog'
import { SiteDialog } from '@/components/sites/site-dialog'
import { PageHeader } from '@/components/shared/page-header'
import { RequirePermission } from '@/components/shared/require-permission'
import { EmptyState, ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card } from '@/components/shadcn-ui/card'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useLocalePath } from '@/hooks/use-locale-path'
import { asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { offlineCount } from '@/lib/utils'
import type { Page, Site } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

export function siteLocation(site: Site): string {
  return [site.city, site.country].filter(Boolean).join(', ')
}

function SitesContent() {
  const dict = useDictionary()
  const fmt = useFormat()
  const { user } = useAuth()
  const localePath = useLocalePath()
  const router = useRouter()
  const resource = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`, { intervalMs: 10000 })
  const [editing, setEditing] = useState<Site | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Site | null>(null)
  const isAdmin = Boolean(user?.admin)

  if (resource.loading && !resource.data) return <PageSkeleton rows={3} />
  if (resource.error && !resource.data) return <ErrorState onRetry={() => void resource.reload()} />
  const sites = asList(resource.data)

  const newButton = isAdmin ? (
    <Button onClick={() => setEditing('new')}>
      <IconPlus /> {dict.sites.new}
    </Button>
  ) : null

  return (
    <div className="space-y-5">
      <PageHeader
        title={dict.sites.title}
        description={dict.sites.description}
        actions={newButton}
      />
      {sites.length === 0 ? (
        <EmptyState
          icon={IconBuildingStore}
          title={dict.sites.emptyTitle}
          description={dict.sites.emptyDescription}
          action={newButton}
        />
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {sites.map((site) => (
            <Card
              key={site.id}
              {...rowActivation(() => router.push(localePath(`/sites/${site.id}`)))}
              className={cn('hover:border-primary/50 min-w-0 gap-3 overflow-hidden p-4', ROW_CLASS)}
            >
              <div className="flex items-start justify-between gap-3">
                <Link
                  href={localePath(`/sites/${site.id}`)}
                  className="flex min-w-0 items-center gap-3"
                >
                  <span className="bg-primary/10 text-primary flex size-10 shrink-0 items-center justify-center rounded-lg">
                    <IconBuildingStore className="size-5" />
                  </span>
                  <div className="min-w-0">
                    <p className="truncate font-medium">{site.name}</p>
                    <p className="text-muted-foreground flex items-center gap-1 truncate text-xs">
                      <IconMapPin className="size-3 shrink-0" />
                      {siteLocation(site) || dict.sites.noLocation}
                    </p>
                  </div>
                </Link>
                {isAdmin ? (
                  <div className="flex">
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      aria-label={dict.common.edit}
                      onClick={() => setEditing(site)}
                    >
                      <IconPencil />
                    </Button>
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      aria-label={dict.common.delete}
                      onClick={() => setDeleting(site)}
                    >
                      <IconTrash />
                    </Button>
                  </div>
                ) : null}
              </div>
              <div className="text-muted-foreground flex items-center gap-1 text-xs">
                <IconClock className="size-3" /> {site.timezone ?? dict.sites.noTimezone}
              </div>
              <div className="flex flex-wrap gap-1.5">
                <Badge variant="secondary">
                  {fmt(dict.sites.playersCount, { count: site.playerCount })}
                </Badge>
                <Badge variant="secondary" className="bg-success/15 text-success">
                  {fmt(dict.sites.onlineCount, { count: site.onlineCount })}
                </Badge>
                {offlineCount(site) > 0 ? (
                  <Badge variant="secondary" className="bg-destructive/10 text-destructive">
                    {fmt(dict.sites.offlineCount, { count: offlineCount(site) })}
                  </Badge>
                ) : null}
              </div>
            </Card>
          ))}
        </div>
      )}
      {editing ? (
        <SiteDialog
          site={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            void resource.reload()
          }}
        />
      ) : null}
      {deleting ? (
        <DeleteSiteDialog
          site={deleting}
          sites={sites}
          onClose={() => setDeleting(null)}
          onDeleted={() => {
            setDeleting(null)
            void resource.reload()
          }}
        />
      ) : null}
    </div>
  )
}

export default function SitesPage() {
  return (
    <RequirePermission permission="PLAYER_VIEW">
      <SitesContent />
    </RequirePermission>
  )
}
