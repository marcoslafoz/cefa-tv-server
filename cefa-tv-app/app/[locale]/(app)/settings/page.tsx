'use client'

import Link from 'next/link'
import { IconUserCircle } from '@tabler/icons-react'
import { PageHeader } from '@/components/shared/page-header'
import { ErrorState, PageSkeleton } from '@/components/shared/state-views'
import { Badge } from '@/components/shadcn-ui/badge'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useApiResource } from '@/hooks/use-api-resource'
import { useLocalePath } from '@/hooks/use-locale-path'
import { API_V1 } from '@/lib/api-client'
import type { SystemInfo } from '@/types/api'

export default function SettingsPage() {
  const dict = useDictionary()
  const { user } = useAuth()
  const localePath = useLocalePath()
  const info = useApiResource<SystemInfo>(`${API_V1}/public/info`)

  if (info.loading && !info.data) return <PageSkeleton rows={2} />
  if (info.error || !info.data) return <ErrorState onRetry={() => void info.reload()} />

  const rows: Array<[string, string]> = [
    [dict.settings.server, info.data.name],
    [dict.settings.version, info.data.version],
    [dict.settings.service, info.data.service],
    [dict.settings.user, user?.displayName || user?.username || ''],
    [
      dict.settings.groups,
      user && user.groups.length > 0 ? user.groups.map((group) => group.name).join(', ') : '-',
    ],
  ]
  const permissions = user?.admin ? [] : (user?.permissions ?? [])

  return (
    <div className="space-y-5">
      <PageHeader
        title={dict.settings.title}
        description={dict.settings.description}
        actions={
          <Button asChild variant="outline">
            <Link href={localePath('/settings/account')}>
              <IconUserCircle /> {dict.settings.myAccount}
            </Link>
          </Button>
        }
      />
      <div className="grid max-w-4xl gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{dict.settings.about}</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="divide-y">
              {rows.map(([label, value]) => (
                <div key={label} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                  <dt className="text-muted-foreground">{label}</dt>
                  <dd className="font-medium">{value}</dd>
                </div>
              ))}
            </dl>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{dict.settings.permissions}</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-1.5">
            {user?.admin ? (
              <Badge>{dict.userGroups.allAccess}</Badge>
            ) : permissions.length === 0 ? (
              <p className="text-muted-foreground text-sm">{dict.settings.noPermissions}</p>
            ) : (
              permissions.map((permission) => (
                <Badge key={permission} variant="secondary">
                  {dict.access.permissions[permission].label}
                </Badge>
              ))
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
