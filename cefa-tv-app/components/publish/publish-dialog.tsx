'use client'

import { useMemo, useState } from 'react'
import Link from 'next/link'
import {
  IconAlertTriangle,
  IconArrowUp,
  IconCircleCheck,
  IconDeviceTv,
  IconLoader2,
  IconRocket,
} from '@tabler/icons-react'
import { dayFormatter, describeWhen } from '@/components/schedule/helpers'
import { TargetPicker, targetNames } from '@/components/schedule/target-picker'
import {
  DEFAULT_WHEN,
  WhenPicker,
  whenIsValid,
  whenToFields,
  type WhenValue,
} from '@/components/schedule/when-picker'
import { DeliveryBadge } from '@/components/shared/delivery-state'
import { Button } from '@/components/shadcn-ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import { useDictionary } from '@/contexts/dictionary-context'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useApiResource } from '@/hooks/use-api-resource'
import { useLocalePath } from '@/hooks/use-locale-path'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { expandTargets, findConflicts } from '@/lib/schedule-logic'
import { toast } from '@/lib/toast'
import type { Page, Player, PlayerGroup, Schedule, ScheduleTargets, Site } from '@/types/api'

const DEFAULT_PRIORITY = 10

export interface PublishPlaylist {
  id: string
  name: string
}

