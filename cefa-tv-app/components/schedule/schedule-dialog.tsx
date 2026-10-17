'use client'

import { useEffect, useState } from 'react'
import { IconLoader2, IconTrash } from '@tabler/icons-react'
import { ALL_DAYS } from '@/components/schedule/helpers'
import { TargetPicker } from '@/components/schedule/target-picker'
import {
  DEFAULT_WHEN,
  WhenPicker,
  fieldsToWhen,
  whenIsValid,
  whenToFields,
  type WhenValue,
} from '@/components/schedule/when-picker'
import { AdvancedOptions } from '@/components/shared/advanced-options'
import { NumberField } from '@/components/shared/number-field'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import { Switch } from '@/components/shadcn-ui/switch'
import { useDictionary } from '@/contexts/dictionary-context'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/api-client'
import { toast } from '@/lib/toast'
import type { Player, PlayerGroup, Playlist, Schedule, ScheduleInput } from '@/types/api'

export interface ScheduleDraft {
  input: ScheduleInput
}

function emptyInput(playlistId: string | undefined): ScheduleInput {
  return {
    name: '',
    playlistId,
    priority: 10,
    startDate: null,
    endDate: null,
    startTime: '08:00',
    endTime: '20:00',
    daysOfWeek: ALL_DAYS,
    allDay: false,
    enabled: true,
    targets: { playerIds: [], groupIds: [] },
  }
}

export function ScheduleDialog({
  scheduleId,
  draft,
  players,
  groups,
  playlists,
  onClose,
  onSaved,
}: {
  scheduleId: string | null
  draft: ScheduleDraft | null
  players: Player[]
  groups: PlayerGroup[]
  playlists: Playlist[]
  onClose: () => void
  onSaved: () => void
}) {
  const dict = useDictionary()
  const initial = draft?.input ?? emptyInput(playlists[0]?.id)
  const [input, setInput] = useState<ScheduleInput>(initial)
  const [when, setWhen] = useState<WhenValue>(
    draft ? fieldsToWhen(initial) : { ...DEFAULT_WHEN, mode: 'slot' }
  )
  const [hiddenPlaylistName, setHiddenPlaylistName] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [loadedExisting, setLoadedExisting] = useState(false)
  const loading = scheduleId !== null && !loadedExisting

  useEffect(() => {
    if (!scheduleId) return
    api.schedules
      .get(scheduleId)
      .then((schedule: Schedule) => {
        setInput({
          name: schedule.name,
          playlistId: schedule.playlistId,
          priority: schedule.priority,
          startDate: schedule.startDate,
          endDate: schedule.endDate,
          startTime: schedule.startTime ?? '08:00',
          endTime: schedule.endTime ?? '20:00',
          daysOfWeek: schedule.daysOfWeek,
          allDay: schedule.allDay,
          enabled: schedule.enabled,
          targets: schedule.targets,
        })
        setWhen(fieldsToWhen(schedule))
        setHiddenPlaylistName(schedule.playlistHidden ? (schedule.playlistName ?? '') : null)
        setLoadedExisting(true)
      })
      .catch(() => toast.error(dict.schedule.loadFailed))
  }, [scheduleId, dict.schedule.loadFailed])

  function patch(update: Partial<ScheduleInput>) {
    setInput((current) => ({ ...current, ...update }))
  }

  const hasTargets =
    input.targets.playerIds.length +
      input.targets.groupIds.length +
      (input.targets.siteIds ?? []).length >
    0
  const hasContent = Boolean(input.playlistId ?? input.assetId)
  const valid = input.name.trim() !== '' && hasTargets && hasContent && whenIsValid(when)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    try {
      const payload: ScheduleInput = { ...input, name: input.name.trim(), ...whenToFields(when) }
      if (scheduleId) await api.schedules.update(scheduleId, payload)
      else await api.schedules.create(payload)
      toast.success(dict.schedule.saved)
      onSaved()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.saveFailed))
    } finally {
      setSaving(false)
    }
  }

  async function remove() {
    if (!scheduleId) return
    try {
      await api.schedules.remove(scheduleId)
      onSaved()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.schedule.deleteFailed))
    }
  }

  return (
    <Dialog open onOpenChange={(value) => !value && onClose()}>
      <DialogContent className="flex max-h-[90vh] flex-col gap-0 p-0 sm:max-w-xl">
        <form onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
          <DialogHeader className="p-6 pb-3">
            <DialogTitle>
              {scheduleId ? dict.schedule.editTitle : dict.schedule.newTitle}
            </DialogTitle>
            <DialogDescription>{dict.schedule.dialogDescription}</DialogDescription>
          </DialogHeader>
          <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-2">
            {loading ? (
              <div className="flex justify-center py-10">
                <IconLoader2 className="text-muted-foreground animate-spin" />
              </div>
            ) : (
              <>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="schedule-name">{dict.common.name}</Label>
                    <Input
                      id="schedule-name"
                      required
                      value={input.name}
                      onChange={(event) => patch({ name: event.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>{dict.schedule.contentLabel}</Label>
                    {hiddenPlaylistName !== null || input.assetId ? (
                      <p className="bg-muted/50 rounded-md border px-3 py-2 text-sm">
                        {hiddenPlaylistName ?? input.name}
                      </p>
                    ) : (
                      <Select
                        value={input.playlistId ?? ''}
                        onValueChange={(value) => patch({ playlistId: value, assetId: undefined })}
                      >
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder={dict.schedule.selectPlaylist} />
                        </SelectTrigger>
                        <SelectContent>
                          {playlists.map((playlist) => (
                            <SelectItem key={playlist.id} value={playlist.id}>
                              {playlist.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  </div>
                </div>

                <div className="space-y-2">
                  <Label>{dict.schedule.wizard.steps.when}</Label>
                  <WhenPicker value={when} onChange={setWhen} />
                </div>

                <div className="space-y-2">
                  <Label>{dict.schedule.targets}</Label>
                  <TargetPicker
                    players={players}
                    groups={groups}
                    value={input.targets}
                    onChange={(targets) => patch({ targets })}
                  />
                </div>

                <AdvancedOptions title={dict.schedule.wizard.advanced}>
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="schedule-priority">{dict.schedule.priority}</Label>
                      <NumberField
                        id="schedule-priority"
                        min={0}
                        max={100}
                        value={input.priority}
                        onValueChange={(priority) => patch({ priority })}
                      />
                    </div>
                    <div className="flex items-center justify-between self-end rounded-lg border px-3 py-2">
                      <Label htmlFor="schedule-enabled">{dict.schedule.enabled}</Label>
                      <Switch
                        id="schedule-enabled"
                        checked={input.enabled}
                        onCheckedChange={(value) => patch({ enabled: value })}
                      />
                    </div>
                    <p className="text-muted-foreground col-span-2 text-xs">
                      {dict.schedule.priorityHint}
                    </p>
                  </div>
                </AdvancedOptions>
              </>
            )}
          </div>
          <DialogFooter className="border-t p-4 sm:justify-between">
            {scheduleId ? (
              <Button type="button" variant="destructive" onClick={() => void remove()}>
                <IconTrash /> {dict.common.delete}
              </Button>
            ) : (
              <span />
            )}
            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={onClose}>
                {dict.common.cancel}
              </Button>
              <Button type="submit" disabled={saving || loading || !valid}>
                {saving ? <IconLoader2 className="animate-spin" /> : null}
                {dict.common.save}
              </Button>
            </div>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
