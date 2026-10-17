'use client'

import { useState } from 'react'
import { IconLoader2, IconPlayerPlay } from '@tabler/icons-react'
import { ALL_DAYS } from '@/components/schedule/helpers'
import { Button } from '@/components/shadcn-ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/shadcn-ui/dialog'
import { useDictionary } from '@/contexts/dictionary-context'
import { api } from '@/lib/api'
import { apiErrorMessage } from '@/lib/api-client'
import { defaultDurationSec } from '@/lib/media'
import { toast } from '@/lib/toast'
import { cn, isoWeekday, minutesToTime, toIsoDate } from '@/lib/utils'
import type { MediaAsset, ScheduleInput } from '@/types/api'

const PLAY_NOW_PRIORITY = 90
const LAST_MINUTE_OF_DAY = 24 * 60 - 1

const DURATIONS = [
  { id: 'm10', minutes: 10 },
  { id: 'm30', minutes: 30 },
  { id: 'h1', minutes: 60 },
  { id: 'h2', minutes: 120 },
  { id: 'forever', minutes: null },
] as const

export function PlayNowDialog({
  asset,
  targetName,
  targets,
  onClose,
  onStarted,
}: {
  asset: MediaAsset
  targetName: string
  targets: ScheduleInput['targets']
  onClose: () => void
  onStarted: () => void
}) {
  const dict = useDictionary()
  const [duration, setDuration] = useState<(typeof DURATIONS)[number]['id']>('m30')
  const [saving, setSaving] = useState(false)

  async function start() {
    setSaving(true)
    const option = DURATIONS.find((entry) => entry.id === duration)
    const now = new Date()
    const today = toIsoDate(now)
    const startMinute = now.getHours() * 60 + now.getMinutes()
    const endMinute = Math.min(startMinute + (option?.minutes ?? 0), LAST_MINUTE_OF_DAY)
    const timed = option?.minutes != null
    const input: ScheduleInput = {
      name: asset.name,
      assetId: asset.id,
      durationSec: defaultDurationSec(asset.type, asset.durationMs),
      priority: PLAY_NOW_PRIORITY,
      startDate: timed ? today : null,
      endDate: timed ? today : null,
      startTime: timed ? minutesToTime(startMinute) : null,
      endTime: timed ? minutesToTime(endMinute) : null,
      daysOfWeek: timed ? [isoWeekday(now)] : ALL_DAYS,
      allDay: !timed,
      enabled: true,
      targets,
    }
    try {
      await api.schedules.create(input)
      toast.success(dict.content.playNowStarted)
      onStarted()
    } catch (err) {
      toast.error(apiErrorMessage(err, dict.content.playNowFailed))
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{dict.content.playNowTitle}</DialogTitle>
          <DialogDescription>
            {dict.content.playNowDescription
              .replace('{name}', asset.name)
              .replace('{target}', targetName)}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <p className="text-sm font-medium">{dict.content.playNowFor}</p>
          <div className="flex flex-wrap gap-1.5">
            {DURATIONS.map((option) => (
              <button
                key={option.id}
                type="button"
                aria-pressed={duration === option.id}
                onClick={() => setDuration(option.id)}
                className={cn(
                  'h-8 rounded-lg border px-3 text-sm font-medium transition-colors',
                  duration === option.id
                    ? 'bg-primary text-primary-foreground border-primary'
                    : 'hover:bg-muted'
                )}
              >
                {dict.content.playNowDurations[option.id]}
              </button>
            ))}
          </div>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>
            {dict.common.cancel}
          </Button>
          <Button type="button" disabled={saving} onClick={() => void start()}>
            {saving ? <IconLoader2 className="animate-spin" /> : <IconPlayerPlay />}
            {dict.content.playNow}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
