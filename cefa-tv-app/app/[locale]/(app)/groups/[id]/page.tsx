'use client'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { useCallback } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import { IconArrowLeft, IconDeviceTv, IconMapPin } from '@tabler/icons-react'
import { ContentEditor } from '@/components/content/content-editor'
import { NowPlayingPanel } from '@/components/content/now-playing'
import { TargetSchedules } from '@/components/content/target-schedules'
import { RequirePermission } from '@/components/shared/require-permission'
import { OnlineDot } from '@/components/shared/player-status'
import { ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { GroupBadge } from '@/components/shared/group-icon'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useLocalePath } from '@/hooks/use-locale-path'
import { asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { canAccessGroup, hasPermission } from '@/lib/permissions'
import type { Page, Player, PlayerGroup } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

function GroupDetail() {
  const dict = useDictionary()
  const fmt = useFormat()
  const { user } = useAuth()
  const params = useParams()
  const localePath = useLocalePath()
  const id = params.id as string
  const groups = useApiResource<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`, {
    intervalMs: 10000,
  })
  const players = useApiResource<Player[] | Page<Player>>(`${API_V1}/players`, {
    intervalMs: 5000,
  })
  const reloadGroups = groups.reload
  const onLinked = useCallback(() => void reloadGroups(), [reloadGroups])

  const group = asList(groups.data).find((entry) => entry.id === id)
  if ((groups.loading && !groups.data) || (players.loading && !players.data)) {
    return <PageSkeleton rows={2} />
  }
  if (groups.error || !group) return <ErrorState onRetry={() => void groups.reload()} />

  const members = asList(players.data).filter((player) => player.groupId === id)
  const pendingCount = members.filter((player) => player.pending).length
  const canProgram = hasPermission(user, 'SCHEDULE_MANAGE') && canAccessGroup(user, id, 'OPERATE')

  const publishNote =
    members.length === 0 ? (
      <Badge variant="outline">{dict.content.groupNoPlayers}</Badge>
    ) : pendingCount > 0 ? (
      <Badge variant="secondary" className="bg-warning/15 text-warning">
        {fmt(dict.content.groupPending, { count: pendingCount })}
      </Badge>
    ) : (
      <Badge variant="secondary" className="bg-success/15 text-success">
        {dict.content.groupPublished}
      </Badge>
    )

  return (
    <div className="space-y-5">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link href={localePath('/groups')}>
          <IconArrowLeft /> {dict.players.back}
        </Link>
      </Button>
      <div className="flex items-center gap-3">
        <GroupBadge
          icon={group.icon}
          color={group.color}
          className="size-12 rounded-xl"
          iconClassName="size-6"
        />
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{group.name}</h1>
          <p className="text-muted-foreground text-sm">
            {fmt(dict.groups.players, { count: members.length })}
          </p>
          {group.siteName ? (
            <p className="text-muted-foreground flex items-center gap-1 text-sm">
              <IconMapPin className="size-3.5 shrink-0" />
              <span className="truncate">{group.siteName}</span>
            </p>
          ) : null}
        </div>
      </div>

      <NowPlayingPanel
        target={{
          groupId: id,
          timezone: members[0]?.timezone ?? null,
          fallbackPlaylistId: group.fallbackPlaylistId ?? null,
        }}
        groupPlayers={members}
      />

      <ContentEditor
        key={group.id}
        target={{
          kind: 'group',
          id: group.id,
          name: group.name,
          fallbackPlaylistId: group.fallbackPlaylistId ?? null,
        }}
        canEdit={canProgram}
        publishNote={publishNote}
        onLinked={onLinked}
      />
      <TargetSchedules groupId={id} canEdit={canProgram} />

      <Card>
        <CardHeader>
          <CardTitle>{dict.groups.playersTitle}</CardTitle>
        </CardHeader>
        <CardContent>
          {members.length === 0 ? (
            <p className="text-muted-foreground py-4 text-center text-sm">
              {dict.groups.noPlayers}
            </p>
          ) : (
            <ul className="divide-y">
              {members.map((player) => (
                <li key={player.id}>
                  <Link
                    href={localePath(`/players/${player.id}`)}
                    className="hover:bg-muted/50 flex items-center justify-between gap-3 rounded-md px-1 py-2.5"
                  >
                    <span className="flex min-w-0 items-center gap-3">
                      <IconDeviceTv className="text-muted-foreground size-4" />
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium">{player.name}</span>
                        {player.description ? (
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <span className="text-muted-foreground block truncate text-xs">
                                {player.description}
                              </span>
                            </TooltipTrigger>
                            <TooltipContent>{player.description}</TooltipContent>
                          </Tooltip>
                        ) : null}
                      </span>
                    </span>
                    <span className="flex items-center gap-2">
                      {player.pending ? (
                        <Badge variant="secondary" className="bg-warning/15 text-warning">
                          {dict.players.pendingDelivery}
                        </Badge>
                      ) : null}
                      <OnlineDot online={player.online} />
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

export default function GroupDetailPage() {
  return (
    <RequirePermission permission="PLAYER_VIEW">
      <GroupDetail />
    </RequirePermission>
  )
}
