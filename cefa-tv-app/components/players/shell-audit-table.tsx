'use client'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/shadcn-ui/table'
import { formatBytes } from '@/components/players/format'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { asList } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import type { Page, ShellAudit } from '@/types/api'

export function ShellAuditTable({ playerId }: { playerId: string }) {
  const dict = useDictionary()
  const text = dict.players.shellAudit
  const { formatDateTime } = useDateFormatter()
  const audits = useApiResource<ShellAudit[] | Page<ShellAudit>>(
    `${API_V1}/players/${playerId}/shell-audits`,
    { intervalMs: 15000 }
  )
  const rows = asList(audits.data)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{text.title}</CardTitle>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <p className="text-muted-foreground py-4 text-center text-sm">{text.empty}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{text.user}</TableHead>
                <TableHead>{text.opened}</TableHead>
                <TableHead>{text.closed}</TableHead>
                <TableHead className="text-right">{text.input}</TableHead>
                <TableHead className="text-right">{text.output}</TableHead>
                <TableHead>{text.reason}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((audit) => (
                <TableRow key={audit.id}>
                  <TableCell className="font-medium">{audit.username}</TableCell>
                  <TableCell>{formatDateTime(audit.openedAt)}</TableCell>
                  <TableCell>
                    {audit.closedAt ? formatDateTime(audit.closedAt) : text.active}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatBytes(audit.bytesIn)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatBytes(audit.bytesOut)}
                  </TableCell>
                  <TableCell>{audit.closeReason ?? '-'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}