function PublishBody({
  playlist,
  initialTargets,
  onClose,
  onPublished,
}: {
  playlist: PublishPlaylist
  initialTargets?: ScheduleTargets
  onClose: () => void
  onPublished?: (schedule: Schedule) => void
}) {
  const dict = useDictionary()
  const { locale } = useDateFormatter()
  const localePath = useLocalePath()
  const [targets, setTargets] = useState<ScheduleTargets>(
    initialTargets ?? { playerIds: [], groupIds: [] }
  )
  const [when, setWhen] = useState<WhenValue>(DEFAULT_WHEN)
  const [priority, setPriority] = useState(DEFAULT_PRIORITY)
  const [name, setName] = useState('')
  const [nameTouched, setNameTouched] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [published, setPublished] = useState<Schedule | null>(null)

  const playersResource = useApiResource<Player[] | Page<Player>>(`${API_V1}/players`, {
    intervalMs: published ? 3000 : undefined,
  })
  const groupsResource = useApiResource<PlayerGroup[] | Page<PlayerGroup>>(`${API_V1}/groups`)
  const sitesResource = useApiResource<Site[] | Page<Site>>(`${API_V1}/sites`)
  const schedulesResource = useApiResource<Schedule[] | Page<Schedule>>(`${API_V1}/schedules`)
  const players = useMemo(() => asList(playersResource.data), [playersResource.data])
  const groups = useMemo(() => asList(groupsResource.data), [groupsResource.data])
  const sites = useMemo(() => asList(sitesResource.data), [sitesResource.data])
  const schedules = useMemo(() => asList(schedulesResource.data), [schedulesResource.data])

  const fields = useMemo(() => whenToFields(when), [when])
  const names = targetNames(targets, players, groups, sites)
  const hasTargets =
    targets.playerIds.length + targets.groupIds.length + (targets.siteIds ?? []).length > 0
  const suggestedName =
    names.length > 0
      ? dict.publish.suggestedName
          .replace('{playlist}', playlist.name)
          .replace('{targets}', names.join(', '))
      : playlist.name
  const shownName = nameTouched ? name : suggestedName
  const finalName = shownName.trim().slice(0, 120)
  const valid = hasTargets && whenIsValid(when) && finalName !== ''

  const conflicts = useMemo(
    () => (valid ? findConflicts({ slot: fields, priority, targets }, schedules, players) : []),
    [valid, fields, priority, targets, schedules, players]
  )
  const highestConflict = conflicts.reduce(
    (top, entry) => Math.max(top, entry.schedule.priority),
    0
  )

  const summary = hasTargets
    ? dict.publish.summary
        .replace('{when}', describeWhen(fields, dict, dayFormatter(locale)))
        .replace('{targets}', names.join(', '))
    : dict.publish.summaryEmpty

  const affected = useMemo(() => {
    const ids = new Set(expandTargets(published?.targets ?? targets, players))
    return players.filter((player) => ids.has(player.id))
  }, [published, targets, players])

  async function publish() {
    setPublishing(true)
    try {
      const created = await api.schedules.create({
        name: finalName,
        playlistId: playlist.id,
        priority,
        startDate: fields.startDate,
        endDate: fields.endDate,
        startTime: fields.startTime,
        endTime: fields.endTime,
        daysOfWeek: fields.daysOfWeek,
        allDay: fields.allDay,
        enabled: true,
        targets,
      })
      setPublished(created)
      onPublished?.(created)
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.saveFailed))
    } finally {
      setPublishing(false)
    }
  }

  if (published) {
    const single = affected.length === 1 ? affected[0] : null
    return (
      <div className="space-y-5">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <IconCircleCheck className="text-success size-5" /> {dict.publish.doneTitle}
          </DialogTitle>
          <DialogDescription>
            {dict.publish.doneDescription.replace('{name}', playlist.name)}
          </DialogDescription>
        </DialogHeader>
        <ul className="divide-y rounded-xl border">
          {affected.map((player) => (
            <li key={player.id} className="flex items-center justify-between gap-3 px-3 py-2.5">
              <span className="flex min-w-0 items-center gap-2 text-sm font-medium">
                <IconDeviceTv className="text-muted-foreground size-4 shrink-0" />
                <span className="truncate">{player.name}</span>
              </span>
              <DeliveryBadge player={player} long />
            </li>
          ))}
          {affected.length === 0 ? (
            <li className="text-muted-foreground px-3 py-4 text-center text-sm">
              {dict.publish.noScreensYet}
            </li>
          ) : null}
        </ul>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {dict.common.close}
          </Button>
          <Button asChild>
            <Link href={localePath(single ? `/players/${single.id}` : '/players')}>
              {dict.publish.viewDelivery}
            </Link>
          </Button>
        </DialogFooter>
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <DialogHeader>
        <DialogTitle>{dict.publish.title.replace('{name}', playlist.name)}</DialogTitle>
        <DialogDescription>{dict.publish.description}</DialogDescription>
      </DialogHeader>

      <section className="space-y-2">
        <Label className="text-base">{dict.publish.whereTitle}</Label>
        <TargetPicker players={players} groups={groups} value={targets} onChange={setTargets} />
      </section>

      <section className="space-y-2">
        <Label className="text-base">{dict.publish.whenTitle}</Label>
        <WhenPicker value={when} onChange={setWhen} />
      </section>

      <section className="space-y-2">
        <Label htmlFor="publish-name" className="text-base">
          {dict.publish.nameLabel}
        </Label>
        <Input
          id="publish-name"
          required
          maxLength={120}
          value={shownName}
          onChange={(event) => {
            setName(event.target.value)
            setNameTouched(true)
          }}
        />
      </section>

      <div className="bg-muted/50 rounded-xl border px-4 py-3 text-sm">
        <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">
          {dict.publish.summaryLabel}
        </p>
        <p className="mt-1 font-medium">{summary}</p>
      </div>

      {conflicts.length > 0 ? (
        <div className="border-warning/40 bg-warning/10 space-y-2 rounded-xl border px-4 py-3 text-sm">
          <p className="text-warning flex items-center gap-2 font-medium">
            <IconAlertTriangle className="size-4" /> {dict.publish.conflictTitle}
          </p>
          <ul className="space-y-1">
            {conflicts.map((conflict) => (
              <li key={conflict.schedule.id}>
                {dict.publish.conflictItem
                  .replace('{name}', conflict.schedule.playlistName ?? conflict.schedule.name)
                  .replace('{priority}', String(conflict.schedule.priority))
                  .replace(
                    '{winner}',
                    conflict.winner === 'existing'
                      ? (conflict.schedule.playlistName ?? conflict.schedule.name)
                      : playlist.name
                  )}
              </li>
            ))}
          </ul>
          {conflicts.some((conflict) => conflict.winner === 'existing') ? (
            <Button
              size="sm"
              variant="outline"
              onClick={() => setPriority(Math.max(priority, highestConflict) + 1)}
            >
              <IconArrowUp /> {dict.publish.raisePriority}
            </Button>
          ) : null}
        </div>
      ) : null}

      <DialogFooter>
        <Button variant="outline" onClick={onClose}>
          {dict.common.cancel}
        </Button>
        <Button disabled={!valid || publishing} onClick={() => void publish()}>
          {publishing ? <IconLoader2 className="animate-spin" /> : <IconRocket />}
          {dict.publish.publish}
        </Button>
      </DialogFooter>
    </div>
  )
}

export function PublishDialog({
  open,
  onOpenChange,
  playlist,
  initialTargets,
  onPublished,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  playlist: PublishPlaylist | null
  initialTargets?: ScheduleTargets
  onPublished?: (schedule: Schedule) => void
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        {open && playlist ? (
          <PublishBody
            playlist={playlist}
            initialTargets={initialTargets}
            onClose={() => onOpenChange(false)}
            onPublished={onPublished}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  )
}
