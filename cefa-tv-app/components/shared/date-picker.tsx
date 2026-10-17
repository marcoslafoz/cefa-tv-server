'use client'

import { useState } from 'react'
import type { DateRange } from 'react-day-picker'
import { enGB, es, pl } from 'react-day-picker/locale'
import { IconCalendar, IconClock } from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import { Calendar } from '@/components/shadcn-ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/shadcn-ui/popover'
import { ToggleGroup, ToggleGroupItem } from '@/components/shadcn-ui/toggle-group'
import { useDictionary } from '@/contexts/dictionary-context'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { cn, parseIsoDate, toIsoDate } from '@/lib/utils'

function pickerLocale(intlLocale: string) {
  if (intlLocale.startsWith('pl')) return pl
  if (intlLocale.startsWith('en')) return enGB
  return es
}

function parseValue(value: string | null | undefined): Date | undefined {
  return value ? parseIsoDate(value) : undefined
}

function useDateLabel() {
  const { locale } = useDateFormatter()
  return (value: string | null | undefined) =>
    value
      ? new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(parseIsoDate(value))
      : ''
}

function Footer({
  onToday,
  onClear,
  canClear,
}: {
  onToday: () => void
  onClear: () => void
  canClear: boolean
}) {
  const dict = useDictionary()
  return (
    <div className="flex items-center justify-between border-t px-3 py-2">
      <Button type="button" size="sm" variant="ghost" onClick={onToday}>
        {dict.datePicker.today}
      </Button>
      <Button type="button" size="sm" variant="ghost" disabled={!canClear} onClick={onClear}>
        {dict.datePicker.clear}
      </Button>
    </div>
  )
}

export function DatePicker({
  id,
  value,
  onChange,
  min,
  max,
  placeholder,
  className,
}: {
  id?: string
  value: string
  onChange: (value: string) => void
  min?: string
  max?: string
  placeholder?: string
  className?: string
}) {
  const dict = useDictionary()
  const { locale } = useDateFormatter()
  const label = useDateLabel()
  const [open, setOpen] = useState(false)
  const selected = parseValue(value)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          className={cn('w-full justify-start gap-2 font-normal', className)}
        >
          <IconCalendar className="text-muted-foreground size-4" />
          {value ? (
            <span className="truncate">{label(value)}</span>
          ) : (
            <span className="text-muted-foreground truncate">
              {placeholder ?? dict.datePicker.placeholder}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto p-0">
        <Calendar
          mode="single"
          locale={pickerLocale(locale)}
          selected={selected}
          defaultMonth={selected}
          startMonth={min ? parseIsoDate(min) : undefined}
          endMonth={max ? parseIsoDate(max) : undefined}
          disabled={[
            ...(min ? [{ before: parseIsoDate(min) }] : []),
            ...(max ? [{ after: parseIsoDate(max) }] : []),
          ]}
          onSelect={(date) => {
            onChange(date ? toIsoDate(date) : '')
            setOpen(false)
          }}
        />
        <Footer
          canClear={value !== ''}
          onToday={() => {
            onChange(toIsoDate(new Date()))
            setOpen(false)
          }}
          onClear={() => {
            onChange('')
            setOpen(false)
          }}
        />
      </PopoverContent>
    </Popover>
  )
}

type RangeMode = 'between' | 'from' | 'until'

function modeOf(start: string, end: string): RangeMode {
  if (start && !end) return 'from'
  if (!start && end) return 'until'
  return 'between'
}

export function DateRangePicker({
  id,
  start,
  end,
  onChange,
  min,
  max,
  className,
}: {
  id?: string
  start: string
  end: string
  onChange: (range: { start: string; end: string }) => void
  min?: string
  max?: string
  className?: string
}) {
  const dict = useDictionary()
  const picker = dict.datePicker
  const { locale } = useDateFormatter()
  const label = useDateLabel()
  const [open, setOpen] = useState(false)
  const [months, setMonths] = useState(2)
  const [mode, setMode] = useState<RangeMode>(() => modeOf(start, end))
  const [pendingStart, setPendingStart] = useState(false)
  const selected: DateRange | undefined =
    start || end ? { from: parseValue(start), to: parseValue(end) } : undefined

  function changeMode(next: RangeMode) {
    setMode(next)
    setPendingStart(false)
    if (next === 'from') onChange({ start: start || end, end: '' })
    else if (next === 'until') onChange({ start: '', end: end || start })
  }

  function pickDay(day: Date) {
    const iso = toIsoDate(day)
    if (mode === 'from') return onChange({ start: iso, end: '' })
    if (mode === 'until') return onChange({ start: '', end: iso })
    if (pendingStart && start && iso >= start) {
      setPendingStart(false)
      return onChange({ start, end: iso })
    }
    setPendingStart(true)
    onChange({ start: iso, end: '' })
  }

  const hint =
    mode === 'from' ? picker.pickStart : mode === 'until' ? picker.pickEnd : picker.pickStartThenEnd
  const triggerLabel = !(start || end)
    ? null
    : mode === 'from' || (start && !end)
      ? `${picker.from} ${label(start)} · ${picker.openEnd}`
      : mode === 'until' || (!start && end)
        ? `${picker.until} ${label(end)} · ${picker.openStart}`
        : `${label(start)} - ${label(end)}`

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        if (next) {
          setMonths(window.innerWidth >= 640 ? 2 : 1)
          setMode(modeOf(start, end))
          setPendingStart(false)
        }
        setOpen(next)
      }}
    >
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          className={cn('w-full justify-start gap-2 font-normal', className)}
        >
          <IconCalendar className="text-muted-foreground size-4" />
          {triggerLabel ? (
            <span className="truncate">{triggerLabel}</span>
          ) : (
            <span className="text-muted-foreground truncate">{picker.rangePlaceholder}</span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto max-w-[calc(100vw-2rem)] p-0">
        <div className="space-y-2 border-b p-3">
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            value={mode}
            onValueChange={(next) => next && changeMode(next as RangeMode)}
            className="flex-wrap"
          >
            <ToggleGroupItem value="between">{picker.modeBetween}</ToggleGroupItem>
            <ToggleGroupItem value="from">{picker.modeFrom}</ToggleGroupItem>
            <ToggleGroupItem value="until">{picker.modeUntil}</ToggleGroupItem>
          </ToggleGroup>
          <p className="text-muted-foreground text-xs">{hint}</p>
        </div>
        <Calendar
          mode="range"
          numberOfMonths={months}
          locale={pickerLocale(locale)}
          selected={selected}
          defaultMonth={selected?.from ?? selected?.to}
          startMonth={min ? parseIsoDate(min) : undefined}
          endMonth={max ? parseIsoDate(max) : undefined}
          disabled={[
            ...(min ? [{ before: parseIsoDate(min) }] : []),
            ...(max ? [{ after: parseIsoDate(max) }] : []),
          ]}
          onDayClick={pickDay}
        />
        <Footer
          canClear={start !== '' || end !== ''}
          onToday={() => {
            const today = toIsoDate(new Date())
            if (mode === 'from') onChange({ start: today, end: '' })
            else if (mode === 'until') onChange({ start: '', end: today })
            else onChange({ start: today, end: today })
          }}
          onClear={() => {
            setMode('between')
            setPendingStart(false)
            onChange({ start: '', end: '' })
          }}
        />
      </PopoverContent>
    </Popover>
  )
}

