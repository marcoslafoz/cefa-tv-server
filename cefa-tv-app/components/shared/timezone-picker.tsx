'use client'

import { useMemo, useState } from 'react'
import { IconCheck, IconChevronDown, IconWorld } from '@tabler/icons-react'
import { Button } from '@/components/shadcn-ui/button'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/shadcn-ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/shadcn-ui/popover'
import { useDictionary } from '@/contexts/dictionary-context'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import {
  POPULAR_ZONES,
  groupByRegion,
  listZones,
  zoneOffsetLabel,
  zoneSearchText,
  zoneTime,
  type ZoneInfo,
} from '@/lib/timezones'
import { cn } from '@/lib/utils'

function normalize(value: string) {
  return value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}

function ZoneRow({
  zone,
  selected,
  locale,
  now,
}: {
  zone: ZoneInfo
  selected: boolean
  locale: string
  now: Date
}) {
  return (
    <>
      <IconCheck className={cn('size-4', selected ? 'opacity-100' : 'opacity-0')} />
      <span className="text-muted-foreground w-20 shrink-0 text-xs tabular-nums">
        ({zone.offsetLabel})
      </span>
      <span className="min-w-0 flex-1 truncate">
        {zone.id}
        {zone.country ? <span className="text-muted-foreground"> · {zone.country}</span> : null}
      </span>
      <span className="text-muted-foreground text-xs tabular-nums">
        {zoneTime(zone.id, locale, now)}
      </span>
    </>
  )
}

export function TimezonePicker({
  id,
  value,
  onChange,
}: {
  id?: string
  value: string
  onChange: (zone: string) => void
}) {
  const dict = useDictionary()
  const { locale } = useDateFormatter()
  const [open, setOpen] = useState(false)
  const [now, setNow] = useState(() => new Date())
  const zones = useMemo(() => listZones(locale, now), [locale, now])
  const popular = useMemo(
    () =>
      POPULAR_ZONES.map((zone) => zones.find((entry) => entry.id === zone)).filter(
        (zone): zone is ZoneInfo => zone !== undefined
      ),
    [zones]
  )
  const regions = useMemo(() => groupByRegion(zones), [zones])

  function filter(itemValue: string, search: string) {
    const needle = normalize(search).trim()
    if (!needle) return 1
    return needle.split(/\s+/).every((token) => itemValue.includes(token)) ? 1 : 0
  }

  function pick(zone: string) {
    onChange(zone)
    setOpen(false)
  }

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        if (next) setNow(new Date())
        setOpen(next)
      }}
    >
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className="w-full justify-between font-normal"
        >
          <span className="flex min-w-0 items-center gap-2">
            <IconWorld className="text-muted-foreground size-4 shrink-0" />
            {value ? (
              <span className="truncate">
                ({zoneOffsetLabel(value)}) {value}
                <span className="text-muted-foreground ml-2 text-xs tabular-nums">
                  {zoneTime(value, locale)}
                </span>
              </span>
            ) : (
              <span className="text-muted-foreground">{dict.timezonePicker.placeholder}</span>
            )}
          </span>
          <IconChevronDown className="size-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-(--radix-popover-trigger-width) min-w-80 p-0">
        <Command filter={filter}>
          <CommandInput placeholder={dict.timezonePicker.search} />
          <CommandList>
            <CommandEmpty>{dict.timezonePicker.empty}</CommandEmpty>
            <CommandGroup heading={dict.timezonePicker.popular}>
              {popular.map((zone) => (
                <CommandItem
                  key={`popular-${zone.id}`}
                  value={`popular ${zoneSearchText(zone)}`}
                  onSelect={() => pick(zone.id)}
                >
                  <ZoneRow zone={zone} selected={zone.id === value} locale={locale} now={now} />
                </CommandItem>
              ))}
            </CommandGroup>
            {regions.map(([region, list]) => (
              <CommandGroup key={region} heading={region}>
                {list.map((zone) => (
                  <CommandItem
                    key={zone.id}
                    value={zoneSearchText(zone)}
                    onSelect={() => pick(zone.id)}
                  >
                    <ZoneRow zone={zone} selected={zone.id === value} locale={locale} now={now} />
                  </CommandItem>
                ))}
              </CommandGroup>
            ))}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
