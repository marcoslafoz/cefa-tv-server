'use client'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/shadcn-ui/tooltip'
import Link from 'next/link'
import { IconArrowLeft } from '@tabler/icons-react'
import { ChangePasswordForm } from '@/components/account/change-password-form'
import { PageHeader } from '@/components/shared/page-header'
import { Button } from '@/components/shadcn-ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/shadcn-ui/card'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { useLocalePath } from '@/hooks/use-locale-path'

export default function AccountPage() {
  const dict = useDictionary()
  const { user } = useAuth()
  const localePath = useLocalePath()
  if (!user) return null

  const rows: Array<[string, string]> = [
    [dict.settings.user, user.displayName || user.username],
    [dict.users.username, user.username],
    [dict.users.email, user.email ?? '-'],
    [
      dict.settings.groups,
      user.groups.length > 0
        ? user.groups.map((group) => group.name).join(', ')
        : dict.users.noGroups,
    ],
    [dict.users.authProvider, dict.users.providers[user.authProvider ?? 'LOCAL']],
  ]
  const microsoftOnly = user.authProvider === 'MICROSOFT'

  return (
    <div className="space-y-5">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link href={localePath('/settings')}>
          <IconArrowLeft /> {dict.account.back}
        </Link>
      </Button>
      <PageHeader title={dict.account.title} description={dict.account.description} />
      <div className="grid max-w-4xl gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{dict.account.profile}</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="divide-y">
              {rows.map(([label, value]) => (
                <div key={label} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                  <dt className="text-muted-foreground">{label}</dt>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <dd className="min-w-0 truncate font-medium">{value}</dd>
                    </TooltipTrigger>
                    <TooltipContent>{value}</TooltipContent>
                  </Tooltip>
                </div>
              ))}
            </dl>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{dict.access.changePassword.title}</CardTitle>
          </CardHeader>
          <CardContent>
            {microsoftOnly ? (
              <p className="text-muted-foreground text-sm">{dict.account.microsoftManaged}</p>
            ) : (
              <ChangePasswordForm />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
