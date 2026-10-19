'use client'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useDictionary } from '@/contexts/dictionary-context'
import { formatBytes } from '@/lib/utils'
import type { AracastHardware } from '@/types/api'

export function AracastHardwarePanel({
  hardware,
}: {
  hardware: AracastHardware | null | undefined
}) {
  const dict = useDictionary()
  const text = dict.aracast

  const disk =
    hardware?.diskTotalBytes != null && hardware.diskFreeBytes != null
      ? text.fields.diskFree
          .replace('{free}', formatBytes(hardware.diskFreeBytes))
          .replace('{total}', formatBytes(hardware.diskTotalBytes))
      : '-'

  const rows: Array<[string, string]> = [
    [text.fields.model, hardware?.model ?? '-'],
    [text.fields.mac, hardware?.mac ?? '-'],
    [text.fields.uptime, hardware?.uptime ?? '-'],
    [text.fields.disk, disk],
    [text.fields.currentTemplate, hardware?.currentTemplate ?? '-'],
  ]

  return (
    <Card>
      <CardHeader>
        <CardTitle>{text.hardware.title}</CardTitle>
      </CardHeader>
      <CardContent>
        {hardware ? (
          <dl className="divide-y text-sm">
            {rows.map(([label, value]) => (
              <div key={label} className="flex justify-between gap-3 py-2">
                <dt className="text-muted-foreground shrink-0">{label}</dt>
                <dd className="text-right font-medium break-all">{value}</dd>
              </div>
            ))}
          </dl>
        ) : (
          <p className="text-muted-foreground py-4 text-center text-sm">{text.hardware.empty}</p>
        )}
      </CardContent>
    </Card>
  )
}
