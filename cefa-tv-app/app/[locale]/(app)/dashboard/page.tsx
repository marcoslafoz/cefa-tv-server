'use client'

import Link from 'next/link'
import {
  IconCloudOff,
  IconDatabase,
  IconDeviceTv,
  IconHourglass,
  IconLock,
  IconPlaylist,
  IconWifi,
} from '@tabler/icons-react'
import { PageHeader } from '@/components/shared/page-header'
import { OnboardingChecklist } from '@/components/shared/onboarding-checklist'
import { SitesStatus } from '@/components/shared/sites-status'
import { StatCard } from '@/components/shared/stat-card'
import { EmptyState, ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useLocalePath } from '@/hooks/use-locale-path'
import { API_V1 } from '@/lib/api-client'
import { asList } from '@/lib/api'
import { hasPermission } from '@/lib/permissions'
import { formatBytes } from '@/lib/utils'
import type { DashboardStats, Player } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

export default function DashboardPage() {
  const dict = useDictionary()
  const fmt = useFormat()
  const { user } = useAuth()
  const localePath = useLocalePath()
  const { formatRelative } = useDateFormatter()
  const withoutAccess = user !== null && !user.admin && user.permissions.length === 0
  const stats = useApiResource<DashboardStats>(withoutAccess ? null : `${API_V1}/dashboard`, {
    intervalMs: 10000,
  })
  const players = useApiResource<Player[] | { content: Player[] }>(
    withoutAccess ? null : `${API_V1}/players`,
    { intervalMs: 10000 }
  )

  if (withoutAccess) {
    return (
      <EmptyState
        icon={IconLock}
        title={dict.access.noAccessTitle}
        description={dict.access.noGroupsDescription}
      />
    )
  }

  if (stats.loading && !stats.data) return <PageSkeleton rows={4} />
  if (stats.error || !stats.data) return <ErrorState onRetry={() => void stats.reload()} />

  const data = stats.data
  const list = asList(players.data as Player[] | undefined)
  const needAttention = list.filter((player) => !player.online || player.pending).slice(0, 6)

  return (
    <div className="space-y-6">
      <PageHeader title={dict.dashboard.title} description={dict.dashboard.description} />
      <OnboardingChecklist stats={data} />
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label={dict.dashboard.online}
          value={data.playersOnline}
          hint={dict.dashboard.ofTotal.replace('{total}', String(data.players))}
          icon={IconWifi}
          tone="bg-success/15 text-success"
        />
        <StatCard
          label={dict.dashboard.offline}
          value={data.playersOffline}
          icon={IconCloudOff}
          tone="bg-destructive/15 text-destructive"
        />
        <StatCard
          label={dict.dashboard.pending}
          value={data.playersPending}
          hint={dict.dashboard.pendingHint}
          icon={IconHourglass}
          tone="bg-warning/15 text-warning"
        />
        <StatCard label={dict.dashboard.playlists} value={data.playlists} icon={IconPlaylist} />
      </div>
      {hasPermission(user, 'PLAYER_VIEW') ? <SitesStatus /> : null}
      <div className="grid gap-3 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader className="flex-row items-center justify-between">
            <CardTitle>{dict.dashboard.attention}</CardTitle>
            {hasPermission(user, 'PLAYER_VIEW') ? (
              <Button asChild variant="ghost" size="sm">
                <Link href={localePath('/players')}>{dict.dashboard.viewAll}</Link>
              </Button>
            ) : null}
          </CardHeader>
          <CardContent>
            {needAttention.length === 0 ? (
              <p className="text-muted-foreground py-6 text-center text-sm">
                {dict.dashboard.allGood}
              </p>
            ) : (
              <ul className="divide-y">
                {needAttention.map((player) => (
                  <li key={player.id} className="flex items-center justify-between gap-3 py-3">
                    <Link
                      href={localePath(`/players/${player.id}`)}
                      className="flex min-w-0 items-center gap-3 hover:underline"
                    >
                      <IconDeviceTv className="text-muted-foreground size-4 shrink-0" />
                      <span className="truncate text-sm font-medium">{player.name}</span>
                    </Link>
                    <div className="flex items-center gap-2">
                      {!player.online ? (
                        <Badge variant="destructive">
                          {dict.players.offline}
                          {player.lastSeenAt ? ` · ${formatRelative(player.lastSeenAt)}` : ''}
                        </Badge>
                      ) : null}
                      {player.pending ? (
                        <Badge variant="secondary">{dict.players.pendingDelivery}</Badge>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{dict.dashboard.storage}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex items-center gap-3">
              <span className="bg-primary/10 text-primary flex size-10 items-center justify-center rounded-lg">
                <IconDatabase className="size-5" />
              </span>
              <div>
                <p className="text-2xl font-semibold tabular-nums">
                  {formatBytes(data.storageBytes)}
                </p>
                <p className="text-muted-foreground text-xs">
                  {fmt(dict.dashboard.mediaCount, { count: data.media })}
                </p>
              </div>
            </div>
            {hasPermission(user, 'MEDIA_MANAGE') ? (
              <Button asChild variant="outline" className="w-full">
                <Link href={localePath('/media')}>{dict.dashboard.openLibrary}</Link>
              </Button>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