const HOURS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'))
const MINUTES = Array.from({ length: 12 }, (_, step) => String(step * 5).padStart(2, '0'))

export function TimePicker({
  id,
  value,
  onChange,
  className,
}: {
  id?: string
  value: string
  onChange: (value: string) => void
  className?: string
}) {
  const dict = useDictionary()
  const [open, setOpen] = useState(false)
  const [hour = '08', minute = '00'] = value ? value.split(':') : []

  function column(items: string[], current: string, apply: (item: string) => void, label: string) {
    return (
      <div
        role="listbox"
        aria-label={label}
        className="flex max-h-56 flex-col gap-0.5 overflow-y-auto p-1"
      >
        {items.map((item) => (
          <Button
            key={item}
            type="button"
            role="option"
            aria-selected={item === current}
            size="sm"
            variant={item === current ? 'default' : 'ghost'}
            className="w-12 tabular-nums"
            ref={(node) => {
              if (node && item === current && open) node.scrollIntoView({ block: 'center' })
            }}
            onClick={() => apply(item)}
          >
            {item}
          </Button>
        ))}
      </div>
    )
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          className={cn('w-full justify-start gap-2 font-normal tabular-nums', className)}
        >
          <IconClock className="text-muted-foreground size-4" />
          {value || dict.datePicker.timePlaceholder}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="flex w-auto gap-1 p-1">
        {column(HOURS, hour, (item) => onChange(`${item}:${minute}`), dict.datePicker.hours)}
        {column(
          MINUTES,
          minute,
          (item) => {
            onChange(`${hour}:${item}`)
            setOpen(false)
          },
          dict.datePicker.minutes
        )}
      </PopoverContent>
    </Popover>
  )
}
