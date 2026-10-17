'use client'

import { Badge } from '@/components/shadcn-ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useDictionary } from '@/contexts/dictionary-context'
import {
  formatMegabytes,
  formatPercent,
  formatUptime,
  usedPercent,
} from '@/components/players/format'
import { cn, formatDecimal } from '@/lib/utils'
import type { PlayerSystem } from '@/types/api'

function Meter({
  label,
  percent,
  detail,
}: {
  label: string
  percent: number | null
  detail: string
}) {
  const tone =
    percent === null
      ? 'bg-muted-foreground/30'
      : percent >= 90
        ? 'bg-destructive'
        : percent >= 75
          ? 'bg-warning'
          : 'bg-primary'
  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-2 text-sm">
        <span className="text-muted-foreground">{label}</span>
        <span className="font-medium tabular-nums">{detail}</span>
      </div>
      <div
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent ?? undefined}
        className="bg-muted h-2 overflow-hidden rounded-full"
      >
        <div
          className={cn('h-full rounded-full transition-[width] duration-300', tone)}
          style={{ width: `${percent ?? 0}%` }}
        />
      </div>
    </div>
  )
}

export function SystemPanel({ system }: { system: PlayerSystem | null | undefined }) {
  const dict = useDictionary()
  const text = dict.players.system

  if (!system) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{text.title}</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground py-4 text-center text-sm">{text.empty}</p>
        </CardContent>
      </Card>
    )
  }

  const ramPercent = usedPercent(system.ramUsedMb, system.ramTotalMb)
  const diskUsed =
    system.diskTotalMb !== null && system.diskFreeMb !== null
      ? system.diskTotalMb - system.diskFreeMb
      : null
  const diskPercent = usedPercent(diskUsed, system.diskTotalMb)
  const tempPercent = system.tempC === null ? null : Math.min(100, (system.tempC / 90) * 100)

  const rows: Array<[string, string]> = [
    [text.os, [system.os, system.kernel].filter(Boolean).join(' / ') || '-'],
    [
      text.cpu,
      system.cpuModel
        ? `${system.cpuModel}${system.cpuCount ? ` (${system.cpuCount})` : ''}`
        : (system.arch ?? '-'),
    ],
    [text.uptime, formatUptime(system.uptimeSec, text.uptimeFormat)],
    [text.ips, system.ips?.join(', ') || '-'],
    [text.macs, system.macs?.join(', ') || '-'],
    [text.displayMode, system.displayMode ?? '-'],
    [text.playerVersion, system.playerVersion ?? '-'],
    [text.javaVersion, system.javaVersion ?? '-'],
    [text.chromiumVersion, system.chromiumVersion ?? '-'],
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between gap-2">
          {text.title}
          {system.shellEnabled === false ? (
            <Badge variant="outline">{text.shellDisabled}</Badge>
          ) : system.shellMode ? (
            <Badge variant="secondary">
              {text.shell}: {system.shellMode}
            </Badge>
          ) : null}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="space-y-3">
          <Meter
            label={text.cpuUsage}
            percent={system.cpuPercent}
            detail={`${formatPercent(system.cpuPercent)}${
              system.load1 !== null ? ` · ${text.load} ${formatDecimal(system.load1, 2)}` : ''
            }`}
          />
          <Meter
            label={text.ram}
            percent={ramPercent}
            detail={`${formatMegabytes(system.ramUsedMb)} / ${formatMegabytes(system.ramTotalMb)}`}
          />
          <Meter
            label={text.disk}
            percent={diskPercent}
            detail={`${formatMegabytes(diskUsed)} / ${formatMegabytes(system.diskTotalMb)}`}
          />
          <Meter
            label={text.temperature}
            percent={tempPercent}
            detail={system.tempC === null ? text.noSensor : `${formatDecimal(system.tempC)} °C`}
          />
        </div>
        <dl className="divide-y text-sm">
          {rows.map(([label, value]) => (
            <div key={label} className="flex justify-between gap-3 py-2">
              <dt className="text-muted-foreground shrink-0">{label}</dt>
              <dd className="text-right font-medium break-all">{value}</dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  )
}
