'use client'

import { Suspense, useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import {
  IconChevronLeft,
  IconChevronRight,
  IconDownload,
  IconFilterOff,
  IconHistory,
  IconSearch,
  IconSettings,
} from '@tabler/icons-react'
import { BarList, DayBars } from '@/components/audit/bar-charts'
import { OUTCOMES, actionLabel, entityLabel } from '@/components/audit/audit-labels'
import { PageHeader } from '@/components/shared/page-header'
import { RequirePermission } from '@/components/shared/require-permission'
import { EmptyState, ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { DateRangePicker } from '@/components/shared/date-picker'
import { Input } from '@/components/shadcn-ui/input'
import { Label } from '@/components/shadcn-ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/shadcn-ui/select'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/shadcn-ui/sheet'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/shadcn-ui/table'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useDateFormatter } from '@/hooks/use-date-formatter'
import { useFormat } from '@/hooks/use-format'
import { useLocalePath } from '@/hooks/use-locale-path'
import { api, asList, auditQuery } from '@/lib/api'
import { API_V1 } from '@/lib/api-client'
import { hasPermission } from '@/lib/permissions'
import { toIsoDate } from '@/lib/utils'
import type {
  AppUser,
  AuditAction,
  AuditChange,
  AuditEvent,
  AuditFilters,
  AuditOutcome,
  AuditStats,
  Page,
} from '@/types/api'

const PAGE_SIZE = 25
const STATS_DAYS = 30
const ALL = 'all'

function resultVariant(result: AuditOutcome) {
  if (result === 'SUCCESS') return 'bg-success/15 text-success'
  if (result === 'DENIED') return 'bg-warning/15 text-warning'
  return 'bg-destructive/10 text-destructive'
}

function filtersFromParams(params: URLSearchParams): AuditFilters {
  const outcome = params.get('outcome')
  return {
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
    userId: params.get('userId') ?? undefined,
    action: params.get('action') ?? undefined,
    entityType: params.get('entityType') ?? undefined,
    entityId: params.get('entityId') ?? undefined,
    outcome:
      outcome === 'SUCCESS' || outcome === 'FAILED' || outcome === 'DENIED' ? outcome : undefined,
    q: params.get('q') ?? undefined,
  }
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '-'
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

function EventDetail({ event, onClose }: { event: AuditEvent | null; onClose: () => void }) {
  const dict = useDictionary()
  const { formatDateTime } = useDateFormatter()
  const detail = useApiResource<AuditEvent>(event ? `${API_V1}/audit/${event.id}` : null)
  const shown = detail.data && event && detail.data.id === event.id ? detail.data : event
  const rawChanges = shown?.details?.changes
  const changes: AuditChange[] = Array.isArray(rawChanges) ? (rawChanges as AuditChange[]) : []
  const details = shown?.details
    ? Object.entries(shown.details).filter(([key]) => key !== 'changes')
    : []

  return (
    <Sheet open={event !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-lg">
        {shown ? (
          <>
            <SheetHeader>
              <SheetTitle>{actionLabel(dict, shown.action)}</SheetTitle>
              <SheetDescription>{formatDateTime(shown.at)}</SheetDescription>
            </SheetHeader>
            <div className="space-y-5 px-4 pb-6">
              <dl className="divide-y rounded-xl border text-sm">
                {(
                  [
                    [dict.audit.columns.user, shown.username],
                    [
                      dict.audit.columns.entity,
                      `${entityLabel(dict, shown.entityType)}${shown.entityName ? ` · ${shown.entityName}` : ''}`,
                    ],
                    [dict.audit.columns.ip, shown.ip ?? '-'],
                    [dict.audit.detail.code, shown.action],
                  ] as Array<[string, string]>
                ).map(([label, value]) => (
                  <div key={label} className="flex justify-between gap-3 px-3 py-2">
                    <dt className="text-muted-foreground">{label}</dt>
                    <dd className="text-right font-medium break-all">{value}</dd>
                  </div>
                ))}
                <div className="flex items-center justify-between gap-3 px-3 py-2">
                  <dt className="text-muted-foreground">{dict.audit.columns.result}</dt>
                  <dd>
                    <Badge variant="secondary" className={resultVariant(shown.outcome)}>
                      {dict.audit.results[shown.outcome]}
                    </Badge>
                  </dd>
                </div>
              </dl>

              {changes.length > 0 ? (
                <section className="space-y-2">
                  <h3 className="text-sm font-medium">{dict.audit.detail.changes}</h3>
                  <ul className="divide-y rounded-xl border text-sm">
                    {changes.map((change) => (
                      <li key={change.field} className="space-y-1 px-3 py-2">
                        <p className="text-muted-foreground text-xs">{change.field}</p>
                        <p className="flex flex-wrap items-center gap-2">
                          <span className="bg-destructive/10 text-destructive rounded px-1.5 py-0.5 line-through">
                            {formatValue(change.before)}
                          </span>
                          <span aria-hidden>→</span>
                          <span className="bg-success/15 text-success rounded px-1.5 py-0.5">
                            {formatValue(change.after)}
                          </span>
                        </p>
                      </li>
                    ))}
                  </ul>
                </section>
              ) : null}

              {details.length > 0 ? (
                <section className="space-y-2">
                  <h3 className="text-sm font-medium">{dict.audit.detail.details}</h3>
                  <dl className="divide-y rounded-xl border text-sm">
                    {details.map(([key, value]) => (
                      <div key={key} className="flex justify-between gap-3 px-3 py-2">
                        <dt className="text-muted-foreground">{key}</dt>
                        <dd className="text-right font-mono text-xs break-all">
                          {formatValue(value)}
                        </dd>
                      </div>
                    ))}
                  </dl>
                </section>
              ) : null}
            </div>
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  )
}

function AuditContent() {
  const dict = useDictionary()
  const fmt = useFormat()
  const { user } = useAuth()
  const localePath = useLocalePath()
  const searchParams = useSearchParams()
  const { formatDateTime, locale } = useDateFormatter()
  const [filters, setFilters] = useState<AuditFilters>(() => filtersFromParams(searchParams))
  const [search, setSearch] = useState(filters.q ?? '')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<AuditEvent | null>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setFilters((current) =>
        (current.q ?? '') === search ? current : { ...current, q: search || undefined }
      )
      setPage(0)
    }, 350)
    return () => window.clearTimeout(timer)
  }, [search])

  const list = useApiResource<Page<AuditEvent>>(
    `${API_V1}/audit?${auditQuery(filters, { page, size: PAGE_SIZE })}`,
    { intervalMs: 15000 }
  )
  const defaultFrom = useMemo(() => {
    const date = new Date()
    date.setDate(date.getDate() - STATS_DAYS)
    return toIsoDate(date)
  }, [])
  const stats = useApiResource<AuditStats>(
    `${API_V1}/audit/stats?${auditQuery({ ...filters, from: filters.from ?? defaultFrom })}`
  )
  const actions = useApiResource<AuditAction[]>(`${API_V1}/audit/actions`)
  const entityTypes = Array.from(
    new Set((actions.data ?? []).flatMap((entry) => (entry.entityType ? [entry.entityType] : [])))
  )
  const canListUsers = hasPermission(user, 'USER_MANAGE')
  const users = useApiResource<AppUser[] | Page<AppUser>>(canListUsers ? `${API_V1}/users` : null)

  function patch(update: Partial<AuditFilters>) {
    setFilters((current) => ({ ...current, ...update }))
    setPage(0)
  }

  function clear() {
    setFilters({})
    setSearch('')
    setPage(0)
  }

  const activeFilters = Object.values(filters).filter(Boolean).length
  const total = list.data?.totalElements ?? 0
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const rows = asList(list.data)
  const userList = asList(users.data)
  const formatDay = (date: string) =>
    new Intl.DateTimeFormat(locale, { day: '2-digit', month: 'short' }).format(
      new Date(`${date}T00:00:00`)
    )
  const entityScoped = filters.entityType && filters.entityId

  if (list.loading && !list.data) return <PageSkeleton rows={3} />
  if (list.error && !list.data) return <ErrorState onRetry={() => void list.reload()} />

  return (
    <div className="space-y-5">
      <PageHeader
        title={dict.audit.title}
        description={dict.audit.description}
        actions={
          <>
            <Button asChild variant="outline">
              <a href={api.audit.exportUrl(filters)} download>
                <IconDownload /> {dict.audit.export}
              </a>
            </Button>
            <Button asChild variant="outline">
              <Link href={localePath('/audit/settings')}>
                <IconSettings /> {dict.audit.settings.link}
              </Link>
            </Button>
          </>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle>{dict.audit.charts.topActions}</CardTitle>
          </CardHeader>
          <CardContent>
            <BarList
              emptyLabel={dict.audit.charts.empty}
              items={(stats.data?.topActions ?? []).slice(0, 6).map((entry) => ({
                key: entry.action,
                label: actionLabel(dict, entry.action),
                value: entry.total,
              }))}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{dict.audit.charts.byUser}</CardTitle>
          </CardHeader>
          <CardContent>
            <BarList
              emptyLabel={dict.audit.charts.empty}
              items={(stats.data?.topUsers ?? []).slice(0, 6).map((entry) => ({
                key: entry.username,
                label: entry.username,
                value: entry.total,
              }))}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{dict.audit.charts.byDay}</CardTitle>
          </CardHeader>
          <CardContent>
            <DayBars
              days={(stats.data?.byDay ?? []).map((entry) => ({
                date: entry.day,
                count: entry.total,
              }))}
              emptyLabel={dict.audit.charts.empty}
              format={formatDay}
            />
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardContent className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-6">
            <div className="space-y-1.5 xl:col-span-2">
              <Label htmlFor="audit-search">{dict.audit.filters.search}</Label>
              <div className="relative">
                <IconSearch className="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
                <Input
                  id="audit-search"
                  className="pl-8"
                  placeholder={dict.audit.filters.searchPlaceholder}
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="audit-range">{dict.audit.filters.range}</Label>
              <DateRangePicker
                id="audit-range"
                start={filters.from ?? ''}
                end={filters.to ?? ''}
                onChange={(range) =>
                  patch({ from: range.start || undefined, to: range.end || undefined })
                }
              />
            </div>
            {canListUsers ? (
              <div className="space-y-1.5">
                <Label>{dict.audit.filters.user}</Label>
                <Select
                  value={filters.userId ?? ALL}
                  onValueChange={(value) => patch({ userId: value === ALL ? undefined : value })}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={ALL}>{dict.audit.filters.anyone}</SelectItem>
                    {userList.map((entry) => (
                      <SelectItem key={entry.id} value={entry.id}>
                        {entry.displayName || entry.username}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            ) : null}
            <div className="space-y-1.5">
              <Label>{dict.audit.filters.action}</Label>
              <Select
                value={filters.action ?? ALL}
                onValueChange={(value) => patch({ action: value === ALL ? undefined : value })}
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>{dict.audit.filters.anyAction}</SelectItem>
                  {(actions.data ?? []).map(({ action: code }) => (
                    <SelectItem key={code} value={code}>
                      {actionLabel(dict, code)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>{dict.audit.filters.entity}</Label>
              <Select
                value={filters.entityType ?? ALL}
                onValueChange={(value) =>
                  patch({
                    entityType: value === ALL ? undefined : value,
                    entityId: value === ALL ? undefined : filters.entityId,
                  })
                }
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>{dict.audit.filters.anyEntity}</SelectItem>
                  {entityTypes.map((type) => (
                    <SelectItem key={type} value={type}>
                      {entityLabel(dict, type)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>{dict.audit.filters.result}</Label>
              <Select
                value={filters.outcome || ALL}
                onValueChange={(value) =>
                  patch({ outcome: value === ALL ? undefined : (value as AuditOutcome) })
                }
              >
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>{dict.audit.filters.anyResult}</SelectItem>
                  {OUTCOMES.map((result) => (
                    <SelectItem key={result} value={result}>
                      {dict.audit.results[result]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          {activeFilters > 0 ? (
            <div className="flex flex-wrap items-center gap-2">
              {entityScoped ? (
                <Badge variant="secondary">
                  {dict.audit.filters.scoped.replace(
                    '{entity}',
                    `${entityLabel(dict, filters.entityType ?? null)} ${filters.entityId}`
                  )}
                </Badge>
              ) : null}
              <Button size="sm" variant="ghost" onClick={clear}>
                <IconFilterOff /> {dict.audit.filters.clear}
              </Button>
            </div>
          ) : null}
        </CardContent>
      </Card>

      {rows.length === 0 ? (
        <EmptyState
          icon={IconHistory}
          title={dict.audit.emptyTitle}
          description={dict.audit.emptyDescription}
        />
      ) : (
        <Card className="py-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{dict.audit.columns.date}</TableHead>
                <TableHead>{dict.audit.columns.user}</TableHead>
                <TableHead>{dict.audit.columns.action}</TableHead>
                <TableHead>{dict.audit.columns.entity}</TableHead>
                <TableHead>{dict.audit.columns.result}</TableHead>
                <TableHead>{dict.audit.columns.ip}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((event) => (
                <TableRow
                  key={event.id}
                  tabIndex={0}
                  className="cursor-pointer"
                  onClick={() => setSelected(event)}
                  onKeyDown={(keyboardEvent) => {
                    if (keyboardEvent.key === 'Enter') setSelected(event)
                  }}
                >
                  <TableCell className="whitespace-nowrap tabular-nums">
                    {formatDateTime(event.at)}
                  </TableCell>
                  <TableCell className="font-medium">{event.username}</TableCell>
                  <TableCell>{actionLabel(dict, event.action)}</TableCell>
                  <TableCell>
                    <span className="text-muted-foreground">
                      {entityLabel(dict, event.entityType)}
                    </span>
                    {event.entityName ? ` · ${event.entityName}` : ''}
                  </TableCell>
                  <TableCell>
                    <Badge variant="secondary" className={resultVariant(event.outcome)}>
                      {dict.audit.results[event.outcome]}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground tabular-nums">
                    {event.ip ?? '-'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <div className="flex items-center justify-between border-t px-4 py-3 text-sm">
            <span className="text-muted-foreground tabular-nums">
              {fmt(dict.audit.pagination.summary, { total, page: page + 1, pages })}
            </span>
            <div className="flex gap-1">
              <Button
                size="icon-sm"
                variant="outline"
                aria-label={dict.audit.pagination.previous}
                disabled={page === 0}
                onClick={() => setPage((value) => value - 1)}
              >
                <IconChevronLeft />
              </Button>
              <Button
                size="icon-sm"
                variant="outline"
                aria-label={dict.audit.pagination.next}
                disabled={page + 1 >= pages}
                onClick={() => setPage((value) => value + 1)}
              >
                <IconChevronRight />
              </Button>
            </div>
          </div>
        </Card>
      )}

      <EventDetail event={selected} onClose={() => setSelected(null)} />
    </div>
  )
}

export default function AuditPage() {
  return (
    <RequirePermission permission="AUDIT_VIEW">
      <Suspense fallback={<PageSkeleton rows={3} />}>
        <AuditContent />
      </Suspense>
    </RequirePermission>
  )
}
