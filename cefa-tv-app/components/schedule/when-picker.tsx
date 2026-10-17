'use client'

import { IconCalendarEvent, IconCalendarWeek, IconInfinity } from '@tabler/icons-react'
import { ALL_DAYS, type WhenFields } from '@/components/schedule/helpers'
import { DateRangePicker, TimePicker } from '@/components/shared/date-picker'
import { Label } from '@/components/shadcn-ui/label'
import { useDictionary } from '@/contexts/dictionary-context'
import { cn } from '@/lib/utils'

export interface WhenValue {
  mode: 'always' | 'slot' | 'range'
  days: number[]
  startTime: string
  endTime: string
  startDate: string
  endDate: string
}

export const DEFAULT_WHEN: WhenValue = {
  mode: 'always',
  days: ALL_DAYS,
  startTime: '08:00',
  endTime: '20:00',
  startDate: '',
  endDate: '',
}

export function whenToFields(value: WhenValue): WhenFields {
  if (value.mode === 'always') {
    return {
      allDay: true,
      startTime: null,
      endTime: null,
      daysOfWeek: ALL_DAYS,
      startDate: null,
      endDate: null,
    }
  }
  if (value.mode === 'range') {
    return {
      allDay: true,
      startTime: null,
      endTime: null,
      daysOfWeek: ALL_DAYS,
      startDate: value.startDate || null,
      endDate: value.endDate || null,
    }
  }
  return {
    allDay: false,
    startTime: value.startTime,
    endTime: value.endTime,
    daysOfWeek: value.days,
    startDate: value.startDate || null,
    endDate: value.endDate || null,
  }
}

export function fieldsToWhen(fields: WhenFields): WhenValue {
  const fullDays = fields.allDay && fields.daysOfWeek.length === ALL_DAYS.length
  const always = fullDays && !fields.startDate && !fields.endDate
  const range = fullDays && !always
  return {
    mode: always ? 'always' : range ? 'range' : 'slot',
    days: fields.daysOfWeek.length > 0 ? fields.daysOfWeek : ALL_DAYS,
    startTime: fields.startTime ?? DEFAULT_WHEN.startTime,
    endTime: fields.endTime ?? DEFAULT_WHEN.endTime,
    startDate: fields.startDate ?? '',
    endDate: fields.endDate ?? '',
  }
}

export function whenIsValid(value: WhenValue): boolean {
  if (value.mode === 'always') return true
  if (value.mode === 'range') {
    return (
      (value.startDate !== '' || value.endDate !== '') &&
      (value.startDate === '' || value.endDate === '' || value.startDate <= value.endDate)
    )
  }
  return value.days.length > 0 && value.startTime !== '' && value.endTime !== ''
}

const PRESETS: Array<{ key: 'everyDay' | 'weekdays' | 'weekend'; days: number[] }> = [
  { key: 'everyDay', days: ALL_DAYS },
  { key: 'weekdays', days: [1, 2, 3, 4, 5] },
  { key: 'weekend', days: [6, 7] },
]

function sameDays(left: number[], right: number[]): boolean {
  return left.length === right.length && right.every((day) => left.includes(day))
}

export function WhenPicker({
  value,
  onChange,
}: {
  value: WhenValue
  onChange: (value: WhenValue) => void
}) {
  const dict = useDictionary()
  const wizard = dict.schedule.wizard

  function patch(update: Partial<WhenValue>) {
    onChange({ ...value, ...update })
  }

  function toggleDay(day: number) {
    patch({
      days: value.days.includes(day)
        ? value.days.filter((entry) => entry !== day)
        : [...value.days, day].sort(),
    })
  }

  const modes = [
    { id: 'always' as const, icon: IconInfinity, label: wizard.always, hint: wizard.alwaysHint },
    { id: 'slot' as const, icon: IconCalendarWeek, label: wizard.slot, hint: wizard.slotHint },
    { id: 'range' as const, icon: IconCalendarEvent, label: wizard.range, hint: wizard.rangeHint },
  ]

  return (
    <div className="space-y-4">
      <div className="grid gap-2 sm:grid-cols-3">
        {modes.map((mode) => (
          <button
            key={mode.id}
            type="button"
            aria-pressed={value.mode === mode.id}
            onClick={() =>
              patch(
                value.mode === 'range' && mode.id !== 'range'
                  ? { mode: mode.id, startDate: '', endDate: '' }
                  : { mode: mode.id }
              )
            }
            className={cn(
              'flex items-start gap-3 rounded-xl border p-3 text-left transition-colors',
              value.mode === mode.id
                ? 'border-primary bg-primary/5 ring-primary/30 ring-2'
                : 'hover:bg-muted/50'
            )}
          >
            <span className="bg-primary/10 text-primary flex size-9 shrink-0 items-center justify-center rounded-lg">
              <mode.icon className="size-5" />
            </span>
            <span>
              <span className="block text-sm font-medium">{mode.label}</span>
              <span className="text-muted-foreground block text-xs">{mode.hint}</span>
            </span>
          </button>
        ))}
      </div>

      {value.mode === 'slot' ? (
        <div className="space-y-4 rounded-xl border p-4">
          <div className="space-y-2">
            <Label>{dict.schedule.days}</Label>
            <div className="flex flex-wrap gap-1.5">
              {PRESETS.map((preset) => (
                <button
                  key={preset.key}
                  type="button"
                  onClick={() => patch({ days: preset.days })}
                  className={cn(
                    'h-7 rounded-full border px-3 text-xs font-medium transition-colors',
                    sameDays(value.days, preset.days)
                      ? 'bg-primary text-primary-foreground border-primary'
                      : 'hover:bg-muted'
                  )}
                >
                  {wizard[preset.key]}
                </button>
              ))}
            </div>
            <div className="flex flex-wrap gap-1.5">
              {ALL_DAYS.map((day) => {
                const active = value.days.includes(day)
                return (
                  <button
                    key={day}
                    type="button"
                    aria-pressed={active}
                    onClick={() => toggleDay(day)}
                    className={cn(
                      'h-8 min-w-11 rounded-lg border px-2 text-xs font-medium transition-colors',
                      active
                        ? 'bg-primary text-primary-foreground border-primary'
                        : 'hover:bg-muted'
                    )}
                  >
                    {dict.schedule.weekdaysShort[day - 1]}
                  </button>
                )
              })}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="when-start-time">{dict.schedule.startTime}</Label>
              <TimePicker
                id="when-start-time"
                value={value.startTime}
                onChange={(startTime) => patch({ startTime })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="when-end-time">{dict.schedule.endTime}</Label>
              <TimePicker
                id="when-end-time"
                value={value.endTime}
                onChange={(endTime) => patch({ endTime })}
              />
            </div>
          </div>
        </div>
      ) : null}
      {value.mode === 'range' ? (
        <div className="space-y-2 rounded-xl border p-4">
          <Label htmlFor="when-range">{dict.schedule.dateRange}</Label>
          <DateRangePicker
            id="when-range"
            start={value.startDate}
            end={value.endDate}
            onChange={(range) => patch({ startDate: range.start, endDate: range.end })}
          />
        </div>
      ) : null}
    </div>
  )
}
