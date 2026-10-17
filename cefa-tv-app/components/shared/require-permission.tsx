'use client'

import { IconLock } from '@tabler/icons-react'
import { EmptyState } from '@/components/shared/state-views'
import { useAuth } from '@/contexts/auth-context'
import { useDictionary } from '@/contexts/dictionary-context'
import { hasPermission } from '@/lib/permissions'
import type { Permission } from '@/types/api'

export function RequirePermission({
  permission,
  children,
}: {
  permission: Permission
  children: React.ReactNode
}) {
  const dict = useDictionary()
  const { user } = useAuth()
  if (hasPermission(user, permission)) return <>{children}</>
  return (
    <EmptyState
      icon={IconLock}
      title={dict.access.noAccessTitle}
      description={dict.access.noAccessDescription}
    />
  )
}
