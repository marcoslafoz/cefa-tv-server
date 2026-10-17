'use client'

import { useMemo, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { ToggleGroup, ToggleGroupItem } from '@/components/shadcn-ui/toggle-group'
import { LineChart, type ChartPoint } from '@/components/players/line-chart'
import { formatMegabytes } from '@/components/players/format'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { API_V1 } from '@/lib/api-client'
import { currentIntlLocale, formatDecimal } from '@/lib/utils'
import type { PlayerMetric, PlayerSystem } from '@/types/api'

const RANGES = [1, 6, 24] as const

export function MetricsCharts({
  playerId,
  system,
}: {
  playerId: string
  system: PlayerSystem | null | undefined
}) {
  const dict = useDictionary()
  const text = dict.players.metrics
  const [hours, setHours] = useState<number>(1)
  const metrics = useApiResource<PlayerMetric[]>(
    `${API_V1}/players/${playerId}/metrics?hours=${hours}`,
    {
      intervalMs: 15000,
    }
  )

  const formatTime = useMemo(() => {
    const options: Intl.DateTimeFormatOptions =
      hours > 6
        ? { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }
        : { hour: '2-digit', minute: '2-digit' }
    return (at: number) => new Date(at).toLocaleString(currentIntlLocale(), options)
  }, [hours])

  const series = useMemo(() => {
    const rows = [...(metrics.data ?? [])].sort((a, b) => a.at.localeCompare(b.at))
    const build = (pick: (row: PlayerMetric) => number | null): ChartPoint[] =>
      rows.map((row) => ({ at: new Date(row.at).getTime(), value: pick(row) }))
    const ramTotal = system?.ramTotalMb ?? null
    return {
      cpu: build((row) => row.cpuPercent),
      ram: build((row) =>
        row.ramUsedMb !== null && ramTotal ? (row.ramUsedMb / ramTotal) * 100 : null
      ),
      disk: build((row) => row.diskFreeMb),
      temp: build((row) => row.tempC),
    }
  }, [metrics.data, system?.ramTotalMb])

  const percent = (value: number) => `${Math.round(value)}%`

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center justify-between gap-2">
          {text.title}
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            value={String(hours)}
            onValueChange={(value) => value && setHours(Number(value))}
          >
            {RANGES.map((range) => (
              <ToggleGroupItem
                key={range}
                value={String(range)}
                className="data-[state=on]:bg-primary data-[state=on]:text-primary-foreground"
              >
                {range} h
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
        </CardTitle>
      </CardHeader>
      <CardContent className="grid gap-6 md:grid-cols-2">
        <LineChart
          title={text.cpu}
          points={series.cpu}
          domain={[0, 100]}
          format={percent}
          formatTime={formatTime}
          emptyLabel={text.empty}
        />
        <LineChart
          title={text.ram}
          points={series.ram}
          domain={[0, 100]}
          format={percent}
          formatTime={formatTime}
          emptyLabel={text.empty}
        />
        <LineChart
          title={text.diskFree}
          points={series.disk}
          format={formatMegabytes}
          formatTime={formatTime}
          emptyLabel={text.empty}
        />
        <LineChart
          title={text.temperature}
          points={series.temp}
          format={(value) => `${formatDecimal(value)} °C`}
          formatTime={formatTime}
          emptyLabel={text.noSensor}
        />
      </CardContent>
    </Card>
  )
}
