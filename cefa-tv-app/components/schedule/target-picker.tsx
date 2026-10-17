'use client'

import { DynamicIcon } from '@/components/shared/group-icon'
import { useMemo, useState } from 'react'
import { IconBuildingStore, IconDeviceTv, IconSearch, IconX } from '@tabler/icons-react'
import { OnlineDot } from '@/components/shared/player-status'
import { Badge } from '@/components/shadcn-ui/badge'
import { Checkbox } from '@/components/shadcn-ui/checkbox'
import { Input } from '@/components/shadcn-ui/input'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { canAccessGroup, canAccessSite, canOnPlayer } from '@/lib/permissions'
import { normalizeText } from '@/lib/utils'
import type { Page, Player, PlayerGroup, ScheduleTargets, Site } from '@/types/api'
import { useFormat } from '@/hooks/use-format'

export function operablePlayers(
  user: Parameters<typeof canOnPlayer>[0],
  players: Player[]
): Player[] {
  return players.filter((player) =>
    canOnPlayer(
      user,
      'SCHEDULE_MANAGE',
      { groupId: player.groupId, siteId: player.siteId },
      'OPERATE'
    )
  )
}

export function operableGroups(
  user: Parameters<typeof canAccessGroup>[0],
  groups: PlayerGroup[]
): PlayerGroup[] {
  return groups.filter((group) => canAccessGroup(user, group.id, 'OPERATE'))
}

export function operableSites(user: Parameters<typeof canAccessSite>[0], sites: Site[]): Site[] {
  return sites.filter((site) => canAccessSite(user, site.id, 'OPERATE'))
}

export function targetNames(
  targets: ScheduleTargets,
  players: Player[],
  groups: PlayerGroup[],
  sites: Site[] = []
): string[] {
  return [
    ...sites.filter((site) => (targets.siteIds ?? []).includes(site.id)).map((site) => site.name),
    ...groups.filter((group) => targets.groupIds.includes(group.id)).map((group) => group.name),
    ...players
      .filter((player) => targets.playerIds.includes(player.id))
      .map((player) => player.name),
  ]
}

