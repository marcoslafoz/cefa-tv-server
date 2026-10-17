'use client'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { useState } from 'react'
import { IconCalendarPlus, IconCheck, IconPencil, IconTrash } from '@tabler/icons-react'
import { dayFormatter, describeWhen, scheduleToInput } from '@/components/schedule/helpers'
import { ScheduleWizard } from '@/components/schedule/schedule-wizard'
import { ThumbRow, resolveThumbItems } from '@/components/schedule/thumb-strip'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { Input } from '@/components/shadcn-ui/input'
import { Switch } from '@/components/shadcn-ui/switch'
import { useDictionary } from '@/contexts/dictionary-context'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useApiResource } from '@/hooks/use-api-resource'
import { api, asList } from '@/lib/api'
import { API_V1, apiErrorMessage } from '@/lib/api-client'
import { toast } from '@/lib/toast'
import type { Page, Playlist, Schedule } from '@/types/api'

export function TargetSchedules({
  playerId,
  groupId,
  siteId,
  canEdit,
}: {
  playerId?: string
  groupId: string | null
  siteId?: string | null
  canEdit: boolean
}) {
  const dict = useDictionary()
  const { locale } = useDateFormatter()
  const resource = useApiResource<Schedule[] | Page<Schedule>>(`${API_V1}/schedules`, {
    intervalMs: 10000,
  })
  const playlists = useApiResource<Playlist[] | Page<Playlist>>(`${API_V1}/playlists`)
  const [creating, setCreating] = useState(false)
  const [renaming, setRenaming] = useState<string | null>(null)
  const [draftName, setDraftName] = useState('')

  const relevant = asList(resource.data).filter(
    (schedule) =>
      (playerId !== undefined && schedule.targets.playerIds.includes(playerId)) ||
      (groupId !== null && schedule.targets.groupIds.includes(groupId)) ||
      (Boolean(siteId) && (schedule.targets.siteIds ?? []).includes(siteId as string))
  )

  async function toggle(schedule: Schedule, enabled: boolean) {
    try {
      await api.schedules.update(schedule.id, {
        name: schedule.name,
        playlistId: schedule.playlistId,
        priority: schedule.priority,
        startDate: schedule.startDate,
        endDate: schedule.endDate,
        startTime: schedule.startTime,
        endTime: schedule.endTime,
        daysOfWeek: schedule.daysOfWeek,
        allDay: schedule.allDay,
        enabled,
        targets: schedule.targets,
      })
      void resource.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.saveFailed))
    }
  }

  function startRename(schedule: Schedule) {
    setDraftName(schedule.name)
    setRenaming(schedule.id)
  }

  async function rename(schedule: Schedule) {
    try {
      await api.schedules.update(schedule.id, {
        ...scheduleToInput(schedule),
        name: draftName.trim(),
      })
      setRenaming(null)
      void resource.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.saveFailed))
    }
  }

  async function remove(schedule: Schedule) {
    try {
      await api.schedules.remove(schedule.id)
      toast.success(dict.content.scheduleRemoved)
      void resource.reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.deleteFailed))
    }
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>
          {playerId !== undefined ? dict.content.schedulesTitle : dict.content.schedulesTitleGroup}
        </CardTitle>
        {canEdit ? (
          <Button size="sm" variant="outline" onClick={() => setCreating(true)}>
            <IconCalendarPlus /> {dict.content.newSchedule}
          </Button>
        ) : null}
      </CardHeader>
      <CardContent>
        {relevant.length === 0 ? (
          <p className="text-muted-foreground py-4 text-center text-sm">
            {dict.content.noSchedules}
          </p>
        ) : (
          <ul className="divide-y">
            {relevant.map((schedule) => (
              <li key={schedule.id} className="flex items-center justify-between gap-3 py-2.5">
                <div className="flex min-w-0 items-center gap-3">
                  <ThumbRow
                    max={3}
                    items={resolveThumbItems(
                      schedule.thumbnailUrls,
                      asList(playlists.data).find((entry) => entry.id === schedule.playlistId)
                    )}
                    className="hidden shrink-0 sm:flex"
                  />
                  <div className="min-w-0">
                    {renaming === schedule.id ? (
                      <form
                        className="flex items-center gap-1"
                        onSubmit={(event) => {
                          event.preventDefault()
                          void rename(schedule)
                        }}
                      >
                        <Input
                          autoFocus
                          required
                          maxLength={120}
                          className="h-7"
                          value={draftName}
                          onChange={(event) => setDraftName(event.target.value)}
                          onKeyDown={(event) => event.key === 'Escape' && setRenaming(null)}
                        />
                        <Button
                          size="icon-sm"
                          type="submit"
                          variant="ghost"
                          aria-label={dict.common.save}
                        >
                          <IconCheck />
                        </Button>
                      </form>
                    ) : (
                      <div className="flex min-w-0 items-center gap-1">
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <p
                              className="truncate text-sm font-medium"
                              onDoubleClick={() => canEdit && startRename(schedule)}
                            >
                              {schedule.name}
                            </p>
                          </TooltipTrigger>
                          <TooltipContent>{schedule.name}</TooltipContent>
                        </Tooltip>
                        {canEdit ? (
                          <Button
                            size="icon-sm"
                            variant="ghost"
                            className="size-6 shrink-0"
                            aria-label={dict.schedule.renameSchedule}
                            title={dict.schedule.renameSchedule}
                            onClick={() => startRename(schedule)}
                          >
                            <IconPencil />
                          </Button>
                        ) : null}
                      </div>
                    )}
                    <p className="text-muted-foreground truncate text-xs">
                      {schedule.allDay && schedule.daysOfWeek.length === 7 && !schedule.startDate
                        ? dict.content.always
                        : describeWhen(schedule, dict, dayFormatter(locale))}
                    </p>
                  </div>
                </div>
                {canEdit ? (
                  <div className="flex items-center gap-2">
                    <Switch
                      checked={schedule.enabled}
                      aria-label={dict.content.enabled}
                      onCheckedChange={(value) => void toggle(schedule, value)}
                    />
                    <Button
                      size="icon-sm"
                      variant="ghost"
                      aria-label={dict.content.removeSchedule}
                      onClick={() => void remove(schedule)}
                    >
                      <IconTrash />
                    </Button>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
      {creating ? (
        <ScheduleWizard
          open
          initial={{
            targets:
              playerId !== undefined
                ? { playerIds: [playerId], groupIds: [] }
                : { playerIds: [], groupIds: groupId ? [groupId] : [] },
          }}
          onClose={() => setCreating(false)}
          onPublished={() => {
            setCreating(false)
            void resource.reload()
          }}
        />
      ) : null}
    </Card>
  )
}
