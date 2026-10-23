'use client'

import { useCallback, useState } from 'react'
import Link from 'next/link'
import { useParams, useRouter } from 'next/navigation'
import {
  IconArrowLeft,
  IconDeviceTv,
  IconHistory,
  IconMouse,
  IconPower,
  IconRefresh,
  IconTerminal2,
  IconTrash,
  IconWorldDownload,
} from '@tabler/icons-react'
import { ContentEditor } from '@/components/content/content-editor'
import { NowPlayingPanel } from '@/components/content/now-playing'
import { TargetSchedules } from '@/components/content/target-schedules'
import { AracastBadge } from '@/components/players/aracast-badge'
import { AracastHardwarePanel } from '@/components/players/aracast-hardware-panel'
import { AracastSettings } from '@/components/players/aracast-settings'
import { DisplaysPanel } from '@/components/players/displays-panel'
import { formatPlayerVersion } from '@/components/players/player-version'
import { AUTO_OUTPUT, OutputSelect } from '@/components/players/output-select'
import {
  DEFAULT_IDLE_LOGO,
  IDLE_LOGO_INHERIT,
  IdleLogoSelect,
  idleLogoStatus,
  type IdleLogoValue,
} from '@/components/players/idle-logo-select'
import { MetricsCharts } from '@/components/players/metrics-charts'
import { VolumeControl } from '@/components/players/volume-control'
import { ShellAuditTable } from '@/components/players/shell-audit-table'
import { SystemPanel } from '@/components/players/system-panel'
import { ConfirmDialog } from '@/components/shared/confirm-dialog'
import { OnlineDot, PlayerBadges } from '@/components/shared/player-status'
import { RequirePermission } from '@/components/shared/require-permission'
import { ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { Switch } from '@/components/shadcn-ui/switch'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useLocalePath } from '@/hooks/use-locale-path'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { canOnPlayer, hasPermission } from '@/lib/permissions'
import { toast } from '@/lib/toast'
import type { Delivery, Orientation, Page, Player, PlayerGroup, Site } from '@/types/api'

const NO_GROUP = 'none'
const NO_SITE = 'none'

