'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useParams, useRouter } from 'next/navigation'
import {
  IconArrowLeft,
  IconBuildingStore,
  IconClock,
  IconDeviceTv,
  IconMapPin,
  IconRadar2,
  IconPencil,
  IconPlus,
  IconTrash,
  IconX,
} from '@tabler/icons-react'
import { AracastBadge } from '@/components/players/aracast-badge'
import { AddPlayersDialog } from '@/components/sites/add-players-dialog'
import { AracastDiscoveryDialog } from '@/components/sites/aracast-discovery-dialog'
import { DeleteSiteDialog } from '@/components/sites/delete-site-dialog'
import { SiteDialog } from '@/components/sites/site-dialog'
import { DeliveryBadge } from '@/components/shared/delivery-state'
import { OnlineDot } from '@/components/shared/player-status'
import { RequirePermission } from '@/components/shared/require-permission'
import { ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useLocalePath } from '@/hooks/use-locale-path'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { canAccessSite, hasPermission } from '@/lib/permissions'
import { toast } from '@/lib/toast'
import { offlineCount } from '@/lib/utils'
import type { Page, Player, Site } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

function SiteDetail() {
  const dict = useDictionary()
  const fmt = useFormat()
  const { user } = useAuth()
  const params = useParams()
  const router = useRouter()
  const localePath = useLocalePath()
  const id = params.id as string
  const site = useApiResource<Site>(`${API_V1}/sites/${id}`, { intervalMs: 10000 })
  const allSites = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`)
  const players = useApiResource<Player[] | Page<Player>>(`${API_V1}/players?siteId=${id}`, {
    intervalMs: 5000,
  })
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [adding, setAdding] = useState(false)
  const [discovering, setDiscovering] = useState(false)
  const isAdmin = Boolean(user?.admin)
  const canManagePlayers =
    hasPermission(user, 'PLAYER_MANAGE') && canAccessSite(user, id, 'OPERATE')

  async function detach(player: Player) {
    try {
      await api.players.update(player.id, { clearSite: true })
      await Promise.all([players.reload(), site.reload()])
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.sites.detachFailed))
    }
  }

  if ((site.loading && !site.data) || (players.loading && !players.data)) {
    return <PageSkeleton rows={2} />
  }
  if (site.error || !site.data) return <ErrorState onRetry={() => void site.reload()} />
  const current = site.data
  const members = asList(players.data).filter((player) => player.siteId === id)
  const location = [current.city, current.country].filter(Boolean).join(', ')

  return (
    <div className="space-y-5">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link href={localePath('/sites')}>
          <IconArrowLeft /> {dict.players.back}
        </Link>
      </Button>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="bg-primary/10 text-primary flex size-12 items-center justify-center rounded-xl">
            <IconBuildingStore className="size-6" />
          </span>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{current.name}</h1>
            <p className="text-muted-foreground flex flex-wrap items-center gap-x-3 text-sm">
              <span className="flex items-center gap-1">
                <IconMapPin className="size-3.5" /> {location || dict.sites.noLocation}
              </span>
              <span className="flex items-center gap-1">
                <IconClock className="size-3.5" /> {current.timezone ?? dict.sites.noTimezone}
              </span>
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {canManagePlayers ? (
            <Button onClick={() => setAdding(true)}>
              <IconPlus /> {dict.sites.addPlayers}
            </Button>
          ) : null}
          {canManagePlayers ? (
            <Button variant="outline" onClick={() => setDiscovering(true)}>
              <IconRadar2 /> {dict.aracast.discovery.button}
            </Button>
          ) : null}
          {isAdmin ? (
            <>
              <Button variant="outline" onClick={() => setEditing(true)}>
                <IconPencil /> {dict.common.edit}
              </Button>
              <Button
                variant="outline"
                className="text-destructive"
                onClick={() => setDeleting(true)}
              >
                <IconTrash /> {dict.common.delete}
              </Button>
            </>
          ) : null}
        </div>
      </div>

      <div className="flex flex-wrap gap-1.5">
        <Badge variant="secondary">
          {fmt(dict.sites.playersCount, { count: current.playerCount })}
        </Badge>
        <Badge variant="secondary" className="bg-success/15 text-success">
          {fmt(dict.sites.onlineCount, { count: current.onlineCount })}
        </Badge>
        <Badge variant="secondary" className="bg-destructive/10 text-destructive">
          {fmt(dict.sites.offlineCount, { count: offlineCount(current) })}
        </Badge>
      </div>

      {current.ipRanges.length > 0 ? (
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-muted-foreground text-sm">{dict.aracast.ranges.label}</span>
          {current.ipRanges.map((range) => (
            <Badge key={range} variant="outline" className="font-mono">
              {range}
            </Badge>
          ))}
        </div>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>{dict.sites.playersTitle}</CardTitle>
        </CardHeader>
        <CardContent>
          {members.length === 0 ? (
            <p className="text-muted-foreground py-6 text-center text-sm">{dict.sites.noPlayers}</p>
          ) : (
            <ul className="divide-y">
              {members.map((player) => (
                <li key={player.id} className="flex items-center justify-between gap-3 py-2.5">
                  <Link
                    href={localePath(`/players/${player.id}`)}
                    className="flex min-w-0 items-center gap-3 hover:underline"
                  >
                    <IconDeviceTv className="text-muted-foreground size-4 shrink-0" />
                    <span className="min-w-0">
                      <span className="flex items-center gap-1.5 text-sm font-medium">
                        <span className="truncate">{player.name}</span>
                        <AracastBadge player={player} />
                      </span>
                      {player.description ? (
                        <span className="text-muted-foreground block truncate text-xs">
                          {player.description}
                        </span>
                      ) : null}
                    </span>
                  </Link>
                  <span className="flex items-center gap-2">
                    <DeliveryBadge player={player} />
                    <OnlineDot online={player.online} />
                    {canManagePlayers ? (
                      <Button
                        size="icon-sm"
                        variant="ghost"
                        aria-label={dict.sites.detach}
                        title={dict.sites.detach}
                        onClick={() => void detach(player)}
                      >
                        <IconX />
                      </Button>
                    ) : null}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {editing ? (
        <SiteDialog
          site={current}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false)
            void site.reload()
          }}
        />
      ) : null}
      {deleting ? (
        <DeleteSiteDialog
          site={current}
          sites={asList(allSites.data)}
          onClose={() => setDeleting(false)}
          onDeleted={() => router.replace(localePath('/sites'))}
        />
      ) : null}
      {discovering ? (
        <AracastDiscoveryDialog
          site={current}
          onClose={() => setDiscovering(false)}
          onAdded={() => void Promise.all([players.reload(), site.reload()])}
        />
      ) : null}
      {adding ? (
        <AddPlayersDialog
          site={current}
          onClose={() => setAdding(false)}
          onAdded={() => {
            setAdding(false)
            void Promise.all([players.reload(), site.reload()])
          }}
        />
      ) : null}
    </div>
  )
}

export default function SiteDetailPage() {
  return (
    <RequirePermission permission="PLAYER_VIEW">
      <SiteDetail />
    </RequirePermission>
  )
}
