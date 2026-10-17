'use client'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import { cn } from '@/lib/utils'

export function BarList({
  items,
  emptyLabel,
  className,
}: {
  items: Array<{ key: string; label: string; value: number }>
  emptyLabel: string
  className?: string
}) {
  const max = Math.max(1, ...items.map((item) => item.value))
  if (items.length === 0) {
    return <p className="text-muted-foreground py-6 text-center text-sm">{emptyLabel}</p>
  }
  return (
    <ul className={cn('space-y-2', className)}>
      {items.map((item) => (
        <li key={item.key} className="space-y-1">
          <div className="flex items-baseline justify-between gap-3 text-xs">
            <span className="truncate font-medium">{item.label}</span>
            <span className="text-muted-foreground tabular-nums">{item.value}</span>
          </div>
          <div className="bg-muted h-2 overflow-hidden rounded-full">
            <div
              className="bg-chart-1 h-full rounded-full"
              style={{ width: `${(item.value / max) * 100}%` }}
            />
          </div>
        </li>
      ))}
    </ul>
  )
}

export function DayBars({
  days,
  emptyLabel,
  format,
}: {
  days: Array<{ date: string; count: number }>
  emptyLabel: string
  format: (date: string) => string
}) {
  const max = Math.max(1, ...days.map((day) => day.count))
  if (days.length === 0) {
    return <p className="text-muted-foreground py-6 text-center text-sm">{emptyLabel}</p>
  }
  return (
    <div>
      <div className="flex h-32 items-end gap-1" role="img" aria-label={emptyLabel}>
        {days.map((day) => (
          <Tooltip key={day.date}>
            <TooltipTrigger asChild>
              <div
                className="bg-chart-1 hover:bg-chart-1/80 min-h-0.5 flex-1 rounded-t transition-colors"
                style={{ height: `${(day.count / max) * 100}%` }}
              />
            </TooltipTrigger>
            <TooltipContent>{`${format(day.date)}: ${day.count}`}</TooltipContent>
          </Tooltip>
        ))}
      </div>
      <div className="text-muted-foreground mt-1 flex justify-between text-[10px] tabular-nums">
        <span>{format(days[0].date)}</span>
        <span>{format(days[days.length - 1].date)}</span>
      </div>
    </div>
  )
}