export function TargetPicker({
  players,
  groups,
  value,
  onChange,
}: {
  players: Player[]
  groups: PlayerGroup[]
  value: ScheduleTargets
  onChange: (targets: ScheduleTargets) => void
}) {
  const dict = useDictionary()
  const fmt = useFormat()
  const { user } = useAuth()
  const [query, setQuery] = useState('')
  const sitesResource = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`)
  const availableSites = useMemo(
    () => operableSites(user, asList(sitesResource.data)),
    [user, sitesResource.data]
  )
  const availableGroups = useMemo(() => operableGroups(user, groups), [user, groups])
  const availablePlayers = useMemo(() => operablePlayers(user, players), [user, players])
  const needle = normalizeText(query)
  const shownSites = availableSites.filter(
    (site) => needle === '' || normalizeText(site.name).includes(needle)
  )
  const shownGroups = availableGroups.filter(
    (group) => needle === '' || normalizeText(group.name).includes(needle)
  )
  const shownPlayers = availablePlayers.filter(
    (player) =>
      needle === '' ||
      normalizeText(player.name).includes(needle) ||
      normalizeText(player.groupName ?? '').includes(needle)
  )

  function toggle(kind: 'playerIds' | 'groupIds' | 'siteIds', id: string) {
    const current = value[kind] ?? []
    onChange({
      ...value,
      [kind]: current.includes(id) ? current.filter((entry) => entry !== id) : [...current, id],
    })
  }

  if (availableSites.length + availableGroups.length + availablePlayers.length === 0) {
    return (
      <p className="text-muted-foreground rounded-lg border border-dashed p-4 text-center text-sm">
        {dict.schedule.wizard.noTargets}
      </p>
    )
  }

  const selectedSites = availableSites.filter((site) => (value.siteIds ?? []).includes(site.id))
  const selectedGroups = availableGroups.filter((group) => value.groupIds.includes(group.id))
  const selectedPlayers = availablePlayers.filter((player) => value.playerIds.includes(player.id))

  return (
    <div className="space-y-2">
      <div className="relative">
        <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
        <Input
          className="pl-8"
          placeholder={dict.publish.searchTargets}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>
      {selectedSites.length + selectedGroups.length + selectedPlayers.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {selectedSites.map((site) => (
            <Badge key={site.id} variant="default" className="gap-1 pr-1">
              <IconBuildingStore className="size-3" />
              {dict.publish.wholeSite.replace('{name}', site.name)}
              <button
                type="button"
                aria-label={dict.common.delete}
                onClick={() => toggle('siteIds', site.id)}
                className="hover:bg-foreground/10 rounded p-0.5"
              >
                <IconX className="size-3" />
              </button>
            </Badge>
          ))}
          {selectedGroups.map((group) => (
            <Badge key={group.id} variant="secondary" className="gap-1 pr-1">
              <DynamicIcon name={group.icon} className="size-3" />
              {group.name}
              <button
                type="button"
                aria-label={dict.common.delete}
                onClick={() => toggle('groupIds', group.id)}
                className="hover:bg-foreground/10 rounded p-0.5"
              >
                <IconX className="size-3" />
              </button>
            </Badge>
          ))}
          {selectedPlayers.map((player) => (
            <Badge key={player.id} variant="secondary" className="gap-1 pr-1">
              <IconDeviceTv className="size-3" />
              {player.name}
              <button
                type="button"
                aria-label={dict.common.delete}
                onClick={() => toggle('playerIds', player.id)}
                className="hover:bg-foreground/10 rounded p-0.5"
              >
                <IconX className="size-3" />
              </button>
            </Badge>
          ))}
        </div>
      ) : null}
      <div className="max-h-64 space-y-0.5 overflow-y-auto rounded-xl border p-1.5">
        {shownSites.length + shownGroups.length + shownPlayers.length === 0 ? (
          <p className="text-muted-foreground py-6 text-center text-sm">
            {dict.publish.noTargetMatches}
          </p>
        ) : null}
        {shownSites.map((site) => (
          <label
            key={site.id}
            className="hover:bg-muted flex cursor-pointer items-center gap-3 rounded-lg px-2.5 py-2"
          >
            <Checkbox
              checked={(value.siteIds ?? []).includes(site.id)}
              onCheckedChange={() => toggle('siteIds', site.id)}
            />
            <IconBuildingStore className="text-muted-foreground size-4" />
            <span className="text-sm font-medium">
              {dict.publish.wholeSite.replace('{name}', site.name)}
            </span>
            <span className="text-muted-foreground text-xs">
              {fmt(dict.publish.screensCount, { count: site.playerCount })}
            </span>
          </label>
        ))}
        {shownGroups.map((group) => (
          <label
            key={group.id}
            className="hover:bg-muted flex cursor-pointer items-center gap-3 rounded-lg px-2.5 py-2"
          >
            <Checkbox
              checked={value.groupIds.includes(group.id)}
              onCheckedChange={() => toggle('groupIds', group.id)}
            />
            <DynamicIcon name={group.icon} className="text-muted-foreground size-4" />
            <span className="text-sm font-medium">{group.name}</span>
            <span className="text-muted-foreground text-xs">
              {dict.schedule.wizard.group}
              {group.playerCount !== undefined
                ? ` · ${fmt(dict.publish.screensCount, { count: group.playerCount })}`
                : ''}
            </span>
          </label>
        ))}
        {shownPlayers.map((player) => (
          <label
            key={player.id}
            className="hover:bg-muted flex cursor-pointer items-center gap-3 rounded-lg px-2.5 py-2"
          >
            <Checkbox
              checked={value.playerIds.includes(player.id)}
              onCheckedChange={() => toggle('playerIds', player.id)}
            />
            <IconDeviceTv className="text-muted-foreground size-4" />
            <span className="text-sm font-medium">{player.name}</span>
            <OnlineDot online={player.online} className="size-2" />
            {player.groupName ? (
              <span className="text-muted-foreground text-xs">{player.groupName}</span>
            ) : null}
          </label>
        ))}
      </div>
    </div>
  )
}