function PlayerDetail() {
  const dict = useDictionary()
  const { user } = useAuth()
  const params = useParams()
  const router = useRouter()
  const localePath = useLocalePath()
  const { formatDateTime, formatRelative } = useDateFormatter()
  const id = params.id as string
  const player = useApiResource<Player>(`${API_V1}/players/${id}`, { intervalMs: 5000 })
  const deliveries = useApiResource<Delivery[] | Page<Delivery>>(
    `${API_V1}/players/${id}/deliveries`,
    { intervalMs: 5000 }
  )
  const groups = useApiResource<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`)
  const sites = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`)
  const [edits, setEdits] = useState<{
    name?: string
    groupId?: string
    orientation?: Orientation
    output?: string
    idleLogo?: IdleLogoValue
    description?: string
    siteId?: string
  }>({})
  const [removing, setRemoving] = useState(false)

  const current = player.data
  const reloadPlayer = player.reload
  const onContentLinked = useCallback(() => void reloadPlayer(), [reloadPlayer])
  const groupOfPlayer = current?.groupId ?? null
  const scope = { groupId: groupOfPlayer, siteId: current?.siteId ?? null }
  const canManage = canOnPlayer(user, 'PLAYER_MANAGE', scope, 'OPERATE')
  const canRemote = canOnPlayer(user, 'PLAYER_REMOTE', scope, 'OPERATE')
  const canTerminal = hasPermission(user, 'PLAYER_TERMINAL') && Boolean(user?.admin)
  const canProgram = canOnPlayer(user, 'SCHEDULE_MANAGE', scope, 'OPERATE')
  const isAracast = current?.kind === 'ARACAST'
  const name = edits.name ?? current?.name ?? ''
  const groupId = edits.groupId ?? current?.groupId ?? NO_GROUP
  const orientation = edits.orientation ?? current?.orientation ?? 'LANDSCAPE'
  const description = edits.description ?? current?.description ?? ''
  const siteId = edits.siteId ?? current?.siteId ?? NO_SITE
  const setDescription = (value: string) =>
    setEdits((previous) => ({ ...previous, description: value }))
  const setSiteId = (value: string) => setEdits((previous) => ({ ...previous, siteId: value }))
  const output = edits.output ?? current?.preferredOutput ?? AUTO_OUTPUT
  const idleLogo: IdleLogoValue = edits.idleLogo ?? current?.idleLogo ?? IDLE_LOGO_INHERIT
  const setIdleLogo = (value: IdleLogoValue) =>
    setEdits((previous) => ({ ...previous, idleLogo: value }))
  const setOutput = (value: string) => setEdits((previous) => ({ ...previous, output: value }))
  const setName = (value: string) => setEdits((previous) => ({ ...previous, name: value }))
  const setGroupId = (value: string) => setEdits((previous) => ({ ...previous, groupId: value }))
  const setOrientation = (value: Orientation) =>
    setEdits((previous) => ({ ...previous, orientation: value }))

  async function save() {
    try {
      await api.players.update(id, {
        name: name.trim(),
        groupId: groupId === NO_GROUP ? null : groupId,
        ...(isAracast ? {} : { orientation }),
        ...(edits.description === undefined ? {} : { description: edits.description.trim() }),
        ...(edits.siteId === undefined || inheritedFrom !== null
          ? {}
          : edits.siteId === NO_SITE
            ? { clearSite: true }
            : { siteId: edits.siteId }),
        ...(edits.idleLogo === undefined
          ? {}
          : edits.idleLogo === IDLE_LOGO_INHERIT
            ? { clearIdleLogo: true }
            : { idleLogo: edits.idleLogo }),
        ...(isAracast || edits.output === undefined
          ? {}
          : edits.output === AUTO_OUTPUT
            ? { clearPreferredOutput: true }
            : { preferredOutput: edits.output }),
      })
      toast.success(dict.players.saved)
      setEdits({})
      void player.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.players.saveFailed))
    }
  }

  async function toggleStatus(enabled: boolean) {
    try {
      await api.players.update(id, { status: enabled ? 'ACTIVE' : 'DISABLED' })
      void player.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.players.saveFailed))
    }
  }

  async function command(kind: 'resync' | 'restart' | 'reload') {
    try {
      await api.players.command(id, kind)
      toast.success(dict.players.commandSent)
      void player.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.players.commandFailed))
    }
  }

  async function remove() {
    try {
      await api.players.remove(id)
      router.replace(localePath('/players'))
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.players.deleteFailed))
    }
  }

  if (player.loading && !current) return <PageSkeleton rows={3} />
  if (player.error || !current) return <ErrorState onRetry={() => void player.reload()} />

  const history = asList(deliveries.data)
  const groupList = asList(groups.data)
  const siteList = asList(sites.data)
  const selectedGroup = groupList.find((group) => group.id === groupId) ?? null
  const inheritedFrom = groupList.find((group) => group.id === groupId && group.siteId) ?? null

  return (
    <div className="space-y-5">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link href={localePath('/players')}>
          <IconArrowLeft /> {dict.players.back}
        </Link>
      </Button>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="bg-primary/10 text-primary flex size-12 items-center justify-center rounded-xl">
            <IconDeviceTv className="size-6" />
          </span>
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight">
              {current.name}
              <OnlineDot online={current.online} />
              <AracastBadge player={current} />
            </h1>
            {current.description ? (
              <Tooltip>
                <TooltipTrigger asChild>
                  <p className="text-muted-foreground mt-0.5 max-w-xl truncate text-sm">
                    {current.description}
                  </p>
                </TooltipTrigger>
                <TooltipContent className="max-w-sm">{current.description}</TooltipContent>
              </Tooltip>
            ) : null}
            <div className="mt-1">
              <PlayerBadges player={current} />
            </div>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <VolumeControl
            playerId={id}
            volume={current.volume}
            disabled={!canManage}
            onVolumeChange={(volume) =>
              player.setData((previous) => (previous ? { ...previous, volume } : previous))
            }
            onPlayerUpdated={player.setData}
          />
          {canRemote ? (
            <Button asChild disabled={!current.online}>
              <Link href={localePath(`/players/${id}/remote`)}>
                <IconMouse /> {dict.players.remoteControl}
              </Link>
            </Button>
          ) : null}
          {canTerminal ? (
            <Button asChild variant="outline" disabled={!current.online}>
              <Link href={localePath(`/players/${id}/terminal`)}>
                <IconTerminal2 /> {dict.players.terminal.open}
              </Link>
            </Button>
          ) : null}
          {hasPermission(user, 'AUDIT_VIEW') ? (
            <Button asChild variant="ghost">
              <Link href={localePath(`/audit?entityType=PLAYER&entityId=${id}`)}>
                <IconHistory /> {dict.audit.viewHistory}
              </Link>
            </Button>
          ) : null}
          {canManage ? (
            <>
              <Button variant="outline" onClick={() => void command('resync')}>
                <IconWorldDownload /> {dict.players.resync}
              </Button>
              {isAracast ? null : (
                <Button variant="outline" onClick={() => void command('reload')}>
                  <IconRefresh /> {dict.players.reload}
                </Button>
              )}
              <Button variant="outline" onClick={() => void command('restart')}>
                <IconPower /> {dict.players.restart}
              </Button>
            </>
          ) : null}
        </div>
      </div>

      <NowPlayingPanel
        target={{
          playerId: id,
          groupId: groupOfPlayer,
          siteId: current.siteId ?? null,
          timezone: current.timezone,
          fallbackPlaylistId:
            current.fallbackPlaylistId ??
            groupList.find((group) => group.id === groupOfPlayer)?.fallbackPlaylistId ??
            null,
        }}
        player={current}
      />

      <ContentEditor
        key={current.id}
        target={{
          kind: 'player',
          id,
          name: current.name,
          fallbackPlaylistId: current.fallbackPlaylistId ?? null,
        }}
        canEdit={canProgram}
        publishNote={
          current.pending ? (
            <Badge variant="secondary" className="bg-warning/15 text-warning">
              {current.online ? dict.content.pending : dict.content.offlinePending}
            </Badge>
          ) : (
            <Badge variant="secondary" className="bg-success/15 text-success">
              {dict.content.published}
            </Badge>
          )
        }
        onLinked={onContentLinked}
      />
      <TargetSchedules
        playerId={id}
        groupId={groupOfPlayer}
        siteId={current.siteId ?? null}
        canEdit={canProgram}
      />

      <div className="grid gap-4 lg:grid-cols-3">
        {canManage ? (
          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>{dict.players.settings}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="player-name">{dict.common.name}</Label>
                  <Input
                    id="player-name"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label>{dict.players.site}</Label>
                  <Select
                    value={inheritedFrom?.siteId ?? siteId}
                    onValueChange={setSiteId}
                    disabled={inheritedFrom !== null}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NO_SITE}>{dict.players.noSite}</SelectItem>
                      {siteList.map((site) => (
                        <SelectItem key={site.id} value={site.id}>
                          {site.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {inheritedFrom ? (
                    <p className="text-muted-foreground text-xs">
                      {dict.players.siteInheritedFrom}{' '}
                      <Link
                        href={localePath(`/groups/${inheritedFrom.id}`)}
                        className="text-foreground font-medium hover:underline"
                      >
                        {inheritedFrom.name}
                      </Link>
                    </p>
                  ) : null}
                </div>
                <div className="space-y-2">
                  <Label>{dict.players.group}</Label>
                  <Select value={groupId} onValueChange={setGroupId}>
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NO_GROUP}>{dict.players.noGroup}</SelectItem>
                      {groupList.map((group) => (
                        <SelectItem key={group.id} value={group.id}>
                          {group.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                {isAracast ? null : (
                  <div className="space-y-2">
                    <Label>{dict.players.orientation}</Label>
                    <Select
                      value={orientation}
                      onValueChange={(value) => setOrientation(value as Orientation)}
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="LANDSCAPE">{dict.players.landscape}</SelectItem>
                        <SelectItem value="PORTRAIT">{dict.players.portrait}</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                )}
                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="player-description">{dict.players.descriptionLabel}</Label>
                  <Input
                    id="player-description"
                    value={description}
                    maxLength={300}
                    placeholder={dict.players.descriptionPlaceholder}
                    onChange={(event) => setDescription(event.target.value)}
                  />
                </div>
                {isAracast ? null : (
                  <OutputSelect player={current} value={output} onChange={setOutput} />
                )}
                <IdleLogoSelect
                  value={idleLogo}
                  onChange={setIdleLogo}
                  disabled={!canManage}
                  hint={dict.idleLogo.hint}
                  status={idleLogoStatus(current)}
                  inheritLogo={selectedGroup?.idleLogo ?? DEFAULT_IDLE_LOGO}
                  inheritLabel={
                    selectedGroup?.idleLogo
                      ? dict.idleLogo.inheritFromGroup
                          .replace('{group}', selectedGroup.name)
                          .replace('{logo}', dict.idleLogo.logos[selectedGroup.idleLogo])
                      : dict.idleLogo.inheritDefault.replace(
                          '{logo}',
                          dict.idleLogo.logos[DEFAULT_IDLE_LOGO]
                        )
                  }
                  effective={
                    current.effectiveIdleLogo
                      ? {
                          logo: current.effectiveIdleLogo,
                          text: `${dict.idleLogo.effective.replace('{logo}', dict.idleLogo.logos[current.effectiveIdleLogo])} (${
                            current.idleLogoSource === 'PLAYER'
                              ? dict.idleLogo.sourcePlayer
                              : current.idleLogoSource === 'GROUP'
                                ? dict.idleLogo.sourceGroup.replace(
                                    '{group}',
                                    current.groupName ?? ''
                                  )
                                : dict.idleLogo.sourceDefault
                          })`,
                        }
                      : null
                  }
                />
                <div className="flex items-end justify-between gap-3 rounded-lg border px-3 py-2">
                  <Label htmlFor="player-enabled">{dict.players.enabled}</Label>
                  <Switch
                    id="player-enabled"
                    checked={current.status === 'ACTIVE'}
                    onCheckedChange={(value) => void toggleStatus(value)}
                  />
                </div>
              </div>
              <div className="flex justify-between gap-2">
                <Button variant="destructive" onClick={() => setRemoving(true)}>
                  <IconTrash /> {dict.players.remove}
                </Button>
                <Button onClick={() => void save()}>{dict.common.save}</Button>
              </div>
            </CardContent>
          </Card>
        ) : null}

        <Card className={canManage ? undefined : 'lg:col-span-3'}>
          <CardHeader>
            <CardTitle>{dict.players.info}</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="divide-y text-sm">
              {(
                [
                  [dict.players.version, `${current.appliedVersion} / ${current.desiredVersion}`],
                  [dict.players.state, current.state ?? '-'],
                  [
                    dict.players.lastSeen,
                    current.lastSeenAt ? formatRelative(current.lastSeenAt) : dict.players.never,
                  ],
                  ['IP', current.ip ?? '-'],
                  [dict.players.hostname, current.hostname ?? '-'],
                  [dict.players.resolution, current.resolution ?? '-'],
                  ...(isAracast
                    ? []
                    : [[dict.players.playerVersion, formatPlayerVersion(current) ?? '-']]),
                  [dict.players.timezone, current.timezone ?? '-'],
                ] as Array<[string, string]>
              ).map(([label, value]) => (
                <div key={label} className="flex justify-between gap-3 py-2">
                  <dt className="text-muted-foreground">{label}</dt>
                  <dd className="text-right font-medium break-all">{value}</dd>
                </div>
              ))}
            </dl>
            {current.lastError ? (
              <p className="text-destructive bg-destructive/10 mt-3 rounded-md p-2 text-xs">
                {current.lastError}
              </p>
            ) : null}
          </CardContent>
        </Card>
      </div>

      {isAracast ? (
        <>
          <AracastHardwarePanel hardware={current.hardware} />
          {canManage ? <AracastSettings player={current} isAdmin={Boolean(user?.admin)} /> : null}
        </>
      ) : (
        <>
          <div className="grid gap-4 lg:grid-cols-2">
            <SystemPanel system={current.system} />
            <DisplaysPanel
              playerId={id}
              displays={current.displays}
              online={current.online}
              canManage={canManage}
            />
          </div>

          <MetricsCharts playerId={id} system={current.system} />
        </>
      )}

      <Card>
        <CardHeader>
          <CardTitle>{dict.players.deliveries}</CardTitle>
        </CardHeader>
        <CardContent>
          {history.length === 0 ? (
            <p className="text-muted-foreground py-4 text-center text-sm">
              {dict.players.noDeliveries}
            </p>
          ) : (
            <ul className="divide-y">
              {history.map((delivery) => (
                <li
                  key={delivery.version}
                  className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm"
                >
                  <div className="flex items-center gap-3">
                    <span className="font-mono font-medium">v{delivery.version}</span>
                    <Badge
                      variant={
                        delivery.status === 'FAILED'
                          ? 'destructive'
                          : delivery.status === 'APPLIED'
                            ? 'default'
                            : 'secondary'
                      }
                    >
                      {dict.players.deliveryStatus[delivery.status]}
                    </Badge>
                    {delivery.error ? (
                      <span className="text-destructive text-xs">{delivery.error}</span>
                    ) : null}
                  </div>
                  <span className="text-muted-foreground text-xs">
                    {formatDateTime(delivery.deliveredAt ?? delivery.createdAt)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {canTerminal ? <ShellAuditTable playerId={id} /> : null}

      <ConfirmDialog
        open={removing}
        onOpenChange={setRemoving}
        title={dict.players.removeTitle}
        description={dict.players.removeDescription.replace('{name}', current.name)}
        confirmLabel={dict.players.remove}
        onConfirm={() => {
          setRemoving(false)
          void remove()
        }}
      />
    </div>
  )
}

export default function PlayerDetailPage() {
  return (
    <RequirePermission permission="PLAYER_VIEW">
      <PlayerDetail />
    </RequirePermission>
  )
}
